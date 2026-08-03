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
B3__current_schema.sql
V1__migrate_post_counters.sql
V2__add_user_auth_version.sql
V3__expand_post_view_counts.sql
```

두 형식을 구분한다.

```text
B{버전}__{설명}.sql
→ 그 버전까지의 전체 구조를 한 번에 만드는 baseline migration

V{버전}__{설명}.sql
→ 직전 버전에서 다음 버전으로 변경하는 versioned migration
```

`B3`와 `V3`가 같은 버전 번호인 것은 오류가 아니다.

```text
새 빈 DB
→ B3 실행
→ V1, V2, V3는 실행하지 않음

이미 V1~V3까지 적용된 기존 RDS
→ B3를 실행하지 않음

앞으로 V4가 추가된 경우
→ 두 DB 모두 V4 실행
```

한 번 운영에 적용된 기존 migration 파일을 수정하면 checksum 검증이 실패하거나 환경별 이력이 달라질 수 있다. 새로운 변경은 다음 `V` 버전 파일로 추가한다. `B3`도 배포되어 사용된 뒤에는 수정하지 않고, 나중에 baseline을 압축할 필요가 생기면 더 높은 버전의 새 `B` 파일을 추가한다.

## 9.3 운영 Flyway 실제 설정

파일: `application-prod.yaml`의 실제 관련 원문:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: false
    show-sql: false
    defer-datasource-initialization: false

  sql:
    init:
      mode: never

  flyway:
    enabled: true
    baseline-on-migrate: false
    locations: classpath:db/migration
    validate-on-migrate: true
```

중요한 줄:

```yaml
baseline-on-migrate: false
# 이력이 없는 기존 non-empty DB를 자동으로 정상 상태라고 등록하지 않는다.

locations: classpath:db/migration
# JAR 안의 해당 경로에서 migration SQL을 찾는다.

validate-on-migrate: true
# 적용 이력과 현재 파일이 일치하는지 시작 시 검사한다.
```

현재 설정에서 반드시 구분할 점이 있다.

```text
Flyway migration 실행·검증
→ Hibernate EntityManagerFactory 생성
→ ddl-auto: validate가 Entity와 DB 구조의 일치 여부만 검사
→ 일치하지 않으면 애플리케이션 시작 실패
```

운영에서 Hibernate는 더 이상 테이블이나 컬럼을 자동 변경하지 않는다. Flyway가 구조를 변경하는 단일 담당자이고 Hibernate는 migration 결과가 Entity와 맞는지 검사하는 담당자다.

`baseline-on-migrate: false`와 `B3__current_schema.sql`은 서로 반대되는 설정이 아니다.

- `baseline-on-migrate`: 이력 없는 **기존 non-empty DB**를 자동 baseline 처리할지 정하는 옵션
- `B3` baseline migration: **새 빈 DB**에 현재 전체 스키마를 실제로 생성하는 SQL 파일

자동 baseline을 끄면 잘못된 기존 DB를 애플리케이션이 임의로 받아들이지 않는다. 새 빈 DB에는 `B3`가 정상 실행된다.

### 9.3.1 B3가 추가된 이유

기존 `V1~V3`는 처음부터 전체 DB를 만드는 파일이 아니다. 이미 Hibernate가 만들어 둔 `users`, `posts` 등을 전제로 일부 구조만 변경한다. 따라서 빈 RDS에서는 `V2`의 `ALTER TABLE users`부터 실패할 수 있었다.

`B3__current_schema.sql`은 현재 모든 Entity와 `V1~V3`의 최종 결과를 대조하여 다음을 한 번에 만든다.

```text
users
posts
auth_sessions
comments
post_counters
post_images
post_likes
post_likes_seq
post_reports
post_view_counts
```

`post_likes_seq`도 빼면 안 된다. `Like.postLikeId`가 `GenerationType.AUTO`를 사용하므로 현재 Hibernate의 MySQL mapping은 이 테이블에서 다음 ID 묶음을 가져온다. 나머지 ID Entity는 `GenerationType.IDENTITY`이므로 해당 PK에 `AUTO_INCREMENT`가 붙는다.

