# 1단계. 빌드·profile·Spring Boot 시작점

이 단계의 목적은 기능을 구현하는 것이 아니다. 애플리케이션이 어떤 dependency와 profile로 시작하고, main 메서드가 어떤 Spring 기능을 켜는지 확인하는 것이다.

## 읽는 순서

1. `KTB4_Miles_Week12_Back/build.gradle`
2. `KTB4_Miles_Week12_Back/settings.gradle`
3. `src/main/resources/application.yaml`
4. `application-local.yaml`
5. `application-prod.yaml`
6. `SpringdatajpaApplication.java`

설정 파일은 실행 전에 읽고 Java main은 Spring Boot가 시작될 때 실행된다. 따라서 설정을 먼저 보고 main으로 이동한다.

## 1. build.gradle

파일: `KTB4_Miles_Week12_Back/build.gradle`

### 이 파일을 먼저 읽는 이유

Java 코드가 사용하는 Spring, JPA, Security, Redis, JWT, 테스트 library가 여기서 결정된다. 소스에서 annotation이나 class가 어디서 왔는지 모르면 이후 파일의 import를 이해할 수 없다.

~~~groovy
plugins {
    id 'java'
    id 'jacoco'
    id 'org.springframework.boot' version '4.0.6'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'kr.adapterz'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

repositories {
    mavenCentral()
}
~~~

- `plugins`: Gradle에 Java compile, JaCoCo coverage, Spring Boot packaging, dependency version 관리 기능을 추가한다.
- `java`: source/test source set과 compile/test task를 만든다.
- `jacoco`: 테스트가 실행한 line coverage를 계산하는 task를 추가한다.
- Spring Boot plugin: 실행 jar와 bootRun 관련 task를 제공한다.
- dependency-management: Spring Boot가 관리하는 library version을 일관되게 적용한다.
- `group`, `version`: Gradle artifact 식별값이다. Java package와는 별개다.
- `toolchain`: Gradle이 Java 26 toolchain을 사용하도록 요청한다.
- `mavenCentral()`: dependency를 가져올 repository다.

처음 나오는 Groovy 문법은 `{ ... }` closure를 Gradle DSL에 전달하는 구조다. `java { ... }`는 Java class의 method body가 아니라 Gradle 설정 closure다.

## 2. dependency가 제공하는 코드

~~~groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.redisson:redisson-spring-boot-starter:4.6.1'
    implementation 'io.jsonwebtoken:jjwt-api:0.13.0'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.13.0'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.13.0'
    runtimeOnly 'com.h2database:h2'
    runtimeOnly 'com.mysql:mysql-connector-j'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:testcontainers'
}
~~~

- `spring-boot-starter-web`: Controller, embedded server, JSON 처리에 필요한 Spring MVC를 제공한다.
- `validation`: `@Valid`, `@NotBlank`, `@Email`이 여기서 온다.
- `data-jpa`: `@Entity`, Repository, transaction 기능을 제공한다.
- `flyway`: migration 실행 기능을 제공한다.
- `security`: SecurityFilterChain, Filter, Authentication을 제공한다.
- Redisson: Redis client와 분산 lock 기능을 제공한다.
- JJWT API/impl/jackson: JWT API와 runtime 구현을 나눈다.
- H2/MySQL: local/test와 production/integration test의 JDBC driver다.
- Testcontainers: 실제 Redis/MySQL container를 띄우는 테스트 library다.

`implementation`은 main compile/runtime, `runtimeOnly`는 실행 시 필요한 library, `testImplementation`은 test source 전용 library다. 이 구분은 테스트 task를 읽을 때 다시 사용한다.

## 3. settings.gradle

~~~groovy
rootProject.name = 'springdatajpa'
~~~

Gradle root project 이름만 지정한다. 다른 source file을 호출하지 않으므로 이 한 줄의 역할만 확인하고 스킵한다.

## 4. application.yaml: 공통 profile

파일: `src/main/resources/application.yaml`

~~~yaml
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
~~~

- `profiles.default: local`: active profile이 없을 때 local을 사용한다.
- `profiles.group.test: local`: test profile이 local profile도 함께 적용하도록 한다.
- `${NAME:default}`: 환경변수 NAME이 있으면 그 값을 쓰고, 없을 때만 default를 쓴다.
- `app.view-count`: `ViewCountProperties`가 `@ConfigurationProperties(prefix = "app.view-count")`로 받는다.
- Redis key와 flush 설정의 실제 사용 위치는 Redis service에서 확인한다.

YAML은 들여쓰기가 부모-자식 구조를 결정한다. `spring.data.redis.host`는 세 단계 map의 값이지 Java 변수 선언이 아니다.

## 5. local/prod profile의 책임 차이

### local

`application-local.yaml`은 H2 in-memory datasource, `ddl-auto: create`, H2 console, SQL init, Flyway disabled, local JWT secret, HTTP용 cookie, local CORS를 설정한다.

### prod

`application-prod.yaml`은 환경변수 DB 연결, MySQL driver, `ddl-auto: validate`, Flyway enabled, migration location, JWT secret, 운영 CORS를 설정한다.

local은 Hibernate가 schema를 만들고 prod는 Flyway가 migration한 schema를 Hibernate가 validate한다. 이 차이는 RDS 단계에서 migration 파일과 함께 다시 확인한다.

## 6. SpringdatajpaApplication.java

파일: `src/main/java/kr/adapterz/springdatajpa/SpringdatajpaApplication.java`

~~~java
package kr.adapterz.springdatajpa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SpringdatajpaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringdatajpaApplication.class, args);
    }
}
~~~

- `package`: class namespace다.
- `import`: 다른 package class를 짧은 이름으로 쓰기 위한 참조다.
- `@SpringBootApplication`: configuration, component scan, auto-configuration을 묶은 시작 annotation이다.
- `@ConfigurationPropertiesScan`: `ViewCountProperties` 같은 설정 binding class를 찾는다.
- `@EnableScheduling`: `@Scheduled` 메서드를 실행할 infrastructure를 켠다.
- `main(String[] args)`: JVM이 호출하는 정적 진입점이다. 현재 `args`는 사용하지 않는다.
- `SpringApplication.run(...)`: application context를 만들고 설정/Bean/embedded server 시작을 요청한다. 반환 context는 저장하지 않는다.

### 실행 순서

~~~text
JVM → main() → SpringApplication.run()
→ YAML/profile 병합 → component scan
→ ConfigurationProperties binding → scheduling 등록
→ Controller/Service/Repository/Filter Bean 생성
→ HTTP 요청 대기
~~~

이 class는 회원가입이나 게시글을 직접 처리하지 않는다. 나머지 Bean이 생성될 수 있는 시작 조건을 제공한다.

## 다음 단계

다음 문서에서 `User.java → UserRequestDto.java → UserResponseDto.java → UserRepository.java` 순서로 회원가입 데이터가 Entity, DTO, Repository 사이를 어떻게 이동하는지 확인한다.

## 진행 상태

- 이 문서까지 확인한 고유 파일: **12/213개**
- 진행률: **5.6%**
- 계산 기준: 00번 문서의 파일별 누적 진행률표. wrapper와 설정 파일도 역할을 확인했으므로 집계했으며, 내부 구현을 반복 정독한 것은 아닙니다.
- 다음 도달 지점: 02번 회원가입 문서 완료 시 19/213 (8.9%)
