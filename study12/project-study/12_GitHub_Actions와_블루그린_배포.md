# 12장. GitHub Actions와 블루–그린 배포

## 12.1 학습 목표

GitHub에 코드를 push한 뒤 테스트, 이미지 제작, Registry 저장, AWS 인증, EC2 배포와 rollback까지 이어지는 흐름을 학습한다.

```text
push 또는 pull request
→ CI 검증
→ Docker image build
→ GHCR push
→ AWS OIDC 임시 인증
→ SSM 명령
→ EC2 배포 스크립트
→ 비활성 색상 실행
→ health check
→ Nginx 전환
→ 같은 EC2에서 Nginx 경유 health check
→ 기존 색상 중지
```

## 12.2 실제 Workflow 시작 조건

```yaml
name: Backend CI/CD

on:
  push:
    branches:
      - main
  pull_request:
    branches:
      - main
  workflow_dispatch:
```

```text
main push
→ 검증 후 이미지 제작과 조건부 배포

main 대상 pull request
→ 검증만 실행

workflow_dispatch
→ GitHub 화면에서 수동 실행
```

## 12.3 실제 코드 발췌: 백엔드 테스트 Job

```yaml
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
```

중요한 줄:

```yaml
runs-on: ubuntu-latest
# GitHub가 제공하는 새 Linux Runner에서 Job을 실행한다.

uses: actions/checkout@v6
# Repository 소스를 Runner로 가져온다.

./gradlew clean check
# 일반 테스트, Redis 테스트, JaCoCo 검증까지 수행한다.
```

Redis Testcontainers 때문에 Runner에서도 Docker가 필요하며 GitHub의 Ubuntu Runner가 Docker 실행 환경을 제공한다.

## 12.4 실제 코드 발췌: 이미지 빌드와 push

```yaml
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
```

```text
needs: test
→ 테스트 Job 성공 후에만 시작

PR 제외
→ 검토 중 코드로 운영 image를 만들지 않음

commit SHA tag
→ 정확히 어떤 코드의 image인지 식별

GHCR
→ GitHub Container Registry에 image 저장
```

`latest`만 사용하면 어떤 commit을 배포했는지 추적하기 어렵다. 배포 스크립트에는 현재 `${{ github.sha }}`가 전달된다. 실제 metadata 설정에서 `latest`는 실행 ref가 repository default branch일 때만 활성화되고, SHA tag는 image를 만드는 모든 비-PR 실행에 붙는다.

Job마다 서로 다른 새 Runner를 사용하므로 build Job에도 다시 `checkout`이 필요하다. test Job의 workspace가 다음 Job에 자동 공유되는 것은 아니다.

## 12.5 OIDC와 AWS 임시 인증

실제 코드:

```yaml
permissions:
  id-token: write
  contents: read

steps:
  - name: Configure temporary AWS credentials
    uses: aws-actions/configure-aws-credentials@v6.1.1
    with:
      role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }}
      aws-region: ${{ vars.AWS_REGION }}
```

```text
GitHub Workflow가 OIDC token 요청
→ AWS가 Repository·branch 등 신뢰 조건 확인
→ IAM Role의 임시 자격 증명 발급
→ Workflow가 AWS API 호출
```

장기 AWS Access Key를 GitHub Secret에 저장하지 않고 짧은 수명의 권한을 받는 구조다.

## 12.6 `vars`, `secrets`, `env`

```text
vars
→ 배포 경로, Region, Instance ID 같은 설정

secrets
→ 비밀번호·토큰처럼 노출하면 안 되는 값

env
→ 현재 Workflow, Job, Step 프로세스에 전달하는 환경변수

${{ ... }}
→ GitHub Actions가 Runner 명령 실행 전에 평가하는 표현식

${VAR}
→ Runner 안의 shell이 평가하는 환경변수
```

현재 운영 DB 비밀번호와 JWT 비밀키는 GitHub Workflow가 직접 EC2로 전달하지 않는다. EC2 배포 경로의 `.env`에 미리 안전하게 준비되어 있고 Compose가 읽는다.

## 12.7 SSM으로 EC2 명령 전송

배포 Job 자체에는 다음 조건이 있다.

```yaml
deploy:
  name: Deploy backend with Blue/Green
  if: vars.DEPLOY_ENABLED == 'true' && github.event_name != 'pull_request'
  needs:
    - build-and-push
  runs-on: ubuntu-latest
  permissions:
    id-token: write
    contents: read
```

따라서 `main`에 push했다고 항상 EC2 배포까지 실행되는 것은 아니다. Repository variable인 `DEPLOY_ENABLED`가 문자열 `true`여야 하며, PR 실행은 제외된다. `needs` 때문에 테스트와 image push가 성공한 뒤에만 배포 Job이 시작된다.

Workflow는 다음 작업을 수행하는 명령 목록을 만든다.

```text
배포 디렉터리 생성
→ 현재 commit의 compose.yaml 다운로드
→ Nginx blue/green 설정 다운로드
→ deploy script 다운로드
→ 실행 권한 부여
→ IMAGE_TAG=현재 SHA로 배포 스크립트 실행
```

실제 Workflow가 만드는 명령 배열과 전송 코드:

```bash
PARAMETERS=$(jq -n \
  --arg deploy_path "${DEPLOY_PATH}" \
  --arg raw_base_url "${RAW_BASE_URL}" \
  --arg image_tag "${IMAGE_TAG}" \
  '{
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
  --timeout-seconds 900 \
  --parameters "${PARAMETERS}" \
  --query "Command.CommandId" \
  --output text)

echo "command_id=${COMMAND_ID}" >> "$GITHUB_OUTPUT"
```

SSH 포트를 열고 개인키로 직접 접속하는 대신 AWS Systems Manager가 관리하는 채널을 사용한다.

`jq -n`은 shell 문자열을 직접 이어 붙여 JSON을 만드는 대신, 배포 경로·URL·tag를 JSON 문자열로 안전하게 escape한다. `install -d`는 디렉터리를 만들고, `curl -f`는 HTTP 오류를 실패 exit code로 처리하며, 마지막 줄이 SHA tag를 환경변수로 전달해 EC2의 배포 스크립트를 실행한다. `$GITHUB_OUTPUT`에 기록한 `command_id`는 다음 Step이 `steps.send-command.outputs.command_id`로 읽는다.

Workflow는 `CommandId`를 받아 상태를 반복 조회하고 `Success`일 때만 성공한다.

실제 상태 조회 코드:

```bash
for attempt in {1..90}; do
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

  echo "Deployment status: ${STATUS:-Pending} (${attempt}/90)"
  sleep 5
done

echo "Backend deployment did not finish within 450 seconds." >&2
exit 1
```

90회마다 5초를 기다리므로 Workflow는 최대 약 450초 동안 조회한다. 하지만 `send-command`의 `--timeout-seconds`는 900초다. 따라서 450초가 지나 Workflow가 실패해도 SSM 명령을 취소하는 코드는 없으며, EC2의 배포 명령은 그 뒤에도 실행 중일 수 있다.

## 12.8 실제 배포 스크립트 흐름

색상 선택:

```bash
active_color="$(get_env_value BACKEND_ACTIVE_COLOR blue)"

case "${active_color}" in
  blue)
    target_color="green"
    target_port="$(get_env_value BACKEND_GREEN_PORT 8082)"
    ;;
  green)
    target_color="blue"
    target_port="$(get_env_value BACKEND_BLUE_PORT 8081)"
    ;;
esac
```

```text
현재 blue
→ target green

현재 green
→ target blue
```

배포 핵심:

```text
Redis 실행과 PING 확인
→ target image tag를 새 commit SHA로 변경
→ target image pull
→ target container 실행
→ target의 /actuator/health 확인
→ ACTIVE_COLOR 변경
→ Nginx 재생성
→ 같은 EC2의 host port에서 Nginx 경유 /health 확인
→ 이전 container 중지
```

배포 파일을 받기 전에 `.env`를 새로 생성하거나 다운로드하지 않는다. 다음 실제 코드처럼 EC2의 배포 경로에 이미 존재해야 한다.

```bash
DEPLOY_DIR="${DEPLOY_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-${DEPLOY_DIR}/compose.yaml}"
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG is required}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing environment file: ${ENV_FILE}" >&2
  exit 1
fi

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "Missing Compose file: ${COMPOSE_FILE}" >&2
  exit 1
fi
```

`.env`에는 RDS, Redis, JWT, 각 색상의 tag와 port 등이 들어간다. Workflow가 내려받는 것은 `compose.yaml`, Nginx 설정, 배포 스크립트이며 `.env`의 운영 비밀값은 덮어쓰지 않는다.

실제 `.env` 읽기·수정과 Compose 호출 함수:

```bash
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
```

`get_env_value`는 마지막으로 일치한 `KEY=value`에서 `value`만 꺼내고, 키가 없으면 전달받은 기본값을 쓴다. `set_env_value`는 키가 있으면 그 줄을 바꾸고 없으면 파일 끝에 추가한다. `compose`의 `"$@"`는 함수에 전달된 모든 인자를 원래 인자 경계를 유지한 채 `docker compose` 뒤에 전달한다.

## 12.9 rollback

새 target이 실패하면:

```text
target tag를 이전 값으로 복구
→ 실패한 target container 중지
→ 기존 active는 계속 서비스
```

Nginx 전환 후 같은 EC2의 Nginx 경유 health check가 실패하면:

```text
ACTIVE_COLOR를 이전 색상으로 복구
→ Nginx를 이전 upstream으로 재생성
→ 실패한 target 정리
```

배포 스크립트의 `set -Eeuo pipefail`은 처리하지 않은 명령 실패, 정의되지 않은 변수와 pipeline 실패를 조기에 중단하게 한다.

중요하게도 이 rollback은 성공이 보장되는 transaction이 아니라 최선 시도다. 실제 함수는 다음과 같다.

```bash
restore_inactive_container() {
  set_env_value "${target_tag_key}" "${previous_target_tag}"
  compose stop "${target_service}" >/dev/null 2>&1 || true
}

rollback_proxy() {
  set_env_value BACKEND_ACTIVE_COLOR "${active_color}"
  compose up -d --no-deps --force-recreate nginx >/dev/null 2>&1 || true
  restore_inactive_container
}
```

