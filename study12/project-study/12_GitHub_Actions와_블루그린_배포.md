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

      - name: Grant execute permission
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
  if: github.event_name != 'pull_request'
  needs:
    - test

  steps:
    - uses: docker/setup-buildx-action@v4

    - uses: docker/login-action@v4
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}

    - id: metadata
      uses: docker/metadata-action@v6
      with:
        images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
        tags: |
          type=sha,format=long,prefix=
          type=raw,value=latest

    - uses: docker/build-push-action@v7
      with:
        context: .
        push: true
        tags: ${{ steps.metadata.outputs.tags }}
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

`latest`만 사용하면 어떤 commit을 배포했는지 추적하기 어렵다. 배포 스크립트에는 현재 `${{ github.sha }}`가 전달된다.

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

Workflow는 다음 작업을 수행하는 명령 목록을 만든다.

```text
배포 디렉터리 생성
→ 현재 commit의 compose.yaml 다운로드
→ Nginx blue/green 설정 다운로드
→ deploy script 다운로드
→ 실행 권한 부여
→ IMAGE_TAG=현재 SHA로 배포 스크립트 실행
```

그 뒤:

```bash
aws ssm send-command \
  --instance-ids "${DEPLOY_INSTANCE_ID}" \
  --document-name "AWS-RunShellScript" \
  --parameters "${PARAMETERS}"
```

SSH 포트를 열고 개인키로 직접 접속하는 대신 AWS Systems Manager가 관리하는 채널을 사용한다.

Workflow는 `CommandId`를 받아 상태를 반복 조회하고 `Success`일 때만 성공한다.

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
→ 외부 /health 확인
→ 이전 container 중지
```

## 12.9 rollback

새 target이 실패하면:

```text
target tag를 이전 값으로 복구
→ 실패한 target container 중지
→ 기존 active는 계속 서비스
```

Nginx 전환 후 외부 health check가 실패하면:

```text
ACTIVE_COLOR를 이전 색상으로 복구
→ Nginx를 이전 upstream으로 재생성
→ 실패한 target 정리
```

배포 스크립트의 `set -Eeuo pipefail`은 처리하지 않은 명령 실패, 정의되지 않은 변수와 pipeline 실패를 조기에 중단하게 한다.

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
→ 외부 health → 이전 색상 정리
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

      - name: Grant execute permission # Linux에서 Wrapper 권한을 준비하는 단계다.
        run: chmod +x gradlew    # gradlew 파일에 실행 권한을 추가한다.

      - name: Run existing tests and verification # 최종 검증 단계 이름이다.
        run: ./gradlew clean check --no-daemon # 이전 산출물을 지우고 일반·Redis 테스트와 커버리지 검증을 실행한다.
```

### Image build와 push

```yaml
build-and-push:                  # 검증 후 image를 만드는 Job ID다.
  if: github.event_name != 'pull_request' # PR 검증에서는 image 제작과 Registry push를 하지 않는다.
  needs:
    - test                       # test Job이 성공해야 이 Job이 시작된다.

  steps:
    - uses: docker/setup-buildx-action@v4 # cache와 멀티 플랫폼 기능이 있는 Docker Buildx를 준비한다.

    - uses: docker/login-action@v4 # GHCR에 push할 Docker 인증을 수행한다.
      with:
        registry: ${{ env.REGISTRY }} # Workflow env의 ghcr.io 주소를 사용한다.
        username: ${{ github.actor }} # Workflow를 실행한 GitHub 계정을 사용자 이름으로 사용한다.
        password: ${{ secrets.GITHUB_TOKEN }} # GitHub가 실행마다 제공하는 임시 token을 비밀번호로 쓴다.

    - id: metadata               # 다음 step이 output을 참조할 수 있도록 metadata ID를 준다.
      uses: docker/metadata-action@v6 # image tag와 label 문자열을 계산하는 Action이다.
      with:
        images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }} # Registry와 Repository image 이름을 합친다.
        tags: |                  # 한 image에 붙일 tag 규칙 목록이다.
          type=sha,format=long,prefix= # 전체 commit SHA를 변경 없이 tag로 만든다.
          type=raw,value=latest  # latest라는 읽기 쉬운 추가 tag도 만든다.

    - uses: docker/build-push-action@v7 # Dockerfile로 image를 만들고 Registry에 push한다.
      with:
        context: .               # Repository root를 Docker build context로 사용한다.
        push: true               # build 후 로컬에만 두지 않고 GHCR로 전송한다.
        tags: ${{ steps.metadata.outputs.tags }} # 앞 단계가 계산한 SHA와 latest tag를 모두 적용한다.
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

### SSM 명령 전송

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

### 배포 대상 색상 선택

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

if ! wait_for_health "http://127.0.0.1:${nginx_port}/health"; then # 실제 외부 입구를 통한 새 backend 응답도 확인한다.
  rollback_proxy # 실패하면 이전 색상으로 Nginx와 환경값을 복구한다.
  exit 1 # 잘못된 전환을 성공으로 처리하지 않는다.
fi

compose stop "${previous_service}" # 새 경로가 정상인 뒤에만 기존 backend container를 중지한다.
```

### rollback 함수

```bash
rollback_proxy() { # Nginx 전환 뒤 실패했을 때 호출할 복구 절차다.
  set_env_value BACKEND_ACTIVE_COLOR "${active_color}" # .env의 활성 색상을 배포 전 값으로 되돌린다.
  compose up -d --no-deps --force-recreate nginx >/dev/null 2>&1 || true # Nginx를 이전 upstream 설정으로 다시 만든다.
  restore_inactive_container # 실패한 새 target의 tag와 container도 정리한다.
}
```

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
- health check 전후의 Nginx 전환
- rollback 시 환경변수 복구


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
9. Nginx 전환 후 외부 health check가 실패하면 무엇을 복구하는가?
10. 프론트와 백엔드 CI/CD에서 서로 다른 검증과 배포 요소는 무엇인가?
11. OIDC 확인 Workflow를 전체 배포와 분리한 이유는 무엇인가?

## 12.15 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.
