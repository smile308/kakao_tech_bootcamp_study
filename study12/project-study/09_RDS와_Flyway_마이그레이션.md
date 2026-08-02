# 9장. RDS와 Flyway 마이그레이션

## 9.1 학습 목표

이미 데이터가 존재하는 운영 RDS의 구조를 애플리케이션 변경에 맞춰 안전하게 발전시키는 방법을 학습한다.

```text
애플리케이션 시작
→ Flyway가 flyway_schema_history 확인
→ 아직 실행하지 않은 V번호 SQL 선택
→ 번호 순서대로 실행
→ 성공 이력 기록
→ 애플리케이션 실행 계속
```

## 9.2 마이그레이션 파일 규칙

현재 파일:

```text
V1__migrate_post_counters.sql
V2__add_user_auth_version.sql
V3__expand_post_view_counts.sql
```

형식:

```text
V{버전}__{설명}.sql
```

한번 운영에 적용된 기존 migration 파일을 수정하면 checksum 검증이 실패하거나 환경별 이력이 달라질 수 있다. 새로운 변경은 다음 버전 파일로 추가하는 것이 기본이다.

## 9.3 운영 Flyway 실제 설정

```yaml
flyway:
  enabled: true
  baseline-on-migrate: true
  baseline-version: "0"
  locations: classpath:db/migration
  validate-on-migrate: true
```

중요한 줄:

```yaml
baseline-on-migrate: true
# Flyway 이력이 없지만 기존 스키마가 있는 DB를 baseline으로 등록할 수 있게 한다.

locations: classpath:db/migration
# JAR 안의 해당 경로에서 migration SQL을 찾는다.

validate-on-migrate: true
# 적용 이력과 현재 파일이 일치하는지 시작 시 검사한다.
```

## 9.4 V1 실제 코드의 핵심 원문

```sql
SET @posts_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
);

SET @create_post_counters_sql = IF(
    @posts_exists = 1,
    'CREATE TABLE IF NOT EXISTS post_counters (
        post_id BIGINT NOT NULL,
        like_count INT NOT NULL DEFAULT 0,
        report_count INT NOT NULL DEFAULT 0,
        reply_count INT NOT NULL DEFAULT 0,
        view_count INT NOT NULL DEFAULT 0,
        PRIMARY KEY (post_id),
        CONSTRAINT fk_post_counters_post
            FOREIGN KEY (post_id)
            REFERENCES posts (post_id)
    ) ENGINE=InnoDB',
    'SELECT 1'
);

PREPARE create_post_counters_statement
FROM @create_post_counters_sql;
EXECUTE create_post_counters_statement;
DEALLOCATE PREPARE create_post_counters_statement;
```

V1은 DB에 `posts` 테이블이 있는지 확인한 뒤 실행할 SQL 문자열을 결정한다.

```text
posts 있음
→ post_counters 생성 SQL

posts 없음
→ SELECT 1로 아무 변경도 하지 않음
```

MySQL의 `IF` 결과로 DDL 문장을 직접 실행할 수 없어서 `PREPARE → EXECUTE → DEALLOCATE`를 사용한다.

## 9.5 V1 데이터 이전

```sql
INSERT INTO post_counters (
    post_id,
    like_count,
    report_count,
    reply_count,
    view_count
)
SELECT
    post.post_id,
    post.like_count,
    post.report_count,
    post.reply_count,
    post.view_count
FROM posts post
LEFT JOIN post_counters counter
    ON counter.post_id = post.post_id
WHERE counter.post_id IS NULL;
```

```text
기존 posts의 카운터
→ 새 post_counters의 같은 post_id 행으로 복사

LEFT JOIN + IS NULL
→ 아직 새 counter가 없는 게시글만 복사
```

V1에서 like/report/reply/view 컬럼 존재 여부를 확인하고 기본값을 보정하는 코드가 반복된다. `like_count` 하나의 패턴을 이해한 뒤 나머지는 같은 맥락으로 스킵한다.

## 9.6 V2 실제 코드

```sql
ALTER TABLE users
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
```

`auth_version`은 기존 Access Token을 무효화하는 보안 버전이다.

```text
기존 사용자 행
→ DEFAULT 0

새로 발급하는 JWT
→ 현재 User.authVersion claim 포함

비밀번호 변경
→ authVersion 증가
→ 이전 JWT의 버전과 불일치
```

V2는 컬럼이 이미 있는지 검사하지 않는다. Flyway가 버전별 SQL을 한 번만 실행한다는 전제에 의존한다.

## 9.7 V3 실제 코드

