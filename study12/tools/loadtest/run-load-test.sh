#!/usr/bin/env bash

set -euo pipefail

env_file="${1:-target-study11.env}"
test_profile="${2:-baseline}"
data_variant="${3:-default}"
base_test_name="hot-post-view"

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

if [[ ! "$data_variant" =~ ^[a-z0-9-]+$ ]]; then
  echo "data variant must contain only lowercase letters, numbers, and hyphens" >&2
  exit 1
fi

case "$test_profile" in
  smoke)
    observation_seconds=15
    ;;
  baseline)
    observation_seconds=135
    ;;
  capacity)
    observation_seconds=105
    ;;
  *)
    echo "unsupported test profile: $test_profile" >&2
    exit 1
    ;;
esac

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

if [[ "$data_variant" == "default" ]]; then
  test_name="$base_test_name"
else
  test_name="${base_test_name}-${data_variant}"
fi

read_mysql_view_count() {
  local view_table="$view_count_storage"

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
      SELECT view_count
      FROM bamboo_loadtest.${view_table}
      WHERE post_id = ${POST_ID};
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

initial_mysql_view_count="$(read_mysql_view_count)"
initial_redis_view_count="$(read_redis_view_count)"
initial_effective_view_count="$initial_mysql_view_count"

if [[ "$initial_redis_view_count" =~ ^[0-9]+$ ]]; then
  initial_effective_view_count="$initial_redis_view_count"
fi

timestamp="$(date '+%Y%m%d-%H%M%S')"
result_directory="results"
result_prefix="${target_version}__${test_name}__${test_profile}__${timestamp}"
k6_console_file="${result_directory}/${result_prefix}__k6-console.txt"
k6_summary_file="${result_prefix}__k6-summary.json"
run_metadata_file="${result_directory}/${result_prefix}__run-metadata.txt"
observer_console_file="$(mktemp -t bamboo-lock-observer.XXXXXX)"
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
  echo "Load test started"
  echo "started_at=$(date '+%Y-%m-%dT%H:%M:%S%z')"
  echo "target_version=$target_version"
  echo "test_profile=$test_profile"
  echo "data_variant=$data_variant"
  echo "post_id=$POST_ID"
  echo "authentication_preflight=HTTP_200"
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
  /scripts/hot-post-view.js \
  | tee "$k6_console_file"
k6_status="${PIPESTATUS[0]}"

wait "$observer_pid"
observer_status="$?"
set -e

observer_pid=""

final_mysql_view_count="$(read_mysql_view_count)"
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
  echo "Load test finished"
  echo "finished_at=$(date '+%Y-%m-%dT%H:%M:%S%z')"
  echo "k6_status=$k6_status"
  echo "observer_status=$observer_status"
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

exit "$k6_status"
