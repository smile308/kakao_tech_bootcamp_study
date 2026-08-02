#!/usr/bin/env bash

set -euo pipefail

env_file="${1:-target-study11.env}"
test_name="${2:-hot-post-view}"
duration_seconds="${3:-130}"
timestamp="${4:-$(date '+%Y%m%d-%H%M%S')}"
result_directory="${5:-results}"

if [[ ! -f "$env_file" ]]; then
  echo "env file not found: $env_file" >&2
  exit 1
fi

target_version="$(sed -n 's/^TARGET_VERSION=//p' "$env_file")"

if [[ -z "$target_version" ]]; then
  echo "TARGET_VERSION is missing in $env_file" >&2
  exit 1
fi

result_file="${result_directory}/${target_version}__${test_name}__${timestamp}__mysql-locks.txt"
end_epoch="$(( $(date '+%s') + duration_seconds ))"

mkdir -p "$result_directory"

echo "MySQL lock observation started"
echo "result_file=$result_file"
echo "duration_seconds=$duration_seconds"

{
  echo "target_version=$target_version"
  echo "test_name=$test_name"
  echo "started_at=$(date '+%Y-%m-%dT%H:%M:%S%z')"
  echo "duration_seconds=$duration_seconds"

  while (( $(date '+%s') < end_epoch )); do
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
        SELECT CONCAT(
          'metric|',
          DATE_FORMAT(NOW(3), '%Y-%m-%dT%H:%i:%s.%f'),
          '|',
          VARIABLE_NAME,
          '|',
          VARIABLE_VALUE
        )
        FROM performance_schema.global_status
        WHERE VARIABLE_NAME IN (
          'Innodb_row_lock_current_waits',
          'Innodb_row_lock_time',
          'Innodb_row_lock_time_max',
          'Innodb_row_lock_waits'
        )
        ORDER BY VARIABLE_NAME;

        SELECT CONCAT(
          'wait_count|',
          DATE_FORMAT(NOW(3), '%Y-%m-%dT%H:%i:%s.%f'),
          '|',
          COUNT(*)
        )
        FROM performance_schema.data_lock_waits;

        SELECT CONCAT(
          'wait_detail|',
          DATE_FORMAT(NOW(3), '%Y-%m-%dT%H:%i:%s.%f'),
          '|requesting_trx=',
          waits.REQUESTING_ENGINE_TRANSACTION_ID,
          '|blocking_trx=',
          waits.BLOCKING_ENGINE_TRANSACTION_ID,
          '|object=',
          COALESCE(requested.OBJECT_SCHEMA, ''),
          '.',
          COALESCE(requested.OBJECT_NAME, ''),
          '|index=',
          COALESCE(requested.INDEX_NAME, ''),
          '|lock_type=',
          COALESCE(requested.LOCK_TYPE, ''),
          '|lock_mode=',
          COALESCE(requested.LOCK_MODE, '')
        )
        FROM performance_schema.data_lock_waits AS waits
        JOIN performance_schema.data_locks AS requested
          ON requested.ENGINE = waits.ENGINE
         AND requested.ENGINE_LOCK_ID = waits.REQUESTING_ENGINE_LOCK_ID
        WHERE requested.OBJECT_SCHEMA = 'bamboo_loadtest';
      "

    sleep 1
  done

  echo "finished_at=$(date '+%Y-%m-%dT%H:%M:%S%z')"
} | tee "$result_file"

echo "MySQL lock observation finished"
echo "result_file=$result_file"
