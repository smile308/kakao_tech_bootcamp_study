#!/usr/bin/env bash

set -euo pipefail

env_file="${1:-target-study11.env}"
test_profile="${2:-comment-only}"
data_variant="${3:-comments100}"
test_name="post-counter-interference-${data_variant}"
result_set="${RESULT_SET:-}"
run_label="${RUN_LABEL:-}"

if [[ ! -f "$env_file" ]]; then
  echo "env file not found: $env_file" >&2
  exit 1
fi

if [[ -z "${ACCESS_TOKEN:-}" ]]; then
  echo "ACCESS_TOKEN is required" >&2
  exit 1
fi

if [[ -z "${POST_ID:-}" ]]; then
  echo "POST_ID is required" >&2
  exit 1
fi

if [[ ! "$POST_ID" =~ ^[0-9]+$ ]]; then
  echo "POST_ID must be a positive integer" >&2
  exit 1
fi

if [[ ! "$data_variant" =~ ^[a-z0-9-]+$ ]]; then
  echo "data variant must contain only lowercase letters, numbers, and hyphens" >&2
  exit 1
fi

if [[ -n "$result_set" && ! "$result_set" =~ ^[a-z0-9-]+$ ]]; then
  echo "result set must contain only lowercase letters, numbers, and hyphens" >&2
  exit 1
fi

if [[ -n "$run_label" && ! "$run_label" =~ ^[a-z0-9-]+$ ]]; then
  echo "run label must contain only lowercase letters, numbers, and hyphens" >&2
  exit 1
fi

case "$test_profile" in
  comment-smoke)
    observation_seconds=15
    ;;
  comment-only|mixed-normal)
    observation_seconds=75
    ;;
  *)
    echo "unsupported test profile: $test_profile" >&2
    exit 1
    ;;
esac

decode_base64url() {
  local encoded_value="$1"
  local remainder="$(( ${#encoded_value} % 4 ))"

  case "$remainder" in
    2)
      encoded_value="${encoded_value}=="
      ;;
    3)
      encoded_value="${encoded_value}="
      ;;
    1)
      return 1
      ;;
  esac

  printf '%s' "$encoded_value" \
    | tr '_-' '/+' \
    | openssl base64 -d -A
}

jwt_payload="$(printf '%s' "$ACCESS_TOKEN" | cut -d'.' -f2)"

if [[ -z "$jwt_payload" ]]; then
  echo "ACCESS_TOKEN is not a valid JWT" >&2
  exit 1
fi

if ! jwt_payload_json="$(decode_base64url "$jwt_payload")"; then
  echo "failed to decode ACCESS_TOKEN payload" >&2
  exit 1
fi

token_exp_epoch="$(
  printf '%s' "$jwt_payload_json" \
    | sed -nE 's/.*"exp"[[:space:]]*:[[:space:]]*([0-9]+).*/\1/p'
)"

if [[ ! "$token_exp_epoch" =~ ^[0-9]+$ ]]; then
  echo "ACCESS_TOKEN does not contain a numeric exp claim" >&2
  exit 1
fi

current_epoch="$(date '+%s')"
token_remaining_seconds="$(( token_exp_epoch - current_epoch ))"
required_token_seconds="$(( observation_seconds + 30 ))"

if (( token_remaining_seconds < required_token_seconds )); then
  echo "ACCESS_TOKEN expires too soon for this test" >&2
  echo "token_remaining_seconds=$token_remaining_seconds" >&2
  echo "required_token_seconds=$required_token_seconds" >&2
  echo "issue a new access token before running the load test" >&2
  exit 1
fi

target_version="$(sed -n 's/^TARGET_VERSION=//p' "$env_file")"
backend_port="$(sed -n 's/^BACKEND_PORT=//p' "$env_file")"
view_count_storage="$(sed -n 's/^VIEW_COUNT_STORAGE=//p' "$env_file")"
redis_view_count_enabled="$(
  sed -n 's/^REDIS_VIEW_COUNT_ENABLED=//p' "$env_file"
)"

if [[ -z "$target_version" ]]; then
  echo "TARGET_VERSION is missing in $env_file" >&2
  exit 1
fi

if [[ -z "$backend_port" ]]; then
  echo "BACKEND_PORT is missing in $env_file" >&2
  exit 1
fi

