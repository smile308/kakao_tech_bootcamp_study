# 2장. `application.yaml`과 실행 환경

## 2.1 학습 목표

이 장에서는 같은 Java 코드가 로컬, 테스트, 운영 환경에서 서로 다른 DB와 보안 설정을 사용하는 원리를 배운다.

핵심 흐름은 다음과 같다.

```text
공통 application.yaml
+ 현재 활성화된 profile의 application-{profile}.yaml
+ 환경변수를 포함한 외부 Spring property
→ 최종 Spring 설정
→ 설정값이 필요한 Bean 생성
```

## 2.2 실제 코드 원문: 공통 설정

파일: `backend/src/main/resources/application.yaml`

```yaml
spring:
  application:
    name: springdatajpa
  profiles:
    default: local
    group:
      test:
        - local
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      connect-timeout: ${REDIS_CONNECT_TIMEOUT:2s}
      timeout: ${REDIS_COMMAND_TIMEOUT:1s}

app:
  view-count:
    enabled: ${VIEW_COUNT_REDIS_ENABLED:true}
    count-key-prefix: "bamboo:{post-view}:count:"
    dirty-set-key: "bamboo:{post-view}:dirty"
    flush-lock-key: "bamboo:{post-view}:flush-lock"
    flush-interval: ${VIEW_COUNT_FLUSH_INTERVAL:5s}

jwt:
  access-expiration-millis: ${JWT_ACCESS_EXPIRATION_MILLIS:600000}
  refresh-expiration-millis: ${JWT_REFRESH_EXPIRATION_MILLIS:10800000}
  refresh-session-cleanup-interval-millis: ${JWT_REFRESH_SESSION_CLEANUP_INTERVAL_MILLIS:3600000}

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

중요한 줄만 주석을 붙이면 다음과 같다.

```yaml
spring:
  profiles:
    default: local       # 별도 profile을 지정하지 않으면 local을 사용한다.
    group:
      test:
        - local          # test profile은 local 설정도 함께 불러온다.
  data:
    redis:
      host: ${REDIS_HOST:localhost}  # REDIS_HOST property가 있으면 그 주소를, 없으면 기본값 localhost를 사용한다.

app:
  view-count:
    enabled: ${VIEW_COUNT_REDIS_ENABLED:true} # VIEW_COUNT_REDIS_ENABLED property가 있으면 그 값을, 없으면 기본값 true를 사용해 Redis 조회수 구현 활성 여부를 정한다.

management:
  endpoints:
    web:
      exposure:
        include: health  # 배포 상태 검사에 필요한 health endpoint만 공개한다.
```

`${REDIS_HOST:localhost}`의 의미는 다음과 같다.

```text
REDIS_HOST라는 Spring property가 있으면 그 값을 사용
그 property가 없으면 localhost 사용
```

이 property는 여러 외부 설정 경로로 제공할 수 있다. 현재 배포에서는 `.env` 또는 shell의 `REDIS_HOST` → Compose가 backend container에 넣는 `REDIS_HOST` 환경변수 → Spring property의 순서로 전달된다. Compose에도 값이 없으면 `redis`를 넣으므로, Compose로 실행한 backend에서는 Spring YAML의 `localhost`가 아니라 Compose가 제공한 `redis`가 사용된다.

콜론 뒤 기본값이 없는 `${DB_URL}` 같은 표현은 `DB_URL`이라는 Spring property가 반드시 필요하다. 이 값은 환경변수, JVM system property, 명령행 인자 같은 외부 설정으로 제공할 수 있으며, 현재 운영 배포에서는 Compose가 환경변수로 컨테이너에 전달한다.

### 공통 `application.yaml`

```yaml
spring:                                      # Spring Boot가 인식하는 공통 설정의 최상위 영역이다.
  application:                              # 애플리케이션 자체 정보를 설정한다.
    name: springdatajpa                     # Spring이 표시하고 식별할 애플리케이션 이름이다.
  profiles:                                 # 실행 환경별 profile 규칙을 설정한다.
    default: local                          # 별도 지정이 없으면 local profile을 사용한다.
    group:                                  # 하나의 profile을 여러 profile 묶음으로 정의한다.
      test:                                 # test profile이 활성화될 때의 묶음이다.
        - local                             # test 설정에 local 설정도 함께 합친다.
  data:                                     # Spring Data 관련 설정 영역이다.
    redis:                                  # Redis 연결 설정이다.
      host: ${REDIS_HOST:localhost}         # REDIS_HOST property가 있으면 그 주소에, 없으면 기본값 localhost에 연결한다.
      port: ${REDIS_PORT:6379}              # REDIS_PORT property가 있으면 그 포트를, 없으면 기본값 6379를 사용한다.
      connect-timeout: ${REDIS_CONNECT_TIMEOUT:2s} # REDIS_CONNECT_TIMEOUT property가 있으면 그 값을, 없으면 기본값 2초를 Redis 연결 제한 시간으로 사용한다.
      timeout: ${REDIS_COMMAND_TIMEOUT:1s}  # REDIS_COMMAND_TIMEOUT property가 있으면 그 값을, 없으면 기본값 1초를 Redis 읽기 제한 시간으로 사용한다.

