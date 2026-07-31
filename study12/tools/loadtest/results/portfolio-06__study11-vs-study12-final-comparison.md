# 인기 게시글 조회수 동시성 개선 전후 비교

## 비교 목적

study11은 게시글 상세 조회마다 MySQL의 `post_counters` 한 행을 갱신했다. 같은 행에는 조회수뿐 아니라 좋아요·댓글·신고 수도 함께 있었다. 인기 게시글에 조회 요청이 집중되면 조회 요청끼리 경쟁하고, 댓글처럼 다른 카운터를 변경하는 요청까지 같은 행 락의 영향을 받을 수 있었다.

study12에서는 조회수를 다음 구조로 변경했다.

- 상세 조회수 증가는 Redis 원자적 증가 사용
- 변경된 조회수는 약 5초마다 `post_view_counts`에 반영
- 여러 백엔드의 반영 작업은 Redisson 분산 락으로 단일 실행
- MySQL 반영은 기존 값과 Redis 스냅샷 중 큰 값을 저장
- 저장 중 새 조회가 발생하면 Redis의 dirty 표시를 유지해 재반영
- 목록은 MySQL 반영값, 상세는 Redis 증가 결과 사용

## 공통 테스트 환경

| 항목 | 값 |
|---|---|
| 부하 도구 | `grafana/k6:2.0.0` |
| 백엔드 | Spring Boot, 컨테이너 1개 |
| 데이터베이스 | MySQL 8.4, 컨테이너 1개 |
| 개선 후 추가 구성 | Redis 7.4, AOF `everysec` |
| 대상 데이터 | 게시글 1개, 댓글 100개 |
| 실행 위치 | k6·백엔드·MySQL·Redis가 같은 로컬 Mac의 Docker 자원을 공유 |

이 결과는 운영 환경의 절대 최대 성능이 아니라 같은 장비와 같은 k6 시나리오에서 개선 전후를 비교한 상대 자료다.

## 1. 인기 게시글 조회 용량 비교

용량 테스트는 동일한 게시글 상세 API에 초당 200회로 시작해 500회, 최대 1,000회까지 요청했다. 예정 요청은 39,250건이었다.

| 지표 | study11 이전 | study12 이후 | 변화 |
|---|---:|---:|---:|
| 완료 요청 | 29,979 | 39,250 | 9,271건 증가, 약 30.93% |
| 시작하지 못한 요청 | 9,271 | 0 | 전부 제거 |
| 평균 처리율 | 374.65 req/s | 490.51 req/s | 약 30.92% 증가 |
| 평균 응답시간 | 1,012.81ms | 2.21ms | 약 99.78% 감소 |
| p95 응답시간 | 2,366.76ms | 2.98ms | 약 99.87% 감소 |
| p99 응답시간 | 2,874.93ms | 8.24ms | 약 99.71% 감소 |
| HTTP 실패 | 0 | 0 | 완료된 요청은 모두 성공 |

study11의 9,271건은 HTTP 오류가 아니라 필요한 VU를 확보하지 못해 시작하지 못한 `dropped_iterations`다. study12는 같은 예정 요청 39,250건을 모두 시작하고 성공시켰다.

study12의 성공 요청은 조회수 증가량과도 일치했다.

| 검증 항목 | 값 |
|---|---:|
| 성공 상세 조회 | 39,250 |
| 조회수 증가량 | 39,250 |
| 종료 Redis 조회수 | 75,799 |
| 종료 MySQL 조회수 | 75,799 |

## 2. 댓글 카운터 교차 간섭 비교

댓글 전용 조건은 댓글 생성·삭제를 초당 5회 수행했다. 혼합 조건은 같은 댓글 부하에 게시글 상세 조회 초당 200회를 추가했다. 각 조건은 60초씩 3회 실행했고, 지연시간은 3회 결과의 중앙값을 사용했다.

### 댓글 생명주기 지연 중앙값