테이블 생성 순서도 의미가 있다. 예를 들어 `posts.user_id` foreign key는 `users`를 참조하므로 `users`가 먼저 생성되어야 한다. 이후 자식 테이블이 `users`와 `posts`를 참조한다.

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

PREPARE create_post_counters_statement FROM @create_post_counters_sql;
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
SET @legacy_counter_column_count = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
      AND column_name IN (
          'like_count',
          'report_count',
          'reply_count',
          'view_count'
      )
);

SET @backfill_post_counters_sql = IF(
    @posts_exists = 1 AND @legacy_counter_column_count = 4,
    'INSERT INTO post_counters (
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
    WHERE counter.post_id IS NULL',
    'SELECT 1'
);

PREPARE backfill_post_counters_statement FROM @backfill_post_counters_sql;
EXECUTE backfill_post_counters_statement;
DEALLOCATE PREPARE backfill_post_counters_statement;
```

```text
기존 posts의 카운터
→ 새 post_counters의 같은 post_id 행으로 복사

LEFT JOIN + IS NULL
→ 아직 새 counter가 없는 게시글만 복사
```

실제 파일은 `INSERT ... SELECT`를 바로 실행하지 않는다. `posts`가 존재하고 네 legacy counter column이 모두 있을 때만 backfill 문자열을 선택한다. 하나라도 없으면 `SELECT 1`을 실행한다. 따라서 앞서 있던 축약 INSERT만 보고 “column이 없어도 무조건 실행된다”고 이해하면 안 된다.

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

이 전제는 “Flyway가 V2를 이미 성공 기록했다면 다시 실행하지 않는다”는 뜻이다. Flyway 이력 없이 과거의 `ddl-auto: update`나 사람이 먼저 같은 `auth_version` column을 만든 DB에서는 V2가 duplicate column 오류로 실패할 수 있다. 현재는 `ddl-auto: validate`와 `baseline-on-migrate: false`로 바꿔 새 자동 변경과 무검증 자동 baseline을 막았지만, 기존 RDS의 과거 상태는 배포 전에 직접 확인해야 한다.

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

`IF NOT EXISTS`는 table 이름이 존재하는지만 확인한다. 이미 존재하는 table의 column type·foreign key가 현재 기대와 다른 경우까지 자동으로 고쳐주는 것은 아니다.

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

### 새 빈 RDS와 기존 RDS의 서로 다른 시작 경로

새 빈 RDS는 다음 경로를 따른다.

```text
flyway_schema_history도 테이블도 없는 빈 schema
→ Flyway가 가장 최신 baseline migration인 B3 선택
→ B3가 현재 전체 테이블·제약·인덱스 생성
→ V1~V3는 B3보다 오래된 변경이므로 실행하지 않음
→ Hibernate validate가 Entity와 결과 구조 비교
→ 일치하면 애플리케이션 시작
```

기존 RDS는 다음 조건을 만족해야 한다.

```text
flyway_schema_history 존재
→ V1, V2, V3의 성공 이력이 존재
→ B3는 기존 환경에서 실행하지 않음
→ Hibernate validate가 기존 구조와 Entity 비교
```

운영 배포 전에 기존 RDS에서 다음처럼 이력을 확인해야 한다.

```sql
SELECT installed_rank, version, description, type, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

기존 RDS에 실제 테이블은 있지만 `flyway_schema_history`가 없다면 지금 설정은 시작을 실패시키는 것이 정상이다. 이 상태를 자동으로 정상 취급하면 구조가 정확히 어느 버전인지 검증하지 못하기 때문이다. 먼저 실제 테이블·컬럼·제약을 점검한 뒤, 별도의 통제된 1회 작업으로 Flyway 이력을 맞춰야 한다. 이를 확인하지 않고 `baseline-on-migrate`를 다시 켜서 우회하면 안 된다.

또한 MySQL의 많은 DDL은 implicit commit을 일으킨다. 한 migration 파일 중간에 실패하면 앞에서 성공한 DDL까지 일반 Transaction처럼 모두 rollback된다고 가정하면 안 된다. 재실행 전 실제 schema와 `flyway_schema_history` 상태를 함께 확인하고 복구 migration 또는 수동 정리가 필요할 수 있다.

## 9.10 핵심 축약본