app:                                        # Spring 표준이 아닌 이 프로젝트 전용 설정 영역이다.
  view-count:                               # 조회수 처리 기능 설정을 묶는다.
    enabled: ${VIEW_COUNT_REDIS_ENABLED:true} # VIEW_COUNT_REDIS_ENABLED property가 있으면 그 값을, 없으면 기본값 true를 사용해 활성 여부를 정한다.
    count-key-prefix: "bamboo:{post-view}:count:" # 게시글별 조회수 Redis key의 앞부분이다.
    dirty-set-key: "bamboo:{post-view}:dirty" # RDS 반영이 필요한 게시글 ID set의 key다.
    flush-lock-key: "bamboo:{post-view}:flush-lock" # flush Scheduler의 분산 락 key다.
    flush-interval: ${VIEW_COUNT_FLUSH_INTERVAL:5s} # VIEW_COUNT_FLUSH_INTERVAL property가 있으면 그 간격을, 없으면 기본값 5초를 RDS 반영 간격으로 사용한다.

jwt:                                        # 토큰 수명과 세션 정리 설정을 묶는다.
  access-expiration-millis: ${JWT_ACCESS_EXPIRATION_MILLIS:600000} # JWT_ACCESS_EXPIRATION_MILLIS property가 있으면 그 수명을, 없으면 기본값 10분을 사용한다.
  refresh-expiration-millis: ${JWT_REFRESH_EXPIRATION_MILLIS:10800000} # JWT_REFRESH_EXPIRATION_MILLIS property가 있으면 그 수명을, 없으면 기본값 3시간을 사용한다.
  refresh-session-cleanup-interval-millis: ${JWT_REFRESH_SESSION_CLEANUP_INTERVAL_MILLIS:3600000} # JWT_REFRESH_SESSION_CLEANUP_INTERVAL_MILLIS property가 있으면 그 간격을, 없으면 기본값 1시간을 사용한다.

management:                                 # Actuator 운영 endpoint 설정이다.
  endpoints:                                # 여러 Actuator endpoint의 공개 범위를 설정한다.
    web:                                    # HTTP로 공개할 endpoint 설정이다.
      exposure:                             # 외부에 노출할 endpoint 목록이다.
        include: health                     # 배포 상태 검사에 필요한 health만 공개한다.
  endpoint:                                 # 개별 endpoint의 동작을 설정한다.
    health:                                 # health endpoint 설정이다.
      show-details: never                   # 내부 구성요소 상세 상태를 외부 응답에 노출하지 않는다.
```

## 2.3 실제 코드 원문: 로컬 설정

파일: `application-local.yaml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:

  h2:
    console:
      enabled: true
      path: /h2-console

  jpa:
    hibernate:
      ddl-auto: create
    properties:
      hibernate:
        format_sql: true
    show-sql: true
    defer-datasource-initialization: true

  sql:
    init:
      mode: always

  flyway:
    enabled: false

