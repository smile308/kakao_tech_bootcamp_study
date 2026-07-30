# 부하 테스트 실행 규칙

이 디렉터리의 테스트 스크립트는 개선 전후에 공통으로 사용한다.

- Baseline 실행 대상: `study11`
- 개선 후 실행 대상: `study12`
- 테스트 실행은 사용자가 직접 수행한다.
- Codex는 실행 명령, 캡처 시점, 결과 해석 방법을 안내한다.
- 결과는 `study12/docs/concurrency-evidence`에 저장한다.

## 버전 식별자

실행 시 다음 식별자를 사용한다.

```text
study11_baseline
study12_improved-r01
study12_improved-r02
```

동일한 스크립트에서 대상 URL과 버전 식별자만 바꿔 실행해야 한다.
그래야 개선 전후의 부하 조건이 달라지는 것을 방지할 수 있다.

## 테스트 전 확인

1. 테스트 대상 애플리케이션이 `study11`인지 `study12`인지 확인한다.
2. 대상 Git short commit을 기록한다.
3. DB 데이터를 정해진 초기 상태로 복원한다.
4. 다른 백엔드 인스턴스가 실행 중인지 확인한다.
5. 테스트 사용자 수와 대상 게시글 ID를 확인한다.
6. 결과 파일 접두사가 대상 버전과 일치하는지 확인한다.

실제 k6 스크립트와 실행 도구는 MySQL Baseline 환경을 확정한 뒤 단계적으로 추가한다.

## MySQL 실행 대상

공통 Compose 파일과 대상별 환경 파일을 사용한다.

```text
compose.yaml
target-study11.env
target-study12-r01.env
```

대상별 포트:

| 대상 | 백엔드 | MySQL |
|---|---:|---:|
| study11 Baseline | 18081 | 13311 |
| study12 개선 1회차 | 18082 | 13312 |

두 환경은 서로 다른 Compose 프로젝트와 MySQL 볼륨을 사용한다.
비교 테스트에서는 한 번에 하나의 환경만 실행한다.

현재 Flyway 마이그레이션은 빈 MySQL 스키마를 처음부터 만들 수 없으므로 테스트 Compose에서만 Flyway를 비활성화한다.
Hibernate `ddl-auto=create`로 같은 엔티티 스키마를 생성하며, 이 설정을 운영 배포에 사용하면 안 된다.
