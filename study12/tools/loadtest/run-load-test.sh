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

if [[ -z "$target_version" ]]; then
  echo "TARGET_VERSION is missing in $env_file" >&2
  exit 1
fi

if [[ "$data_variant" == "default" ]]; then
  test_name="$base_test_name"
else
  test_name="${base_test_name}-${data_variant}"
fi

timestamp="$(date '+%Y%m%d-%H%M%S')"
result_directory="results"
result_prefix="${target_version}__${test_name}__${test_profile}__${timestamp}"
k6_console_file="${result_directory}/${result_prefix}__k6-console.txt"
k6_summary_file="${result_prefix}__k6-summary.json"
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

echo "Load test started"
echo "target_version=$target_version"
echo "test_profile=$test_profile"
echo "data_variant=$data_variant"
echo "post_id=$POST_ID"
echo "k6_console_file=$k6_console_file"
echo "k6_summary_file=${result_directory}/${k6_summary_file}"

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

echo
echo "MySQL observer output"
cat "$observer_console_file"

echo
echo "Load test finished"
echo "k6_status=$k6_status"
echo "observer_status=$observer_status"
echo "k6_console_file=$k6_console_file"
echo "k6_summary_file=${result_directory}/${k6_summary_file}"

if (( observer_status != 0 )); then
  exit "$observer_status"
fi

exit "$k6_status"