jwt:
  secret: ${JWT_SECRET:default-development-jwt-secret-key-must-be-at-least-32-chars-change-me}
  refresh-cookie-secure: false
  refresh-cookie-path: /sessions

cors:
  allowed-origins: http://localhost:5500,http://127.0.0.1:5500,http://localhost:5173,http://127.0.0.1:5173
```

핵심 property를 점 표기법으로 축약하면 다음과 같다. 이것은 위 YAML의 구조를 읽기 쉽게 펼어 쓴 설명이다.

```text
spring.jpa.hibernate.ddl-auto=create # 시작할 때 기존 H2 스키마를 제거하고 Entity 기준으로 다시 만든다.
spring.jpa.show-sql=true             # 실행된 SQL을 개발자가 볼 수 있게 한다.
spring.sql.init.mode=always          # 시작할 때 data.sql을 실행한다.
spring.flyway.enabled=false          # 로컬 H2에서는 운영 마이그레이션을 실행하지 않는다.
jwt.refresh-cookie-secure=false      # 로컬 HTTP에서도 쿠키를 전송할 수 있게 Secure 속성을 끄는다.
```

### `application-local.yaml`

```yaml
spring:                                     # local profile에서 덮어쓸 Spring 설정이다.
  datasource:                               # 로컬 DB 연결 정보다.
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE # 마지막 연결이 닫힌 뒤에도 testdb를 유지하고 JVM 종료 시 H2의 자동 close를 끄는 접속 URL이다.
    driver-class-name: org.h2.Driver        # H2 JDBC 드라이버를 사용한다.
    username: sa                            # 로컬 H2 사용자 이름이다.
    password:                               # 로컬 H2 비밀번호는 빈 값이다.

  h2:                                       # H2 전용 기능 설정이다.
    console:                                # 브라우저 H2 Console 설정이다.
      enabled: true                         # 로컬에서 Console을 켠다.
      path: /h2-console                     # Console 접속 경로를 지정한다.

  jpa:                                      # JPA와 Hibernate 설정이다.
    hibernate:                              # Hibernate의 스키마 처리 설정이다.
      ddl-auto: create                      # 시작할 때 기존 스키마를 제거하고 Entity 기준으로 다시 만든다.
    properties:                             # Hibernate 세부 속성이다.
      hibernate:
        format_sql: true                    # 로그의 SQL을 읽기 쉽게 줄바꿈한다.
    show-sql: true                          # 실행한 SQL을 콘솔에 출력한다.
    defer-datasource-initialization: true   # Hibernate 스키마 생성 뒤 data.sql을 실행한다.

  sql:                                      # Spring SQL 초기화 설정이다.
    init:
      mode: always                          # local 시작 시 SQL 초기화를 활성해 data.sql을 실행한다.

  flyway:                                   # Flyway migration 설정이다.
    enabled: false                          # H2 local 환경에서는 MySQL migration을 실행하지 않는다.

jwt:                                        # local 전용 JWT와 Cookie 설정이다.
  secret: ${JWT_SECRET:default-development-jwt-secret-key-must-be-at-least-32-chars-change-me} # JWT_SECRET property가 있으면 그 값을, 없으면 개발용 비밀키를 쓴다.
  refresh-cookie-secure: false              # HTTP localhost에서도 Cookie를 전송할 수 있게 Secure를 끈다.
  refresh-cookie-path: /sessions            # local API에서 Refresh Cookie를 보낼 경로다.

cors:                                       # 브라우저 교차 출처 요청 허용 설정이다.
  allowed-origins: http://localhost:5500,http://127.0.0.1:5500,http://localhost:5173,http://127.0.0.1:5173 # 허용할 로컬 프론트 주소다.
```

## 2.4 실제 코드 원문: 운영 설정

파일: `application-prod.yaml`

```yaml
spring:
  datasource:
    url: ${DB_URL}
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  h2:
    console:
      enabled: false

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