```text
V1
→ 게시글 카운터 별도 테이블 생성과 기존 데이터 복사

V2
→ 사용자 authVersion 추가

V3
→ BIGINT 조회수 테이블 생성과 기존 조회수 복사

B3
→ 새 빈 DB를 V3까지 반영된 현재 전체 구조로 생성

Flyway
→ 아직 실행하지 않은 버전만 순서대로 적용하고 이력 관리

Hibernate validate
→ Flyway 결과와 Entity가 다르면 자동 수정하지 않고 시작 실패
```


## 9.10.1 전체 원문 코드 라인별 주석본

아래 주석은 학습을 위해 추가한 것이며 실제 프로젝트 파일에는 없다. 줄 끝 주석을 붙인 주석본은 의미를 따라가기 위한 설명용이므로 그대로 복사해 실행하지 않는다.

### Flyway 설정

```yaml
spring:                              # Spring Boot 공통 설정 영역이다.
  jpa:                               # JPA와 Hibernate 설정이다.
    hibernate:                       # Hibernate schema 처리 설정이다.
      ddl-auto: validate             # Flyway가 만든 schema와 Entity가 맞는지만 검사하고 DB 구조를 자동 수정하지 않는다.
    properties:                      # Hibernate 세부 property 영역이다.
      hibernate:                     # Hibernate 자체 namespace다.
        format_sql: false            # 운영 SQL log formatting을 끈다.
    show-sql: false                  # 운영 console에 SQL을 출력하지 않는다.
    defer-datasource-initialization: false # JPA 초기화를 별도 SQL init 뒤로 미루지 않는다.

  sql:                               # Spring 기본 SQL initialization 설정이다.
    init:                            # schema.sql·data.sql 자동 실행 설정이다.
      mode: never                    # 운영에서는 기본 SQL init을 실행하지 않는다.

  flyway:                            # Spring Boot의 Flyway 설정 영역이다.
    enabled: true                    # prod 실행 시 migration 기능을 켠다.
    baseline-on-migrate: false       # 이력 없는 기존 non-empty DB를 자동 baseline으로 받아들이지 않는다.
    locations: classpath:db/migration # 빌드된 JAR의 해당 경로에서 SQL 파일을 찾는다.
    validate-on-migrate: true        # 기존 적용 이력의 checksum과 현재 파일을 실행 전에 비교한다.
```

### B3 현재 전체 스키마

아래는 실제 `B3__current_schema.sql` 전체를 학습용 줄별 주석과 함께 표시한 것이다. 같은 `CREATE TABLE` 문법의 의미는 처음 자세히 설명하고 이후에는 각 테이블에서 달라지는 column과 제약의 역할에 집중한다.

