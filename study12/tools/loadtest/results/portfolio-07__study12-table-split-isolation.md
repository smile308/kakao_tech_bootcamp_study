# study12 테이블 분리 효과 검증

## 검증 목적

기존 개선 전후 비교에서는 다음 두 변경이 동시에 적용되었습니다.

- 조회수 영구 저장 위치를 `post_counters`에서 `post_view_counts`로 분리했습니다.
- 상세 조회의 조회수 증가를 MySQL에서 Redis로 옮겼습니다.

따라서 study12에서 확인된 락 대기 감소가 테이블 분리 때문인지 Redis 때문인지 분리할 수 없었습니다.

이번 테스트에서는 study12 코드를 그대로 사용하되 `VIEW_COUNT_REDIS_ENABLED=false`로 실행했습니다. Redis 컨테이너는 실행 상태로 유지했지만, 백엔드는 `DatabaseViewCountUpdater`를 사용해 매 상세 조회마다 `post_view_counts`를 MySQL에서 원자적으로 갱신하도록 했습니다.

이 조건은 테이블 분리만 적용된 상태를 확인하기 위한 것입니다.

## 테스트 조건

| 항목 | 값 |
|---|---|
| 대상 버전 | `study12_table-only` |
| 조회수 테이블 | `post_view_counts` |
| Redis 조회수 증가 | 비활성화 |
| Redis 컨테이너 | 실행 |
| 부하 프로필 | `mixed-normal` |
| 조회 요청 | 초당 200회 |
| 댓글 생명주기 | 초당 5회 생성·삭제 |
| 실행 시간 | 60초 |
| 게시글 | 1개 |
| 초기 댓글 | 100개 |
| MySQL 락 관찰 | 75초 |

## table-only 실행 결과

| 차수 | 성공 조회 | dropped iterations | 댓글 생명주기 p95 | 댓글 생명주기 p99 | 락 대기 증가 | 락 대상 |
|---|---:|---:|---:|---:|---:|---|
| 1차 | 12,000 | 0 | 40ms | 132ms | 1,237회 | `post_view_counts` |
| 2차 | 11,870 | 131 | 387ms | 1,705ms | 4,047회 | `post_view_counts` |
| 3차 | 12,001 | 0 | 21ms | 64ms | 684회 | `post_view_counts` |

세 실행 모두 다음 정합성 검증은 통과했습니다.

- HTTP 실패율 0%.
- 댓글 생성·삭제 생명주기 실패율 0%.
- 종료 댓글 카운터 100.
- 실제 댓글 행 100.
- Redis 조회수 값은 비활성화 상태로 `not-applicable`.

2차는 HTTP 실패는 없었지만 `dropped_iterations=131`이 발생해 예정된 조회량을 모두 시작하지 못했습니다. 따라서 2차는 오류 결과로 삭제하지 않고, MySQL 조회수 행 경합이 처리량과 지연시간을 악화시킨 관찰 자료로 별도 표시합니다. 동일 부하의 대표값을 계산할 때는 1차와 3차의 완전 실행 결과와 분리해서 봅니다.

## 기존 결과와의 비교

| 조건 | 조회수 증가 방식 | 락 대상 | 혼합 테스트 락 대기 |
|---|---|---|---:|
| study11 기존 | `post_counters` MySQL 원자적 UPDATE | `post_counters` | 1,400회, 2,105회, 1,658회 |
| study12 table-only | `post_view_counts` MySQL 원자적 UPDATE | `post_view_counts` | 1,237회, 4,047회, 684회 |
| study12 Redis-on | Redis 원자적 증가 후 주기적 MySQL 반영 | 기존 혼합 요청에서는 관찰되지 않음 | 0회, 0회, 0회 |

기존 study11 혼합 테스트의 `wait_detail`은 모두 `post_counters`를 가리켰습니다. 새 table-only 테스트의 `wait_detail`은 모두 `post_view_counts`를 가리켰고 `post_counters`는 나타나지 않았습니다.

## 결론

이번 검증으로 테이블 분리와 Redis의 효과를 다음처럼 나눠서 설명할 수 있습니다.

### 테이블 분리로 확인된 효과

조회 요청과 댓글 요청이 같은 카운터 행을 사용하지 않게 되었습니다.

study11에서는 조회와 댓글이 `post_counters`를 함께 갱신했지만, table-only 조건에서는 조회가 `post_view_counts`, 댓글이 `post_counters`를 갱신했습니다. 그 결과 table-only의 락 대기는 `post_view_counts`에서만 확인되었고 `post_counters`에서의 조회·댓글 교차 경합은 관찰되지 않았습니다.

다만 전체 락 대기 횟수가 사라진 것은 아닙니다. Redis를 끄면 조회 요청끼리는 여전히 같은 `post_view_counts` 행을 갱신하기 때문에 조회·조회 경합이 남습니다.

### Redis로 추가로 확인된 효과

study12 Redis-on 조건에서는 상세 조회 요청이 매번 MySQL의 `post_view_counts`를 갱신하지 않습니다. 따라서 table-only 조건에서 관찰된 `post_view_counts` 조회·조회 경합이 요청 경로에서 제거됩니다.

따라서 기존의 “study12에서 락 대기가 0회가 되었다”는 결과는 테이블 분리만의 결과가 아니라, 조회수 증가를 Redis로 옮겨 요청 경로의 MySQL 쓰기를 없앤 효과까지 포함한 결과입니다.

## 포트폴리오에 사용할 표현

이번 추가 검증을 반영하면 다음과 같이 설명하는 것이 정확합니다.

> 조회수 테이블을 분리한 결과 댓글은 `post_counters`를 계속 사용하면서도 조회수 요청과 같은 행을 경쟁하지 않게 되었다. 다만 Redis를 사용하지 않으면 조회 요청끼리는 분리된 `post_view_counts` 행에서 계속 경합했다. 이후 Redis 원자적 증가를 적용하면서 조회 요청의 MySQL 쓰기 자체를 요청 경로에서 제거했고, 최종적으로 조회·댓글 혼합 부하에서 관찰되던 MySQL 락 대기가 사라졌다.

## 원본 증거 파일

### table-only 신규 실행

- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-01__k6-console.txt`
- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-01__k6-summary.json`
- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-01__mysql-locks.txt`
- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-01__run-metadata.txt`
- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-02__k6-console.txt`
- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-02__k6-summary.json`
- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-02__mysql-locks.txt`
- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-02__run-metadata.txt`
- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-03__k6-console.txt`
- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-03__k6-summary.json`
- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-03__mysql-locks.txt`
- `table-only-isolation-20260810/study12_table-only__post-counter-interference-comments100__mixed-normal__run-03__run-metadata.txt`

2차 파일은 실행 자체는 성공했지만 `dropped_iterations=131`이 있어 완전 실행 결과와 구분합니다.

### 비교에 사용한 기존 파일

- `study11_before__post-counter-interference-comments100__mixed-normal__run-01~03__k6-summary.json`
- `study11_before__post-counter-interference-comments100__mixed-normal__run-01~03__mysql-locks.txt`
- `study12_after__post-counter-interference-comments100__mixed-normal__run-01~03__k6-summary.json`
- `study12_after__post-counter-interference-comments100__mixed-normal__run-01~03__mysql-locks.txt`