```sql
CREATE TABLE IF NOT EXISTS post_view_counts (
    post_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_view_counts_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;

INSERT INTO post_view_counts (
    post_id,
    view_count
)
SELECT
    legacy_counter.post_id,
    legacy_counter.view_count
FROM post_counters legacy_counter
LEFT JOIN post_view_counts view_counter
    ON view_counter.post_id = legacy_counter.post_id
WHERE view_counter.post_id IS NULL;

UPDATE post_view_counts view_counter
INNER JOIN post_counters legacy_counter
    ON legacy_counter.post_id = view_counter.post_id
SET view_counter.view_count = GREATEST(
    view_counter.view_count,
    legacy_counter.view_count
);
```

위 블록은 `V3__expand_post_view_counts.sql` 전체 원문이다.

- `CREATE TABLE`: 조회수를 기존 `INT` 카운터에서 별도 `BIGINT` 테이블로 확장한다.
- `INSERT ... SELECT`: 기존 조회수를 새 테이블로 backfill한다.
- `UPDATE ... GREATEST`: 두 위치에 값이 이미 있다면 큰 값을 선택하여 마이그레이션 과정에서 조회수가 감소하지 않게 한다.

## 9.8 Entity와 migration 비교

migration 적용 후 DB 구조가 Java Entity가 기대하는 구조와 일치해야 한다.

```text
V1 post_counters
↔ PostCounter Entity

V2 users.auth_version
↔ User.authVersion

V3 post_view_counts
↔ PostViewCount Entity
```

Entity만 변경하고 운영 DB migration을 만들지 않으면 애플리케이션 시작이나 쿼리 실행 중 컬럼·테이블 없음 오류가 발생할 수 있다.

반대로 migration만 적용하고 코드가 새 구조를 사용하지 않으면 새 테이블은 존재하지만 기능은 바뀌지 않는다.

## 9.9 RDS 연결과 migration 시점

Compose가 제공하는 환경변수:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
SPRING_PROFILES_ACTIVE=prod
```

```text
Spring Boot 컨테이너 시작
→ prod profile
→ RDS DataSource 생성
→ Flyway가 RDS에 migration 적용·검증
→ JPA EntityManagerFactory 생성
→ 웹 요청 처리 준비
```

DB 계정에는 migration에 필요한 DDL 권한이 있어야 한다. 권한이 없거나 SQL이 실패하면 정상적인 애플리케이션 시작도 실패할 수 있다.

## 9.10 핵심 축약본

```text
V1
→ 게시글 카운터 별도 테이블 생성과 기존 데이터 복사

V2
→ 사용자 authVersion 추가

V3
→ BIGINT 조회수 테이블 생성과 기존 조회수 복사

Flyway
→ 아직 실행하지 않은 버전만 순서대로 적용하고 이력 관리
```


## 9.10.1 전체 원문 코드 라인별 주석본

아래 주석은 학습을 위해 추가한 것이며 실제 프로젝트 파일에는 없다. 줄 끝 주석을 붙인 주석본은 의미를 따라가기 위한 설명용이므로 그대로 복사해 실행하지 않는다.

### Flyway 설정

```yaml
flyway:                        # Spring Boot의 Flyway 설정 영역이다.
  enabled: true                # prod 실행 시 migration 기능을 켠다.
  baseline-on-migrate: true    # 기존 스키마에 이력 테이블이 없으면 baseline 생성을 허용한다.
  baseline-version: "0"        # 기존 상태를 버전 0으로 간주해 V1부터 적용 가능하게 한다.
  locations: classpath:db/migration # 빌드된 JAR의 해당 경로에서 SQL 파일을 찾는다.
  validate-on-migrate: true    # 기존 적용 이력의 checksum과 현재 파일을 실행 전에 비교한다.
```

### V1 테이블 확인과 동적 실행

```sql
SET @posts_exists = ( -- posts 테이블 존재 결과를 세션 변수에 저장한다.
    SELECT COUNT(*) -- 조건에 맞는 테이블 개수를 센다.
    FROM information_schema.tables -- MySQL의 테이블 구조 정보를 조회한다.
    WHERE table_schema = DATABASE() -- 현재 연결된 DB schema로 범위를 제한한다.
      AND table_name = 'posts' -- posts라는 테이블만 찾는다.
);