```sql
CREATE TABLE users ( -- 다른 테이블이 참조할 사용자 테이블을 먼저 생성한다.
    user_id BIGINT NOT NULL AUTO_INCREMENT, -- Long 사용자 PK이며 insert 때 MySQL이 값을 증가시킨다.
    email VARCHAR(255) NOT NULL, -- null이 아닌 이메일 문자열을 최대 255자로 저장한다.
    password VARCHAR(255), -- 소셜 로그인 사용자 등을 위해 null을 허용하는 비밀번호 hash다.
    nickname VARCHAR(10) NOT NULL, -- null이 아닌 닉네임을 최대 10자로 제한한다.
    profile_image LONGTEXT, -- 긴 이미지 문자열을 저장하며 null을 허용한다.
    received_report_count INT NOT NULL, -- 사용자가 받은 누적 신고 수다.
    deleted BIT NOT NULL, -- 탈퇴 여부 boolean 값을 저장한다.
    auth_version BIGINT NOT NULL DEFAULT 0, -- JWT 무효화 버전이며 DB 기본값은 0이다.
    PRIMARY KEY (user_id) -- user_id를 사용자 행의 고유 식별자로 정한다.
) ENGINE=InnoDB; -- foreign key와 transaction을 지원하는 InnoDB 테이블로 만든다.

CREATE TABLE posts ( -- 게시글 본문과 작성자 연결을 저장한다.
    post_id BIGINT NOT NULL AUTO_INCREMENT, -- Long 게시글 PK를 자동 증가시킨다.
    version BIGINT NOT NULL, -- @Version 낙관적 lock 비교에 사용할 값이다.
    user_id BIGINT NOT NULL, -- 작성자 users 행의 ID다.
    post_title VARCHAR(26) NOT NULL, -- 제목을 최대 26자로 저장한다.
    post_content VARCHAR(255) NOT NULL, -- 본문을 현재 Entity 기본 길이인 255자로 저장한다.
    is_fixed BIT NOT NULL, -- 게시글 수정 여부 boolean 값이다.
    created_at DATETIME(6) NOT NULL, -- microsecond 정밀도의 생성 시각이다.
    deleted BIT NOT NULL, -- soft delete 여부 boolean 값이다.
    PRIMARY KEY (post_id), -- 게시글 ID를 PK로 정한다.
    CONSTRAINT fk_posts_user -- 다음 foreign key의 이름을 고정한다.
        FOREIGN KEY (user_id) REFERENCES users (user_id) -- 존재하는 사용자만 작성자로 참조하게 한다.
) ENGINE=InnoDB;

CREATE TABLE auth_sessions ( -- refresh token 회전·폐기 상태를 DB에 저장한다.
    auth_session_id BIGINT NOT NULL AUTO_INCREMENT, -- 인증 세션 PK다.
    user_id BIGINT NOT NULL, -- 세션 소유 사용자 ID다.
    refresh_token_hash VARCHAR(64) NOT NULL, -- 원문 대신 SHA-256 hash의 64자리 16진수 문자열을 저장한다.
    refresh_expires_at DATETIME(6) NOT NULL, -- refresh token 만료 시각이다.
    created_at DATETIME(6) NOT NULL, -- 세션 생성 시각이다.
    revoked_at DATETIME(6), -- 폐기되지 않았으면 null이고 폐기되면 그 시각을 저장한다.
    PRIMARY KEY (auth_session_id), -- 인증 세션 ID를 PK로 정한다.
    CONSTRAINT uk_auth_sessions_refresh_token_hash -- token hash 중복 금지 제약의 이름이다.
        UNIQUE (refresh_token_hash), -- 같은 refresh token hash를 두 세션이 가질 수 없게 한다.
    CONSTRAINT fk_auth_sessions_user -- 사용자 연결 foreign key 이름이다.
        FOREIGN KEY (user_id) REFERENCES users (user_id) -- 존재하는 사용자에게만 인증 세션을 연결한다.
) ENGINE=InnoDB;

CREATE INDEX idx_auth_sessions_user_id -- 사용자별 세션 조회를 위한 index 이름이다.
    ON auth_sessions (user_id); -- auth_sessions.user_id 기준 검색을 빠르게 한다.

CREATE INDEX idx_auth_sessions_refresh_expires_at -- 만료 세션 정리를 위한 index 이름이다.
    ON auth_sessions (refresh_expires_at); -- 만료 시각 범위 검색을 빠르게 한다.

CREATE TABLE comments ( -- 댓글과 작성자·게시글 연결을 저장한다.
    comment_id BIGINT NOT NULL AUTO_INCREMENT, -- 댓글 PK를 자동 증가시킨다.
    user_id BIGINT NOT NULL, -- 댓글 작성자 ID다.
    post_id BIGINT NOT NULL, -- 댓글이 속한 게시글 ID다.
    comment_content VARCHAR(255) NOT NULL, -- 댓글 본문을 최대 255자로 저장한다.
    created_at DATETIME(6) NOT NULL, -- 댓글 생성 시각이다.
    PRIMARY KEY (comment_id), -- 댓글 ID를 PK로 정한다.
    CONSTRAINT fk_comments_user -- 작성자 foreign key 이름이다.
        FOREIGN KEY (user_id) REFERENCES users (user_id), -- 없는 사용자의 댓글을 막는다.
    CONSTRAINT fk_comments_post -- 게시글 foreign key 이름이다.
        FOREIGN KEY (post_id) REFERENCES posts (post_id) -- 없는 게시글의 댓글을 막는다.
) ENGINE=InnoDB;

CREATE TABLE post_counters ( -- 좋아요·신고·댓글 수와 legacy 조회수를 게시글별 한 행에 저장한다.
    post_id BIGINT NOT NULL, -- 게시글 ID이며 별도 자동 증가 PK가 아니다.
    like_count INT NOT NULL DEFAULT 0, -- 좋아요 수의 DB 기본값은 0이다.
    report_count INT NOT NULL DEFAULT 0, -- 신고 수의 DB 기본값은 0이다.
    reply_count INT NOT NULL DEFAULT 0, -- 댓글 수의 DB 기본값은 0이다.
    view_count INT NOT NULL DEFAULT 0, -- 이전 조회수 호환 필드의 기본값은 0이다.
    PRIMARY KEY (post_id), -- 같은 게시글의 counter 행을 최대 하나로 제한한다.
    CONSTRAINT fk_post_counters_post -- 게시글 연결 foreign key 이름이다.
        FOREIGN KEY (post_id) REFERENCES posts (post_id) -- 실제 게시글과 1:1로 연결한다.
) ENGINE=InnoDB;

CREATE TABLE post_images ( -- 게시글별 여러 이미지와 표시 순서를 저장한다.
    post_image_id BIGINT NOT NULL AUTO_INCREMENT, -- 이미지 행 PK를 자동 증가시킨다.
    post_id BIGINT NOT NULL, -- 이미지가 속한 게시글 ID다.
    image_file LONGTEXT NOT NULL, -- 긴 이미지 문자열을 null 없이 저장한다.
    image_order INT NOT NULL, -- 게시글 안에서 이미지 표시 순서를 저장한다.
    PRIMARY KEY (post_image_id), -- 이미지 행 ID를 PK로 정한다.
    CONSTRAINT fk_post_images_post -- 게시글 연결 foreign key 이름이다.
        FOREIGN KEY (post_id) REFERENCES posts (post_id) -- 없는 게시글의 이미지를 막는다.
) ENGINE=InnoDB;

CREATE TABLE post_likes ( -- 어떤 사용자가 어떤 게시글을 좋아요 했는지 저장한다.
    post_like_id BIGINT NOT NULL, -- AUTO 전략이 아래 post_likes_seq에서 만든 값을 넣는 PK다.
    post_id BIGINT NOT NULL, -- 좋아요 대상 게시글 ID다.
    user_id BIGINT NOT NULL, -- 좋아요한 사용자 ID다.
    PRIMARY KEY (post_like_id), -- 좋아요 행 ID를 PK로 정한다.
    CONSTRAINT uk_post_like_post_user -- 게시글·사용자 조합 unique 제약 이름이다.
        UNIQUE (post_id, user_id), -- 한 사용자가 같은 게시글을 두 번 좋아요하지 못하게 한다.
    CONSTRAINT fk_post_likes_post -- 게시글 foreign key 이름이다.
        FOREIGN KEY (post_id) REFERENCES posts (post_id), -- 존재하는 게시글만 참조한다.
    CONSTRAINT fk_post_likes_user -- 사용자 foreign key 이름이다.
        FOREIGN KEY (user_id) REFERENCES users (user_id) -- 존재하는 사용자만 참조한다.
) ENGINE=InnoDB;

CREATE TABLE post_likes_seq ( -- Like의 GenerationType.AUTO ID 값을 공급할 table generator다.
    next_val BIGINT -- Hibernate가 다음에 확보할 ID 묶음의 시작 값을 저장한다.
) ENGINE=InnoDB;

INSERT INTO post_likes_seq (next_val) VALUES (1); -- 최초 ID 생성을 시작할 값을 1로 넣는다.

CREATE TABLE post_reports ( -- 사용자별 게시글 신고 기록을 저장한다.
    post_report_id BIGINT NOT NULL AUTO_INCREMENT, -- 신고 행 PK를 자동 증가시킨다.
    post_id BIGINT NOT NULL, -- 신고 대상 게시글 ID다.
    user_id BIGINT NOT NULL, -- 신고한 사용자 ID다.
    created_at DATETIME(6) NOT NULL, -- 신고 생성 시각이다.
    PRIMARY KEY (post_report_id), -- 신고 행 ID를 PK로 정한다.
    CONSTRAINT uk_post_report_post_user -- 게시글·사용자 조합 unique 제약 이름이다.
        UNIQUE (post_id, user_id), -- 한 사용자의 같은 게시글 중복 신고를 DB에서도 막는다.
    CONSTRAINT fk_post_reports_post -- 게시글 foreign key 이름이다.
        FOREIGN KEY (post_id) REFERENCES posts (post_id), -- 존재하는 게시글만 신고하게 한다.
    CONSTRAINT fk_post_reports_user -- 사용자 foreign key 이름이다.
        FOREIGN KEY (user_id) REFERENCES users (user_id) -- 존재하는 사용자만 신고 주체가 된다.
) ENGINE=InnoDB;

CREATE TABLE post_view_counts ( -- Redis 조회수를 주기적으로 반영할 BIGINT 영구 조회수 테이블이다.
    post_id BIGINT NOT NULL, -- 게시글 ID를 그대로 PK로 사용한다.
    view_count BIGINT NOT NULL DEFAULT 0, -- INT보다 넓은 조회수이며 기본값은 0이다.
    PRIMARY KEY (post_id), -- 게시글마다 조회수 행을 최대 하나로 제한한다.
    CONSTRAINT fk_post_view_counts_post -- 게시글 연결 foreign key 이름이다.
        FOREIGN KEY (post_id) REFERENCES posts (post_id) -- 실제 게시글과 1:1로 연결한다.
) ENGINE=InnoDB;
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
SET @legacy_counter_column_count = ( -- 네 legacy counter column의 존재 개수를 변수에 저장한다.
    SELECT COUNT(*) -- 조건에 맞는 column metadata 행 수를 센다.
    FROM information_schema.columns -- MySQL의 column 구조 정보를 조회한다.
    WHERE table_schema = DATABASE() -- 현재 schema로 제한한다.
      AND table_name = 'posts' -- 기존 posts table의 column만 확인한다.
      AND column_name IN ( -- 다음 네 이름 중 하나인 column만 센다.
          'like_count', -- legacy 좋아요 column이다.
          'report_count', -- legacy 신고 column이다.
          'reply_count', -- legacy 댓글 column이다.
          'view_count' -- legacy 조회수 column이다.
      )
);

SET @backfill_post_counters_sql = IF( -- 실행할 backfill SQL 문자열을 조건에 따라 선택한다.
    @posts_exists = 1 AND @legacy_counter_column_count = 4, -- posts와 네 legacy column이 모두 있을 때만 INSERT를 선택한다.
    'INSERT INTO post_counters ( -- 문자열 내부의 INSERT 대상 table을 지정한다.
        post_id,
        like_count,
        report_count,
        reply_count,
        view_count
    )
    SELECT
        post.post_id, -- 같은 게시글 ID를 복사한다.
        post.like_count, -- 기존 좋아요 수를 복사한다.
        post.report_count, -- 기존 신고 수를 복사한다.
        post.reply_count, -- 기존 댓글 수를 복사한다.
        post.view_count -- 기존 조회수를 복사한다.
    FROM posts post -- legacy 데이터 원본 table이다.
    LEFT JOIN post_counters counter -- 이미 새 counter가 있는지 비교한다.
        ON counter.post_id = post.post_id -- 같은 게시글 ID끼리 연결한다.
    WHERE counter.post_id IS NULL', -- 새 counter가 없는 게시글만 선택하고 SQL 문자열을 닫는다.
    'SELECT 1' -- 조건이 맞지 않으면 변경 없는 query를 선택한다.
);

PREPARE backfill_post_counters_statement FROM @backfill_post_counters_sql; -- 선택한 문자열을 실행 가능하게 준비한다.
EXECUTE backfill_post_counters_statement; -- INSERT 또는 SELECT 1을 실행한다.
DEALLOCATE PREPARE backfill_post_counters_statement; -- prepared statement 자원을 해제한다.
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

## 9.10.2 빈 DB baseline 검증 테스트

파일: `FlywayBaselineMigrationTest.java`

이 테스트는 MySQL 호환 모드의 빈 H2 DB에 실제 migration 폴더를 적용한다. 운영 MySQL 자체를 완전히 대체하는 테스트는 아니지만, `B3` SQL이 빈 DB를 만들 수 있는지와 Flyway가 `V1~V3` 대신 `B3`를 선택하는지는 빠르게 검증한다.

```java
package kr.adapterz.springdatajpa.config; // migration 설정 테스트가 속한 package다.

