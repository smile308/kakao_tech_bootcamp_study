#!/usr/bin/env bash

set -Eeuo pipefail

DEPLOY_DIR="${DEPLOY_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-${DEPLOY_DIR}/compose.yaml}"
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG is required}"
HEALTH_ATTEMPTS="${HEALTH_ATTEMPTS:-30}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-5}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing environment file: ${ENV_FILE}" >&2
  exit 1
fi

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "Missing Compose file: ${COMPOSE_FILE}" >&2
  exit 1
fi

for command_name in docker curl grep sed flock; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Required command is not installed: ${command_name}" >&2
    exit 1
  fi
done

LOCK_FILE="${DEPLOY_DIR}/.deploy.lock"
exec 9>"${LOCK_FILE}"

if ! flock -n 9; then
  echo "Another backend deployment is already running." >&2
  exit 1
fi

cd "${DEPLOY_DIR}"

get_env_value() {
  local key="$1"
  local default_value="$2"
  local line

  line="$(grep -E "^${key}=" "${ENV_FILE}" | tail -n 1 || true)"

  if [[ -z "${line}" ]]; then
    printf '%s' "${default_value}"
  else
    printf '%s' "${line#*=}"
  fi
}

set_env_value() {
  local key="$1"
  local value="$2"

  if grep -qE "^${key}=" "${ENV_FILE}"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "${ENV_FILE}"
  else
    printf '%s=%s\n' "${key}" "${value}" >> "${ENV_FILE}"
  fi
}

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

wait_for_health() {
  local url="$1"
  local attempt

  for ((attempt = 1; attempt <= HEALTH_ATTEMPTS; attempt += 1)); do
    if curl --fail --silent --show-error --max-time 3 "${url}" >/dev/null; then
      return 0
    fi

    echo "Health check ${attempt}/${HEALTH_ATTEMPTS} failed: ${url}"
    sleep "${HEALTH_INTERVAL_SECONDS}"
  done

  return 1
}

wait_for_redis() {
  local attempt

  for ((attempt = 1; attempt <= HEALTH_ATTEMPTS; attempt += 1)); do
    if [[ "$(compose exec -T redis redis-cli PING 2>/dev/null || true)" == "PONG" ]]; then
      return 0
    fi

    echo "Redis health check ${attempt}/${HEALTH_ATTEMPTS} failed"
    sleep "${HEALTH_INTERVAL_SECONDS}"
  done

  return 1
}

active_color="$(get_env_value BACKEND_ACTIVE_COLOR blue)"

case "${active_color}" in
  blue)
    target_color="green"
    target_tag_key="BACKEND_GREEN_TAG"
    target_port="$(get_env_value BACKEND_GREEN_PORT 8082)"
    ;;
  green)
    target_color="blue"
    target_tag_key="BACKEND_BLUE_TAG"
    target_port="$(get_env_value BACKEND_BLUE_PORT 8081)"
    ;;
  *)
    echo "Invalid BACKEND_ACTIVE_COLOR: ${active_color}" >&2
    exit 1
    ;;
esac

target_service="backend-${target_color}"
previous_service="backend-${active_color}"
previous_target_tag="$(get_env_value "${target_tag_key}" initial)"
nginx_port="$(get_env_value BACKEND_NGINX_PORT 80)"

restore_inactive_container() {
  set_env_value "${target_tag_key}" "${previous_target_tag}"
  compose stop "${target_service}" >/dev/null 2>&1 || true
}

rollback_proxy() {
  set_env_value BACKEND_ACTIVE_COLOR "${active_color}"
  compose up -d --no-deps --force-recreate nginx >/dev/null 2>&1 || true
  restore_inactive_container
}

echo "Active backend: ${active_color}"
echo "Deployment target: ${target_color}"
echo "Image tag: ${IMAGE_TAG}"

if ! compose up -d redis; then
  exit 1
fi

if ! wait_for_redis; then
  compose logs --tail=100 redis || true
  exit 1
fi

set_env_value "${target_tag_key}" "${IMAGE_TAG}"

if ! compose pull "${target_service}"; then
  restore_inactive_container
  exit 1
fi

if ! compose up -d --no-deps "${target_service}"; then
  restore_inactive_container
  exit 1
fi

if ! wait_for_health "http://127.0.0.1:${target_port}/actuator/health"; then
  compose logs --tail=100 "${target_service}" || true
  restore_inactive_container
  exit 1
fi

set_env_value BACKEND_ACTIVE_COLOR "${target_color}"

if ! compose up -d --no-deps --force-recreate nginx; then
  rollback_proxy
  exit 1
fi

if ! wait_for_health "http://127.0.0.1:${nginx_port}/health"; then
  compose logs --tail=100 nginx || true
  rollback_proxy
  exit 1
fi

if ! compose stop "${previous_service}"; then
  echo "Failed to stop previous backend: ${previous_service}" >&2
  rollback_proxy
  exit 1
fi

echo "Backend deployment completed: ${active_color} -> ${target_color}"