jwt:
  secret: ${JWT_SECRET}
  refresh-cookie-secure: ${JWT_REFRESH_COOKIE_SECURE:true}
  refresh-cookie-path: /api/sessions

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS}
```

운영에서는 DB 주소, 계정, 비밀번호, JWT 비밀키와 허용 Origin을 코드에 적지 않고 외부 Spring property로 받는다. 현재 Compose 배포에서는 이 값들을 container 환경변수로 제공한다.

`refresh-cookie-secure`는 YAML에서는 property가 없을 때 `true`를 쓴다. 그러나 현재 `deploy/compose.yaml`은 `JWT_REFRESH_COOKIE_SECURE`이 없으면 `false`를 container에 넣는다. 따라서 **현재 Compose의 기본 배포값은 `false`**이며, HTTPS로 배포할 때는 `.env`에 `JWT_REFRESH_COOKIE_SECURE=true`를 지정해야 한다.

현재 운영 DB 구조 변경과 검증 책임은 다음처럼 나뉜다.

```text
Flyway
→ 버전이 붙은 SQL 파일을 순서대로 적용하고 이력을 기록

ddl-auto: validate
→ Entity와 migration 결과가 맞는지 검사하고 구조는 변경하지 않음
```

새 빈 DB에는 `B3__current_schema.sql`이 현재 전체 스키마를 생성한다. `baseline-on-migrate: false`는 이력 없는 기존 non-empty DB를 자동으로 받아들이지 않게 하며, 빈 DB에서 B3를 실행하는 동작은 막지 않는다. 자세한 migration 흐름은 10장에서 다룬다.

### `application-prod.yaml`

```yaml
spring:                                     # prod profile에서 덮어쓸 Spring 설정이다.
  datasource:                               # RDS MySQL 연결 정보다.
    url: ${DB_URL}                          # 기본값 없는 DB_URL property가 필요하며, 운영 Compose가 환경변수로 전달한다.
    driver-class-name: com.mysql.cj.jdbc.Driver # MySQL JDBC 드라이버를 사용한다.
    username: ${DB_USERNAME}                # 기본값 없는 DB_USERNAME property를 운영 Compose의 환경변수에서 받는다.
    password: ${DB_PASSWORD}                # 기본값 없는 DB_PASSWORD property를 운영 Compose의 환경변수에서 받는다.

  h2:
    console:
      enabled: false                        # 운영에서는 H2 Console을 공개하지 않는다.

  jpa:
    hibernate:
      ddl-auto: validate                    # Flyway 결과와 Entity가 맞는지 검사하고 DB 구조는 자동 변경하지 않는다.
    properties:
      hibernate:
        format_sql: false                   # 운영 SQL 로그 포맷 기능을 끈다.
    show-sql: false                         # 운영에서 SQL을 표준 출력에 노출하지 않는다.
    defer-datasource-initialization: false  # data.sql 실행을 기다리는 local 동작을 사용하지 않는다.

  sql:
    init:
      mode: never                           # 운영 RDS에는 data.sql을 자동 적용하지 않는다.

  flyway:
    enabled: true                           # 운영 시작 시 Flyway migration을 실행한다.
    baseline-on-migrate: false              # 이력 없는 기존 non-empty DB를 자동 baseline으로 받아들이지 않는다.
    locations: classpath:db/migration       # JAR의 db/migration 경로에서 SQL 파일을 찾는다.
    validate-on-migrate: true               # 적용 이력과 현재 migration 파일의 일치 여부를 검사한다.

