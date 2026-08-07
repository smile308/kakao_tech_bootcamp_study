# 19장. Docker·Compose·Nginx·GitHub Actions 배포 전체 흐름

이 장은 애플리케이션 코드를 실행 가능한 이미지로 만들고, backend와 frontend를 blue/green 방식으로 교체하며, GitHub Actions에서 테스트·이미지 배포·AWS SSM 원격 실행까지 연결하는 마지막 장입니다.

## 진행률과 공식 집계

- 18장까지 완료한 공식 파일: 199/213개, 약 93.4%
- 이번 장에서 새로 정독하는 공식 파일: 14개
- 19장 완료 후 공식 진행률: **213/213개, 100.0%**
- 공식 14개: backend 7개 + frontend 7개
- 추가로 확인한 지원 파일: frontend/nginx/default.conf
- frontend/nginx/default.conf는 frontend Dockerfile이 COPY하는 실제 파일이지만, 기존 00번의 공식 213개 목록에는 포함되어 있지 않습니다. 문서에는 역할을 기록하되 공식 진행률에는 중복 집계하지 않습니다.

이번 장은 실제 운영 성공을 증명하는 장이 아닙니다. 저장소에서 확인되는 선언·명령·분기와 실제 AWS/RDS/EC2/GHCR/도메인에서 실행되어야 하는 부분을 분리합니다.

## 19.1 전체 배포 실행 지도

~~~~text
GitHub push 또는 pull request
→ backend workflow 또는 frontend workflow 시작
→ source checkout
→ runtime 설정
→ test/lint/build 검증
→ pull request이면 image build/push와 deploy job을 건너뜀
→ main push이면 Docker image build
→ GHCR 로그인
→ commit SHA와 latest image tag 생성
→ image push
→ DEPLOY_ENABLED=true이면 AWS OIDC로 임시 credentials 획득
→ AWS SSM send-command로 EC2에 배포 script 실행
→ 대상 색상의 inactive container 선택
→ compose가 target image pull/up
→ target health endpoint 반복 확인
→ Nginx upstream을 target 색상으로 변경
→ Nginx health 확인
→ 이전 색상 container stop
~~~~

~~~~text
backend image
→ Dockerfile build stage: JDK 26 + Gradle bootJar
→ runtime stage: JRE 26 + app.jar + spring 사용자
→ Compose backend-blue/backend-green
→ Spring profile prod + RDS/MySQL + Redis 환경변수
→ backend Nginx /health → /actuator/health
→ backend Nginx / → Spring backend:8080
~~~~

~~~~text
frontend image
→ Dockerfile build stage: Node 24 + npm ci + VITE_API_BASE_URL=/api + npm run build
→ runtime stage: Nginx 1.28 + dist + nginx/default.conf
→ Compose frontend-blue/frontend-green
→ frontend edge Nginx가 active frontend로 정적 파일 proxy
→ /api/ 요청은 BACKEND_UPSTREAM으로 proxy
→ /health는 frontend container의 health로 proxy
~~~~

## 19.2 이 장의 읽기 순서

backend:

1. Dockerfile
2. deploy/.env.example
3. deploy/compose.yaml
4. deploy/nginx/backend-blue.conf와 backend-green.conf
5. deploy/deploy-backend.sh
6. .github/workflows/backend-ci.yml

frontend:

7. frontend Dockerfile
8. frontend/deploy/.env.example
9. frontend/nginx/default.conf
10. frontend/deploy/compose.yaml
11. frontend/deploy/nginx/frontend-blue.conf.template와 green template
12. frontend/deploy/deploy-frontend.sh
13. frontend/.github/workflows/frontend-ci.yml

backend에서 처음 설명한 Docker·Compose·Shell·Nginx·GitHub Actions 문법은 frontend에서 반복하지 않고 차이만 확인합니다.

---

## 19.3 backend Dockerfile: JDK 빌드 이미지와 JRE 실행 이미지 분리

### 실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/Dockerfile:1-29

### 코드 원문

~~~~dockerfile
# syntax=docker/dockerfile:1

FROM eclipse-temurin:26-jdk AS build

WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:26-jre AS runtime

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build --chown=spring:spring /workspace/build/libs/*.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
~~~~


### 코드 바로 아래 설명

- syntax directive는 Dockerfile parser가 사용할 문법 버전을 알립니다.
- 첫 FROM은 build stage입니다. eclipse-temurin:26-jdk에는 Java 컴파일과 Gradle bootJar 생성에 필요한 JDK가 있습니다.
- AS build는 이 stage에 이름을 붙입니다. 뒤의 COPY from build가 이 stage의 결과를 가져옵니다.
- WORKDIR /workspace는 이후 RUN과 COPY의 작업 디렉터리를 고정합니다.
- gradlew, gradle, build.gradle, settings.gradle을 먼저 복사하고 dependencies를 실행합니다. 의존성 입력과 source 입력을 분리해 Docker layer cache를 활용합니다.
- chmod +x는 Gradle wrapper를 실행 가능하게 만들고 dependencies --no-daemon은 dependency cache를 준비합니다.
- COPY src src 뒤 clean bootJar가 실행 가능한 Spring Boot jar를 build/libs에 만듭니다.
- 두 번째 FROM은 runtime stage입니다. JDK가 아니라 JRE를 사용해 실행 image에 컴파일 도구를 남기지 않습니다.
- groupadd/useradd와 USER spring:spring은 root가 아닌 사용자로 Java process를 실행하게 합니다.
- COPY from build와 chown은 build stage의 jar를 runtime image에 spring 소유자로 복사합니다.
- EXPOSE 8080은 포트 metadata이며 host 공개를 단독으로 결정하지 않습니다. host binding은 Compose가 담당합니다.
- ENTRYPOINT는 container 시작 시 java -jar app.jar를 실행합니다.

호출자: workflow의 docker build-push-action 또는 운영자의 docker build입니다.
입력: Gradle wrapper, build.gradle, settings.gradle, src, Java 26 base image입니다.
출력: 8080에서 실행 가능한 Spring Boot image입니다.
실패 지점: base image pull, dependency download, Gradle build, jar glob, 권한 설정입니다.
RDS와 Redis 연결은 build 단계가 아니라 runtime 환경에서 확인됩니다.

---

## 19.4 backend .env.example: Compose 변수 계약

### 실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/deploy/.env.example:1-28

### 코드 원문

~~~~dotenv
BACKEND_IMAGE=ghcr.io/your-github-account/your-backend-repository
BACKEND_BLUE_TAG=initial
BACKEND_GREEN_TAG=initial
BACKEND_ACTIVE_COLOR=blue

BACKEND_NGINX_PORT=80
BACKEND_BLUE_PORT=8081
BACKEND_GREEN_PORT=8082

SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://your-rds-endpoint:3306/bamboo_board_week12
DB_USERNAME=week12_app
DB_PASSWORD=replace-with-a-strong-password

JWT_SECRET=replace-with-a-random-secret-at-least-32-characters
JWT_ACCESS_EXPIRATION_MILLIS=600000
JWT_REFRESH_EXPIRATION_MILLIS=10800000
JWT_REFRESH_SESSION_CLEANUP_INTERVAL_MILLIS=3600000
JWT_REFRESH_COOKIE_SECURE=false

CORS_ALLOWED_ORIGINS=http://your-week12-frontend-address

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_CONNECT_TIMEOUT=2s
REDIS_COMMAND_TIMEOUT=1s
VIEW_COUNT_REDIS_ENABLED=true
VIEW_COUNT_FLUSH_INTERVAL=5s
~~~~


### 코드 바로 아래 설명

- BACKEND_IMAGE는 GHCR image repository이고 BLUE_TAG/GREEN_TAG는 두 container가 사용할 tag입니다.
- BACKEND_ACTIVE_COLOR는 edge Nginx가 전달할 active 색상입니다.
- NGINX_PORT는 edge Nginx host port, BLUE_PORT/GREEN_PORT는 각 backend container의 host port입니다.
- SPRING_PROFILES_ACTIVE=prod는 application-prod.yaml을 읽게 합니다.
- DB_URL, DB_USERNAME, DB_PASSWORD는 외부 RDS/MySQL 연결값입니다.
- JWT_SECRET은 prod YAML의 jwt.secret으로 전달됩니다. example 값은 실제 secret이 아닙니다.
- JWT_REFRESH_COOKIE_SECURE=false는 example 값입니다. prod YAML의 placeholder 기본값은 true이지만 Compose가 false를 주입하면 그 값이 우선합니다. HTTPS 운영에서는 true로 바꿔야 합니다.
- CORS_ALLOWED_ORIGINS는 frontend origin입니다.
- REDIS_HOST=redis는 Compose service name입니다. backend가 localhost가 아니라 redis로 Redis를 찾는 이유입니다.
- 조회수 Redis 설정은 16장의 기능 설정이고 Refresh Session cleanup interval과는 다른 설정입니다.
- .env.example은 실제 secret을 생성하거나 공급하지 않습니다. 운영 .env, secret manager, EC2 파일 상태는 저장소만으로 확인할 수 없습니다.

---

## 19.5 backend Compose: Redis·두 backend·edge Nginx

### 실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/deploy/compose.yaml:1-100

### 코드 원문

~~~~yaml
name: week12-backend

services:
  redis:
    image: redis:7.4-alpine
    restart: unless-stopped
    command:
      - redis-server
      - --appendonly
      - "yes"
      - --appendfsync
      - everysec
    volumes:
      - redis-data:/data
    healthcheck:
      test:
        - CMD
        - redis-cli
        - ping
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 5s
    networks:
      - backend-network

  backend-blue:
    image: ${BACKEND_IMAGE:?BACKEND_IMAGE is required}:${BACKEND_BLUE_TAG:?BACKEND_BLUE_TAG is required}
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}
      DB_URL: ${DB_URL:?DB_URL is required}
      DB_USERNAME: ${DB_USERNAME:?DB_USERNAME is required}
      DB_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD is required}
      JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}
      JWT_ACCESS_EXPIRATION_MILLIS: ${JWT_ACCESS_EXPIRATION_MILLIS:-600000}
      JWT_REFRESH_EXPIRATION_MILLIS: ${JWT_REFRESH_EXPIRATION_MILLIS:-10800000}
      JWT_REFRESH_SESSION_CLEANUP_INTERVAL_MILLIS: ${JWT_REFRESH_SESSION_CLEANUP_INTERVAL_MILLIS:-3600000}
      JWT_REFRESH_COOKIE_SECURE: ${JWT_REFRESH_COOKIE_SECURE:-false}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:?CORS_ALLOWED_ORIGINS is required}
      REDIS_HOST: ${REDIS_HOST:-redis}
      REDIS_PORT: ${REDIS_PORT:-6379}
      REDIS_CONNECT_TIMEOUT: ${REDIS_CONNECT_TIMEOUT:-2s}
      REDIS_COMMAND_TIMEOUT: ${REDIS_COMMAND_TIMEOUT:-1s}
      VIEW_COUNT_REDIS_ENABLED: ${VIEW_COUNT_REDIS_ENABLED:-true}
      VIEW_COUNT_FLUSH_INTERVAL: ${VIEW_COUNT_FLUSH_INTERVAL:-5s}
    ports:
      - "127.0.0.1:${BACKEND_BLUE_PORT:-8081}:8080"
    networks:
      - backend-network

  backend-green:
    image: ${BACKEND_IMAGE:?BACKEND_IMAGE is required}:${BACKEND_GREEN_TAG:?BACKEND_GREEN_TAG is required}
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}
      DB_URL: ${DB_URL:?DB_URL is required}
      DB_USERNAME: ${DB_USERNAME:?DB_USERNAME is required}
      DB_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD is required}
      JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}
      JWT_ACCESS_EXPIRATION_MILLIS: ${JWT_ACCESS_EXPIRATION_MILLIS:-600000}
      JWT_REFRESH_EXPIRATION_MILLIS: ${JWT_REFRESH_EXPIRATION_MILLIS:-10800000}
      JWT_REFRESH_SESSION_CLEANUP_INTERVAL_MILLIS: ${JWT_REFRESH_SESSION_CLEANUP_INTERVAL_MILLIS:-3600000}
      JWT_REFRESH_COOKIE_SECURE: ${JWT_REFRESH_COOKIE_SECURE:-false}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:?CORS_ALLOWED_ORIGINS is required}
      REDIS_HOST: ${REDIS_HOST:-redis}
      REDIS_PORT: ${REDIS_PORT:-6379}
      REDIS_CONNECT_TIMEOUT: ${REDIS_CONNECT_TIMEOUT:-2s}
      REDIS_COMMAND_TIMEOUT: ${REDIS_COMMAND_TIMEOUT:-1s}
      VIEW_COUNT_REDIS_ENABLED: ${VIEW_COUNT_REDIS_ENABLED:-true}
      VIEW_COUNT_FLUSH_INTERVAL: ${VIEW_COUNT_FLUSH_INTERVAL:-5s}
    ports:
      - "127.0.0.1:${BACKEND_GREEN_PORT:-8082}:8080"
    networks:
      - backend-network

  nginx:
    image: nginx:1.28-alpine
    restart: unless-stopped
    environment:
      ACTIVE_COLOR: ${BACKEND_ACTIVE_COLOR:-blue}
    command:
      - /bin/sh
      - -c
      - |
        cp "/etc/nginx/bluegreen/backend-$${ACTIVE_COLOR}.conf" /etc/nginx/conf.d/default.conf
        exec nginx -g 'daemon off;'
    ports:
      - "${BACKEND_NGINX_PORT:-80}:80"
    volumes:
      - ./nginx:/etc/nginx/bluegreen:ro
    networks:
      - backend-network

networks:
  backend-network:
    driver: bridge

volumes:
  redis-data:
~~~~


### 코드 바로 아래 설명

- name은 Compose project name입니다.
- redis는 redis:7.4-alpine image와 redis-data named volume을 사용합니다.
- appendonly yes와 appendfsync everysec는 Redis AOF persistence 설정입니다.
- healthcheck의 redis-cli ping은 Redis container의 상태를 검사합니다. backend에 depends_on condition은 없고 deploy script가 별도로 PING을 반복합니다.
- backend-blue와 backend-green은 같은 image repository의 서로 다른 tag를 사용합니다. container 내부 port는 8080이고 host에서는 8081/8082로 나뉩니다.
- environment의 required expansion은 값이 없으면 Compose parse 단계에서 실패하게 하고, default expansion은 값이 없을 때 기본값을 넣습니다.
- ports의 127.0.0.1 binding은 backend를 외부에 직접 공개하지 않고 같은 host의 Nginx와 health script에서 접근하게 합니다.
- nginx command는 ACTIVE_COLOR에 해당하는 config를 default.conf로 복사합니다. Compose가 dollar 기호를 먼저 해석하지 않도록 source의 escape 표기를 사용합니다.
- nginx는 ./nginx를 read-only mount하고 80을 host NGINX_PORT에 연결합니다.
- backend-network는 service name 통신을 위한 bridge network입니다.
- redis-data volume은 container 재생성 이후에도 Redis data를 보존할 수 있는 Docker volume 이름입니다. backup 정책은 확인되지 않습니다.
- Compose는 RDS를 만들지 않습니다. DB_URL은 외부 RDS endpoint입니다.
- backend container 자체 healthcheck는 없고 deploy script가 localhost target port의 Actuator endpoint를 curl합니다.

### backend Compose 실행 흐름

~~~~text
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d redis
→ Redis container/AOF volume
→ deploy script wait_for_redis

docker compose up -d backend-green
→ green image pull/up
→ localhost:8082 → container:8080
→ target /actuator/health 확인

docker compose up -d --force-recreate nginx
→ ACTIVE_COLOR=green
→ backend-green.conf를 default.conf로 복사
→ host:80 → backend-green:8080
~~~~

---

## 19.6 backend Nginx: health와 reverse proxy

### blue

실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/deploy/nginx/backend-blue.conf:1-26


~~~~nginx
upstream active_backend {
    server backend-blue:8080;
}

server {
    listen 80;
    server_name _;

    location = /health {
        proxy_pass http://active_backend/actuator/health;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        client_max_body_size 20m;
        proxy_pass http://active_backend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
~~~~


### green

실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/deploy/nginx/backend-green.conf:1-26


~~~~nginx
upstream active_backend {
    server backend-green:8080;
}

server {
    listen 80;
    server_name _;

    location = /health {
        proxy_pass http://active_backend/actuator/health;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        client_max_body_size 20m;
        proxy_pass http://active_backend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
~~~~


### 코드 바로 아래 설명

- upstream active_backend는 proxy 대상 backend service를 묶습니다.
- blue와 green의 실제 차이는 upstream server가 backend-blue인지 backend-green인지입니다. 나머지 location/header/body limit 구조는 동일합니다.
- 정확한 /health만 Spring /actuator/health로 전달하고, location /은 나머지 request를 active backend로 전달합니다.
- client_max_body_size 20m은 이미지 포함 요청 body 제한입니다.
- proxy_set_header는 host, client IP, forwarding chain, scheme을 backend에 전달합니다.
- Nginx는 backend active 색상을 결정하지 않습니다. Compose command가 선택한 파일을 default.conf로 복사하는 단계가 색상을 결정합니다.

---

## 19.7 deploy-backend.sh: inactive backend 전환

### 실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/deploy/deploy-backend.sh:1-184

### 코드 원문

~~~~bash
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
~~~~


### 코드 바로 아래 설명

- shebang은 Bash 실행을 지정합니다.
- set -Eeuo pipefail은 command 오류, unset variable, pipeline 오류를 엄격하게 처리합니다.
- DEPLOY_DIR, ENV_FILE, COMPOSE_FILE은 환경변수 우선, script 위치 기반 기본값 후순위입니다.
- IMAGE_TAG는 필수입니다. 값이 없으면 시작 시 실패합니다.
- 파일 존재 검사와 command -v 검사는 docker, curl, grep, sed, flock가 준비됐는지 확인합니다.
- file descriptor 9와 flock -n 9는 같은 EC2 host에서 backend deploy script 두 개가 동시에 실행되지 않게 합니다. GitHub Actions concurrency나 Redis lock과는 다른 범위입니다.
- get_env_value/set_env_value는 .env의 key/value를 읽고 바꿉니다. set_env_value는 sed delimiter나 개행이 포함된 값을 별도 escape하지 않습니다.
- compose 함수는 모든 호출에 같은 env-file과 compose-file을 적용합니다.
- wait_for_health는 최대 attempts 동안 curl --fail을 반복합니다.
- wait_for_redis는 Compose 안의 redis service에 redis-cli PING을 보내 PONG을 확인합니다.
- active color가 blue이면 green을 target으로, green이면 blue를 target으로 선택합니다.
- restore_inactive_container와 rollback_proxy는 target 실패 또는 proxy 실패 때 tag/active 색상/target container를 되돌립니다.
- redis를 먼저 올리고 PING이 성공해야 image pull을 시작합니다.
- target tag를 IMAGE_TAG로 바꾸고 target service만 pull/up합니다.
- target port의 /actuator/health 성공 뒤에만 active color를 target으로 변경합니다.
- Nginx recreate 뒤 edge /health를 확인하고, 이전 backend를 stop합니다.
- 이 script에는 Flyway migration을 직접 호출하거나 RDS migration 성공을 별도 확인하는 명령이 없습니다.

~~~~text
필수 파일/명령 누락 → exit 1
flock 실패 → 다른 deploy 실행 중 → exit 1
Redis PING 실패 → Redis logs → exit 1
target pull/up 실패 → tag 복구/target stop → exit 1
target Actuator health 실패 → target logs/복구 → exit 1
Nginx 또는 edge health 실패 → active color 원복/Nginx 복구 → exit 1
old backend stop 실패 → proxy rollback → exit 1
~~~~

---

## 19.8 backend GitHub Actions: test → image → SSM

### 실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/.github/workflows/backend-ci.yml:1-190

### 코드 원문

~~~~yaml
name: Backend CI/CD

on:
  push:
    branches:
      - main
  pull_request:
    branches:
      - main
  workflow_dispatch:

permissions:
  contents: read
  packages: write

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: 100-hours-a-week/ktb4_miles_week12_back

jobs:
  test:
    name: Test backend
    runs-on: ubuntu-latest

    steps:
      - name: Check out source code
        uses: actions/checkout@v6

      - name: Set up Java 26
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "26"
          cache: gradle

      - name: Grant execute permission to Gradle Wrapper
        run: chmod +x gradlew

      - name: Run existing tests and verification
        run: ./gradlew clean check --no-daemon

  build-and-push:
    name: Build and push backend image
    if: github.event_name != 'pull_request'
    needs:
      - test
    runs-on: ubuntu-latest

    steps:
      - name: Check out source code
        uses: actions/checkout@v6

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v4

      - name: Log in to GHCR
        uses: docker/login-action@v4
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Prepare image tags and labels
        id: metadata
        uses: docker/metadata-action@v6
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,format=long,prefix=
            type=raw,value=latest,enable=${{ github.ref == format('refs/heads/{0}', github.event.repository.default_branch) }}

      - name: Build and push image
        uses: docker/build-push-action@v7
        with:
          context: .
          push: true
          tags: ${{ steps.metadata.outputs.tags }}
          labels: ${{ steps.metadata.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy:
    name: Deploy backend with Blue/Green
    if: vars.DEPLOY_ENABLED == 'true' && github.event_name != 'pull_request'
    needs:
      - build-and-push
    runs-on: ubuntu-latest
    concurrency:
      group: backend-production-deploy
      cancel-in-progress: false
    permissions:
      id-token: write
      contents: read

    steps:
      - name: Configure temporary AWS credentials
        uses: aws-actions/configure-aws-credentials@v6.1.1
        with:
          role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }}
          aws-region: ${{ vars.AWS_REGION }}

      - name: Send backend deployment command
        id: send-command
        env:
          DEPLOY_PATH: ${{ vars.DEPLOY_PATH }}
          DEPLOY_INSTANCE_ID: ${{ vars.DEPLOY_INSTANCE_ID }}
          RAW_BASE_URL: https://raw.githubusercontent.com/${{ github.repository }}/${{ github.sha }}/deploy
          IMAGE_TAG: ${{ github.sha }}
        run: |
          PARAMETERS=$(jq -n \
            --arg deploy_path "${DEPLOY_PATH}" \
            --arg raw_base_url "${RAW_BASE_URL}" \
            --arg image_tag "${IMAGE_TAG}" \
            --arg execution_timeout "900" \
            '{
              executionTimeout: [$execution_timeout],
              commands: [
                "set -Eeuo pipefail",
                ("install -d -m 755 " + $deploy_path + "/nginx"),
                ("curl -fsSL " + $raw_base_url + "/compose.yaml -o " + $deploy_path + "/compose.yaml"),
                ("curl -fsSL " + $raw_base_url + "/nginx/backend-blue.conf -o " + $deploy_path + "/nginx/backend-blue.conf"),
                ("curl -fsSL " + $raw_base_url + "/nginx/backend-green.conf -o " + $deploy_path + "/nginx/backend-green.conf"),
                ("curl -fsSL " + $raw_base_url + "/deploy-backend.sh -o " + $deploy_path + "/deploy-backend.sh"),
                ("chmod 755 " + $deploy_path + "/deploy-backend.sh"),
                ("cd " + $deploy_path),
                ("IMAGE_TAG=" + $image_tag + " ./deploy-backend.sh")
              ]
            }')

          COMMAND_ID=$(aws ssm send-command \
            --instance-ids "${DEPLOY_INSTANCE_ID}" \
            --document-name "AWS-RunShellScript" \
            --comment "Deploy backend commit ${IMAGE_TAG} with Blue/Green" \
            --timeout-seconds 60 \
            --parameters "${PARAMETERS}" \
            --query "Command.CommandId" \
            --output text)

          echo "command_id=${COMMAND_ID}" >> "$GITHUB_OUTPUT"

      - name: Wait for backend deployment
        id: wait-deployment
        timeout-minutes: 18
        env:
          COMMAND_ID: ${{ steps.send-command.outputs.command_id }}
          DEPLOY_INSTANCE_ID: ${{ vars.DEPLOY_INSTANCE_ID }}
        run: |
          for attempt in {1..200}; do
            STATUS=$(aws ssm get-command-invocation \
              --command-id "${COMMAND_ID}" \
              --instance-id "${DEPLOY_INSTANCE_ID}" \
              --query "Status" \
              --output text 2>/dev/null || true)

            case "${STATUS}" in
              Success)
                exit 0
                ;;
              Failed|Cancelled|TimedOut|Cancelling)
                exit 1
                ;;
            esac

            echo "Deployment status: ${STATUS:-Pending} (${attempt}/200)"
            sleep 5
          done

          echo "Backend deployment did not finish within 1000 seconds." >&2
          exit 1

      - name: Cancel unfinished backend deployment
        if: ${{ (failure() || cancelled()) && steps.send-command.outputs.command_id != '' }}
        env:
          COMMAND_ID: ${{ steps.send-command.outputs.command_id }}
          DEPLOY_INSTANCE_ID: ${{ vars.DEPLOY_INSTANCE_ID }}
        run: |
          aws ssm cancel-command \
            --command-id "${COMMAND_ID}" \
            --instance-ids "${DEPLOY_INSTANCE_ID}" || true

      - name: Show backend deployment result
        if: always() && steps.send-command.outputs.command_id != ''
        env:
          COMMAND_ID: ${{ steps.send-command.outputs.command_id }}
          DEPLOY_INSTANCE_ID: ${{ vars.DEPLOY_INSTANCE_ID }}
        run: |
          aws ssm get-command-invocation \
            --command-id "${COMMAND_ID}" \
            --instance-id "${DEPLOY_INSTANCE_ID}" \
            --query '{Status:Status,Output:StandardOutputContent,Error:StandardErrorContent}'
~~~~


### 코드 바로 아래 설명

- push main, pull_request main, workflow_dispatch가 workflow trigger입니다.
- test job은 Java 26을 준비하고 Gradle wrapper로 clean check를 실행합니다.
- build-and-push는 pull request가 아니고 test job이 성공했을 때만 실행됩니다.
- metadata-action은 SHA tag와 default branch의 latest tag를 생성합니다.
- build-push-action은 Dockerfile을 build하고 GHCR에 push합니다.
- deploy는 DEPLOY_ENABLED가 true이고 pull request가 아니며 image job이 성공했을 때만 실행됩니다.
- configure-aws-credentials는 OIDC로 AWS role을 assume합니다. role trust policy와 권한은 workflow 파일만으로 확인할 수 없습니다.
- send-command는 jq로 SSM parameter JSON을 만들고, raw GitHub commit URL에서 Compose/Nginx/script를 EC2 deploy path에 내려받은 뒤 IMAGE_TAG와 함께 script를 실행하도록 합니다.
- wait step은 최대 200회, 5초 간격으로 SSM status를 조회합니다. cancel step과 always 결과 출력 step이 실패/취소 경로에 연결됩니다.
- workflow concurrency와 deploy script flock은 서로 다른 범위의 중복 배포 방지입니다.

~~~~text
pull_request → test만 실행
main push → test → image build/push → DEPLOY_ENABLED=true일 때 SSM deploy
workflow_dispatch → 같은 job 조건을 사용하되 event 조건은 별도로 평가
~~~~

---

## 19.9 frontend Dockerfile과 runtime Nginx

### frontend Dockerfile

실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/Dockerfile:1-25


~~~~dockerfile
# syntax=docker/dockerfile:1

FROM node:24-alpine AS build

WORKDIR /workspace

COPY package.json package-lock.json ./

RUN npm ci

COPY . .

ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

FROM nginx:1.28-alpine AS runtime

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
~~~~


### 코드 바로 아래 설명

- Node 24 build stage에서 package.json과 package-lock.json을 먼저 복사하고 npm ci로 lockfile dependency를 설치합니다.
- COPY . . 뒤 ARG VITE_API_BASE_URL과 ENV가 build 환경을 구성합니다.
- Vite는 VITE_ prefix 환경변수를 npm run build 시 bundle에 정적으로 삽입합니다. 현재 frontend workflow가 /api를 build-arg로 전달합니다.
- runtime stage에는 Nginx와 dist, nginx/default.conf만 남습니다.
- CMD의 daemon off는 Nginx가 foreground process로 남아 container가 종료되지 않게 합니다.

### Dockerfile이 COPY하는 실제 지원 파일

실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/nginx/default.conf:1-24


~~~~nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    client_max_body_size 20m;

    location = /health {
        access_log off;
        default_type text/plain;
        return 200 "ok\n";
    }

    location /assets/ {
        try_files $uri =404;
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
~~~~


### 코드 바로 아래 설명

- root/index는 dist 정적 파일 위치입니다.
- /health는 200 ok를 직접 반환하므로 frontend container health endpoint입니다.
- /assets/는 파일이 없으면 404이고 1년 immutable cache header를 붙입니다.
- location /의 try_files는 React Router route가 서버에 직접 들어와도 index.html로 fallback합니다.
- 이 파일은 official 213 목록 밖이지만 Dockerfile의 실제 COPY 입력이므로 문서에서 별도 표시했습니다.

---

## 19.10 frontend env와 Compose

### frontend .env.example

실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/deploy/.env.example:1-10


~~~~dotenv
FRONTEND_IMAGE=ghcr.io/your-github-account/your-frontend-repository
FRONTEND_BLUE_TAG=initial
FRONTEND_GREEN_TAG=initial
FRONTEND_ACTIVE_COLOR=blue

FRONTEND_NGINX_PORT=80
FRONTEND_BLUE_PORT=3001
FRONTEND_GREEN_PORT=3002

BACKEND_UPSTREAM=your-week12-backend-private-ip:80
~~~~


### 코드 바로 아래 설명

- FRONTEND_IMAGE와 blue/green tag는 image 교체 대상입니다.
- FRONTEND_ACTIVE_COLOR는 edge Nginx가 선택할 frontend 색상입니다.
- FRONTEND_NGINX_PORT는 외부 edge port, blue/green port는 각 container host port입니다.
- BACKEND_UPSTREAM은 frontend edge Nginx의 API proxy 대상입니다.
- 실제 private backend address, DNS, TLS와 .env 공급 방식은 repository만으로 확인할 수 없습니다.

### frontend Compose

실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/deploy/compose.yaml:1-43


~~~~yaml
name: week12-frontend

services:
  frontend-blue:
    image: ${FRONTEND_IMAGE:?FRONTEND_IMAGE is required}:${FRONTEND_BLUE_TAG:?FRONTEND_BLUE_TAG is required}
    restart: unless-stopped
    ports:
      - "127.0.0.1:${FRONTEND_BLUE_PORT:-3001}:80"
    networks:
      - frontend-network

  frontend-green:
    image: ${FRONTEND_IMAGE:?FRONTEND_IMAGE is required}:${FRONTEND_GREEN_TAG:?FRONTEND_GREEN_TAG is required}
    restart: unless-stopped
    ports:
      - "127.0.0.1:${FRONTEND_GREEN_PORT:-3002}:80"
    networks:
      - frontend-network

  nginx:
    image: nginx:1.28-alpine
    restart: unless-stopped
    environment:
      ACTIVE_COLOR: ${FRONTEND_ACTIVE_COLOR:-blue}
      BACKEND_UPSTREAM: ${BACKEND_UPSTREAM:?BACKEND_UPSTREAM is required}
    command:
      - /bin/sh
      - -c
      - |
        envsubst '$$BACKEND_UPSTREAM' \
          < "/etc/nginx/bluegreen/frontend-$${ACTIVE_COLOR}.conf.template" \
          > /etc/nginx/conf.d/default.conf
        exec nginx -g 'daemon off;'
    ports:
      - "${FRONTEND_NGINX_PORT:-80}:80"
    volumes:
      - ./nginx:/etc/nginx/bluegreen:ro
    networks:
      - frontend-network

networks:
  frontend-network:
    driver: bridge
~~~~


### 코드 바로 아래 설명

- frontend-blue/green은 같은 image repository의 서로 다른 tag입니다.
- edge nginx는 ACTIVE_COLOR와 BACKEND_UPSTREAM을 environment로 받습니다.
- command의 envsubst는 선택된 template에서 backend upstream을 치환해 /etc/nginx/conf.d/default.conf를 생성합니다.
- Compose source의 dollar escape는 Compose가 먼저 변수를 소비하지 않고 container shell/envsubst 단계에 전달하기 위한 것입니다.
- frontend-network는 frontend 두 container와 edge Nginx의 내부 service network입니다.
- backend Compose와 frontend Compose는 서로 다른 project/network입니다. BACKEND_UPSTREAM이 host/private backend Nginx 주소인 이유입니다.

---

## 19.11 frontend Nginx template: SPA·API proxy·active color

### blue template

실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/deploy/nginx/frontend-blue.conf.template:1-39


~~~~nginx
upstream active_frontend {
    server frontend-blue:80;
}

server {
    listen 80;
    server_name _;

    location = /health {
        proxy_pass http://active_frontend/health;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /api {
        return 308 /api/;
    }

    location /api/ {
        client_max_body_size 20m;
        proxy_pass http://${BACKEND_UPSTREAM}/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://active_frontend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
~~~~


### green template

실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/deploy/nginx/frontend-green.conf.template:1-39


~~~~nginx
upstream active_frontend {
    server frontend-green:80;
}

server {
    listen 80;
    server_name _;

    location = /health {
        proxy_pass http://active_frontend/health;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /api {
        return 308 /api/;
    }

    location /api/ {
        client_max_body_size 20m;
        proxy_pass http://${BACKEND_UPSTREAM}/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://active_frontend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
~~~~


### 코드 바로 아래 설명

- blue와 green의 실제 차이는 upstream service name입니다.
- /health는 active frontend container health를 proxy합니다.
- /api에 정확히 일치하면 308으로 /api/를 사용하게 합니다.
- /api/는 BACKEND_UPSTREAM으로 proxy합니다. proxy_pass의 trailing slash가 upstream URI 전달 방식에 영향을 줍니다. backend Controller의 실제 path와 운영 proxy path가 일치해야 합니다.
- /는 active frontend container로 전달합니다.
- 두 template의 header와 body limit은 동일합니다.

---

## 19.12 deploy-frontend.sh: inactive frontend 전환

### 실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/deploy/deploy-frontend.sh:1-153

### 코드 원문

~~~~bash
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
  echo "Another frontend deployment is already running." >&2
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

active_color="$(get_env_value FRONTEND_ACTIVE_COLOR blue)"

case "${active_color}" in
  blue)
    target_color="green"
    target_tag_key="FRONTEND_GREEN_TAG"
    target_port="$(get_env_value FRONTEND_GREEN_PORT 3002)"
    ;;
  green)
    target_color="blue"
    target_tag_key="FRONTEND_BLUE_TAG"
    target_port="$(get_env_value FRONTEND_BLUE_PORT 3001)"
    ;;
  *)
    echo "Invalid FRONTEND_ACTIVE_COLOR: ${active_color}" >&2
    exit 1
    ;;
esac

target_service="frontend-${target_color}"
previous_target_tag="$(get_env_value "${target_tag_key}" initial)"
nginx_port="$(get_env_value FRONTEND_NGINX_PORT 80)"

restore_inactive_container() {
  set_env_value "${target_tag_key}" "${previous_target_tag}"
  compose stop "${target_service}" >/dev/null 2>&1 || true
}

rollback_proxy() {
  set_env_value FRONTEND_ACTIVE_COLOR "${active_color}"
  compose up -d --no-deps --force-recreate nginx >/dev/null 2>&1 || true
  restore_inactive_container
}

echo "Active frontend: ${active_color}"
echo "Deployment target: ${target_color}"
echo "Image tag: ${IMAGE_TAG}"

set_env_value "${target_tag_key}" "${IMAGE_TAG}"

if ! compose pull "${target_service}"; then
  restore_inactive_container
  exit 1
fi

if ! compose up -d --no-deps "${target_service}"; then
  restore_inactive_container
  exit 1
fi

if ! wait_for_health "http://127.0.0.1:${target_port}/health"; then
  compose logs --tail=100 "${target_service}" || true
  restore_inactive_container
  exit 1
fi

set_env_value FRONTEND_ACTIVE_COLOR "${target_color}"

if ! compose up -d --no-deps --force-recreate nginx; then
  rollback_proxy
  exit 1
fi

if ! wait_for_health "http://127.0.0.1:${nginx_port}/health"; then
  compose logs --tail=100 nginx || true
  rollback_proxy
  exit 1
fi

echo "Frontend deployment completed: ${active_color} -> ${target_color}"
~~~~


### 코드 바로 아래 설명

- backend script와 동일하게 strict mode, env parser, file lock, color selection, health retry, rollback 구조를 사용합니다.
- frontend는 Redis 준비 단계가 없습니다.
- target frontend image를 pull/up하고 target port /health를 확인합니다.
- target health 성공 뒤 active color를 바꾸고 edge Nginx를 recreate합니다.
- edge /health가 성공하면 이전 frontend를 stop합니다.
- target 또는 edge health 실패 시 tag와 active color를 복구합니다.
- 새로운 핵심 문법은 backend script에서 설명했으며, frontend 차이는 Redis 없음, frontend service/port/tag key, /health path입니다.

---

## 19.13 frontend GitHub Actions: lint/build → image → SSM

### 실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/.github/workflows/frontend-ci.yml:1-194

### 코드 원문

~~~~yaml
name: Frontend CI/CD

on:
  push:
    branches:
      - main
  pull_request:
    branches:
      - main
  workflow_dispatch:

permissions:
  contents: read
  packages: write

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: 100-hours-a-week/ktb4_miles_week12_front

jobs:
  validate:
    name: Validate frontend
    runs-on: ubuntu-latest

    steps:
      - name: Check out source code
        uses: actions/checkout@v6

      - name: Set up Node.js 24
        uses: actions/setup-node@v7
        with:
          node-version: "24"
          cache: npm

      - name: Install locked dependencies
        run: npm ci

      - name: Run lint
        run: npm run lint

      - name: Build frontend
        run: npm run build

  build-and-push:
    name: Build and push frontend image
    if: github.event_name != 'pull_request'
    needs:
      - validate
    runs-on: ubuntu-latest

    steps:
      - name: Check out source code
        uses: actions/checkout@v6

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v4

      - name: Log in to GHCR
        uses: docker/login-action@v4
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Prepare image tags and labels
        id: metadata
        uses: docker/metadata-action@v6
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,format=long,prefix=
            type=raw,value=latest,enable=${{ github.ref == format('refs/heads/{0}', github.event.repository.default_branch) }}

      - name: Build and push image
        uses: docker/build-push-action@v7
        with:
          context: .
          push: true
          build-args: |
            VITE_API_BASE_URL=/api
          tags: ${{ steps.metadata.outputs.tags }}
          labels: ${{ steps.metadata.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy:
    name: Deploy frontend with Blue/Green
    if: vars.DEPLOY_ENABLED == 'true' && github.event_name != 'pull_request'
    needs:
      - build-and-push
    runs-on: ubuntu-latest
    concurrency:
      group: frontend-production-deploy
      cancel-in-progress: false
    permissions:
      id-token: write
      contents: read

    steps:
      - name: Configure temporary AWS credentials
        uses: aws-actions/configure-aws-credentials@v6.1.1
        with:
          role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }}
          aws-region: ${{ vars.AWS_REGION }}

      - name: Send frontend deployment command
        id: send-command
        env:
          DEPLOY_PATH: ${{ vars.DEPLOY_PATH }}
          DEPLOY_INSTANCE_ID: ${{ vars.DEPLOY_INSTANCE_ID }}
          RAW_BASE_URL: https://raw.githubusercontent.com/${{ github.repository }}/${{ github.sha }}/deploy
          IMAGE_TAG: ${{ github.sha }}
        run: |
          PARAMETERS=$(jq -n \
            --arg deploy_path "${DEPLOY_PATH}" \
            --arg raw_base_url "${RAW_BASE_URL}" \
            --arg image_tag "${IMAGE_TAG}" \
            --arg execution_timeout "900" \
            '{
              executionTimeout: [$execution_timeout],
              commands: [
                "set -Eeuo pipefail",
                ("install -d -m 755 " + $deploy_path + "/nginx"),
                ("curl -fsSL " + $raw_base_url + "/compose.yaml -o " + $deploy_path + "/compose.yaml"),
                ("curl -fsSL " + $raw_base_url + "/nginx/frontend-blue.conf.template -o " + $deploy_path + "/nginx/frontend-blue.conf.template"),
                ("curl -fsSL " + $raw_base_url + "/nginx/frontend-green.conf.template -o " + $deploy_path + "/nginx/frontend-green.conf.template"),
                ("curl -fsSL " + $raw_base_url + "/deploy-frontend.sh -o " + $deploy_path + "/deploy-frontend.sh"),
                ("chmod 755 " + $deploy_path + "/deploy-frontend.sh"),
                ("cd " + $deploy_path),
                ("IMAGE_TAG=" + $image_tag + " ./deploy-frontend.sh")
              ]
            }')

          COMMAND_ID=$(aws ssm send-command \
            --instance-ids "${DEPLOY_INSTANCE_ID}" \
            --document-name "AWS-RunShellScript" \
            --comment "Deploy frontend commit ${IMAGE_TAG} with Blue/Green" \
            --timeout-seconds 60 \
            --parameters "${PARAMETERS}" \
            --query "Command.CommandId" \
            --output text)

          echo "command_id=${COMMAND_ID}" >> "$GITHUB_OUTPUT"

      - name: Wait for frontend deployment
        id: wait-deployment
        timeout-minutes: 18
        env:
          COMMAND_ID: ${{ steps.send-command.outputs.command_id }}
          DEPLOY_INSTANCE_ID: ${{ vars.DEPLOY_INSTANCE_ID }}
        run: |
          for attempt in {1..200}; do
            STATUS=$(aws ssm get-command-invocation \
              --command-id "${COMMAND_ID}" \
              --instance-id "${DEPLOY_INSTANCE_ID}" \
              --query "Status" \
              --output text 2>/dev/null || true)

            case "${STATUS}" in
              Success)
                exit 0
                ;;
              Failed|Cancelled|TimedOut|Cancelling)
                exit 1
                ;;
            esac

            echo "Deployment status: ${STATUS:-Pending} (${attempt}/200)"
            sleep 5
          done

          echo "Frontend deployment did not finish within 1000 seconds." >&2
          exit 1

      - name: Cancel unfinished frontend deployment
        if: ${{ (failure() || cancelled()) && steps.send-command.outputs.command_id != '' }}
        env:
          COMMAND_ID: ${{ steps.send-command.outputs.command_id }}
          DEPLOY_INSTANCE_ID: ${{ vars.DEPLOY_INSTANCE_ID }}
        run: |
          aws ssm cancel-command \
            --command-id "${COMMAND_ID}" \
            --instance-ids "${DEPLOY_INSTANCE_ID}" || true

      - name: Show frontend deployment result
        if: always() && steps.send-command.outputs.command_id != ''
        env:
          COMMAND_ID: ${{ steps.send-command.outputs.command_id }}
          DEPLOY_INSTANCE_ID: ${{ vars.DEPLOY_INSTANCE_ID }}
        run: |
          aws ssm get-command-invocation \
            --command-id "${COMMAND_ID}" \
            --instance-id "${DEPLOY_INSTANCE_ID}" \
            --query '{Status:Status,Output:StandardOutputContent,Error:StandardErrorContent}'
~~~~


### 코드 바로 아래 설명

- validate job은 Node.js 24, npm ci, npm run lint, npm run build를 실행합니다.
- build-and-push는 pull request가 아니고 validate가 성공할 때만 실행됩니다.
- Docker build-args로 VITE_API_BASE_URL=/api를 전달합니다. 이것은 runtime Compose environment가 아니라 image build-time 값입니다.
- deploy는 backend workflow와 같은 DEPLOY_ENABLED, needs, concurrency, OIDC, SSM 구조입니다.
- raw deploy command로 Compose, frontend Nginx template, deploy script를 EC2에 내려받습니다. Dockerfile과 runtime nginx/default.conf는 image build에 이미 포함되어 있으므로 원격 deploy path에 다시 다운로드하지 않습니다.
- status polling, timeout, cancel, 최종 결과 출력은 backend workflow와 같은 구조이며, frontend 차이는 Node/npm/build-arg와 frontend 파일 URL입니다.

---

## 19.14 환경변수 평가 시점과 데이터 이동

~~~~text
GitHub SHA
→ workflow IMAGE_TAG
→ Docker metadata tag
→ GHCR image tag
→ SSM command IMAGE_TAG
→ deploy script target tag key
→ Compose image interpolation
→ inactive container
~~~~

~~~~text
backend .env
→ Compose environment
→ application-prod.yaml placeholder
→ DB_URL/JWT_SECRET/CORS/Redis
→ Spring Bean·Flyway·Security·Redis client
~~~~

~~~~text
frontend workflow build-arg /api
→ Dockerfile ARG/ENV
→ Vite npm run build
→ dist bundle
→ browser /api request
→ frontend edge Nginx
→ backend upstream
~~~~

- Docker ARG는 image build 시점 값입니다.
- Compose interpolation은 Compose가 YAML을 읽는 시점에 평가됩니다.
- Spring placeholder는 application startup 시 평가됩니다.
- GitHub expression은 workflow 실행 시 평가됩니다.
- Bash variable은 deploy script 실행 시 평가됩니다.
- envsubst는 frontend edge Nginx container command 시점에 template를 생성합니다.
- 문법 모양이 비슷해도 평가 주체와 시점이 다릅니다.

### prod YAML 연결

실제 파일

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/resources/application-prod.yaml:1-39


~~~~yaml
spring:
  datasource:
    url: ${DB_URL}
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  h2:
    console:
      enabled: false

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: false
    show-sql: false
    defer-datasource-initialization: false

  sql:
    init:
      mode: never

  flyway:
    enabled: true
    baseline-on-migrate: false
    locations: classpath:db/migration
    validate-on-migrate: true

jwt:
  secret: ${JWT_SECRET}
  # HTTPS 운영 배포 환경에서는 JWT_REFRESH_COOKIE_SECURE=true로 설정해야 한다.
  refresh-cookie-secure: ${JWT_REFRESH_COOKIE_SECURE:true}
  refresh-cookie-path: /api/sessions

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS}
~~~~


- prod YAML은 DB_URL/username/password, JWT_SECRET, cookie secure, CORS origin을 환경변수로 받습니다.
- ddl-auto=validate는 Hibernate가 schema를 자동 생성하지 않고 현재 schema가 Entity와 맞는지 검증하게 합니다.
- Flyway enabled=true, baseline-on-migrate=false, validate-on-migrate=true가 RDS migration을 application startup에 연결합니다.
- 배포 script가 Flyway를 직접 호출하지 않아도 backend container startup 과정에서 Spring Boot/Flyway가 참여할 수 있습니다. 실제 RDS에서 성공했는지는 실행 로그가 필요합니다.

---

## 19.15 정상·실패·rollback 경계

### 정상 배포

1. 검증 job 성공
2. image push 성공
3. inactive target pull/up 성공
4. target health 성공
5. edge Nginx 전환 성공
6. edge health 성공
7. 이전 container stop

### backend 실패

- Redis 준비 실패: backend 전환 전에 종료
- target health 실패: target logs, tag 복구, target stop
- Nginx 또는 edge health 실패: active color 원복과 proxy rollback
- old backend stop 실패: proxy rollback 후 실패

### frontend 실패

- target health 실패: target tag 복구와 target stop
- Nginx 또는 edge health 실패: active color 원복과 proxy rollback
- Redis 준비 단계는 없음

### 현재 코드가 보장하지 않는 것

- RDS connection과 migration 성공을 deploy script가 별도 명령으로 확인하지 않습니다.
- 두 색상 container의 graceful connection draining은 구현되어 있다고 말할 수 없습니다.
- TLS certificate, domain, load balancer는 이 repository deployment file에서 확인되지 않습니다.
- secret manager에서 .env를 공급하는 코드는 없습니다.
- 실제 AWS OIDC role policy, SSM agent, EC2, GHCR 상태는 확인되지 않습니다.

---

## 19.16 파일별 책임·호출·값 이동 요약

| 파일 | 읽는 이유 | 실행 주체 | 내부 변경 | 결과/실패 |
|---|---|---|---|---|
| backend Dockerfile | Java image 생성 | workflow/docker build | build/runtime image | Gradle/base image/jar 실패 |
| backend .env.example | Compose 변수 계약 | Compose/script | tag/color/connection 값 | 실제 secret 공급 미확인 |
| backend compose.yaml | Redis/backend/Nginx topology | deploy script의 compose 함수 | containers/network/volume | interpolation/image/port 실패 |
| backend Nginx 2개 | active backend proxy | Compose nginx command | Nginx default.conf | upstream/proxy 오류 |
| deploy-backend.sh | backend 전환 순서 | AWS SSM 원격 command | .env tag/color/container | health/lock/rollback 실패 |
| backend-ci.yml | test/image/deploy graph | GitHub Actions | GHCR/SSM command | check/build/AWS/SSM 실패 |
| frontend Dockerfile | Vite bundle/runtime image | workflow/docker build | dist/runtime image | npm/build 실패 |
| frontend .env.example | frontend 변수 계약 | Compose/script | tag/color/upstream | 실제 address 미확인 |
| frontend nginx/default.conf | image 내부 SPA server | Dockerfile COPY | static/health routing | dist/config 오류 |
| frontend compose.yaml | frontend 두 container/edge | deploy script | containers/network | envsubst/interpolation 실패 |
| frontend Nginx templates | active frontend/API proxy | Compose envsubst | edge default.conf | upstream/URI 오류 |
| deploy-frontend.sh | frontend 전환 순서 | AWS SSM 원격 command | .env tag/color/container | health/rollback 실패 |
| frontend-ci.yml | lint/build/image/deploy graph | GitHub Actions | GHCR/SSM command | npm/AWS/SSM 실패 |

---

## 19.17 중복으로 스킵할 수 있는 부분

### backend-green.conf

- 역할: green backend를 upstream으로 지정합니다.
- blue와 중복: location, proxy header, body limit, health 구조가 같습니다.
- 차이: upstream service name만 backend-green입니다.
- 새 문법: 없습니다. blue에서 설명합니다.
- 실제 두 원문은 모두 확인했습니다.

### frontend green template

- 역할: green frontend를 upstream으로 지정합니다.
- blue와 중복: health, API, SPA, proxy header 구조가 같습니다.
- 차이: upstream service name만 frontend-green입니다.
- 새 문법: 없습니다. blue에서 설명합니다.
- 실제 두 원문은 모두 확인했습니다.

### frontend deploy script

- backend script와 중복: strict mode, env parser, flock, color 선택, health retry, rollback입니다.
- 차이: Redis wait 없음, frontend service/port/tag key입니다.
- 새 문법: 없습니다. backend script에서 설명하고 frontend 차이만 확인합니다.

---

## 19.18 검증 범위

현재 deployment source에는 Dockerfile unit test, Compose integration test, Nginx config test, deploy script test, GitHub Actions workflow test가 없습니다.

이번 문서 작성에서 정적으로 확인한 것:

- 14개 공식 deployment 파일과 frontend runtime nginx 지원 파일의 경로·원문
- Dockerfile stage/COPY/ENTRYPOINT
- Compose service/network/volume/interpolation
- Nginx upstream/location/proxy
- Bash color selection/health/rollback
- workflow trigger/needs/if/concurrency/OIDC/SSM
- backend prod YAML과 deployment 환경변수 연결

실행하지 않은 것:

- docker build
- docker compose config/up
- RDS·Redis 연결
- Nginx 실제 reload/proxy
- GitHub Actions 실행
- AWS OIDC·SSM·EC2·GHCR
- 실제 blue/green traffic 전환
- browser API proxy와 Cookie/CORS

## 19.19 체크포인트

1. backend Dockerfile에서 JDK build stage와 JRE runtime stage를 나눈 이유는 무엇인가?
2. Dockerfile ARG, Compose interpolation, Spring YAML placeholder, Bash variable, GitHub expression은 각각 언제 평가되는가?
3. backend blue/green이 내부 8080을 공유해도 host port가 충돌하지 않는 이유는 무엇인가?
4. backend Nginx의 /health가 /actuator/health로 연결되는 이유는 무엇인가?
5. deploy-backend.sh가 inactive 색상을 선택하고 rollback하는 부분은 어디인가?
6. deploy script flock과 GitHub Actions concurrency의 범위가 어떻게 다른가?
7. frontend VITE_API_BASE_URL=/api는 build-time인가 runtime인가?
8. frontend nginx/default.conf와 deploy edge Nginx template의 책임은 어떻게 다른가?
9. frontend /api/ proxy가 backend upstream으로 전달되는 경로를 어느 Nginx 설정이 담당하는가?
10. Compose의 dollar escape와 envsubst가 필요한 이유는 무엇인가?
11. workflow가 pull request에서 image push/deploy를 건너뛰는 조건은 무엇인가?
12. 실제 운영 성공을 repository만으로 확정할 수 없는 항목은 무엇인가?

## 결론

~~~~text
GitHub Actions 검증
→ Docker image build/push
→ AWS OIDC credentials
→ SSM 원격 실행
→ inactive 색상 선택
→ Compose image pull/up
→ target health
→ Nginx active upstream 전환
→ edge health
→ 이전 container stop
~~~~

backend는 Redis 준비와 Spring Actuator health가 추가되고, frontend는 Vite build-time API base와 SPA fallback 및 /api proxy가 추가됩니다. 두 영역 모두 blue/green container와 edge Nginx를 사용하지만 실행 대상과 health 경로가 다릅니다.

19장까지 공식 파일 진행률은 **213/213개, 100.0%**입니다. 실제 운영 성공, AWS/RDS/EC2/GHCR/SSM/TLS 상태는 repository source만으로 확인할 수 없으며 이번 장에서도 runtime 검증은 실행하지 않았습니다.

