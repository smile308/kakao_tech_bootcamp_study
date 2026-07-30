#!/usr/bin/env bash

set -euo pipefail

env_file="${1:-target-study11.env}"
target_comment_count="${2:-100}"

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

if [[ ! "$target_comment_count" =~ ^[0-9]+$ ]]; then
  echo "target comment count must be a non-negative integer" >&2
  exit 1
fi

backend_port="$(sed -n 's/^BACKEND_PORT=//p' "$env_file")"

if [[ -z "$backend_port" ]]; then
  echo "BACKEND_PORT is missing in $env_file" >&2
  exit 1
fi

base_url="${BASE_URL:-http://localhost:${backend_port}}"
response_file="$(mktemp -t bamboo-comment-response.XXXXXX)"

cleanup() {
  rm -f "$response_file"
}

trap cleanup EXIT INT TERM

read_comment_state() {
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
        COUNT(comment.comment_id)
      FROM bamboo_loadtest.post_counters AS counter
      LEFT JOIN bamboo_loadtest.comments AS comment
        ON comment.post_id = counter.post_id
      WHERE counter.post_id = ${POST_ID}
      GROUP BY counter.post_id, counter.reply_count;
    "
}

initial_state="$(read_comment_state)"

if [[ -z "$initial_state" ]]; then
  echo "post counter not found: post_id=$POST_ID" >&2
  exit 1
fi

initial_counter="$(printf '%s\n' "$initial_state" | awk '{print $1}')"
initial_rows="$(printf '%s\n' "$initial_state" | awk '{print $2}')"

echo "Popular post seed started"
echo "post_id=$POST_ID"
echo "base_url=$base_url"
echo "current_comment_counter=$initial_counter"
echo "current_comment_rows=$initial_rows"
echo "target_comment_count=$target_comment_count"

if [[ "$initial_counter" != "$initial_rows" ]]; then
  echo "comment counter and row count do not match" >&2
  exit 1
fi

if (( initial_rows > target_comment_count )); then
  echo "current comments exceed target; existing data will not be deleted" >&2
  exit 1
fi

remaining_count="$(( target_comment_count - initial_rows ))"

for (( offset=1; offset<=remaining_count; offset++ )); do
  comment_number="$(( initial_rows + offset ))"
  http_status="$(
    curl -sS \
      -o "$response_file" \
      -w '%{http_code}' \
      -X POST "${base_url}/posts/${POST_ID}/comments" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "{\"commentContent\":\"인기 게시글 부하테스트 댓글 ${comment_number}\"}"
  )"

  if [[ "$http_status" != "201" ]]; then
    echo "comment creation failed: number=$comment_number http_status=$http_status" >&2
    cat "$response_file" >&2
    exit 1
  fi

  if (( comment_number % 10 == 0 || offset == remaining_count )); then
    echo "created_comments=$comment_number/$target_comment_count"
  fi
done

final_state="$(read_comment_state)"
final_counter="$(printf '%s\n' "$final_state" | awk '{print $1}')"
final_rows="$(printf '%s\n' "$final_state" | awk '{print $2}')"

echo "final_comment_counter=$final_counter"
echo "final_comment_rows=$final_rows"

if [[ "$final_counter" != "$target_comment_count" || "$final_rows" != "$target_comment_count" ]]; then
  echo "popular post seed verification failed" >&2
  exit 1
fi

echo "Popular post seed finished"
