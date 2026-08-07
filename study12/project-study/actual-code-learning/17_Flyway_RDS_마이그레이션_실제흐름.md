# 17장. Flyway·RDS 마이그레이션 실제 흐름

## 17.0 이 장에서 먼저 구분할 것

이 장은 현재 RDS의 테이블을 직접 수정하는 방법이 아니라, 애플리케이션이 시작될 때 Flyway가 migration 파일과 이력 table을 이용해 DB 구조를 원하는 버전까지 맞추는 흐름을 설명합니다.

| 환경 | DB | schema 생성·변경 주체 | Flyway |
|---|---|---|---|
| local | H2 메모리 DB | Hibernate ddl-auto: create, data.sql | 꺼짐 |
| prod | MySQL RDS | Flyway migration | 켜짐 |
| 일반 test | H2 | test profile이 포함한 local 설정 | 꺼짐 |
| Flyway baseline test | H2를 MySQL 모드로 실행 | 테스트가 Flyway를 직접 생성 | 직접 켬 |
| MySQL integration test | Testcontainers MySQL 8.4 | Spring Boot Flyway | 켜짐 |

운영 RDS 접속 값은 application-prod.yaml의 DB_URL, DB_USERNAME, DB_PASSWORD 환경변수에서 들어옵니다. 이 장의 테스트는 실제 AWS RDS가 아니라 H2와 Testcontainers MySQL로 migration 동작을 검증합니다.

## 17.1 애플리케이션 시작에서 Flyway까지

확인 파일:

- backend/src/main/java/kr/adapterz/springdatajpa/SpringdatajpaApplication.java
- backend/src/main/resources/application.yaml
- backend/src/main/resources/application-local.yaml
- backend/src/main/resources/application-prod.yaml
- backend/src/test/resources/application-test.yaml

### 17.1.1 main과 profile

실제 main:

```java
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SpringdatajpaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringdatajpaApplication.class, args);
    }
}
```

SpringApplication.run이 시작되면 Spring Boot가 base YAML과 활성 profile YAML을 병합하고 datasource·JPA·Flyway Bean을 자동 구성합니다. 이 Java 파일에 migration SQL을 호출하는 코드는 없습니다. SQL 실행은 spring.flyway.enabled와 spring.flyway.locations를 읽어 구성한 Flyway Bean의 책임입니다.

### 17.1.2 local은 Flyway를 실행하지 않는다

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
  jpa:
    hibernate:
      ddl-auto: create
  sql:
    init:
      mode: always
  flyway:
    enabled: false
```

- ddl-auto: create는 시작 때 Hibernate가 Entity 기준 schema를 새로 만듭니다.
- sql.init.mode: always는 schema 생성 뒤 data.sql 초기화를 사용합니다.
- flyway.enabled: false이므로 db/migration SQL은 local 시작 흐름에 들어오지 않습니다.

따라서 local H2에서 애플리케이션이 실행된다고 해서 Flyway 파일이 검증된 것은 아닙니다.

### 17.1.3 prod는 Hibernate가 아니라 Flyway가 schema를 관리한다

```yaml
spring:
  datasource:
    url: ${DB_URL}
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: validate

  sql:
    init:
      mode: never

  flyway:
    enabled: true
    baseline-on-migrate: false
    locations: classpath:db/migration
    validate-on-migrate: true
```

실행 의미:

1. 환경변수로 MySQL/RDS datasource를 만듭니다.
2. classpath:db/migration에서 migration 파일을 찾습니다.
3. DB의 flyway_schema_history와 파일 목록을 비교합니다.
4. 적용되지 않은 migration을 버전 순서대로 실행합니다.
5. validate-on-migrate: true로 이미 적용된 파일의 checksum·이름 변경 여부를 검사합니다.
6. Hibernate는 validate만 수행하므로 schema를 자동 생성·수정하지 않습니다.

ddl-auto: validate는 schema를 만들어 주는 설정이 아니라 Flyway 결과가 Entity mapping과 맞는지 확인하는 설정입니다.

배포 환경변수의 템플릿 파일도 함께 확인합니다.

파일: backend/deploy/.env.example

```dotenv
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://your-rds-endpoint:3306/bamboo_board_week12
DB_USERNAME=week12_app
DB_PASSWORD=replace-with-a-strong-password
```

이 파일은 실제 secret을 저장하는 실행 파일이 아니라 입력값의 이름과 예시를 보여 주는 template입니다. Compose가 이 값을 backend container의 environment로 주입하면 Spring의 prod YAML placeholder가 datasource 설정으로 사용합니다. 현재 문서만으로 실제 RDS endpoint나 password 값은 확인할 수 없습니다.

### 17.1.4 일반 test profile

```yaml
spring:
  autoconfigure:
    exclude:
      - org.redisson.spring.starter.RedissonAutoConfigurationV4