import org.flywaydb.core.Flyway; // 코드에서 직접 Flyway 실행 객체를 만들기 위한 class다.
import org.junit.jupiter.api.Test; // method를 JUnit test로 표시하는 annotation이다.

import java.sql.Connection; // 생성된 DB를 JDBC로 확인할 연결 type이다.
import java.sql.DriverManager; // H2 JDBC 연결을 여는 class다.
import java.sql.ResultSet; // SELECT 결과 행을 읽는 type이다.
import java.sql.Statement; // 확인용 SQL을 실행하는 type이다.

import static org.assertj.core.api.Assertions.assertThat; // 실제 값과 기대값을 읽기 쉽게 비교한다.

class FlywayBaselineMigrationTest { // 빈 DB baseline 동작만 검증하는 test class다.

    @Test // 아래 method를 독립 test case로 실행한다.
    void emptyDatabaseIsCreatedFromCurrentBaseline() throws Exception { // 빈 DB가 현재 baseline으로 생성되는지 검증한다.
        String jdbcUrl = "jdbc:h2:mem:flyway-baseline;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"; // 메모리 H2를 MySQL 호환·소문자 table mode로 열고 연결 종료 뒤에도 유지한다.
        Flyway flyway = Flyway.configure() // Flyway 설정 builder를 시작한다.
                .dataSource(jdbcUrl, "sa", "") // 방금 정의한 빈 H2를 migration 대상으로 지정한다.
                .locations("classpath:db/migration") // 운영과 같은 migration classpath 폴더를 읽는다.
                .load(); // 설정을 실행 가능한 Flyway 객체로 만든다.

        flyway.migrate(); // 빈 DB에서 발견한 migration을 실제 실행한다.

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3"); // 실행 후 현재 schema version이 B3의 3인지 확인한다.

        try ( // 확인이 끝나면 Connection과 Statement를 자동으로 닫는다.
                Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""); // migration이 끝난 같은 H2에 연결한다.
                Statement statement = connection.createStatement() // metadata 확인 SQL을 실행할 Statement를 만든다.
        ) {
            assertThat(tableExists(statement, "users")).isTrue(); // 핵심 사용자 table이 생성됐는지 확인한다.
            assertThat(tableExists(statement, "posts")).isTrue(); // 핵심 게시글 table이 생성됐는지 확인한다.
            assertThat(tableExists(statement, "comments")).isTrue(); // 댓글 table이 생성됐는지 확인한다.
            assertThat(tableExists(statement, "post_counters")).isTrue(); // counter table이 생성됐는지 확인한다.
            assertThat(tableExists(statement, "post_images")).isTrue(); // 게시글 이미지 table이 생성됐는지 확인한다.
            assertThat(tableExists(statement, "post_likes")).isTrue(); // 좋아요 기록 table이 생성됐는지 확인한다.
            assertThat(tableExists(statement, "post_likes_seq")).isTrue(); // 좋아요 ID 공급 table이 생성됐는지 확인한다.
            assertThat(tableExists(statement, "post_reports")).isTrue(); // 신고 기록 table이 생성됐는지 확인한다.
            assertThat(tableExists(statement, "post_view_counts")).isTrue(); // BIGINT 조회수 table이 생성됐는지 확인한다.
            assertThat(tableExists(statement, "auth_sessions")).isTrue(); // refresh 인증 세션 table이 생성됐는지 확인한다.

            try (ResultSet resultSet = statement.executeQuery(""" // Flyway 이력에서 B3 성공 행을 조회하고 결과를 자동으로 닫는다.
                    SELECT COUNT(*) // 조건에 맞는 이력 행 수를 센다.
                    FROM flyway_schema_history // Flyway가 실행 결과를 기록한 table이다.
                    WHERE script = 'B3__current_schema.sql' // B3 파일의 이력만 선택한다.
                      AND success = TRUE // 성공으로 기록된 행만 선택한다.
                    """)) {
                resultSet.next(); // COUNT 결과의 첫 행으로 cursor를 이동한다.
                assertThat(resultSet.getInt(1)).isEqualTo(1); // B3 성공 이력이 정확히 한 행인지 확인한다.
            }

            try (ResultSet resultSet = statement.executeQuery(""" // V migration 실행 이력을 조회한다.
                    SELECT COUNT(*) // V 파일 이력 수를 센다.
                    FROM flyway_schema_history // 같은 Flyway 이력 table을 조회한다.
                    WHERE script LIKE 'V%' // 파일 이름이 V로 시작하는 이력만 고른다.
                    """)) {
                resultSet.next(); // COUNT 결과 행으로 이동한다.
                assertThat(resultSet.getInt(1)).isZero(); // 새 DB에서는 V1~V3를 실행하지 않았는지 확인한다.
            }
        }
    }

    private boolean tableExists(Statement statement, String tableName) throws Exception { // 지정 table 존재 여부를 재사용해 확인한다.
        try (ResultSet resultSet = statement.executeQuery(""" // information_schema 조회 결과를 자동으로 닫는다.
                SELECT COUNT(*) // 이름이 일치하는 table 개수를 센다.
                FROM information_schema.tables // DB가 제공하는 table metadata를 조회한다.
                WHERE table_schema = CURRENT_SCHEMA() // 현재 test schema로 범위를 제한한다.
                  AND table_name = '%s' // method로 받은 table 이름을 조건에 넣는다.
                """.formatted(tableName))) {
            resultSet.next(); // COUNT 결과 행으로 이동한다.
            return resultSet.getInt(1) == 1; // 정확히 한 table이 있으면 true를 반환한다.
        }
    }
}
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