Nginx 재생성이나 container 중지 뒤의 `|| true`는 복구 명령이 실패해도 함수 실행을 계속하게 한다. 따라서 스크립트는 배포 실패로 종료하더라도 Nginx가 실제로 이전 상태로 돌아갔다고 보장할 수 없다. 특히 기존 backend 중지가 일부 진행된 뒤 실패하면 이전 색상으로 proxy를 돌리는 것만으로 서비스가 반드시 회복되는 것도 아니다.

## 12.10 프론트 CI/CD의 차이

검증:

```text
npm ci
→ npm run lint
→ npm run build
```

이미지 build argument:

```yaml
build-args: |
  VITE_API_BASE_URL=/api
```

프론트 배포 Nginx에는 `BACKEND_UPSTREAM`을 `envsubst`로 넣는다.

백엔드 배포 흐름을 이해하면 프론트는 다음 차이만 확인하면 된다.

- Redis 준비 없음
- Spring Actuator 대신 프론트 Nginx `/health`
- `/api/`를 별도 백엔드 서버로 proxy
- 기존 frontend container는 현재 스크립트에서 즉시 중지하지 않고 active proxy만 전환


### 프론트 image 빌드 인자 라인별 주석본

```yaml
build-args:                 # Dockerfile의 ARG로 전달할 build 시점 변수 목록이다.
  VITE_API_BASE_URL=/api    # Vite가 브라우저 bundle에 넣을 API 기준 주소를 /api로 지정한다.
```

이 값은 image가 만들어진 뒤 container를 실행할 때가 아니라 `npm run build`가 실행되는 시점에 JavaScript 코드에 포함된다.

## 12.11 OIDC 확인 Workflow

`aws-oidc-check.yml`은 전체 배포 전에 다음만 독립적으로 검사한다.

```text
AWS OIDC 인증 성공 여부
→ aws sts get-caller-identity

SSM 연결 성공 여부
→ EC2에서 hostname, whoami, docker --version
```

배포 문제를 인증·연결 문제와 애플리케이션 문제로 분리해서 진단할 수 있게 한다.

## 12.11.1 현재 배포 구조에서 반드시 알아야 할 한계

### 동시 배포를 막는 잠금이 없다

백엔드와 프론트 Workflow 각각에는 GitHub Actions의 `concurrency` 설정이 없고, 배포 스크립트에도 파일 lock이 없다. 같은 repository에 짧은 간격으로 두 commit이 push되어 같은 종류의 배포 run 두 개가 겹치면, 두 run이 해당 `DEPLOY_PATH`의 같은 `.env`, container와 Nginx를 동시에 바꿀 수 있다. 현재 코드만으로는 나중에 시작한 배포가 항상 최종 상태가 된다고 보장할 수 없다. 백엔드와 프론트가 서로 같은 배포 경로를 쓴다는 뜻은 아니며, 각 repository 안에서 이전 run과 다음 run이 겹치는 문제다.

### EC2가 GHCR image를 pull할 인증은 별도 전제다

Workflow Runner는 `docker/login-action`으로 GHCR에 로그인하지만 그 인증은 EC2로 전달되지 않는다. 배포 스크립트에도 `docker login`이 없다. 따라서 package가 private이면 EC2 Docker가 미리 GHCR에 로그인되어 있어야 하며, public package라면 인증 없이 pull할 수 있다.

### 배포 파일 다운로드는 인증 없는 raw URL을 사용한다

SSM 명령은 `https://raw.githubusercontent.com/...`에서 `curl`로 파일을 받으며 GitHub token을 붙이지 않는다. private repository라면 이 다운로드가 실패할 수 있으므로 별도의 인증 방식이나 artifact 전달 구조가 필요하다.

### Nginx 검사는 공개 인터넷 경로 검사가 아니다

```bash
wait_for_health "http://127.0.0.1:${nginx_port}/health"
```

이 요청은 EC2 자신이 host에 publish된 Nginx port로 보내는 것이다. Nginx가 새 backend로 연결되는지는 확인하지만 DNS, 외부 Load Balancer, Security Group의 inbound 경로, CDN과 실제 사용자 인터넷 경로까지 검증하지는 않는다.

### SSM 실행과 Workflow 대기 시간이 다르다

Workflow의 상태 조회는 450초 뒤 실패하지만 SSM 명령 제한은 900초이고 취소 단계가 없다. 이 경우 GitHub Actions 화면은 실패인데 EC2에서는 배포가 계속되는 상태가 생길 수 있다.

## 12.12 핵심 축약본

```text
CI:
코드 checkout → 언어 환경 → test/lint/build

Image:
Docker build → SHA tag → GHCR push

AWS:
OIDC → 임시 IAM Role → SSM

EC2:
배포 파일 다운로드 → 비활성 색상 실행
→ 내부 health → Nginx 전환
→ EC2 loopback의 Nginx 경유 health → 이전 색상 정리
→ 실패 시 rollback
```


## 12.12.1 전체 원문 코드 라인별 주석본

아래 주석은 학습을 위해 추가한 것이며 실제 프로젝트 파일에는 없다. 줄 끝 주석을 붙인 주석본은 의미를 따라가기 위한 설명용이므로 그대로 복사해 실행하지 않는다.

### Workflow trigger

