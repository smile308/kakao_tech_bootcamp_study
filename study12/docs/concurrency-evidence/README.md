# 동시성 부하 테스트 증거 저장 규칙

`study11`은 개선 전 Baseline, `study12`는 개선 후 버전이다.
결과 파일은 디렉터리뿐 아니라 파일명만 보더라도 어느 버전의 결과인지 구분할 수 있어야 한다.

## 파일명 규칙

```text
study11_baseline__{scenario}__{yyyyMMdd-HHmmss}__{commit}__{artifact}.{ext}
study12_improved-r{회차}__{scenario}__{yyyyMMdd-HHmmss}__{commit}__{artifact}.{ext}
```

예시:

```text
study11_baseline__post-view__20260730-143000__f5804789__k6-summary.json
study11_baseline__post-view__20260730-143000__f5804789__mysql-locks.txt
study11_baseline__post-view__20260730-143000__f5804789__result.png

study12_improved-r01__post-view__20260731-143000__10f3c096__k6-summary.json
study12_improved-r01__post-view__20260731-143000__10f3c096__mysql-locks.txt
study12_improved-r01__post-view__20260731-143000__10f3c096__result.png
```

## 시나리오 이름

- `post-view`: 동일 게시글 상세 조회 집중
- `post-like`: 서로 다른 사용자의 좋아요 집중
- `post-comment`: 댓글 등록 집중
- `post-mixed`: 조회·좋아요·댓글 혼합
- `report-threshold`: 신고 임계점 경계
- `report-delete`: 신고와 삭제 경쟁

## 결과별 artifact 이름

- `k6-summary.json`: k6 요약 원본
- `k6-console.txt`: k6 콘솔 출력
- `backend.log`: 백엔드 로그
- `mysql-locks.txt`: MySQL 락 대기 정보
- `mysql-deadlock.txt`: MySQL 데드락 정보
- `correctness.txt`: 카운터와 실제 행 개수 검증
- `result.png`: 대표 결과 화면
- `notes.md`: 실행 조건과 결과 해석

## 보존 원칙

- Baseline 결과는 `study11_baseline__`으로 시작한다.
- 개선 결과는 `study12_improved-rNN__`으로 시작한다.
- 변경 전후에는 동일한 시나리오 이름을 사용한다.
- 모든 결과에 Git short commit을 포함한다.
- 스크린샷만 남기지 않고 요약 JSON과 텍스트 로그도 저장한다.
- JWT, Refresh Token, 비밀번호, DB 비밀번호는 저장 전에 반드시 제거한다.
- 테스트가 실패해도 결과 파일을 삭제하지 않고 실패 이유를 `notes.md`에 기록한다.

## 권장 디렉터리

```text
concurrency-evidence/
├── study11-baseline/
│   ├── post-view/
│   ├── post-like/
│   ├── post-comment/
│   ├── post-mixed/
│   └── lock-conflict/
└── study12-improved/
    ├── r01/
    ├── r02/
    └── final/
```