| 버전 | 조건 | p95 | p99 |
|---|---|---:|---:|
| study11 | 댓글 전용 | 22ms | 29ms |
| study11 | 조회·댓글 혼합 | 52ms | 181.21ms |
| study12 | 댓글 전용 | 31.05ms | 38ms |
| study12 | 조회·댓글 혼합 | 13ms | 38ms |

study11은 조회 트래픽을 추가하자 댓글 p95가 약 2.36배, p99가 약 6.25배가 됐다. 반면 study12는 혼합 조건의 p95가 댓글 전용의 약 0.42배였고 p99는 같았다. 이는 조회 트래픽 추가로 인한 댓글 지연 악화가 재현되지 않았다는 결과다.

study12 댓글 전용의 절대 지연은 study11 댓글 전용보다 높았다. 따라서 “모든 댓글 요청이 Redis 도입으로 빨라졌다”고 해석하지 않는다. JVM 워밍업, 로컬 자원 상태, 실행 순서의 영향을 받을 수 있다. 핵심 판단은 같은 버전 안에서 조회 트래픽을 추가했을 때의 변화와 MySQL 락 대기 자료를 함께 보는 것이다.

### 댓글 전용 절대 지연 증가 해석

| 댓글 전용 지표 | study11 중앙값 | study12 중앙값 | 변화 |
|---|---:|---:|---:|
| 댓글 생성 p95 | 14.12ms | 19.94ms | 약 41.2% 증가 |
| 댓글 삭제 p95 | 8.29ms | 10.38ms | 약 25.1% 증가 |
| 전체 생명주기 p95 | 22ms | 31.05ms | 약 41.1% 증가 |

이 차이를 Redis 구조의 직접적인 성능 회귀로 단정하지 않는다.

1. study11과 study12의 `CommentService` 댓글 생성·삭제 경로는 동일하다.
2. 새 `PostViewCount` 연관관계는 지연 로딩이며 댓글 생성·삭제 쿼리에서 조회하지 않는다.
3. 두 버전의 댓글 전용 테스트 모두 MySQL 행 락 대기 증가가 0이었다.
4. study12에서 생성과 삭제 지연이 함께 증가했고, 실행을 반복할수록 생명주기 p95가 `33ms → 31.05ms → 27ms`로 감소했다. 특정 SQL 락 병목보다는 JVM JIT 컴파일, 커넥션 풀과 MySQL 버퍼 워밍업, 당시 로컬 자원 상태의 영향을 받았을 가능성이 있다.
5. study12는 같은 로컬 Docker 환경에 Redis 컨테이너와 Redisson 네트워크 스레드·주기 작업이 추가됐다. 댓글 요청이 Redis를 직접 사용하지 않더라도 CPU와 메모리를 공유하므로 간접 오버헤드 가능성을 완전히 배제할 수 없다.

이번 테스트만으로 위 요인별 영향을 분리하거나 정확한 원인을 확정할 수는 없다. 이를 엄밀하게 구분하려면 각 버전의 컨테이너 자원을 고정하고 동일한 워밍업을 수행한 뒤 `study11 → study12 → study11 → study12`처럼 실행 순서를 교차해 반복해야 한다.

따라서 이 보고서의 결론은 “댓글 자체가 전반적으로 빨라졌다”가 아니다. 검증된 개선점은 조회 트래픽을 추가했을 때 study11에서 발생한 댓글 꼬리 지연 증가와 `post_counters` 행 락 대기가 study12에서는 재현되지 않았다는 것이다.

### MySQL 행 락 대기

| 버전·조건 | 3회 실행의 평균 락 대기 횟수 | 대기 1회당 평균 시간 |
|---|---:|---:|
| study11 댓글 전용 | 0 | 해당 없음 |
| study11 조회·댓글 혼합 | 1,721회 | 15.04ms |
| study12 댓글 전용 | 0 | 해당 없음 |
| study12 조회·댓글 혼합 | 0 | 해당 없음 |