```yaml
name: Backend CI/CD              # GitHub Actions 화면에 표시할 Workflow 이름이다.

on:                              # Workflow를 시작할 이벤트 조건이다.
  push:                          # branch에 commit이 push될 때의 조건이다.
    branches:
      - main                     # main branch push만 대상으로 한다.
  pull_request:                  # pull request가 생성·갱신될 때의 조건이다.
    branches:
      - main                     # main을 대상으로 하는 PR만 실행한다.
  workflow_dispatch:             # GitHub 화면에서 사람이 수동 실행할 수 있게 한다.
```

### 테스트 Job

```yaml
jobs:                            # 서로 의존하거나 병렬 실행할 Job 목록을 시작한다.
  test:                          # 다른 Job이 needs로 참조할 내부 Job ID다.
    name: Test backend           # GitHub 화면에 표시할 Job 이름이다.
    runs-on: ubuntu-latest       # GitHub가 제공하는 최신 Ubuntu Runner에서 실행한다.

    steps:                       # 이 Runner 안에서 순서대로 실행할 단계다.
      - name: Check out source code # 단계의 표시 이름이다.
        uses: actions/checkout@v6 # 현재 commit의 Repository 파일을 Runner로 내려받는다.

      - name: Set up Java 26     # Java 설치 단계 이름이다.
        uses: actions/setup-java@v5 # Java 설치와 Gradle cache를 지원하는 공식 Action을 사용한다.
        with:                    # Action에 전달할 입력값이다.
          distribution: temurin # Temurin JDK 배포판을 선택한다.
          java-version: "26"     # build.gradle toolchain과 같은 Java 26을 설치한다.
          cache: gradle          # Gradle dependency cache를 다음 실행에서도 재사용한다.

      - name: Grant execute permission to Gradle Wrapper # Linux에서 Wrapper 권한을 준비하는 단계다.
        run: chmod +x gradlew    # gradlew 파일에 실행 권한을 추가한다.

      - name: Run existing tests and verification # 최종 검증 단계 이름이다.
        run: ./gradlew clean check --no-daemon # 이전 산출물을 지우고 일반·Redis 테스트와 커버리지 검증을 실행한다.
```

### Image build와 push

```yaml
build-and-push:                  # 검증 후 image를 만드는 Job ID다.
  name: Build and push backend image # GitHub 화면에 표시할 Job 이름이다.
  if: github.event_name != 'pull_request' # PR 검증에서는 image 제작과 Registry push를 하지 않는다.
  needs:
    - test                       # test Job이 성공해야 이 Job이 시작된다.
  runs-on: ubuntu-latest         # test Job과 별개의 새 Ubuntu Runner에서 실행한다.

  steps:
    - name: Check out source code # 새 build Runner에 현재 commit 파일을 받는 단계다.
      uses: actions/checkout@v6 # Job 사이 workspace가 공유되지 않으므로 다시 checkout한다.

    - name: Set up Docker Buildx # Docker builder 준비 단계다.
      uses: docker/setup-buildx-action@v4 # cache와 멀티 플랫폼 기능이 있는 Docker Buildx를 준비한다.

    - name: Log in to GHCR       # Registry 인증 단계다.
      uses: docker/login-action@v4 # GHCR에 push할 Docker 인증을 수행한다.
      with:
        registry: ${{ env.REGISTRY }} # Workflow env의 ghcr.io 주소를 사용한다.
        username: ${{ github.actor }} # Workflow를 실행한 GitHub 계정을 사용자 이름으로 사용한다.
        password: ${{ secrets.GITHUB_TOKEN }} # GitHub가 실행마다 제공하는 임시 token을 비밀번호로 쓴다.

    - name: Prepare image tags and labels # tag와 OCI label 생성 단계다.
      id: metadata               # 다음 step이 output을 참조할 수 있도록 metadata ID를 준다.
      uses: docker/metadata-action@v6 # image tag와 label 문자열을 계산하는 Action이다.
      with:
        images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }} # Registry와 Repository image 이름을 합친다.
        tags: |                  # 한 image에 붙일 tag 규칙 목록이다.
          type=sha,format=long,prefix= # 전체 commit SHA를 변경 없이 tag로 만든다.
          type=raw,value=latest,enable=${{ github.ref == format('refs/heads/{0}', github.event.repository.default_branch) }} # default branch ref일 때만 latest를 만든다.

    - name: Build and push image # 실제 image build·push 단계다.
      uses: docker/build-push-action@v7 # Dockerfile로 image를 만들고 Registry에 push한다.
      with:
        context: .               # Repository root를 Docker build context로 사용한다.
        push: true               # build 후 로컬에만 두지 않고 GHCR로 전송한다.
        tags: ${{ steps.metadata.outputs.tags }} # 앞 단계가 계산한 SHA와 latest tag를 모두 적용한다.
        labels: ${{ steps.metadata.outputs.labels }} # metadata가 계산한 OCI label을 image에 넣는다.
        cache-from: type=gha     # 이전 GitHub Actions build cache를 읽는다.
        cache-to: type=gha,mode=max # 가능한 layer를 GitHub Actions cache에 저장한다.
```

### OIDC AWS 인증

```yaml
permissions:                     # 이 Job의 GitHub token 권한을 최소 범위로 다시 지정한다.
  id-token: write                # AWS가 검증할 OIDC ID token 발급 권한을 허용한다.
  contents: read                 # Repository 파일은 읽기만 허용한다.

steps:
  - name: Configure temporary AWS credentials # AWS 임시 인증 준비 단계다.
    uses: aws-actions/configure-aws-credentials@v6.1.1 # OIDC token을 AWS Role 자격 증명으로 교환하는 Action이다.
    with:
      role-to-assume: ${{ vars.AWS_DEPLOY_ROLE_ARN }} # GitHub를 신뢰하도록 설정된 IAM Role ARN이다.
      aws-region: ${{ vars.AWS_REGION }} # 이후 AWS CLI가 사용할 Region이다.
```