app:
  view-count:
    enabled: false
```

base YAML의 profile group에서 test는 local을 포함하므로 일반 ActiveProfiles("test") Spring 테스트는 H2와 ddl-auto: create 경로를 사용합니다. FlywayBaselineMigrationTest와 MySqlSchemaIntegrationTest는 각각 테스트 코드에서 Flyway를 별도로 켭니다.

## 17.2 Flyway 파일 이름과 이력 table

현재 migration 파일:

- backend/src/main/resources/db/migration/B3__current_schema.sql
- backend/src/main/resources/db/migration/V1__migrate_post_counters.sql
- backend/src/main/resources/db/migration/V2__add_user_auth_version.sql
- backend/src/main/resources/db/migration/V3__expand_post_view_counts.sql
- backend/src/main/resources/db/migration/V4__remove_legacy_post_counter_view_count.sql
- backend/src/main/resources/db/migration/V5__remove_post_is_fixed.sql
- backend/src/main/resources/db/migration/V6__use_identity_for_post_likes.sql

일반적인 versioned migration 이름은 다음 구조입니다.

```text
V<version>__<description>.sql
```

B3__current_schema.sql은 Baseline migration입니다. 빈 DB에 schema의 출발점을 만들고,
뒤의 V4·V5가 legacy column을 제거한 뒤 V6가 좋아요 ID 생성 구조를 현재 Entity와
맞춰 최종 구조를 완성합니다.

flyway_schema_history는 어떤 파일을 언제 성공적으로 적용했는지를 기록합니다. 실제 table 변경과 별도로 이 history가 있어야 다음 시작 때 성공한 SQL을 다시 실행하지 않습니다.

### 17.2.1 새 RDS와 기존 RDS

```text
새로운 빈 RDS
→ B3 current baseline 적용
→ V4 적용
→ V5 적용
→ V6 적용
→ 현재 schema 완성

기존 RDS
→ flyway_schema_history 확인
→ 이미 성공한 migration은 건너뜀
→ 아직 없는 다음 migration만 적용
```

빈 DB에서 B3가 schema 출발점을 만들고 V4·V5·V6가 뒤처리를 하는 경로는
FlywayBaselineMigrationTest가 검증합니다. V1~V3은 과거 schema를 이미 사용하는 DB가
새 구조로 이동할 때 필요한 역사적 migration입니다. 새 DB에서 V1~V3을 B3와 같은
방식으로 다시 실행하는 흐름으로 이해하면 안 됩니다.

이미 운영 DB에 적용된 migration 파일을 수정하면 Flyway checksum이 달라져 validate 단계에서 실패할 수 있습니다. 기존 파일을 고치지 않고 다음 변경을 새 V6__... 파일로 추가하는 이유입니다.

## 17.3 B3 current schema

파일: backend/src/main/resources/db/migration/B3__current_schema.sql

### 17.3.1 실제 원문

```sql
CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    nickname VARCHAR(10) NOT NULL,
    profile_image LONGTEXT,
    received_report_count INT NOT NULL,
    deleted BIT NOT NULL,
    auth_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB;

