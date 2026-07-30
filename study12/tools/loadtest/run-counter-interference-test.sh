#!/usr/bin/env bash

set -euo pipefail

env_file="${1:-target-study11.env}"
test_profile="${2:-comment-only}"
data_variant="${3:-comments100}"
test_name="post-counter-interference-${data_variant}"

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

if [[ -z "$target_version" ]]; then
  echo "TARGET_VERSION is missing in $env_file" >&2
  exit 1
fi

if [[ -z "$backend_port" ]]; then
  echo "BACKEND_PORT is missing in $env_file" >&2
  exit 1
fi

read_post_state() {
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
        counter.view_count
      FROM bamboo_loadtest.post_counters AS counter
      LEFT JOIN bamboo_loadtest.comments AS comment
        ON comment.post_id = counter.post_id
      WHERE counter.post_id = ${POST_ID}
      GROUP BY counter.post_id, counter.reply_count, counter.view_count;
    "
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
initial_view_count="$(printf '%s\n' "$initial_post_state" | awk '{print $3}')"

if [[ "$initial_comment_counter" != "$initial_comment_rows" ]]; then
  echo "initial comment counter and row count do not match" >&2
  exit 1
fi

timestamp="$(date '+%Y%m%d-%H%M%S')"
result_directory="results"
result_prefix="${target_version}__${test_name}__${test_profile}__${timestamp}"
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
  echo "post_id=$POST_ID"
  echo "authentication_preflight=HTTP_200"
  echo "token_exp_epoch=$token_exp_epoch"
  echo "token_remaining_seconds_at_start=$token_remaining_seconds"
  echo "required_token_seconds=$required_token_seconds"
  echo "initial_comment_counter=$initial_comment_counter"
  echo "initial_comment_rows=$initial_comment_rows"
  echo "initial_view_count=$initial_view_count"
  echo "k6_console_file=$k6_console_file"
  echo "k6_summary_file=${result_directory}/${k6_summary_file}"
  echo "mysql_locks_file=${result_directory}/${result_prefix}__mysql-locks.txt"
  echo "run_metadata_file=$run_metadata_file"
} | tee "$run_metadata_file"

bash observe-mysql-locks.sh \
  "$env_file" \
  "${test_name}__${test_profile}" \
  "$observation_seconds" \
  "$timestamp" \
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
  --summary-export="/results/${k6_summary_file}" \
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
final_view_count="$(printf '%s\n' "$final_post_state" | awk '{print $3}')"

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
  echo "final_view_count=$final_view_count"
  echo "view_count_delta=$(( final_view_count - initial_view_count ))"
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