### 배포 Job 조건

```yaml
deploy:                         # EC2 배포를 담당하는 Job ID다.
  name: Deploy backend with Blue/Green # GitHub 화면에 표시할 이름이다.
  if: vars.DEPLOY_ENABLED == 'true' && github.event_name != 'pull_request' # 배포 활성 변수가 true이고 PR이 아닐 때만 실행한다.
  needs:
    - build-and-push             # image build와 GHCR push가 성공한 뒤 실행한다.
  runs-on: ubuntu-latest         # 앞 Job과 별개의 새 Runner를 배정받는다.
  permissions:
    id-token: write              # AWS OIDC token 발급을 허용한다.
    contents: read               # Repository 내용 읽기만 허용한다.
```

### SSM 명령 전송

```bash
PARAMETERS=$(jq -n \ # 입력 파일 없이 SSM parameters JSON을 만들고 결과를 PARAMETERS에 저장한다.
  --arg deploy_path "${DEPLOY_PATH}" \ # 배포 경로를 jq 문자열 변수로 안전하게 전달한다.
  --arg raw_base_url "${RAW_BASE_URL}" \ # 현재 commit의 deploy 파일 raw URL을 전달한다.
  --arg image_tag "${IMAGE_TAG}" \ # 현재 commit SHA인 image tag를 전달한다.
  '{ # jq가 생성할 JSON object를 시작한다.
    commands: [ # SSM Agent가 EC2에서 순서대로 실행할 shell 명령 배열이다.
      "set -Eeuo pipefail", # EC2 쪽 shell에도 엄격한 오류 처리 옵션을 켠다.
      ("install -d -m 755 " + $deploy_path + "/nginx"), # 배포 및 Nginx 설정 디렉터리를 만든다.
      ("curl -fsSL " + $raw_base_url + "/compose.yaml -o " + $deploy_path + "/compose.yaml"), # 해당 commit의 Compose 파일을 받는다.
      ("curl -fsSL " + $raw_base_url + "/nginx/backend-blue.conf -o " + $deploy_path + "/nginx/backend-blue.conf"), # blue용 Nginx 설정을 받는다.
      ("curl -fsSL " + $raw_base_url + "/nginx/backend-green.conf -o " + $deploy_path + "/nginx/backend-green.conf"), # green용 Nginx 설정을 받는다.
      ("curl -fsSL " + $raw_base_url + "/deploy-backend.sh -o " + $deploy_path + "/deploy-backend.sh"), # 배포 스크립트를 받는다.
      ("chmod 755 " + $deploy_path + "/deploy-backend.sh"), # 받은 스크립트에 실행 권한을 준다.
      ("cd " + $deploy_path), # Compose와 .env가 있는 배포 경로로 이동한다.
      ("IMAGE_TAG=" + $image_tag + " ./deploy-backend.sh") # SHA tag를 넘겨 실제 blue/green 배포를 시작한다.
    ] # commands 배열을 닫는다.
  }') # JSON object와 명령 치환을 닫고 결과를 PARAMETERS에 대입한다.
```

이 주석본은 줄 끝 주석 때문에 그대로 실행하는 코드가 아니다. 바로 위 12.7의 주석 없는 원문이 실행 가능한 실제 형식이다.

```bash
COMMAND_ID=$(aws ssm send-command \ # EC2에 원격 shell 명령을 보내고 결과 ID를 변수에 저장한다.
  --instance-ids "${DEPLOY_INSTANCE_ID}" \ # 배포할 관리 대상 EC2 ID를 지정한다.
  --document-name "AWS-RunShellScript" \ # EC2에서 Linux shell 명령을 실행하는 SSM 문서를 사용한다.
  --comment "Deploy backend commit ${IMAGE_TAG} with Blue/Green" \ # AWS 기록에 배포 commit 설명을 남긴다.
  --timeout-seconds 900 \ # SSM 명령 전체 제한 시간을 15분으로 지정한다.
  --parameters "${PARAMETERS}" \ # jq로 만든 실제 다운로드·배포 명령 목록을 전달한다.
  --query "Command.CommandId" \ # 전체 JSON 중 추적에 필요한 CommandId만 선택한다.
  --output text) # 선택한 ID를 plain text로 출력해 shell 변수에 담는다.
```

### SSM 상태 조회