jwt:
  secret: ${JWT_SECRET}                     # 기본값 없는 JWT_SECRET property를 운영 Compose의 필수 환경변수에서 받는다.
  refresh-cookie-secure: ${JWT_REFRESH_COOKIE_SECURE:true} # property가 있으면 그 boolean을, 없으면 YAML 기본값 true를 쓴다. 단, 현재 Compose는 별도 지정이 없으면 false를 넣는다.
  refresh-cookie-path: /api/sessions        # 외부 프록시 경로를 포함한 세션 API에만 Cookie를 보낸다.

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS}  # 기본값 없는 property를 운영 Compose의 필수 환경변수에서 받는다.
```

## 2.5 실제 코드 원문: 테스트 설정

파일: `src/test/resources/application-test.yaml`

```yaml
spring:
  autoconfigure:
    exclude:
      - org.redisson.spring.starter.RedissonAutoConfigurationV4

app:
  view-count:
    enabled: false
```

테스트 profile은 공통 설정과 local 설정을 불러온 뒤 위 설정으로 일부를 덮어쓴다.

```text
test profile
→ local 설정을 함께 사용
→ H2 사용
→ Redisson 자동 설정 제외
→ Redis 조회수 구현 비활성화
→ DB 조회수 구현 사용
```

Redis가 필요한 테스트는 `redis-integration` 태그와 Testcontainers를 사용하여 별도로 Redis 연결을 직접 준비한다.

### `application-test.yaml`

```yaml
spring:                                     # test profile에서 추가로 덮어쓸 Spring 설정이다.
  autoconfigure:                            # Spring Boot 자동 설정을 제어한다.
    exclude:                                # 자동 설정에서 제외할 클래스 목록이다.
      - org.redisson.spring.starter.RedissonAutoConfigurationV4 # 일반 테스트에서 Redisson 연결 Bean을 만들지 않는다.

app:                                        # 프로젝트 전용 설정을 덮어쓴다.
  view-count:
    enabled: false                          # 일반 테스트는 Redis 대신 DB 조회수 구현을 사용한다.