study11 혼합 테스트의 순간 락 상세는 `post_counters` 기본키 레코드를 가리켰다. study12에서는 세 번의 혼합 테스트 모두 MySQL 행 락 대기 증가가 0이었다. 조회수 증가를 Redis로 옮기고 영구 조회수 행을 `post_view_counts`로 분리하면서 댓글의 `reply_count` 갱신과 조회수 갱신이 같은 행을 경쟁하지 않게 된 결과와 일치한다.

## 3. 혼합 테스트 정합성

study12 혼합 테스트 세 번은 모두 다음 결과를 보였다.

| 차수 | 성공 조회 | 조회수 증가 | HTTP 실패 | 종료 댓글 카운터·행 |
|---|---:|---:|---:|---:|
| 1차 | 12,001 | 12,001 | 0 | 100 / 100 |
| 2차 | 12,001 | 12,001 | 0 | 100 / 100 |
| 3차 | 12,001 | 12,001 | 0 | 100 / 100 |

각 실행이 끝난 뒤 Redis 조회수와 MySQL 조회수도 일치했다. 처리량 개선 과정에서 조회수 유실이나 댓글 카운터 불일치는 관찰되지 않았다.

## 결론

study11의 문제는 MySQL을 사용했다는 사실 자체가 아니라, 인기 게시글의 매 조회마다 동일한 카운터 행을 갱신한 구조였다. study12는 요청 경로의 조회수 쓰기를 Redis 원자적 증가로 바꾸고, MySQL에는 묶어서 반영했다. 또한 조회수 영구 저장 행을 다른 카운터와 분리했다.

그 결과 동일한 로컬 시나리오에서 다음 변화가 확인됐다.

1. 용량 테스트의 예정 요청 39,250건을 누락 없이 처리했다.
2. p95 응답시간이 2,366.76ms에서 2.98ms로 감소했다.
3. 조회·댓글 혼합 테스트의 MySQL 행 락 대기가 실행당 평균 1,721회에서 0회가 됐다.
4. 조회 트래픽 추가로 발생하던 댓글 꼬리 지연 악화가 재현되지 않았다.
5. 성공 조회 수, Redis 조회수 증가, MySQL 최종 반영값이 일치했다.

## 핵심 원본 파일

### 용량 비교

- `study11_before__hot-post-view-comments100__capacity__run-01__k6-summary.json`
- `study11_before__hot-post-view-comments100__capacity__run-01__mysql-locks.txt`
- `study12_after__hot-post-view-comments100__capacity__run-01__k6-summary.json`
- `study12_after__hot-post-view-comments100__capacity__run-01__mysql-locks.txt`
- `study12_after__hot-post-view-comments100__capacity__run-01__run-metadata.txt`

### 댓글 전용·혼합 비교

- `study11_before__post-counter-interference-comments100__comment-only__run-01~03__k6-summary.json`
- `study11_before__post-counter-interference-comments100__mixed-normal__run-01~03__k6-summary.json`
- `study11_before__post-counter-interference-comments100__comment-only__run-01~03__mysql-locks.txt`
- `study11_before__post-counter-interference-comments100__mixed-normal__run-01~03__mysql-locks.txt`
- `study12_after__post-counter-interference-comments100__comment-only__run-01~03__k6-summary.json`
- `study12_after__post-counter-interference-comments100__mixed-normal__run-01~03__k6-summary.json`
- `study12_after__post-counter-interference-comments100__comment-only__run-01~03__mysql-locks.txt`
- `study12_after__post-counter-interference-comments100__mixed-normal__run-01~03__mysql-locks.txt`
- `study12_after__post-counter-interference-comments100__comment-only__run-01~03__run-metadata.txt`
- `study12_after__post-counter-interference-comments100__mixed-normal__run-01~03__run-metadata.txt`