```bash
for attempt in {1..90}; do # 1부터 90까지 최대 90번 상태를 조회한다.
  STATUS=$(aws ssm get-command-invocation \ # 특정 EC2에서 실행 중인 명령 결과를 조회한다.
    --command-id "${COMMAND_ID}" \ # 앞 Step에서 받은 SSM CommandId를 지정한다.
    --instance-id "${DEPLOY_INSTANCE_ID}" \ # 명령을 실행한 EC2 instance를 지정한다.
    --query "Status" \ # 전체 응답에서 Status만 선택한다.
    --output text 2>/dev/null || true) # text로 받고, 아직 조회가 안 되는 순간의 오류는 반복을 위해 무시한다.

  case "${STATUS}" in # 현재 상태 문자열에 따라 종료 여부를 결정한다.
    Success) # EC2 명령 전체가 성공한 경우다.
      exit 0 # Workflow Step을 성공으로 끝낸다.
      ;;
    Failed|Cancelled|TimedOut|Cancelling) # 재시도 대기가 의미 없는 실패·취소 상태들이다.
      exit 1 # Workflow Step을 실패로 끝낸다.
      ;;
  esac

  echo "Deployment status: ${STATUS:-Pending} (${attempt}/90)" # 빈 상태는 Pending으로 표시하고 진행 횟수를 출력한다.
  sleep 5 # 다음 AWS API 조회 전 5초 기다린다.
done

echo "Backend deployment did not finish within 450 seconds." >&2 # 90회 안에 끝나지 않았음을 표준 오류에 쓴다.
exit 1 # Workflow 대기를 실패로 끝내지만 SSM 명령을 취소하지는 않는다.
```

### 배포 대상 색상 선택

그 전에 배포 스크립트는 파일 위치를 결정하고 필수 파일을 검사한다.

```bash
DEPLOY_DIR="${DEPLOY_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}" # 외부 입력이 없으면 이 script가 있는 directory를 배포 경로로 쓴다.
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}" # 외부 입력이 없으면 배포 경로의 .env를 사용한다.
COMPOSE_FILE="${COMPOSE_FILE:-${DEPLOY_DIR}/compose.yaml}" # 외부 입력이 없으면 배포 경로의 Compose 파일을 사용한다.
IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG is required}" # image tag가 없거나 비어 있으면 즉시 오류로 종료한다.

if [[ ! -f "${ENV_FILE}" ]]; then # .env가 일반 파일로 존재하지 않는지 검사한다.
  echo "Missing environment file: ${ENV_FILE}" >&2 # 누락된 파일 경로를 표준 오류에 출력한다.
  exit 1 # 비밀값과 배포 상태를 추측하지 않고 종료한다.
fi

if [[ ! -f "${COMPOSE_FILE}" ]]; then # Compose 파일이 일반 파일로 존재하지 않는지 검사한다.
  echo "Missing Compose file: ${COMPOSE_FILE}" >&2 # 누락된 파일 경로를 표준 오류에 출력한다.
  exit 1 # container 구성을 실행할 수 없으므로 종료한다.
fi
```

`.env`를 다루는 핵심 함수의 주석본:

```bash
get_env_value() { # .env에서 한 key의 마지막 값을 읽는 함수다.
  local key="$1" # 첫 번째 함수 인자를 찾을 key로 저장한다.
  local default_value="$2" # 두 번째 함수 인자를 key가 없을 때의 기본값으로 저장한다.
  local line # 검색 결과 한 줄을 담을 지역 변수를 선언한다.

  line="$(grep -E "^${key}=" "${ENV_FILE}" | tail -n 1 || true)" # KEY=로 시작하는 줄 중 마지막 줄을 읽고, 없어도 script를 종료하지 않는다.

  if [[ -z "${line}" ]]; then # 검색 결과가 빈 문자열인지 확인한다.
    printf '%s' "${default_value}" # key가 없으면 줄바꿈 없이 기본값을 출력한다.
  else
    printf '%s' "${line#*=}" # 첫 번째 =까지 제거하여 value 부분만 출력한다.
  fi
}

set_env_value() { # .env의 key를 수정하거나 새로 추가하는 함수다.
  local key="$1" # 수정할 key를 첫 번째 인자에서 받는다.
  local value="$2" # 저장할 value를 두 번째 인자에서 받는다.

  if grep -qE "^${key}=" "${ENV_FILE}"; then # 기존 KEY= 줄이 있는지 출력 없이 검사한다.
    sed -i "s|^${key}=.*|${key}=${value}|" "${ENV_FILE}" # 있으면 해당 줄 전체를 새 KEY=value로 바꾼다.
  else
    printf '%s=%s\n' "${key}" "${value}" >> "${ENV_FILE}" # 없으면 파일 끝에 새 줄을 추가한다.
  fi
}

compose() { # 매번 같은 .env와 Compose 파일을 사용하게 감싼 함수다.
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@" # 함수가 받은 pull, up 같은 모든 인자를 Compose에 그대로 전달한다.
}
```

```bash
active_color="$(get_env_value BACKEND_ACTIVE_COLOR blue)" # .env에서 현재 색상을 읽고 없으면 blue로 본다.

case "${active_color}" in # 현재 색상 값에 따라 비활성 배포 대상을 고른다.
  blue) # blue가 현재 사용자 요청을 처리하는 경우다.
    target_color="green" # 새 버전은 green에 배포한다.
    target_port="$(get_env_value BACKEND_GREEN_PORT 8082)" # 새 green의 host health check 포트를 읽는다.
    ;;
  green) # green이 현재 활성인 경우다.
    target_color="blue" # 새 버전은 blue에 배포한다.
    target_port="$(get_env_value BACKEND_BLUE_PORT 8081)" # 새 blue의 host health check 포트를 읽는다.
    ;;
  *) # blue와 green 이외의 잘못된 값인 경우다.
    echo "Invalid BACKEND_ACTIVE_COLOR: ${active_color}" >&2 # 오류 원인을 표준 오류에 출력한다.
    exit 1 # 잘못된 대상을 임의 배포하지 않고 중단한다.
    ;;
esac
```