```

## 2.6 설정값이 Java 코드에 들어가는 방법

파일: `src/main/java/kr/adapterz/springdatajpa/auth/JwtProvider.java`

실제 생성자 부분:

```java
public JwtProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-expiration-millis}") long accessExpirationMillis
) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessExpirationMillis = accessExpirationMillis;
}
```

중요한 줄:

```java
@Value("${jwt.secret}") String secret
// 최종으로 합쳐진 설정에서 jwt.secret 값을 찾아 생성자 인자로 넣는다.
```

여러 관련 설정은 `@ConfigurationProperties` 객체로 묶기도 한다.

파일: `src/main/java/kr/adapterz/springdatajpa/config/ViewCountProperties.java`

실제 record 원문:

```java
@ConfigurationProperties(prefix = "app.view-count")
public record ViewCountProperties(
        boolean enabled,
        String countKeyPrefix,
        String dirtySetKey,
        String flushLockKey,
        Duration flushInterval
) {

    public ViewCountProperties {
        requireText(countKeyPrefix, "countKeyPrefix");
        requireText(dirtySetKey, "dirtySetKey");
        requireText(flushLockKey, "flushLockKey");

        if (flushInterval == null || flushInterval.isZero() || flushInterval.isNegative()) {
            throw new IllegalArgumentException("flushInterval must be positive");
        }
    }

    public String countKey(Long postId) {
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId must be positive");
        }
        return countKeyPrefix + postId;
    }

    private static void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
    }
}
```

`app.view-count.enabled`가 `enabled`로, `flush-interval`이 `flushInterval`로 변환된다. record 본문의 검증과 `countKey` 메서드는 설정을 **받는 방법**이 아니라 받은 값을 검증하고 사용하는 코드이다.

이 record가 Spring Bean으로 등록되는 이유는 실행 클래스의 다음 어노테이션이다.

```java
@ConfigurationPropertiesScan
```

Spring은 이 어노테이션으로 `@ConfigurationProperties` 클래스를 찾아 Bean으로 등록하고, 최종 property를 생성자 인자에 변환해 넣는다. `JwtProvider`는 클래스의 `@Component`로 Bean이 되므로 Spring이 그 생성자를 호출하며 `@Value` 인자를 주입한다.


### 설정을 받는 Java 코드

```java
public JwtProvider( // Spring이 JwtProvider Bean을 만들 때 호출하는 생성자다.
        @Value("${jwt.secret}") String secret, // 최종 설정의 jwt.secret 값을 String으로 주입받는다.
        @Value("${jwt.access-expiration-millis}") long accessExpirationMillis // Access Token 수명을 long으로 주입받는다.
) {
    this.secretKey = Keys.hmacShaKeyFor( // 문자열 비밀키를 JWT 서명에 사용할 SecretKey로 변환한다.
            secret.getBytes(StandardCharsets.UTF_8) // 비밀키 문자열을 UTF-8 byte 배열로 바꾼다.
    );
    this.accessExpirationMillis = accessExpirationMillis; // 주입받은 만료 시간을 필드에 저장한다.
}
```

```java
@ConfigurationProperties(prefix = "app.view-count") // app.view-count 아래 설정을 이 record의 필드에 묶는다.
public record ViewCountProperties( // 변경 불가능한 조회수 설정 객체를 선언한다.
        boolean enabled, // app.view-count.enabled를 받는다.
        String countKeyPrefix, // count-key-prefix를 camelCase 필드로 받는다.
        String dirtySetKey, // dirty-set-key를 받는다.
        String flushLockKey, // flush-lock-key를 받는다.
        Duration flushInterval // 5s 같은 값을 Java Duration으로 변환해 받는다.
) {
    public ViewCountProperties { // record의 인자를 모두 받는 compact constructor로, 생성 시점에 값을 검증한다.
        requireText(countKeyPrefix, "countKeyPrefix"); // countKeyPrefix가 null이거나 비어 있거나 공백뿐인지 검증한다.
        requireText(dirtySetKey, "dirtySetKey"); // dirtySetKey가 null이거나 비어 있거나 공백뿐인지 검증한다.
        requireText(flushLockKey, "flushLockKey"); // flushLockKey가 null이거나 비어 있거나 공백뿐인지 검증한다.

        if (flushInterval == null || flushInterval.isZero() || flushInterval.isNegative()) { // 간격이 없거나 0 이하인 잘못된 설정인지 확인한다.
            throw new IllegalArgumentException("flushInterval must be positive"); // 잘못된 간격으로는 설정 객체를 만들지 못하게 한다.
        }
    }

    public String countKey(Long postId) { // 게시글 ID를 받아 해당 게시글의 Redis 조회수 key를 만든다.
        if (postId == null || postId <= 0) { // ID가 없거나 양수가 아닌지 검증한다.
            throw new IllegalArgumentException("postId must be positive"); // 잘못된 ID이면 key를 만들지 않고 예외를 발생시킨다.
        }
        return countKeyPrefix + postId; // 설정에서 받은 prefix 뒤에 게시글 ID를 붙여 key를 반환한다.
    }

    private static void requireText(String value, String propertyName) { // 반복되는 문자열 설정 검증을 하나의 메서드로 묶는다.
        if (value == null || value.isBlank()) { // 값이 null이거나 공백만 있는지 확인한다.
            throw new IllegalArgumentException(propertyName + " must not be blank"); // 잘못된 property 이름을 메시지에 넣어 예외를 발생시킨다.
        }
    }
}
```

```java
@ConfigurationPropertiesScan // @ConfigurationProperties가 붙은 설정 클래스를 찾아 Spring Bean으로 등록하게 한다.
```

## 2.7 핵심 축약본

```text
application.yaml
→ 모든 환경의 공통 설정과 기본값

application-local.yaml
→ H2, data.sql, 개발용 JWT와 CORS

application-prod.yaml
→ RDS MySQL, Flyway, 외부 비밀값, Cookie Secure 설정