### baseline 명령과 baseline migration의 차이

- baseline 명령: 이미 테이블이 있는 DB를 특정 Flyway version부터 관리한다고 **이력에 등록**한다. 전체 테이블을 생성하지 않는다.
- baseline migration `B3`: 빈 DB에 version 3 시점의 **실제 전체 테이블을 생성**하는 SQL이다.

현재 `baseline-on-migrate: false`는 첫 번째 동작을 자동으로 하지 않게 막는다. 두 번째 동작인 `B3`는 빈 DB에서 그대로 사용할 수 있다.

### `ddl-auto: validate`와 Flyway

`ddl-auto: validate`는 Entity mapping과 DB schema가 일치하는지 검사하지만 schema를 변경하지 않는다. Flyway가 먼저 migration을 실행하고 Hibernate가 그 결과를 검증하므로, 운영 구조의 변경 이력은 `V` migration에만 남는다.

```text
Entity에 column 추가
→ 다음 V migration에도 같은 column 추가
→ Flyway가 DB 변경
→ Hibernate validate 통과
```

Entity만 바꾸고 migration을 빼먹거나 migration의 type이 Entity와 다르면 시작이 실패한다. 이 실패는 자동으로 다른 구조를 만들어 버리는 것보다 안전하게 불일치를 드러낸다.