SET @create_post_counters_sql = IF( -- 실행할 SQL 문자열을 조건에 따라 변수에 넣는다.
    @posts_exists = 1, -- 기존 posts 테이블이 정확히 하나 있으면 첫 문자열을 선택한다.
    'CREATE TABLE IF NOT EXISTS post_counters ( -- 새 카운터 테이블 생성 SQL 문자열을 시작한다.
        post_id BIGINT NOT NULL, -- 게시글 ID이며 null을 허용하지 않는다.
        like_count INT NOT NULL DEFAULT 0, -- 좋아요 수의 기본값은 0이다.
        report_count INT NOT NULL DEFAULT 0, -- 신고 수의 기본값은 0이다.
        reply_count INT NOT NULL DEFAULT 0, -- 댓글 수의 기본값은 0이다.
        view_count INT NOT NULL DEFAULT 0, -- 기존 조회수의 기본값은 0이다.
        PRIMARY KEY (post_id), -- 게시글마다 카운터 행 하나만 존재하게 한다.
        CONSTRAINT fk_post_counters_post -- foreign key 제약 이름을 지정한다.
            FOREIGN KEY (post_id) REFERENCES posts (post_id) -- 없는 게시글의 카운터가 생기지 않게 posts와 연결한다.
    ) ENGINE=InnoDB', -- Transaction과 foreign key를 지원하는 InnoDB로 만든다.
    'SELECT 1' -- posts가 없으면 아무 구조도 바꾸지 않는 안전한 SQL을 선택한다.
);

PREPARE create_post_counters_statement FROM @create_post_counters_sql; -- 선택한 문자열을 실행 가능한 statement로 준비한다.
EXECUTE create_post_counters_statement; -- 준비한 CREATE 또는 SELECT 문을 실행한다.
DEALLOCATE PREPARE create_post_counters_statement; -- 사용한 prepared statement 자원을 해제한다.
```

### V1 backfill

```sql
INSERT INTO post_counters ( -- 새 카운터 테이블에 과거 데이터를 추가한다.
    post_id, like_count, report_count, reply_count, view_count -- 삽입할 대상 컬럼 순서를 지정한다.
)
SELECT -- 기존 posts에서 삽입값을 조회한다.
    post.post_id, -- 같은 게시글 ID를 사용한다.
    post.like_count, -- 기존 좋아요 수를 복사한다.
    post.report_count, -- 기존 신고 수를 복사한다.
    post.reply_count, -- 기존 댓글 수를 복사한다.
    post.view_count -- 기존 조회수를 복사한다.
FROM posts post -- 기존 데이터 원본인 posts에 별칭 post를 붙인다.
LEFT JOIN post_counters counter -- 이미 만들어진 새 카운터 행과 비교한다.
    ON counter.post_id = post.post_id -- 같은 게시글 ID끼리 연결한다.
WHERE counter.post_id IS NULL; -- 새 카운터가 아직 없는 게시글만 삽입해 중복을 피한다.
```

### V2 인증 버전

```sql
ALTER TABLE users -- 기존 사용자 테이블 구조를 변경한다.
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0; -- 기존 행은 0을 받고 앞으로 JWT 무효화 버전을 저장한다.
```

### V3 조회수 테이블

```sql
CREATE TABLE IF NOT EXISTS post_view_counts ( -- 새 조회수 전용 테이블이 없을 때 생성한다.
    post_id BIGINT NOT NULL, -- 게시글 ID를 null 없이 저장한다.
    view_count BIGINT NOT NULL DEFAULT 0, -- INT보다 큰 범위의 조회수를 기본 0으로 저장한다.
    PRIMARY KEY (post_id), -- 게시글마다 조회수 행 하나만 허용한다.
    CONSTRAINT fk_post_view_counts_post -- foreign key 제약 이름이다.
        FOREIGN KEY (post_id) REFERENCES posts (post_id) -- 조회수 행을 실제 게시글과 연결한다.
) ENGINE=InnoDB; -- Transaction과 foreign key를 지원하는 엔진을 쓴다.

INSERT INTO post_view_counts (post_id, view_count) -- 새 테이블에 기존 조회수를 backfill한다.
SELECT legacy_counter.post_id, legacy_counter.view_count -- 기존 카운터의 ID와 조회수를 복사한다.
FROM post_counters legacy_counter -- 기존 값의 원본 테이블이다.
LEFT JOIN post_view_counts view_counter -- 이미 새 행이 있는지 비교한다.
    ON view_counter.post_id = legacy_counter.post_id -- 같은 게시글끼리 연결한다.
WHERE view_counter.post_id IS NULL; -- 새 조회수 행이 없는 게시글만 삽입한다.

UPDATE post_view_counts view_counter -- 이미 존재하는 새 조회수 행을 보정한다.
INNER JOIN post_counters legacy_counter -- 같은 게시글의 이전 카운터와 연결한다.
    ON legacy_counter.post_id = view_counter.post_id -- 게시글 ID를 join 조건으로 쓴다.