application-test.yaml
→ Redis 자동 설정을 끄고 일반 테스트를 H2와 DB 조회수 구현으로 실행
```

## 2.8 스킵할 코드

- 각 timeout 숫자는 “Redis 연결 제한 시간과 읽기 제한 시간”이라는 맥락만 확인한다.
- Redis 키 문자열은 Redis 장에서 구조를 다시 설명한다.
- Flyway의 세부 옵션은 마이그레이션 장에서 다시 설명한다.
- JWT 만료 시간의 보안 의미는 인증 장에서 다시 설명한다.


## 2.8.1 이 장에서 필요한 YAML·Spring 설정 문법

### 들여쓰기가 구조다

YAML은 중괄호 대신 공백 들여쓰기로 부모·자식 관계를 표현한다.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
```

점 표기법으로 읽으면 `spring.datasource.url`이다. Tab이 아니라 공백을 사용하며, 같은 깊이의 항목은 같은 칸에 맞춘다.

### Map과 List

```yaml
profiles:
  group:
    test:
      - local
```

- `이름: 값`은 key와 value로 이루어진 Map 항목이다.
- `- local`의 하이픈은 List 원소다.
- 따라서 test 그룹은 local profile 목록을 가진다.

### Scalar와 따옴표

```yaml
enabled: true
port: 6379
example-text: "0"
```

- `true`는 boolean이다.
- `6379`는 숫자다.
- `"0"`은 따옴표가 있으므로 문자열이다. `example-text`는 YAML 문법 설명을 위한 예시 key다.
- Spring은 대상 Java 타입에 맞춰 많은 값을 자동 변환한다.

### Spring property placeholder

```yaml
host: ${REDIS_HOST:localhost}
url: ${DB_URL}
```

- `${이름:기본값}`은 해당 Spring property가 제공되면 그 값을 쓰고, 없으면 콜론 뒤의 기본값을 쓴다. 환경변수는 property를 제공하는 여러 방법 중 하나다.
- `${이름}`은 기본값이 없어 값이 제공되지 않으면 설정 해석이나 Bean 생성이 실패할 수 있다.
- 이것은 GitHub Actions의 `${{ ... }}`와 다른 문법이다.

### 시간 단위 변환

```yaml
flush-interval: 5s
connect-timeout: 2s
```

Spring이 `5s`, `2s`를 Java `Duration`으로 변환한다. `ms`, `s`, `m`처럼 단위를 붙여 의미를 명시한다.

### profile 병합과 덮어쓰기

```text
application.yaml
+ application-prod.yaml
```

같은 key가 양쪽에 있으면 활성 profile 파일의 값이 공통값을 덮어쓴다. 서로 다른 key는 함께 남는다.

### kebab-case와 camelCase

```text
flush-interval
→ flushInterval
```

`@ConfigurationProperties`의 relaxed binding은 YAML의 kebab-case 이름을 Java camelCase 필드에 연결한다.

### `@Value`

```java
@Value("${jwt.secret}") String secret
```

어노테이션의 문자열은 Spring property 표현식이다. Spring은 최종 설정에서 값을 찾고 필요한 Java 타입으로 변환한 뒤 `JwtProvider` 생성자의 해당 매개변수에 전달한다.

## 2.9 이해 확인

1. 별도 profile을 지정하지 않으면 어떤 profile이 활성화되는가?
2. `${REDIS_HOST:localhost}`에서 `REDIS_HOST` property가 없으면 어떤 값이 사용되는가?
3. local과 prod는 각각 어떤 DB를 사용하는가?
4. 운영 DB 비밀번호를 YAML에 직접 적지 않은 이유는 무엇인가?
5. test profile에서 Redis 자동 설정과 조회수 기능을 끄는 이유는 무엇인가?
6. `@Value`와 `@ConfigurationProperties`는 설정을 Java 코드에 어떻게 전달하는가?
7. Flyway와 `ddl-auto`의 책임은 어떻게 다른가?
8. `application-prod.yaml`의 `refresh-cookie-secure` 기본값과 현재 Compose가 실제로 넣는 기본값은 각각 무엇이며, HTTPS 배포에서는 어떻게 설정해야 하는가?

## 2.10 전체 모범 답안

