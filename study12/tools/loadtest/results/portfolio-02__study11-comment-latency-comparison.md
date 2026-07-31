# study11 교차 간섭 테스트 — 댓글 지연시간 비교

## 3회 측정 결과

아래 대표값은 실행별 편차와 순간 이상치의 영향을 줄이기 위해 3회 결과의 중앙값을 사용했다.

| 핵심 지표 | 댓글 전용 3회 중앙값 | 조회·댓글 혼합 3회 중앙값 | 변화 |
|---|---:|---:|---:|
| 댓글 생성 p95 | 14.12ms | 29.04ms | 약 2.06배 |
| 댓글 생성 p99 | 17.53ms | 77.47ms | 약 4.42배 |
| 댓글 삭제 p95 | 8.29ms | 16.34ms | 약 1.97배 |
| 댓글 삭제 p99 | 13.07ms | 66.66ms | 약 5.10배 |
| 생성→삭제 생명주기 p95 | 22ms | 52ms | 약 2.36배 |
| 생성→삭제 생명주기 p99 | 29ms | 181.21ms | 약 6.25배 |

## 각 실행의 원자료 값

| 조건 | 댓글 생성 p95 | 댓글 생성 p99 | 댓글 삭제 p95 | 댓글 삭제 p99 | 생명주기 p95 | 생명주기 p99 |
|---|---:|---:|---:|---:|---:|---:|
| 댓글 전용 1차 | 16.95ms | 19.65ms | 9.69ms | 13.34ms | 26ms | 34ms |
| 댓글 전용 2차 | 14.12ms | 16.90ms | 8.29ms | 13.07ms | 22ms | 29ms |
| 댓글 전용 3차 | 13.81ms | 17.53ms | 7.06ms | 10.75ms | 21ms | 26ms |
| 혼합 1차 | 18.45ms | 63.75ms | 16.34ms | 38.26ms | 36ms | 94ms |
| 혼합 2차 | 45.60ms | 188.14ms | 27.66ms | 125.70ms | 72ms | 355ms |
| 혼합 3차 | 29.04ms | 77.47ms | 16.28ms | 66.66ms | 52ms | 181.21ms |

## 읽는 방법

- `p95=52ms`: 댓글 생명주기 요청 100개 중 약 95개는 52ms 안에 끝났고, 약 5개는 그보다 느렸다는 뜻이다.
- `p99=181.21ms`: 요청 100개 중 가장 느린 쪽 약 1%가 181.21ms보다 오래 걸렸다는 뜻이다.
- 평균 대신 p95·p99를 본 이유: 빠른 요청 다수가 평균을 낮춰도 사용자가 가끔 겪는 긴 지연은 p95·p99에 드러나기 때문이다.

## 해석

댓글 부하는 동일했지만 조회 200회/초를 추가하자 댓글 생명주기의 p95 중앙값은 약 2.36배, p99 중앙값은 약 6.25배가 됐다. 조회와 댓글이 서로 다른 카운터만 변경해도 같은 `post_counters` 행을 갱신하기 때문에 교차 간섭이 발생했다는 성능 측 근거다.

## 이 표를 만든 원본

- `study11_before__post-counter-interference-comments100__comment-only__run-01__k6-summary.json`
- `study11_before__post-counter-interference-comments100__comment-only__run-02__k6-summary.json`
- `study11_before__post-counter-interference-comments100__comment-only__run-03__k6-summary.json`
- `study11_before__post-counter-interference-comments100__mixed-normal__run-01__k6-summary.json`
- `study11_before__post-counter-interference-comments100__mixed-normal__run-02__k6-summary.json`
- `study11_before__post-counter-interference-comments100__mixed-normal__run-03__k6-summary.json`

사용 지표:

- `comment_create_duration`의 `p(95)`, `p(99)`
- `comment_delete_duration`의 `p(95)`, `p(99)`
- `comment_lifecycle_duration`의 `p(95)`, `p(99)`
