# study11 교차 간섭 테스트 — 조건과 정합성

## 무엇을 비교했는가

같은 게시글(`post_id=1`)에 동일한 댓글 쓰기 부하를 유지하고, 조회 트래픽 유무만 변경했다.

| 구분 | 댓글 생성 후 삭제 | 게시글 상세 조회 | 실행 시간 | 반복 |
|---|---:|---:|---:|---:|
| 댓글 전용 | 초당 5회 | 없음 | 60초 | 3회 |
| 조회·댓글 혼합 | 초당 5회 | 초당 200회 | 60초 | 3회 |

댓글 한 생명주기는 `댓글 생성 → 방금 생성한 댓글 삭제`이다. 이 방법으로 `reply_count` 쓰기를 계속 발생시키면서 테스트 전후 댓글 수를 100개로 유지했다.

## 실행 환경

| 항목 | 값 |
|---|---|
| 대상 버전 | `study11_baseline` |
| 부하 도구 | `grafana/k6:2.0.0` |
| 부하 모델 | k6 `constant-arrival-rate` |
| 백엔드 | Spring Boot 4.0.6, Java 26, 컨테이너 1개 |
| 데이터베이스 | MySQL 8.4, 컨테이너 1개 |
| Docker 가용 자원 | CPU 10개, 메모리 약 7.75GiB |
| 실행 위치 | 부하 생성기·백엔드·MySQL 모두 같은 로컬 Mac |

따라서 이 결과는 운영 환경의 절대 처리량이 아니라, 동일 환경에서 study11과 이후 study12를 비교하기 위한 상대 자료이다.

## 유효 실행과 정합성

| 조건·차수 | 실행 시각 | HTTP 실패 | 댓글 생명주기 실패 | 종료 댓글 카운터 | 종료 댓글 행 | 성공 조회와 조회수 증가 |
|---|---|---:|---:|---:|---:|---|
| 댓글 전용 1차 | 16:01:38 | 0 | 0 | 100 | 100 | 조회 없음 |
| 댓글 전용 2차 | 16:30:40 | 0 | 0 | 100 | 100 | 조회 없음, 증가 0 |
| 댓글 전용 3차 | 16:33:14 | 0 | 0 | 100 | 100 | 조회 없음, 증가 0 |
| 혼합 1차 | 16:04:16 | 0 | 0 | 100 | 100 | 12,001회 일치 |
| 혼합 2차 | 16:27:08 | 0 | 0 | 100 | 100 | 11,988회 일치 |
| 혼합 3차 | 16:35:48 | 0 | 0 | 100 | 100 | 12,000회 일치 |

혼합 2차는 예정된 iteration 13개, 혼합 3차는 1개를 시작하지 못했지만 실제로 시작된 HTTP 요청은 모두 성공했다. 성공한 조회 요청 수와 DB 조회수 증가량도 일치했다.

## 이 표를 만든 원본

### k6 요청 성공 여부와 요청 수

- `study11_baseline__post-counter-interference-comments100__comment-only__20260730-160138__k6-summary.json`
- `study11_baseline__post-counter-interference-comments100__comment-only__20260730-163040__k6-summary.json`
- `study11_baseline__post-counter-interference-comments100__comment-only__20260730-163314__k6-summary.json`
- `study11_baseline__post-counter-interference-comments100__mixed-normal__20260730-160416__k6-summary.json`
- `study11_baseline__post-counter-interference-comments100__mixed-normal__20260730-162708__k6-summary.json`
- `study11_baseline__post-counter-interference-comments100__mixed-normal__20260730-163548__k6-summary.json`

### 실행 전후 DB 값

- `study11_baseline__post-counter-interference-comments100__comment-only__20260730-163040__run-metadata.txt`
- `study11_baseline__post-counter-interference-comments100__comment-only__20260730-163314__run-metadata.txt`
- `study11_baseline__post-counter-interference-comments100__mixed-normal__20260730-162708__run-metadata.txt`
- `study11_baseline__post-counter-interference-comments100__mixed-normal__20260730-163548__run-metadata.txt`

1차 실행 때는 메타데이터 자동 저장 기능이 없어서 실행 터미널 출력으로 100/100과 조회수 일치를 확인했다. 이 보존상의 한계를 발견한 뒤 실행기를 보완했으며, 2·3차부터는 위 `run-metadata.txt`에 자동 저장했다.