1. 별도 profile을 지정하지 않으면 `spring.profiles.default` 설정에 따라 `local` profile이 사용된다.
2. `REDIS_HOST` property가 없으면 콜론 뒤의 YAML 기본값 `localhost`가 사용된다. 단, 현재 Compose 실행에서는 Compose가 기본으로 `REDIS_HOST=redis`를 제공한다.
3. local은 memory H2 DB를 사용하고, prod는 MySQL driver로 RDS MySQL에 연결한다. Redis는 이 주 DB 구분과 별개의 조회수 처리용 저장소다.
4. YAML은 Git에 포함될 수 있어 비밀번호를 적으면 유출될 수 있고, 환경별로 값도 다르므로 외부 property로 제공한다.
5. 일반 test는 외부 Redis 연결 없이 H2와 DB 조회수 구현으로 실행하기 위해 Redisson 자동 설정과 Redis 조회수 구현을 끈다. 실제 Redis가 필요한 test는 Testcontainers를 사용하는 `redisTest`로 분리되어 있다.
6. `@Value("${jwt.secret}")`는 최종 Spring property의 `jwt.secret`을 해당 생성자 인자에 넣는다. `@ConfigurationProperties(prefix = "app.view-count")`는 그 prefix 아래의 여러 property를 `ViewCountProperties` record의 생성자 인자에 묶어 넣는다.
7. Flyway는 버전별 migration SQL을 순서대로 적용하고 이력을 DB에 기록한다. `ddl-auto`는 Entity와 DB 스키마를 기준으로 Hibernate가 `create`, `update`, `validate` 등 지정된 방식으로 스키마를 처리한다.
8. `application-prod.yaml`은 property가 없으면 `true`를 쓰지만, 현재 Compose는 별도 값이 없으면 `false`를 container에 넣는다. HTTPS 배포에서는 `.env`에 `JWT_REFRESH_COOKIE_SECURE=true`를 지정해야 한다.

## 2.11 오답노트

### 6번

문제: `@Value`와 `@ConfigurationProperties`는 설정을 Java 코드에 어떻게 전달하는가?

- 나의 답: YAML의 key 값으로
- 부족한 점: 설정을 찾는 기준만 있고 Java 코드에 주입되는 방법이 빠졌다.
- 정답: `@Value("${jwt.secret}")`는 최종 Spring property에서 `jwt.secret`을 찾아 해당 생성자 인자에 넣는다. `@ConfigurationProperties(prefix = "app.view-count")`는 그 prefix 아래의 여러 property를 `ViewCountProperties` record의 생성자 인자에 한번에 묶어 넣는다.

### 7번

문제: Flyway와 `ddl-auto`의 책임은 어떻게 다른가?

- 나의 답: Flyway는 명시적으로 설정하고 `ddl-auto`는 자동으로 스키마를 생성한다.
- 부족한 점: Flyway가 무엇을 순서대로 실행하고 어떤 이력을 관리하는지가 빠졌다. `ddl-auto`도 항상 스키마를 새로 생성하는 것은 아니다.
- 정답: Flyway는 `V1__...sql` 같은 버전별 migration SQL을 순서대로 적용하고 적용 이력을 DB에 기록한다. `ddl-auto`는 Entity와 DB 스키마를 기준으로 Hibernate가 `create`, `update`, `validate` 등 지정된 방식으로 스키마를 처리한다.

### 8번

문제: `application-prod.yaml`의 `refresh-cookie-secure` 기본값과 현재 Compose가 실제로 넣는 기본값은 각각 무엇이며, HTTPS 배포에서는 어떻게 설정해야 하는가?

- 나의 답: `jwt.secret`
- 틀린 점: `jwt.secret`은 JWT 서명용 비밀키이며, 문제는 Refresh Cookie의 `Secure` 속성을 물었다.
- 정답: `application-prod.yaml`의 기본값은 `true`이지만, 현재 Compose는 별도 값이 없으면 `false`를 container에 넣는다. HTTPS 배포에서는 `.env`의 `JWT_REFRESH_COOKIE_SECURE=true`로 설정해야 한다.
