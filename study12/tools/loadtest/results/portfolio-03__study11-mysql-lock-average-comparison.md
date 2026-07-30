# study11 교차 간섭 테스트 — MySQL 락 대기 3회 평균

## 3회 측정값과 산술평균

| 조건 | 차수 | 행 락 대기 증가 | 누적 락 대기시간 증가 |
|---|---:|---:|---:|
| 댓글 전용 | 1차 | 0회 | 0ms |
| 댓글 전용 | 2차 | 0회 | 0ms |
| 댓글 전용 | 3차 | 0회 | 0ms |
| **댓글 전용 평균** | **3회 평균** | **0회** | **0ms** |
| 조회·댓글 혼합 | 1차 | 1,400회 | 15,299ms |
| 조회·댓글 혼합 | 2차 | 2,105회 | 37,730ms |
| 조회·댓글 혼합 | 3차 | 1,658회 | 24,607ms |
| **조회·댓글 혼합 평균** | **3회 평균** | **1,721회** | **25,878.67ms** |

## 평균 계산

```text
혼합 조건 행 락 대기 횟수 평균
= (1,400 + 2,105 + 1,658) / 3
= 1,721회

혼합 조건 누적 락 대기시간 평균
= (15,299 + 37,730 + 24,607) / 3
= 25,878.67ms
= 약 25.879초

혼합 조건 전체 락 대기 1회당 평균 대기시간
= (15,299 + 37,730 + 24,607) / (1,400 + 2,105 + 1,658)
= 77,636ms / 5,163회
= 약 15.04ms/회
```

## 실제로 기다린 락

세 번의 혼합 테스트에서 수집한 `wait_detail`은 모두 같은 대상을 가리켰다.

| 항목 | 값 |
|---|---|
| 테이블 | `bamboo_loadtest.post_counters` |
| 인덱스 | `PRIMARY` |
| 락 종류 | `RECORD` |
| 락 모드 | `X,REC_NOT_GAP` |

`X`는 다른 쓰기 요청이 동시에 같은 레코드를 변경하지 못하게 하는 배타적 락이다. 즉, 조회수와 댓글 수는 서로 다른 열이지만 같은 게시글의 `post_counters` 한 행에 있기 때문에 동일 기본키 레코드의 쓰기 순서를 기다렸다.

## 결론

3회 산술평균 기준, 댓글 전용 조건에서는 행 락 대기가 0회였지만 조회·댓글 혼합 조건에서는 평균 1,721회, 누적 25.879초의 행 락 대기가 발생했다. 정확성은 유지됐지만 같은 행에 모인 카운터 때문에 쓰기가 직렬화되어 댓글 꼬리 지연이 커졌다.

## 이 표를 만든 원본

### 댓글 전용

- `study11_baseline__post-counter-interference-comments100__comment-only__20260730-160138__mysql-locks.txt`
- `study11_baseline__post-counter-interference-comments100__comment-only__20260730-163040__mysql-locks.txt`
- `study11_baseline__post-counter-interference-comments100__comment-only__20260730-163314__mysql-locks.txt`

### 조회·댓글 혼합

- `study11_baseline__post-counter-interference-comments100__mixed-normal__20260730-160416__mysql-locks.txt`
- `study11_baseline__post-counter-interference-comments100__mixed-normal__20260730-162708__mysql-locks.txt`
- `study11_baseline__post-counter-interference-comments100__mixed-normal__20260730-163548__mysql-locks.txt`

계산 방법은 각 원본에서 첫 번째와 마지막 `Innodb_row_lock_waits`의 차이, 첫 번째와 마지막 `Innodb_row_lock_time`의 차이를 구한 뒤 조건별로 3회 산술평균을 낸 것이다.