SET view_counter.view_count = GREATEST( -- 두 값 중 큰 값을 새 조회수로 저장한다.
    view_counter.view_count, -- 새 테이블에 이미 있던 값이다.
    legacy_counter.view_count -- 이전 테이블의 영구 조회수다.
);
```

## 9.11 스킵할 코드

- V1의 네 카운터 컬럼별 동일한 존재 확인과 `ALTER` 반복
- `information_schema` 조회문의 컬럼 이름 반복
- `PREPARE` 문장의 이름 차이

다음은 스킵하지 않는다.

- 기존 테이블 존재 확인 목적
- backfill 조건
- foreign key와 primary key
- `GREATEST`를 이용한 값 보존
- Flyway 적용 순서와 이력


## 9.11.1 이 장에서 필요한 SQL·Flyway 문법

### DDL과 DML

- DDL: 테이블·컬럼·제약 구조를 바꾸는 `CREATE`, `ALTER`
- DML: 행 데이터를 바꾸는 `INSERT`, `UPDATE`, `DELETE`
- `SELECT`: 데이터를 조회

Migration에는 구조 변경과 기존 데이터 이전이 함께 들어갈 수 있다.

### 문장 종료 세미콜론

SQL의 `;`는 한 문장이 끝났음을 표시한다. 여러 문장을 한 migration 파일에 순서대로 작성할 수 있다.

### MySQL 사용자 변수

```sql
SET @posts_exists = (...);
```

`@이름`은 현재 DB session에서 사용할 변수다. query 결과나 실행할 SQL 문자열을 잠시 보관한다.

### `information_schema`

MySQL이 현재 DB의 테이블·컬럼·제약 같은 구조 정보를 제공하는 시스템 schema다. 애플리케이션 데이터가 아니라 DB 구조 존재 여부를 확인한다.

### 집계 함수

```sql
COUNT(*)
MAX(column)
COALESCE(value, 0)
GREATEST(a, b)
```

- `COUNT`: 행 수
- `MAX`: 가장 큰 값
- `COALESCE`: 왼쪽부터 null이 아닌 첫 값
- `GREATEST`: 인자 중 가장 큰 값

### `IF`

```sql
IF(condition, true_value, false_value)
```

조건에 따라 두 값 중 하나를 반환한다. V1에서는 실행할 SQL 문자열을 선택한다.

### 동적 SQL

```sql
PREPARE name FROM @sql;
EXECUTE name;
DEALLOCATE PREPARE name;
```

문자열 상태의 SQL을 실행 가능한 statement로 준비하고, 실행 후 자원을 해제한다.

### Primary key와 Foreign key

- Primary key는 행을 유일하게 식별하고 중복과 null을 허용하지 않는다.
- Foreign key는 자식 값이 부모 테이블에 실제 존재하도록 제한한다.
- `post_id`를 PK이자 FK로 사용하면 게시글당 카운터 행을 최대 하나만 허용한다.

### JOIN

```sql
LEFT JOIN B ON ...
WHERE B.id IS NULL
```

왼쪽 A 행은 모두 유지하면서 B 일치가 없으면 B 컬럼이 null이 된다. 이를 이용해 “아직 이전되지 않은 A 행”만 찾는다.

```sql
INNER JOIN B ON ...
```

양쪽에 일치하는 행만 결과에 남긴다.

### 별칭

```sql
FROM posts post
```

긴 테이블 이름 대신 현재 query 안에서 `post`라는 짧은 이름을 사용한다.

### Backfill과 멱등성

Backfill은 새 컬럼이나 테이블에 기존 행의 데이터를 채우는 작업이다. `WHERE new.id IS NULL`과 `IF NOT EXISTS`는 일부 작업을 다시 수행해도 중복 생성 가능성을 줄인다. 하지만 Flyway migration 전체가 무조건 재실행 안전하다는 뜻은 아니며, Flyway 이력이 한 번 실행을 보장한다.

### Flyway version과 checksum

Flyway는 `flyway_schema_history`에 version, 성공 여부와 checksum을 기록한다. 적용된 파일 내용을 바꾸면 현재 checksum이 이력과 달라져 validation이 실패할 수 있다.

### baseline

기존 운영 DB를 Flyway가 처음 관리할 때 과거 상태를 특정 version으로 등록하는 시작점이다. 실제 테이블을 새로 만드는 동작과 혼동하지 않는다.

## 9.12 이해 확인

1. Flyway는 어떤 정보를 보고 실행할 migration을 결정하는가?
2. 적용된 migration 파일을 나중에 직접 수정하면 왜 위험한가?
3. V1에서 `information_schema`를 조회하는 이유는 무엇인가?
4. `LEFT JOIN ... IS NULL`은 어떤 데이터만 backfill하게 하는가?
5. V2의 `auth_version`은 어떤 보안 기능과 연결되는가?
6. V3에서 조회수를 `BIGINT` 별도 테이블로 옮긴 이유는 무엇인가?
7. `GREATEST`가 조회수 감소를 어떻게 막는가?
8. Entity 변경과 migration은 왜 함께 맞아야 하는가?
9. 운영 컨테이너가 시작될 때 Flyway와 JPA는 어떤 순서로 준비되는가?
10. 운영 DB 계정에 migration용 DDL 권한이 필요한 이유는 무엇인가?

## 9.13 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.