CREATE TABLE posts (
    post_id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    post_title VARCHAR(26) NOT NULL,
    post_content VARCHAR(255) NOT NULL,
    is_fixed BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    deleted BIT NOT NULL,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_posts_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE TABLE auth_sessions (
    auth_session_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    refresh_expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    PRIMARY KEY (auth_session_id),
    CONSTRAINT uk_auth_sessions_refresh_token_hash
        UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE INDEX idx_auth_sessions_user_id
    ON auth_sessions (user_id);

CREATE INDEX idx_auth_sessions_refresh_expires_at
    ON auth_sessions (refresh_expires_at);

CREATE TABLE comments (
    comment_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    comment_content VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (comment_id),
    CONSTRAINT fk_comments_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_comments_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;

CREATE TABLE post_counters (
    post_id BIGINT NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    report_count INT NOT NULL DEFAULT 0,
    reply_count INT NOT NULL DEFAULT 0,
    view_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_counters_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;

CREATE TABLE post_images (
    post_image_id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    image_file LONGTEXT NOT NULL,
    image_order INT NOT NULL,
    PRIMARY KEY (post_image_id),
    CONSTRAINT fk_post_images_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;

CREATE TABLE post_likes (
    post_like_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (post_like_id),
    CONSTRAINT uk_post_like_post_user
        UNIQUE (post_id, user_id),
    CONSTRAINT fk_post_likes_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id),
    CONSTRAINT fk_post_likes_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE TABLE post_likes_seq (
    next_val BIGINT
) ENGINE=InnoDB;

INSERT INTO post_likes_seq (next_val) VALUES (1);

CREATE TABLE post_reports (
    post_report_id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_report_id),
    CONSTRAINT uk_post_report_post_user
        UNIQUE (post_id, user_id),
    CONSTRAINT fk_post_reports_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id),
    CONSTRAINT fk_post_reports_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE TABLE post_view_counts (
    post_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_view_counts_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;
```

이 파일은 schema 출발점을 한 번에 만드는 baseline 원문입니다. `post_likes.post_like_id`
가 아직 `AUTO_INCREMENT`가 아니고 `post_likes_seq` 보조 table을 생성하는 이유는
과거 `Like`의 `GenerationType.AUTO` 구조가 남아 있기 때문입니다. 실제 source에
`post_counters.view_count`와 `posts.is_fixed`도 포함되어 있지만, 새 빈 DB의 최종
migration 흐름에서는 V4·V5·V6가 legacy 구조를 현재 Entity와 맞춥니다.

### 17.3.2 B3 table 생성 순서

| 원문 block | 생성 책임 | 연결 |
|---|---|---|
| users | 회원 기본 정보·soft delete·auth version | posts, comments, likes, reports가 user_id 참조 |
| posts | 게시글·작성자·version | users FK |
| auth_sessions | refresh token hash·만료·revoke | users FK, hash unique |
| comments | 댓글·작성자·게시글 | users/posts FK |
| post_counters | like/report/reply/legacy view counter | posts shared key |
| post_images | 이미지와 순서 | posts FK |
| post_likes | 사용자별 좋아요 | post_id,user_id unique |
| post_likes_seq | V6에서 제거되는 과거 post_likes ID 생성 보조 table | B3에만 존재 |
| post_reports | 사용자별 신고 | post_id,user_id unique |
| post_view_counts | 분리된 영구 조회수 | posts shared key |

### 17.3.3 B3 SQL 문법

```sql
CREATE TABLE post_view_counts (
    post_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_view_counts_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;
```

- CREATE TABLE은 table과 column을 생성합니다.
- NOT NULL은 값이 반드시 있어야 함을 뜻합니다.
- DEFAULT 0은 INSERT에서 값을 생략할 때 0을 넣습니다.
- PRIMARY KEY는 row 식별과 index 역할을 합니다.
- FOREIGN KEY는 존재하지 않는 게시글의 조회수 row를 막습니다.
- ENGINE=InnoDB는 MySQL transaction·foreign key·row lock을 지원하는 engine입니다.

B3 전체 원문은 한 번 읽되, 반복되는 VARCHAR·NOT NULL 선언은 Entity mapping과 중복되므로 table 책임·FK·unique·index 차이를 중심으로 확인합니다.

## 17.4 V1 legacy post counter migration

파일: backend/src/main/resources/db/migration/V1__migrate_post_counters.sql

### 17.4.1 V1 전체 원문

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
            FOREIGN KEY (post_id) REFERENCES posts (post_id)
    ) ENGINE=InnoDB',
    'SELECT 1'
);

PREPARE create_post_counters_statement FROM @create_post_counters_sql;
EXECUTE create_post_counters_statement;
DEALLOCATE PREPARE create_post_counters_statement;

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

SET @like_count_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
      AND column_name = 'like_count'
);
SET @alter_like_count_sql = IF(
    @like_count_exists = 1,
    'ALTER TABLE posts MODIFY COLUMN like_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE alter_like_count_statement FROM @alter_like_count_sql;
EXECUTE alter_like_count_statement;
DEALLOCATE PREPARE alter_like_count_statement;

SET @report_count_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
      AND column_name = 'report_count'
);
SET @alter_report_count_sql = IF(
    @report_count_exists = 1,
    'ALTER TABLE posts MODIFY COLUMN report_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE alter_report_count_statement FROM @alter_report_count_sql;
EXECUTE alter_report_count_statement;
DEALLOCATE PREPARE alter_report_count_statement;

SET @reply_count_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
      AND column_name = 'reply_count'
);
SET @alter_reply_count_sql = IF(
    @reply_count_exists = 1,
    'ALTER TABLE posts MODIFY COLUMN reply_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE alter_reply_count_statement FROM @alter_reply_count_sql;
EXECUTE alter_reply_count_statement;
DEALLOCATE PREPARE alter_reply_count_statement;

SET @view_count_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
      AND column_name = 'view_count'
);
SET @alter_view_count_sql = IF(
    @view_count_exists = 1,
    'ALTER TABLE posts MODIFY COLUMN view_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE alter_view_count_statement FROM @alter_view_count_sql;
EXECUTE alter_view_count_statement;
DEALLOCATE PREPARE alter_view_count_statement;
```

V1은 서로 다른 과거 schema를 한 파일에서 다루기 위해 information_schema를 검사하고, 실행할 SQL을 문자열로 만든 뒤 PREPARE → EXECUTE → DEALLOCATE 합니다.

### 17.4.2 V1 실행 흐름

```text
posts table 존재 여부 확인
→ posts가 있으면 post_counters CREATE TABLE SQL 선택
→ posts의 legacy counter column 4개 존재 여부 확인
→ 네 column이 모두 있으면 data backfill SQL 선택
→ 각 legacy column이 있으면 NOT NULL·DEFAULT 0으로 정규화
→ 없으면 해당 단계는 SELECT 1로 건너뜀
```

### 17.4.3 information_schema와 동적 SQL

```sql
SET @posts_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
);

SET @create_post_counters_sql = IF(
    @posts_exists = 1,
    'CREATE TABLE IF NOT EXISTS post_counters (...) ENGINE=InnoDB',
    'SELECT 1'
);

PREPARE create_post_counters_statement FROM @create_post_counters_sql;
EXECUTE create_post_counters_statement;
DEALLOCATE PREPARE create_post_counters_statement;
```

- information_schema.tables는 현재 database의 metadata입니다.
- DATABASE()는 현재 연결 database 이름을 반환합니다.
- @posts_exists는 MySQL session variable입니다.
- IF는 실행할 SQL 문자열을 선택합니다.
- PREPARE는 문자열을 실행 가능한 statement로 준비합니다.
- EXECUTE는 준비한 statement를 실행합니다.
- DEALLOCATE PREPARE는 준비 statement를 제거합니다.

posts가 없을 때 SELECT 1을 선택하는 것은 해당 schema 상태에서 존재하지 않는 table을 대상으로 SQL을 실행하지 않기 위해서입니다. 모든 오류를 숨긴다는 뜻은 아니며, 처리하지 못한 구조는 SQL 오류나 후속 validation에서 드러날 수 있습니다.

### 17.4.4 legacy counter backfill

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
```

INSERT는 posts의 기존 counter 값을 새 post_counters row로 옮깁니다. LEFT JOIN과 WHERE counter.post_id IS NULL은 같은 post_id의 counter가 이미 있으면 다시 삽입하지 않도록 합니다.

### 17.4.5 반복되는 column 정규화

V1의 like_count, report_count, reply_count, view_count block은 같은 구조입니다.

```sql
SET @like_count_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
      AND column_name = 'like_count'
);

SET @alter_like_count_sql = IF(
    @like_count_exists = 1,
    'ALTER TABLE posts MODIFY COLUMN like_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);

PREPARE alter_like_count_statement FROM @alter_like_count_sql;
EXECUTE alter_like_count_statement;
DEALLOCATE PREPARE alter_like_count_statement;
```

처음 like_count block에서 문법을 자세히 보고, report/reply/view는 column 이름만 다른 반복 코드로 확인합니다.

## 17.5 V2 auth_version 추가

파일: backend/src/main/resources/db/migration/V2__add_user_auth_version.sql

### 17.5.1 실제 원문

```sql
ALTER TABLE users
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
```

auth_version은 Access Token의 현재 인증 버전과 DB User의 버전을 비교하기 위해 추가된 column입니다.

- ALTER TABLE users: 기존 table 변경
- ADD COLUMN: 새 column 추가
- BIGINT: Java Long과 대응할 수 있는 큰 정수형
- NOT NULL DEFAULT 0: 기존 row에도 0을 채워 null을 허용하지 않음

V2에는 ADD COLUMN IF NOT EXISTS가 없습니다. Flyway history가 정상적으로 관리되는 운영 흐름에서는 성공한 V2를 다시 실행하지 않지만, SQL을 수동으로 재실행하면 이미 존재하는 column 오류가 날 수 있습니다.

## 17.6 V3 post_view_counts로 조회수 분리

파일: backend/src/main/resources/db/migration/V3__expand_post_view_counts.sql

### 17.6.1 실제 원문

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

V3 실행 흐름:

```text
post_view_counts table 생성
→ post_counters.view_count를 새 table로 INSERT
→ 이미 있는 post_id는 LEFT JOIN 조건으로 중복 삽입하지 않음
→ 두 table에 값이 모두 있으면 GREATEST로 더 큰 값을 보존
```

### 17.6.2 CREATE·INSERT·UPDATE

```sql
CREATE TABLE IF NOT EXISTS post_view_counts (
    post_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_view_counts_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;
```

CREATE TABLE IF NOT EXISTS는 이미 table이 있을 때 CREATE 오류를 줄입니다. 이것이 migration 파일을 임의로 반복 실행해도 항상 안전하다는 뜻은 아닙니다.

```sql
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
```

LEFT JOIN과 WHERE 조건이 아직 새 table에 없는 게시글만 선택합니다.

```sql
UPDATE post_view_counts view_counter
INNER JOIN post_counters legacy_counter
    ON legacy_counter.post_id = view_counter.post_id
SET view_counter.view_count = GREATEST(
    view_counter.view_count,
    legacy_counter.view_count
);
```

두 table에 값이 모두 있을 수 있으므로 GREATEST로 큰 값을 보존합니다. 그 뒤 V4가 legacy column을 제거합니다.

## 17.7 V4·V5·V6 legacy 구조 정리

### 17.7.1 V4

파일: backend/src/main/resources/db/migration/V4__remove_legacy_post_counter_view_count.sql

```sql
ALTER TABLE post_counters
    DROP COLUMN view_count;
```

V3가 조회수를 post_view_counts로 옮긴 뒤에만 post_counters.view_count를 삭제합니다. 순서를 바꾸면 아직 읽어야 할 legacy 데이터를 먼저 잃습니다.

### 17.7.2 V5

파일: backend/src/main/resources/db/migration/V5__remove_post_is_fixed.sql

```sql
ALTER TABLE posts
    DROP COLUMN is_fixed;
```

V5는 현재 Entity와 API에서 제거된 posts.is_fixed column을 삭제합니다. Entity가 아직 field를 읽는데 V5를 먼저 적용하면 Hibernate validation이나 SQL 실행이 실패할 수 있습니다.

### 17.7.3 V6 — Like IDENTITY 전략으로 통일

파일: backend/src/main/resources/db/migration/V6__use_identity_for_post_likes.sql

```
ALTER TABLE post_likes
    MODIFY COLUMN post_like_id BIGINT NOT NULL AUTO_INCREMENT;

DROP TABLE IF EXISTS post_likes_seq;
```

현재 `Like.java`는 `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 사용합니다.
따라서 MySQL의 `post_likes.post_like_id`도 `AUTO_INCREMENT`여야 Hibernate가 INSERT
시 DB가 발급한 ID를 받을 수 있습니다. `MODIFY COLUMN`은 기존 column의 자료형·필수
조건은 유지하면서 auto-increment 속성을 추가합니다.

`post_likes_seq`는 B3가 만든 과거 `AUTO` 전략용 보조 table입니다. V6는 더 이상 이
table을 사용하지 않으므로 `DROP TABLE IF EXISTS`로 제거합니다. 이미 좋아요 row가
있는 RDS에서는 table을 삭제해도 row 자체는 삭제하지 않고, ID column의 생성 방식만
현재 Entity와 맞춥니다.

이 변경은 이미 적용된 V5 파일을 수정하지 않고 새 V6로 추가되었습니다. Flyway는
성공한 V1~V5의 checksum을 다시 계산해 바꾸는 대신, history에 V6를 새 이력으로
기록합니다.

### 17.7.4 B3와 최종 schema

```text
B3 CREATE
→ post_counters.view_count 존재
→ posts.is_fixed 존재
→ V4 DROP post_counters.view_count
→ V5 DROP posts.is_fixed
→ V6 MODIFY post_likes.post_like_id AUTO_INCREMENT
→ V6 DROP post_likes_seq
→ 현재 Entity와 맞는 최종 schema
```

B3 파일만 보고 최종 DB 구조를 판단하면 안 됩니다. Flyway history와 뒤의 V4·V5·V6
적용 결과까지 봐야 합니다.

## 17.8 Entity·Repository와 RDS schema 연결

확인 파일:

- backend/src/main/java/kr/adapterz/springdatajpa/entity/PostViewCount.java
- backend/src/main/java/kr/adapterz/springdatajpa/entity/AuthSession.java
- backend/src/main/java/kr/adapterz/springdatajpa/entity/Post.java
- backend/src/main/java/kr/adapterz/springdatajpa/repository/PostViewCountRepository.java

```text
post_view_counts.post_id
↔ PostViewCount.postId
↔ Post의 @OneToOne shared key

users.auth_version
↔ User.authVersion
↔ Access Token claim 비교

post_counters.like_count/report_count/reply_count
↔ PostCounter field
```

ddl-auto: validate는 migration 결과와 Java Entity의 이름·타입·nullable·연관관계를 비교합니다. Entity만 고쳐도 RDS schema가 자동으로 바뀌지 않습니다. schema 변경에는 새 migration이 필요합니다.

## 17.9 FlywayBaselineMigrationTest

파일: backend/src/test/java/kr/adapterz/springdatajpa/config/FlywayBaselineMigrationTest.java

이 테스트는 Spring Boot prod datasource가 아니라 H2를 MySQL mode로 만들고 Flyway API를 직접 호출합니다.

### 17.9.1 실제 테스트 원문

```java
package kr.adapterz.springdatajpa.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayBaselineMigrationTest {

    @Test
    void emptyDatabaseIsCreatedFromCurrentBaseline() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:flyway-baseline;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");

        try (
                Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()
        ) {
            assertThat(tableExists(statement, "users")).isTrue();
            assertThat(tableExists(statement, "posts")).isTrue();
            assertThat(tableExists(statement, "comments")).isTrue();
            assertThat(tableExists(statement, "post_counters")).isTrue();
            assertThat(tableExists(statement, "post_images")).isTrue();
            assertThat(tableExists(statement, "post_likes")).isTrue();
            assertThat(tableExists(statement, "post_likes_seq")).isFalse();
            assertThat(tableExists(statement, "post_reports")).isTrue();
            assertThat(tableExists(statement, "post_view_counts")).isTrue();
            assertThat(tableExists(statement, "auth_sessions")).isTrue();
            assertThat(columnExists(statement, "post_counters", "view_count")).isFalse();
            assertThat(columnExists(statement, "posts", "is_fixed")).isFalse();

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE script = 'B3__current_schema.sql'
                      AND success = TRUE
                    """)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE script = 'V4__remove_legacy_post_counter_view_count.sql'
                    """)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE script = 'V5__remove_post_is_fixed.sql'
                    """)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE script = 'V6__use_identity_for_post_likes.sql'
                    """)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }
    }

    private boolean tableExists(Statement statement, String tableName) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = CURRENT_SCHEMA()
                  AND table_name = '%s'
                """.formatted(tableName))) {
            resultSet.next();
            return resultSet.getInt(1) == 1;
        }
    }

    private boolean columnExists(
            Statement statement,
            String tableName,
            String columnName
    ) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = CURRENT_SCHEMA()
                  AND table_name = '%s'
                  AND column_name = '%s'
                """.formatted(tableName, columnName))) {
            resultSet.next();
            return resultSet.getInt(1) == 1;
        }
    }
}
```

### 17.9.2 실행 흐름과 증명 범위

```text
jdbc:h2:mem:flyway-baseline;MODE=MySQL
→ Flyway.configure()
→ classpath:db/migration 위치 지정
→ flyway.migrate()
→ current version == 6 확인
→ 최종 table 9개 존재 확인
→ post_likes_seq 없음 확인
→ post_counters.view_count 없음 확인
→ posts.is_fixed 없음 확인
→ flyway_schema_history에서 B3·V4·V5·V6 성공 기록 확인
```

이 테스트가 증명하는 것:

- 빈 DB에서 current baseline 경로가 실행됨
- 최종 schema에 필요한 9개 table이 있음
- V4·V5·V6 변경이 적용됨
- B3·V4·V5·V6가 history에 성공으로 기록됨
- post_likes_seq가 제거되어 현재 Like의 IDENTITY mapping과 맞음

증명하지 않는 것:

- 실제 AWS RDS 접속·credential
- 모든 과거 운영 schema 변형의 데이터 이동
- 기존 migration checksum 변경 복구
- production network·IAM

## 17.10 MySqlSchemaIntegrationTest

파일: backend/src/test/java/kr/adapterz/springdatajpa/config/MySqlSchemaIntegrationTest.java

### 17.10.1 Spring Boot·Testcontainers 설정

```java
@Tag("mysql-integration")
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=never",
        "app.view-count.enabled=false"
})
class MySqlSchemaIntegrationTest {
```

test profile의 local 기본값을 이 테스트 properties가 덮어써서 Flyway와 validate를 켭니다. Tag 때문에 일반 test에서는 제외되고 mysqlTest에서 선택됩니다.

### 17.10.2 MySQL container와 동적 datasource

```java
@Container
private static final GenericContainer<?> MYSQL =
        new GenericContainer<>(DockerImageName.parse("mysql:8.4"))
                .withEnv("MYSQL_DATABASE", DATABASE_NAME)
                .withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD)
                .withExposedPorts(3306)
                .withStartupTimeout(Duration.ofMinutes(2));

@DynamicPropertySource
static void configureMySql(DynamicPropertyRegistry registry) {
    registry.add(
            "spring.datasource.url",
            () -> "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                    .formatted(
                            MYSQL.getHost(),
                            MYSQL.getMappedPort(3306),
                            DATABASE_NAME
                    )
    );
    registry.add("spring.datasource.username", () -> "root");
    registry.add("spring.datasource.password", () -> ROOT_PASSWORD);
}
```

- Container는 테스트가 끝나면 lifecycle을 정리합니다.
- DynamicPropertySource는 매번 달라지는 mapped port를 Spring datasource에 주입합니다.
- 고정 localhost:3306이 아니라 실제 container 주소를 사용합니다.

### 17.10.3 schema와 동시성 확인

실제 테스트의 schema query:

```java
Integer currentTableCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'users',
              'posts',
              'auth_sessions',
              'comments',
              'post_counters',
              'post_images',
              'post_likes',
              'post_reports',
              'post_view_counts'
          )
        """, Integer.class);

        assertThat(currentTableCount).isEqualTo(9);
```

이후 post_counters.view_count와 posts.is_fixed의 column count가 0인지 확인하고,
post_likes_seq가 없는 최종 구조도 사용하므로 V4·V5·V6 결과를 검증합니다. 그 다음
5개 thread가 같은 게시글에 좋아요를 생성하고 PostCounter.likeCount와 post_likes row
수가 모두 5인지 확인합니다. 이는 migration 후 실제 Entity·Repository·Service가
schema와 호환되는지도 확인하는 단계입니다.

## 17.11 migration task와 테스트 범위

확인 파일: backend/build.gradle

```groovy
tasks.named('test') {
    systemProperty 'spring.profiles.active', 'test'
    useJUnitPlatform {
        excludeTags 'redis-integration', 'mysql-integration'
    }
}

tasks.register('mysqlTest', Test) {
    useJUnitPlatform {
        includeTags 'mysql-integration'
    }
    shouldRunAfter test
}

check.dependsOn tasks.named('mysqlTest')
```

```text
./gradlew test
→ FlywayBaselineMigrationTest와 일반 H2·Mockito 테스트
→ mysql-integration 제외

./gradlew mysqlTest
→ MySqlSchemaIntegrationTest
→ Docker 필요
→ MySQL 8.4 container와 Flyway 실행

./gradlew check
→ 일반 test·coverage 검증
→ mysqlTest
```

이번 문서 갱신에서는 `./gradlew test --tests kr.adapterz.springdatajpa.config.FlywayBaselineMigrationTest`
를 실행했고 통과했습니다. `mysqlTest`와 실제 RDS 연결은 실행하지 않았습니다.

## 17.12 새 RDS·기존 RDS·새 migration

### 새 빈 RDS

```text
prod datasource가 새 RDS를 가리킴
→ flyway_schema_history 없음
→ B3 baseline이 schema 출발점 생성
→ V4·V5가 legacy column 제거
→ V6가 post_likes IDENTITY와 AUTO_INCREMENT를 맞추고 sequence table 제거
→ history에 성공 버전 기록
→ Hibernate validate
→ 애플리케이션 기동
```

### 이미 migration이 적용된 RDS

```text
history의 성공 version 확인
→ 성공한 파일은 다시 실행하지 않음
→ 새 V6 파일이 있으면 V6만 실행
→ 기존 file checksum이 바뀌면 validate 실패 가능
```

이미 적용된 V1~V6를 수정하거나 RDS table을 수동 수정한 뒤 history만 맞추는 방식은 현재 코드의 검증 흐름에 포함되어 있지 않습니다. 다음 구조 변경은 새 버전 migration과 migration test로 추가해야 합니다.

## 17.13 장애·실패 지점

| 지점 | 실제 의미 |
|---|---|
| DB_URL·username·password 오류 | datasource 생성 실패, migration 전에 시작 실패 |
| migration SQL syntax 오류 | 해당 migration 실패, 애플리케이션 기동 실패 가능 |
| 기존 migration checksum 변경 | validate-on-migrate 불일치 |
| FK 순서 오류 | 참조 table보다 자식 table을 먼저 만들면 CREATE 실패 |
| V3 backfill 누락 | post_view_counts가 기존 조회수를 잃을 수 있음 |
| V4를 V3보다 먼저 적용 | legacy view_count 삭제 후 이관할 값이 없어짐 |
| V5를 Entity 제거보다 먼저 적용 | 구버전 애플리케이션 validation·SQL 실패 가능 |
| Hibernate validate 불일치 | migration 결과와 Entity가 다르면 시작 실패 |
| Testcontainers Docker 미실행 | MySQL integration test 실행 불가 |

현재 SQL·테스트에서 확인되는 방어:

- V3는 LEFT JOIN과 GREATEST로 중복·감소를 방지합니다.
- Flyway history가 성공한 migration 재실행을 막습니다.
- prod validate가 DB와 Entity 불일치를 시작 단계에서 발견합니다.
- 두 migration test가 빈 schema와 실제 MySQL 호환성을 별도로 확인합니다.

## 17.14 스킵할 코드

- Gradle wrapper 내부 구현: migration 흐름보다 Gradle 실행기 자체의 코드입니다.
- data.sql fixture row: local H2 초기 데이터이며 Flyway SQL이 아닙니다.
- V1의 report/reply/view 정규화: like_count에서 본 구조의 반복입니다.
- B3의 반복적인 VARCHAR·NOT NULL 선언: Entity mapping과 중복됩니다.

스킵하지 않을 코드:

- B3 table·FK·unique·index 구조
- V1 information_schema·동적 SQL
- V3 data backfill와 GREATEST
- V4·V5·V6 destructive 변경 순서
- Flyway baseline test와 MySQL integration test의 profile·tag 설정

## 17.15 전체 흐름

```text
prod 실행
→ application-prod.yaml
→ MySQL/RDS datasource
→ migration location 검색
→ flyway_schema_history 확인
→ B3 또는 미적용 V migration 실행
→ schema 완성
→ Hibernate ddl-auto=validate
→ EntityManager·Repository Bean 생성
→ Controller 요청 처리

기존 데이터 이동
→ V1 legacy post counters backfill
→ V2 users.auth_version 추가
→ V3 post_view_counts 생성·조회수 이관
→ V4 post_counters.view_count 제거
→ V5 posts.is_fixed 제거
→ V6 post_likes.post_like_id AUTO_INCREMENT 변경·post_likes_seq 제거
```

## 17.16 이해 확인

1. local에서는 왜 Flyway가 아니라 Hibernate ddl-auto: create가 schema를 만드는가?
2. prod의 ddl-auto: validate는 schema를 생성하는가, 검증하는가?
3. flyway_schema_history가 필요한 이유는 무엇인가?
4. 빈 DB에서 B3와 V1~V6가 같은 방식으로 모두 실행된다고 보면 안 되는 이유는 무엇인가?
5. V1이 information_schema와 PREPARE를 사용하는 이유는 무엇인가?
6. V3에서 LEFT JOIN이 하는 역할은 무엇인가?
7. V3와 Repository query에서 GREATEST를 사용하는 이유는 무엇인가?
8. V4를 V3보다 먼저 실행하면 어떤 데이터가 사라질 수 있는가?
9. Like의 ID 생성 전략을 IDENTITY로 바꾸면서 기존 migration을 수정하지 않고 V6를 추가한 이유는 무엇인가?
10. FlywayBaselineMigrationTest와 MySqlSchemaIntegrationTest는 각각 무엇을 검증하는가?
11. ./gradlew test와 ./gradlew mysqlTest의 차이는 무엇인가?
12. Testcontainers MySQL 테스트가 실제 AWS RDS에 대해 증명하지 못하는 것은 무엇인가?

## 17.17 오답노트

아직 이 장에 대한 사용자 답변을 받지 않았습니다. 이후 질문에서 틀린 항목이 생기면 다음 형식으로 추가합니다.

- 질문:
- 사용자의 답:
- 모범 답안:
- 틀린 이유:
- 실제 코드 근거:

## 17.18 진행률

- 이 문서까지 확인한 고유 파일: **169/214개**
- 진행률: **79.0%**
- 이번 문서에서 새로 집계한 migration 파일: B3, V1, V2, V3, V4, V5, V6 7개
- 실행한 검증: `FlywayBaselineMigrationTest` 통과
- 실행하지 않은 검증: 실제 MySQL Testcontainers, AWS RDS, Docker 배포