if [[ "$view_count_storage" != "post_counters" \
  && "$view_count_storage" != "post_view_counts" ]]; then
  echo "unsupported VIEW_COUNT_STORAGE: $view_count_storage" >&2
  exit 1
fi

if [[ "$redis_view_count_enabled" != "true" \
  && "$redis_view_count_enabled" != "false" ]]; then
  echo "unsupported REDIS_VIEW_COUNT_ENABLED: $redis_view_count_enabled" >&2
  exit 1
fi

read_post_state() {
  local view_join=""
  local view_column="counter.view_count"
  local view_group="counter.view_count"

  if [[ "$view_count_storage" == "post_view_counts" ]]; then
    view_join="
      JOIN bamboo_loadtest.post_view_counts AS view_counter
        ON view_counter.post_id = counter.post_id"
    view_column="view_counter.view_count"
    view_group="view_counter.view_count"
  fi

  docker compose \
    --env-file "$env_file" \
    -f compose.yaml \
    exec -T \
    -e MYSQL_PWD=bamboo-loadtest-root-password \
    mysql \
    mysql \
    -uroot \
    --batch \
    --raw \
    --skip-column-names \
    -e "
      SELECT
        counter.reply_count,
        COUNT(comment.comment_id),
        ${view_column}
      FROM bamboo_loadtest.post_counters AS counter
      ${view_join}
      LEFT JOIN bamboo_loadtest.comments AS comment
        ON comment.post_id = counter.post_id
      WHERE counter.post_id = ${POST_ID}
      GROUP BY counter.post_id, counter.reply_count, ${view_group};
    "
}

read_redis_view_count() {
  if [[ "$redis_view_count_enabled" != "true" ]]; then
    printf '%s\n' "not-applicable"
    return
  fi

  local value
  value="$(
    docker compose \
      --env-file "$env_file" \
      -f compose.yaml \
      exec -T \
      redis \
      redis-cli \
      --raw \
      GET "bamboo:{post-view}:count:${POST_ID}"
  )"

  if [[ -z "$value" ]]; then
    printf '%s\n' "not-present"
  else
    printf '%s\n' "$value"
  fi
}

preflight_status="$(
  curl -sS \
    -o /dev/null \
    -w '%{http_code}' \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "http://localhost:${backend_port}/posts?page=0&size=1"
)"

if [[ "$preflight_status" != "200" ]]; then
  echo "authentication preflight failed: http_status=$preflight_status" >&2
  echo "issue a new access token before running the load test" >&2
  exit 1
fi

initial_post_state="$(read_post_state)"
initial_comment_counter="$(printf '%s\n' "$initial_post_state" | awk '{print $1}')"
initial_comment_rows="$(printf '%s\n' "$initial_post_state" | awk '{print $2}')"
initial_mysql_view_count="$(printf '%s\n' "$initial_post_state" | awk '{print $3}')"
initial_redis_view_count="$(read_redis_view_count)"

initial_effective_view_count="$initial_mysql_view_count"
if [[ "$initial_redis_view_count" =~ ^[0-9]+$ ]]; then
  initial_effective_view_count="$initial_redis_view_count"
fi

if [[ "$initial_comment_counter" != "$initial_comment_rows" ]]; then
  echo "initial comment counter and row count do not match" >&2
  exit 1
fi

timestamp="$(date '+%Y%m%d-%H%M%S')"
run_id="${run_label:-$timestamp}"
result_directory="results"
container_result_directory="/results"

if [[ -n "$result_set" ]]; then
  result_directory="${result_directory}/${result_set}"
  container_result_directory="${container_result_directory}/${result_set}"
fi

result_prefix="${target_version}__${test_name}__${test_profile}__${run_id}"
k6_console_file="${result_directory}/${result_prefix}__k6-console.txt"
k6_summary_file="${result_prefix}__k6-summary.json"
run_metadata_file="${result_directory}/${result_prefix}__run-metadata.txt"
observer_console_file="$(mktemp -t bamboo-counter-observer.XXXXXX)"
observer_pid=""

cleanup() {
  if [[ -n "$observer_pid" ]] && kill -0 "$observer_pid" 2>/dev/null; then
    kill "$observer_pid" 2>/dev/null || true
    wait "$observer_pid" 2>/dev/null || true
  fi

  rm -f "$observer_console_file"
}

trap cleanup EXIT INT TERM