### 핵심 배포와 health check

```bash
set_env_value "${target_tag_key}" "${IMAGE_TAG}" # 비활성 색상이 사용할 image tag를 현재 commit SHA로 바꾼다.
compose pull "${target_service}" # GHCR에서 새 target image를 다운로드한다.
compose up -d --no-deps "${target_service}" # Redis·Nginx를 건드리지 않고 새 backend만 background로 실행한다.

if ! wait_for_health "http://127.0.0.1:${target_port}/actuator/health"; then # 새 container가 Spring 준비를 완료했는지 반복 확인한다.
  compose logs --tail=100 "${target_service}" || true # 실패 진단을 위해 마지막 로그를 출력한다.
  restore_inactive_container # target tag와 container를 이전 비활성 상태로 되돌린다.
  exit 1 # Nginx를 전환하지 않고 배포를 실패 처리한다.
fi

set_env_value BACKEND_ACTIVE_COLOR "${target_color}" # health를 통과한 색상을 새 활성값으로 기록한다.
compose up -d --no-deps --force-recreate nginx # 새 활성 설정을 읽도록 Nginx container를 다시 만든다.

if ! wait_for_health "http://127.0.0.1:${nginx_port}/health"; then # 같은 EC2의 host port에서 Nginx를 거쳐 새 backend 응답을 확인한다.
  rollback_proxy # 실패하면 이전 색상으로 Nginx와 환경값을 복구한다.
  exit 1 # 잘못된 전환을 성공으로 처리하지 않는다.
fi

compose stop "${previous_service}" # 새 경로가 정상인 뒤에만 기존 backend container를 중지한다.
```

### rollback 함수

```bash
rollback_proxy() { # Nginx 전환 뒤 실패했을 때 호출할 복구 절차다.
  set_env_value BACKEND_ACTIVE_COLOR "${active_color}" # .env의 활성 색상을 배포 전 값으로 되돌린다.
  compose up -d --no-deps --force-recreate nginx >/dev/null 2>&1 || true # Nginx 복구를 시도하며 이 명령 자체의 실패는 무시한다.
  restore_inactive_container # 실패한 새 target의 tag와 container도 정리한다.
}
```

`|| true` 때문에 rollback 함수가 호출되었다는 사실과 실제 proxy 복구 성공은 같은 뜻이 아니다. 장애 시에는 container 상태와 Nginx 응답을 별도로 다시 확인해야 한다.

## 12.13 스킵할 코드

- 백엔드와 프론트 Workflow의 동일한 checkout, Docker login, metadata 문법
- 90회 반복 상태 조회의 shell 문법
- blue와 green case의 이름만 다른 부분
- 결과 출력용 AWS query 문자열

다음은 스킵하지 않는다.

- trigger별 실행 차이
- Job의 `needs`
- PR에서 image build를 막는 조건
- SHA tag
- OIDC permissions
- SSM 명령 경로
- target health check와 EC2 loopback의 Nginx 경유 health check 차이
- rollback 시 환경변수 복구
- rollback 명령 실패를 무시하는 부분


## 12.13.1 이 장에서 필요한 GitHub Actions·Shell 문법

### Workflow, Job, Step, Action

```text
Workflow
→ YAML 파일 하나의 자동화 전체

Job
→ 독립 Runner에서 실행되는 작업 묶음

Step
→ Job 안에서 순서대로 실행되는 한 단계

Action
→ uses로 불러오는 재사용 가능한 자동화

Shell command
→ run으로 Runner에서 직접 실행
```

Job은 기본적으로 병렬이며 `needs`로 의존 순서를 만든다. Step은 같은 Job 안에서 위에서 아래로 실행된다.

### `uses`와 `with`

```yaml
uses: actions/setup-java@v5
with:
  java-version: "26"
```

`uses`는 외부 Action과 version을 선택하고, `with`는 그 Action이 정의한 입력값을 전달한다.

### GitHub 표현식

```yaml
${{ github.sha }}
${{ vars.AWS_REGION }}
```

이중 중괄호는 GitHub Actions 표현식이다. Workflow 실행 문맥에서 값을 평가한 뒤 Step이나 Action에 전달한다.

### Context

- `github`: event, branch, commit SHA, actor 등 실행 정보
- `env`: Workflow·Job·Step의 환경변수
- `vars`: Repository/Environment configuration variable
- `secrets`: masking과 접근 제한이 적용되는 민감값
- `steps.id.outputs`: 이전 Step이 파일을 통해 공개한 output
- `needs.job.outputs`: 선행 Job output

### `if`

Job이나 Step 실행 여부를 표현식으로 결정한다. shell의 `if`가 아니라 GitHub가 Runner 배정 또는 Step 실행 전에 평가한다.

### YAML `|`

```yaml
run: |
  command one
  command two
```

여러 줄 문자열을 줄바꿈과 함께 `run` 값 하나로 만든다. 그 내용은 Runner의 shell이 해석한다.

### Shell 변수 대입

```bash
active_color="blue"
```

`=` 주변에 공백을 쓰지 않는다. 값을 읽을 때 `$active_color` 또는 안전한 경계의 `${active_color}`를 사용한다.