### MySQL DDL의 implicit commit

MySQL의 `CREATE TABLE`, `ALTER TABLE` 같은 많은 DDL은 Transaction 경계를 암묵적으로 확정할 수 있다. migration 뒤쪽 문장이 실패해도 앞 DDL이 전부 자동 취소된다고 보장할 수 없다. 실패 복구 때는 SQL 파일만 다시 실행하기 전에 실제 table·column과 Flyway 이력을 확인한다.

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
11. 운영에서 `ddl-auto: validate`를 사용하는 이유는 무엇인가?
12. 새 빈 DB에서 `B3`가 실행되면 V1~V3를 다시 실행하지 않는 이유는 무엇인가?
13. V2가 Flyway 이력 없이 이미 `auth_version`이 존재하는 DB에서 실패할 수 있는 이유는 무엇인가?
14. MySQL migration 중간 실패 시 앞의 DDL까지 모두 rollback됐다고 단정하면 안 되는 이유는 무엇인가?
15. `baseline-on-migrate: false`여도 새 빈 DB에서 `B3`를 실행할 수 있는 이유는 무엇인가?
16. 기존 RDS에서는 배포 전에 `flyway_schema_history`의 무엇을 확인해야 하는가?
17. `post_likes_seq`가 baseline에 필요한 이유는 무엇인가?

## 9.13 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.