mkdir -p "$result_directory"

{
  echo "Counter interference test started"
  echo "started_at=$(date '+%Y-%m-%dT%H:%M:%S%z')"
  echo "target_version=$target_version"
  echo "test_profile=$test_profile"
  echo "data_variant=$data_variant"
  echo "result_set=${result_set:-default}"
  echo "run_id=$run_id"
  echo "post_id=$POST_ID"
  echo "authentication_preflight=HTTP_200"
  echo "token_exp_epoch=$token_exp_epoch"
  echo "token_remaining_seconds_at_start=$token_remaining_seconds"
  echo "required_token_seconds=$required_token_seconds"
  echo "initial_comment_counter=$initial_comment_counter"
  echo "initial_comment_rows=$initial_comment_rows"
  echo "view_count_storage=$view_count_storage"
  echo "redis_view_count_enabled=$redis_view_count_enabled"
  echo "initial_mysql_view_count=$initial_mysql_view_count"
  echo "initial_redis_view_count=$initial_redis_view_count"
  echo "initial_effective_view_count=$initial_effective_view_count"
  echo "k6_console_file=$k6_console_file"
  echo "k6_summary_file=${result_directory}/${k6_summary_file}"
  echo "mysql_locks_file=${result_directory}/${result_prefix}__mysql-locks.txt"
  echo "run_metadata_file=$run_metadata_file"
} | tee "$run_metadata_file"

bash observe-mysql-locks.sh \
  "$env_file" \
  "${test_name}__${test_profile}" \
  "$observation_seconds" \
  "$run_id" \
  "$result_directory" \
  >"$observer_console_file" 2>&1 &
observer_pid=$!

sleep 2

set +e
docker compose \
  --env-file "$env_file" \
  -f compose.yaml \
  --profile loadtest \
  run --rm \
  -e ACCESS_TOKEN="$ACCESS_TOKEN" \
  -e POST_ID="$POST_ID" \
  -e TEST_PROFILE="$test_profile" \
  -e DATA_VARIANT="$data_variant" \
  k6 run \
  --summary-export="${container_result_directory}/${k6_summary_file}" \
  /scripts/post-counter-interference.js \
  | tee "$k6_console_file"
k6_status="${PIPESTATUS[0]}"

wait "$observer_pid"
observer_status="$?"
set -e

observer_pid=""

final_post_state="$(read_post_state)"
final_comment_counter="$(printf '%s\n' "$final_post_state" | awk '{print $1}')"
final_comment_rows="$(printf '%s\n' "$final_post_state" | awk '{print $2}')"
final_mysql_view_count="$(printf '%s\n' "$final_post_state" | awk '{print $3}')"
final_redis_view_count="$(read_redis_view_count)"

final_effective_view_count="$final_mysql_view_count"
if [[ "$final_redis_view_count" =~ ^[0-9]+$ ]]; then
  final_effective_view_count="$final_redis_view_count"
fi

echo
echo "MySQL observer output"
cat "$observer_console_file"

echo
{
  echo "Counter interference test finished"
  echo "finished_at=$(date '+%Y-%m-%dT%H:%M:%S%z')"
  echo "k6_status=$k6_status"
  echo "observer_status=$observer_status"
  echo "final_comment_counter=$final_comment_counter"
  echo "final_comment_rows=$final_comment_rows"
  echo "final_mysql_view_count=$final_mysql_view_count"
  echo "final_redis_view_count=$final_redis_view_count"
  echo "final_effective_view_count=$final_effective_view_count"
  echo "view_count_delta=$(( final_effective_view_count - initial_effective_view_count ))"
  echo "k6_console_file=$k6_console_file"
  echo "k6_summary_file=${result_directory}/${k6_summary_file}"
  echo "mysql_locks_file=${result_directory}/${result_prefix}__mysql-locks.txt"
  echo "run_metadata_file=$run_metadata_file"
} | tee -a "$run_metadata_file"

if (( observer_status != 0 )); then
  exit "$observer_status"
fi

if [[ "$final_comment_counter" != "$final_comment_rows" ]]; then
  echo "comment counter and row count do not match after test" >&2
  exit 98
fi

if [[ "$final_comment_counter" != "$initial_comment_counter" ]]; then
  echo "comment count changed during create-delete lifecycle test" >&2
  exit 98
fi

exit "$k6_status"