### 명령 치환

```bash
active_color="$(get_env_value ...)"
```

`$(...)` 안 명령을 실행하고 표준 출력 문자열을 변수값으로 사용한다.

### Parameter expansion

```bash
"${IMAGE_TAG:?IMAGE_TAG is required}"
"${VALUE:-default}"
```

- `:?`: 없거나 비어 있으면 오류와 함께 shell 종료
- `:-`: 없거나 비어 있으면 기본값
- 큰따옴표는 공백과 wildcard가 있는 값도 한 인자로 유지한다.

### `set -Eeuo pipefail`

- `-E`: 함수·subshell에서도 ERR trap 상속
- `-e`: 처리되지 않은 명령 실패 시 종료
- `-u`: 정의되지 않은 변수 사용 시 오류
- `pipefail`: pipeline 중간 명령 실패도 전체 실패로 판단

이 옵션이 있어도 `|| true`, `if command`, `! command`처럼 실패를 의도적으로 처리한 위치는 종료하지 않을 수 있다.

### 함수

```bash
wait_for_health() {
    ...
}
```

이름으로 재사용할 shell 명령 묶음이다. 인자는 `$1`, `$2`로 받고 `local`로 함수 지역 변수를 선언한다.

### `case`

한 값의 여러 문자열 패턴을 분기한다. `;;`가 한 branch의 끝이고 `*)`는 앞 패턴에 해당하지 않는 기본 branch다.

### `if ! command; then`

명령 exit code를 반전하여 실패했을 때 블록을 실행한다. shell에서 0은 성공, 0이 아닌 값은 실패다.

### Redirection

- `>/dev/null`: 표준 출력 버림
- `2>&1`: 표준 오류를 표준 출력과 같은 곳으로 보냄
- `>> "$GITHUB_OUTPUT"`: Step output 파일 끝에 추가
- `>&2`: 메시지를 표준 오류로 보냄

### `|| true`

앞 명령이 실패해도 true를 실행해 전체 명령 결과를 성공으로 만든다. rollback이나 로그 출력처럼 실패해도 원래 오류 처리를 계속해야 하는 정리 작업에 사용한다. 남용하면 실제 장애를 숨긴다.

### Pipe와 `jq`

pipeline `A | B`는 A의 표준 출력을 B의 표준 입력으로 전달한다. `jq -n`은 입력 JSON 없이 인자들로 안전한 JSON을 생성하여 SSM parameters의 quoting 문제를 줄인다.

### AWS CLI `--query`와 `--output`

`--query`는 AWS JSON 응답에서 필요한 필드만 선택하고, `--output text`는 shell 변수에 넣기 쉬운 plain text로 출력한다.

### OIDC 신뢰 관계

GitHub가 서명한 ID token에는 Repository, branch 같은 claim이 들어간다. AWS IAM Role의 trust policy가 이를 확인한 뒤 임시 자격 증명을 발급한다. Workflow의 `id-token: write`는 AWS 권한 자체가 아니라 이 token을 요청할 GitHub 권한이다.

### SSM

SSM Agent가 등록된 EC2에 AWS API를 통해 명령을 전달한다. Workflow는 `CommandId`로 비동기 실행 상태와 출력을 조회한다. SSH key나 공개 SSH port가 없어도 되지만 IAM 권한과 SSM Agent·network 준비가 필요하다.

### Rollback

단순히 실패 container를 끄는 것만이 아니다. 사용자 트래픽을 결정하는 `ACTIVE_COLOR`, Nginx 설정과 비활성 tag를 배포 전 일관된 상태로 되돌려야 한다.

## 12.14 이해 확인

1. pull request와 main push에서 실행 범위는 어떻게 다른가?
2. `needs: test`는 build Job에 어떤 영향을 주는가?
3. image에 commit SHA tag를 붙이는 이유는 무엇인가?
4. OIDC를 사용하면 장기 AWS Access Key 저장을 어떻게 피할 수 있는가?
5. `vars`, `secrets`, `env`는 어떻게 다른가?
6. SSM을 사용하는 배포는 SSH 직접 접속과 무엇이 다른가?
7. 현재 active가 blue이면 새 버전은 어디에 실행되는가?
8. Nginx를 전환하기 전에 target health check를 하는 이유는 무엇인가?
9. Nginx 전환 후 Nginx 경유 health check가 실패하면 무엇을 복구하는가?
10. 프론트와 백엔드 CI/CD에서 서로 다른 검증과 배포 요소는 무엇인가?
11. OIDC 확인 Workflow를 전체 배포와 분리한 이유는 무엇인가?
12. `DEPLOY_ENABLED`가 설정되지 않았을 때 main push가 EC2 배포까지 진행되는가?
13. `127.0.0.1:${nginx_port}/health`가 확인하는 범위와 확인하지 못하는 범위는 무엇인가?
14. Workflow가 450초 뒤 실패해도 EC2 명령이 계속될 수 있는 이유는 무엇인가?
15. `rollback_proxy`가 호출되어도 서비스 복구를 보장할 수 없는 이유는 무엇인가?
16. private GHCR package를 사용한다면 EC2에 어떤 준비가 필요한가?
17. 같은 backend 또는 frontend Workflow의 배포 run 두 개가 겹칠 때 생길 수 있는 문제는 무엇인가?

## 12.15 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.
