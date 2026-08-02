# 1장. 백엔드의 재료와 실행 규칙 읽기

## 1.1 `build.gradle`은 무엇인가?

Java 소스 코드만 있어서는 Spring Boot 애플리케이션을 바로 실행할 수 없다. 웹 서버, 보안, DB 접근, JWT, Redis 같은 기능을 제공하는 외부 라이브러리가 필요하기 때문이다.

Gradle은 다음 작업을 담당하는 빌드 도구다.

```text
필요한 라이브러리 다운로드
→ Java 코드 컴파일
→ 테스트 실행
→ 테스트 커버리지 검사
→ 실행 가능한 JAR 생성
```

`build.gradle`은 Gradle이 이 프로젝트를 어떻게 빌드하고 검사해야 하는지 선언하는 파일이다.

애플리케이션 기능의 호출 흐름에 포함되는 파일은 아니므로 다음처럼 이해해야 한다.

```text
브라우저 요청 → Controller → Service
```

위 흐름 중간에 `build.gradle`이 실행되는 것이 아니다. `build.gradle`은 애플리케이션을 실행하기 전에 필요한 프로그램과 규칙을 준비한다.

## 1.2 실제 `build.gradle` 코드

```groovy
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

configurations {
	mockitoAgent
}

repositories {
	mavenCentral()
}

dependencies {
	implementation 'org.springframework.boot:spring-boot-starter'
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.springframework.boot:spring-boot-starter-web'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
	testImplementation 'org.testcontainers:testcontainers'
	testImplementation 'org.testcontainers:testcontainers-junit-jupiter'
	implementation 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-flyway'
	implementation('org.redisson:redisson-spring-boot-starter:4.6.1') {
		exclude group: 'org.redisson', module: 'redisson-spring-data-41'
	}
	implementation 'org.redisson:redisson-spring-data-40:4.6.1'
	runtimeOnly 'com.h2database:h2'
	runtimeOnly 'org.springframework.boot:spring-boot-h2console'
	runtimeOnly 'com.mysql:mysql-connector-j'
	runtimeOnly 'org.flywaydb:flyway-mysql'
	implementation 'io.jsonwebtoken:jjwt-api:0.13.0'
	runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.13.0'
	runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.13.0'
	implementation 'org.springframework.boot:spring-boot-starter-security'
	testImplementation 'org.mockito:mockito-core'
	mockitoAgent('org.mockito:mockito-core') {
		transitive = false
	}
}

tasks.withType(Test).configureEach {
	useJUnitPlatform()
	jvmArgs(
			"-javaagent:${configurations.mockitoAgent.asPath}",
			"-Xshare:off"
	)
	testLogging {
		events "passed", "skipped", "failed"
		exceptionFormat "full"
	}
}

tasks.named('test') {
	systemProperty 'spring.profiles.active', 'test'
	useJUnitPlatform {
		excludeTags 'redis-integration'
	}
	finalizedBy jacocoTestReport
}

tasks.register('redisTest', Test) {
	description = 'Runs integration tests against a Testcontainers Redis server.'
	group = 'verification'
	testClassesDirs = sourceSets.test.output.classesDirs
	classpath = sourceSets.test.runtimeClasspath
	useJUnitPlatform {
		includeTags 'redis-integration'
	}
	shouldRunAfter test
}

def coverageIncludes = [
		'kr/adapterz/springdatajpa/entity/**',
		'kr/adapterz/springdatajpa/auth/**',
		'kr/adapterz/springdatajpa/config/**'
]

tasks.named('jacocoTestReport') {
	dependsOn test

	reports {
		html.required = true
		xml.required = true
		csv.required = false
	}

	classDirectories.setFrom(files(sourceSets.main.output.classesDirs.collect {
		fileTree(dir: it, include: coverageIncludes)
	}))
}

tasks.named('jacocoTestCoverageVerification') {
	classDirectories.setFrom(files(sourceSets.main.output.classesDirs.collect {
		fileTree(dir: it, include: coverageIncludes)
	}))

	violationRules {
		rule {
			limit {
				counter = 'LINE'
				value = 'COVEREDRATIO'
				minimum = 0.80
			}
		}
	}
}

check.dependsOn jacocoTestCoverageVerification
check.dependsOn tasks.named('redisTest')
```

### 실제 코드의 라인별 주석본

아래 코드는 원문을 다시 복사한 뒤 각 설정 줄의 의미를 주석으로 추가한 학습용 코드다. 위의 실제 원문과 달리 `//` 뒤의 설명은 프로젝트 파일에 존재하지 않는다.

```groovy
plugins { // Gradle에 필요한 빌드 기능을 추가하는 블록을 시작한다.
	id 'java' // Java 소스 컴파일, 테스트, JAR 생성 작업을 추가한다.
	id 'jacoco' // 테스트가 실제 코드의 몇 줄을 실행했는지 측정하는 작업을 추가한다.
	id 'org.springframework.boot' version '4.0.6' // Spring Boot 실행과 bootJar 생성 기능을 4.0.6 버전으로 추가한다.
	id 'io.spring.dependency-management' version '1.1.7' // Spring Boot와 호환되는 라이브러리 버전 조합을 관리한다.
}

group = 'kr.adapterz' // 빌드 결과물을 식별하는 그룹 이름을 지정한다.
version = '0.0.1-SNAPSHOT' // 아직 정식 확정판이 아닌 현재 개발 버전을 지정한다.

java { // Java 컴파일 환경 설정 블록을 시작한다.

	toolchain { // 빌드에 사용할 Java 도구 버전을 지정한다.
		languageVersion = JavaLanguageVersion.of(26) // 이 프로젝트를 Java 26 기준으로 컴파일한다.
	}
}

configurations { // dependency를 묶어서 사용할 사용자 정의 구성을 선언한다.
	mockitoAgent // Mockito JAR를 JVM Agent로 전달하기 위한 별도 dependency 구성을 만든다.
}

repositories { // 외부 라이브러리를 검색하고 다운로드할 저장소를 선언한다.
	mavenCentral() // 공개 Maven Central 저장소를 사용한다.
}

dependencies { // 메인 코드와 테스트 코드에 필요한 외부 라이브러리를 선언한다.
	implementation 'org.springframework.boot:spring-boot-starter' // Spring Boot의 기본 자동 설정과 로깅 기능을 메인 코드에 추가한다.
	implementation 'org.springframework.boot:spring-boot-starter-actuator' // health check 등 운영 상태 확인 기능을 추가한다.
	implementation 'org.springframework.boot:spring-boot-starter-web' // Controller, JSON 변환, 내장 웹 서버 등 웹 기능을 추가한다.
	testImplementation 'org.springframework.boot:spring-boot-starter-test' // JUnit, AssertJ, Mockito, Spring Test 등 기본 테스트 도구를 추가한다.
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher' // 테스트 실행 시 JUnit Platform을 시작하는 Launcher 구현을 제공한다.
	testImplementation 'org.testcontainers:testcontainers' // 테스트 중 Docker 컨테이너를 실행하고 제어하는 기본 기능을 추가한다.
	testImplementation 'org.testcontainers:testcontainers-junit-jupiter' // Testcontainers를 JUnit 5 생명주기와 연결한다.
	implementation 'org.projectlombok:lombok' // 소스 코드에서 Lombok 어노테이션 타입을 사용할 수 있게 한다.
	annotationProcessor 'org.projectlombok:lombok' // 컴파일 중 Lombok이 생성자와 getter 같은 코드를 생성하게 한다.
	implementation 'org.springframework.boot:spring-boot-starter-validation' // DTO에서 입력값 검증 어노테이션을 사용할 수 있게 한다.
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa' // Entity와 Repository를 이용한 JPA DB 접근 기능을 추가한다.
	implementation 'org.springframework.boot:spring-boot-starter-flyway' // 애플리케이션 시작 시 Flyway DB 마이그레이션을 실행할 수 있게 한다.
	implementation('org.redisson:redisson-spring-boot-starter:4.6.1') { // Redisson의 Redis 연결 및 Spring Boot 자동 설정을 4.6.1 버전으로 추가한다.
		exclude group: 'org.redisson', module: 'redisson-spring-data-41' // Starter가 기본으로 가져오는 Spring Data 4.1용 연동 모듈을 제외한다.
	}
	implementation 'org.redisson:redisson-spring-data-40:4.6.1' // 프로젝트와 호환되는 Spring Data 4.0용 Redisson 연동 모듈을 대신 추가한다.
	runtimeOnly 'com.h2database:h2' // 로컬과 테스트 실행 시 H2 메모리 DB를 사용할 수 있게 한다.
	runtimeOnly 'org.springframework.boot:spring-boot-h2console' // 로컬에서 브라우저로 H2 DB를 확인할 수 있는 Console 기능을 추가한다.
	runtimeOnly 'com.mysql:mysql-connector-j' // 운영 실행 시 Java 애플리케이션이 RDS MySQL과 통신할 드라이버를 추가한다.
	runtimeOnly 'org.flywaydb:flyway-mysql' // 운영 실행 시 Flyway가 MySQL 전용 SQL과 DB 정보를 처리할 수 있게 한다.
	implementation 'io.jsonwebtoken:jjwt-api:0.13.0' // Java 코드가 사용할 JWT 생성·검증 API를 추가한다.
	runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.13.0' // 실행 시 JWT API의 실제 구현을 제공한다.
	runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.13.0' // 실행 시 JWT의 claim 데이터를 JSON으로 변환할 Jackson 연동 기능을 제공한다.
	implementation 'org.springframework.boot:spring-boot-starter-security' // 인증 Filter, 접근 제어, 비밀번호 암호화 등 Spring Security 기능을 추가한다.
	testImplementation 'org.mockito:mockito-core' // 테스트에서 실제 의존성을 대신할 Mock 객체를 만들 수 있게 한다.
	mockitoAgent('org.mockito:mockito-core') { // 같은 Mockito JAR를 JVM Agent 용도의 별도 구성에도 추가한다.
		transitive = false // Mockito가 의존하는 다른 라이브러리까지 Agent 경로에 포함하지 않고 해당 JAR만 사용한다.
	}
}

tasks.withType(Test).configureEach { // Gradle의 모든 Test 종류 작업에 공통 설정을 적용한다.
	useJUnitPlatform() // 모든 테스트 작업이 JUnit 5 기반 JUnit Platform을 사용하게 한다.
	jvmArgs( // 테스트 JVM을 시작할 때 전달할 추가 옵션 목록을 지정한다.
			"-javaagent:${configurations.mockitoAgent.asPath}", // Mockito JAR를 JVM Agent로 미리 연결한다.
			"-Xshare:off" // 테스트 JVM에서 Class Data Sharing을 꺼 Agent 사용 시 발생할 수 있는 경고와 충돌을 줄인다.
	)
	testLogging { // 테스트 실행 결과를 콘솔에 어떻게 표시할지 설정한다.
		events "passed", "skipped", "failed" // 성공, 건너뜀, 실패한 테스트를 모두 출력한다.
		exceptionFormat "full" // 테스트 실패 시 생략되지 않은 전체 예외 정보를 출력한다.
	}
}

tasks.named('test') { // Gradle이 기본으로 제공하는 test 작업의 동작을 추가 설정한다.
	systemProperty 'spring.profiles.active', 'test' // 일반 테스트 실행 JVM에서 Spring의 test 프로필을 활성화한다.
	useJUnitPlatform { // 이 test 작업에서 실행할 JUnit 5 테스트 범위를 설정한다.
		excludeTags 'redis-integration' // redis-integration 태그 테스트는 일반 test 작업에서 제외한다.
	}
	finalizedBy jacocoTestReport // test가 끝나면 성공 여부와 관계없이 JaCoCo 보고서 작업을 이어서 실행한다.
}

tasks.register('redisTest', Test) { // redisTest라는 새로운 Test 작업을 직접 만든다.
	description = 'Runs integration tests against a Testcontainers Redis server.' // Gradle 작업 목록에 표시할 설명을 지정한다.
	group = 'verification' // 이 작업을 검증 관련 Gradle 작업 그룹에 포함한다.
	testClassesDirs = sourceSets.test.output.classesDirs // 기존 테스트 소스를 컴파일해서 만들어진 class 파일 위치를 사용한다.
	classpath = sourceSets.test.runtimeClasspath // 기존 테스트 실행에 필요한 class와 라이브러리 경로를 사용한다.
	useJUnitPlatform { // redisTest도 JUnit 5 기반 JUnit Platform으로 실행한다.
		includeTags 'redis-integration' // redis-integration 태그가 붙은 테스트만 선택한다.
	}
	shouldRunAfter test // 두 작업이 함께 예약됐을 때 redisTest를 일반 test 뒤에 실행하도록 순서를 권장한다.
}

def coverageIncludes = [ // JaCoCo 보고서와 통과 기준에 포함할 실제 코드 경로 목록을 만든다.
		'kr/adapterz/springdatajpa/entity/**', // Entity 패키지 아래의 모든 class를 포함한다.
		'kr/adapterz/springdatajpa/auth/**', // 인증 패키지 아래의 모든 class를 포함한다.
		'kr/adapterz/springdatajpa/config/**' // 설정 패키지 아래의 모든 class를 포함한다.
]

tasks.named('jacocoTestReport') { // 테스트 커버리지 결과 파일을 생성하는 JaCoCo 작업을 설정한다.
	dependsOn test // 보고서를 만들기 전에 일반 test 작업이 실행되도록 한다.

	reports { // 생성할 커버리지 보고서 형식을 설정한다.
		html.required = true // 사람이 브라우저에서 볼 HTML 보고서를 생성한다.
		xml.required = true // CI나 외부 도구가 읽을 XML 보고서를 생성한다.
		csv.required = false // CSV 보고서는 생성하지 않는다.
	}

	classDirectories.setFrom(files(sourceSets.main.output.classesDirs.collect { // 메인 코드의 컴파일 결과 중 커버리지 계산에 사용할 class만 다시 선택한다.
		fileTree(dir: it, include: coverageIncludes) // 각 class 디렉터리에서 coverageIncludes 경로에 해당하는 파일만 포함한다.
	}))
}

tasks.named('jacocoTestCoverageVerification') { // 커버리지가 기준 이상인지 검사하는 JaCoCo 작업을 설정한다.
	classDirectories.setFrom(files(sourceSets.main.output.classesDirs.collect { // 보고서와 동일하게 검증 대상 class를 제한한다.
		fileTree(dir: it, include: coverageIncludes) // Entity, auth, config 경로의 class만 검증 대상으로 선택한다.
	}))

	violationRules { // 커버리지 기준을 통과하지 못했을 때 실패시킬 규칙을 선언한다.
		rule { // 하나의 커버리지 검사 규칙을 시작한다.
			limit { // 이 규칙에서 요구할 최소값을 지정한다.
				counter = 'LINE' // 실행된 코드 줄 수를 기준으로 측정한다.
				value = 'COVEREDRATIO' // 전체 대상 줄 중 실행된 줄의 비율을 계산한다.
				minimum = 0.80 // 실행된 줄의 비율이 최소 80% 이상이어야 한다.
			}
		}
	}
}

check.dependsOn jacocoTestCoverageVerification // check 실행 시 JaCoCo 80% 검증도 반드시 실행하게 한다.
check.dependsOn tasks.named('redisTest') // check 실행 시 Redis 통합 테스트도 반드시 실행하게 한다.
```

## 1.3 핵심만 남긴 축약본

다음은 실제 코드가 아니라 구조를 이해하기 위한 축약본이다.

```groovy
plugins {
    // Java, Spring Boot, 테스트 커버리지 기능 활성화
}

java {
    // Java 26 사용
}

repositories {
    // 라이브러리를 Maven Central에서 다운로드
}

dependencies {
    // 웹, 보안, 검증
    // JPA, H2, MySQL, Flyway
    // JWT
    // Redis와 Redisson
    // JUnit, Mockito, Testcontainers
}

tasks.named("test") {
    // 일반 테스트 실행, Redis 통합 테스트 제외
}

tasks.register("redisTest", Test) {
    // Redis 통합 테스트만 별도 실행
}

tasks.named("jacocoTestCoverageVerification") {
    // 지정된 패키지의 라인 커버리지 80% 검사
}

check.dependsOn(/* 커버리지 검사와 Redis 테스트 */)
```

축약본에서는 개별 라이브러리 이름, Mockito JVM 옵션, 보고서 형식, 세부 패키지 경로를 생략했다. 이 값들은 실제 동작을 확인할 때는 중요하지만 전체 구조를 처음 구분할 때는 한꺼번에 외울 필요가 없다.

## 1.4 `plugins`: Gradle 자체의 능력 추가

```groovy
plugins {
	id 'java'
	id 'jacoco'
	id 'org.springframework.boot' version '4.0.6'
	id 'io.spring.dependency-management' version '1.1.7'
}
```

플러그인은 애플리케이션 내부 기능이 아니라 Gradle이 수행할 수 있는 빌드 작업을 추가한다.

| 플러그인 | 역할 |
|---|---|
| `java` | Java 컴파일과 테스트 작업 추가 |
| `jacoco` | 테스트 커버리지 측정 |
| `org.springframework.boot` | Spring Boot 실행과 `bootJar` 생성 |
| `dependency-management` | Spring이 검증한 라이브러리 버전 조합 관리 |

플러그인과 dependency를 혼동하면 안 된다.

```text
plugin
→ Gradle의 빌드 능력을 확장

dependency
→ 애플리케이션이나 테스트 코드가 사용할 라이브러리
```

## 1.5 프로젝트 정보와 Java 버전

```groovy
group = 'kr.adapterz'
version = '0.0.1-SNAPSHOT'
```

`group`은 프로젝트를 구분하는 이름 공간이고 `version`은 현재 산출물의 버전이다. `SNAPSHOT`은 아직 확정된 정식 배포판이 아닌 개발 중 버전이라는 의미다.

```groovy
java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(26)
	}
}
```

이 프로젝트를 Java 26 기준으로 컴파일하도록 지정한다. 개발자의 컴퓨터나 CI에서 다른 Java가 기본값이어도 Gradle이 요구 버전을 알 수 있게 한다.

`settings.gradle`의 실제 코드는 다음 한 줄이다.

```groovy
rootProject.name = 'springdatajpa'
```

라인별 주석본:

```groovy
rootProject.name = 'springdatajpa' // Gradle이 이 전체 빌드를 식별할 최상위 프로젝트 이름을 지정한다.
```

이 값은 Gradle 프로젝트 이름이다. Spring의 `spring.application.name`이나 Java package 이름과 우연히 비슷할 수 있지만 각각 별도의 설정이다.

## 1.6 `repositories`: 라이브러리를 어디서 받을 것인가?

```groovy
repositories {
	mavenCentral()
}
```

`dependencies`에 적힌 외부 라이브러리를 Maven Central 저장소에서 다운로드하라는 뜻이다.

여기서 Repository라는 단어는 두 가지 의미로 사용되므로 구분해야 한다.

```text
build.gradle의 repositories
→ 외부 라이브러리를 다운로드하는 저장소

PostRepository 같은 JPA Repository
→ 애플리케이션이 DB 데이터에 접근하는 객체
```

## 1.7 dependency 범위

이 프로젝트에서 자주 보이는 범위는 다음과 같다.

| 범위 | 의미 |
|---|---|
| `implementation` | 메인 코드를 컴파일하고 실행할 때 사용 |
| `runtimeOnly` | 컴파일에는 직접 필요 없고 실행할 때 필요 |
| `testImplementation` | 테스트 코드를 작성하고 실행할 때 사용 |
| `testRuntimeOnly` | 테스트 실행 시점에만 필요 |
| `annotationProcessor` | Lombok처럼 컴파일 중 코드를 생성 |

예를 들어 Java 코드에서는 주로 JPA 표준 API를 사용한다. 실제 운영 중 MySQL과 통신하는 드라이버는 실행 시 필요하므로 다음처럼 선언되어 있다.

```groovy
runtimeOnly 'com.mysql:mysql-connector-j'
```

`runtimeOnly`이라고 해서 중요하지 않다는 뜻은 아니다. 이 드라이버가 없으면 운영 중 MySQL에 연결할 수 없다.

## 1.8 dependency를 기능별로 분류하기

개별 문자열을 외우기보다 기능별로 묶어서 본다.

### 웹과 운영 상태

```groovy
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

- Web: Controller, JSON, 내장 웹 서버
- Actuator: 배포 health check에 사용하는 `/actuator/health`

### 검증과 보안

```groovy
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'org.springframework.boot:spring-boot-starter-security'
```

- Validation: DTO의 `@NotBlank`, `@Email` 같은 검증
- Security: 인증 Filter, 접근 권한, 비밀번호 암호화

### DB와 마이그레이션

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-flyway'
runtimeOnly 'com.h2database:h2'
runtimeOnly 'com.mysql:mysql-connector-j'
runtimeOnly 'org.flywaydb:flyway-mysql'
```

- JPA: Entity와 Repository를 통한 DB 접근
- H2: 로컬과 테스트용 메모리 DB
- MySQL Connector: 운영 RDS MySQL 연결
- Flyway: 운영 DB 스키마 변경 이력 적용

### JWT

```groovy
implementation 'io.jsonwebtoken:jjwt-api:0.13.0'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.13.0'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.13.0'
```

`jjwt-api`는 코드가 사용하는 공개 타입과 메서드를 제공한다. `jjwt-impl`은 실제 구현을, `jjwt-jackson`은 JWT 데이터와 JSON 변환을 지원한다.

### Redis와 Redisson

```groovy
implementation('org.redisson:redisson-spring-boot-starter:4.6.1') {
	exclude group: 'org.redisson', module: 'redisson-spring-data-41'
}
implementation 'org.redisson:redisson-spring-data-40:4.6.1'
```

Redisson Starter가 기본으로 가져오는 Spring Data 4.1 연동 모듈을 제외하고 4.0 연동 모듈을 명시적으로 사용한다. 현재 프로젝트의 Spring Data 버전과 호환되는 모듈을 선택하기 위한 설정이다.

Redisson은 이 프로젝트에서 Redis 연결뿐 아니라 여러 백엔드 인스턴스 사이의 분산 락에 사용된다.

### 테스트

```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.testcontainers:testcontainers'
testImplementation 'org.testcontainers:testcontainers-junit-jupiter'
testImplementation 'org.mockito:mockito-core'
```

- Spring Boot Test: JUnit, AssertJ, Spring 테스트 기능
- Mockito: 가짜 의존성 생성
- Testcontainers: 테스트 중 실제 Redis Docker 컨테이너 실행

## 1.9 왜 Redis 테스트를 분리하는가?

일반 테스트 설정은 다음과 같다.

```groovy
tasks.named('test') {
	systemProperty 'spring.profiles.active', 'test'
	useJUnitPlatform {
		excludeTags 'redis-integration'
	}
	finalizedBy jacocoTestReport
}
```

`test` 작업은 `test` 프로필을 사용하고 `redis-integration` 태그가 붙은 테스트를 제외한다. 일반 단위 테스트와 H2 기반 통합 테스트를 먼저 빠르게 실행하기 위한 구조다.

Redis 통합 테스트는 별도 작업이다.

```groovy
tasks.register('redisTest', Test) {
	useJUnitPlatform {
		includeTags 'redis-integration'
	}
}
```

Testcontainers를 사용하는 Redis 테스트는 Docker 컨테이너를 시작해야 하므로 일반 테스트보다 무겁다. 그렇다고 최종 검사에서 생략하지는 않는다.

```groovy
check.dependsOn tasks.named('redisTest')
```

따라서 `check`를 실행하면 Redis 통합 테스트까지 포함된다.

## 1.10 JaCoCo가 검사하는 범위

```groovy
def coverageIncludes = [
		'kr/adapterz/springdatajpa/entity/**',
		'kr/adapterz/springdatajpa/auth/**',
		'kr/adapterz/springdatajpa/config/**'
]
```

커버리지 보고서와 80% 검증 대상은 다음 패키지로 제한되어 있다.

- Entity
- 인증 관련 코드
- 설정 관련 코드

```groovy
minimum = 0.80
```

해당 범위에서 실행된 코드 줄의 비율이 80%보다 낮으면 커버리지 검증이 실패한다.

주의할 점은 Service, Controller, Repository가 커버리지 강제 범위에서 빠져 있다는 것이다. 이 코드들의 테스트가 전혀 없다는 뜻은 아니며, 현재 80% 통과 조건을 계산할 때 제외된다는 뜻이다.

## 1.11 `test`와 `check`의 차이

이 프로젝트에서는 다음처럼 구분할 수 있다.

```text
test
→ 일반 테스트
→ redis-integration 제외
→ 종료 후 JaCoCo 보고서 생성

redisTest
→ redis-integration 태그 테스트만 실행

check
→ 일반적인 검증 작업
→ 커버리지 80% 검증
→ redisTest도 실행
```

CI가 `./gradlew clean check`를 실행하는 이유는 일반 테스트만 실행하는 것보다 더 넓은 검증을 하기 위해서다.

## 1.12 새 기능을 작성할 때 `build.gradle`은 언제 수정하는가?

새 기능이 기존 라이브러리만으로 작성 가능하면 `build.gradle`을 변경하지 않는다.

새로운 외부 기능이 필요하면 일반적으로 다음 순서가 된다.

```text
필요한 기능과 라이브러리 결정
→ build.gradle에 dependency 추가
→ Gradle이 라이브러리 다운로드
→ 그 라이브러리를 사용하는 Java 코드 작성
→ 테스트와 빌드 실행
```

예를 들어 Redis를 처음 도입한다면 Redis 코드를 먼저 작성하기 전에 Redis 연동 라이브러리를 dependency로 추가해야 컴파일할 수 있다.

## 1.13 지금 단계에서 스킵할 부분

다음 부분은 테스트 장에서 다시 실제 테스트 코드와 연결하므로 지금 완전히 외우지 않아도 된다.

- `mockitoAgent` configuration
- `-javaagent`와 `-Xshare:off`
- `testClassesDirs`
- `sourceSets.test.runtimeClasspath`
- JaCoCo의 `classDirectories.setFrom`

지금은 이들이 다음 맥락이라는 것만 기억한다.

```text
Mockito Agent 세부 설정
→ 최신 Java 환경에서 Mockito가 테스트 대상을 가짜 객체로 만들도록 지원

testClassesDirs와 classpath
→ 새로 만든 redisTest가 기존 테스트 코드와 라이브러리를 사용하게 함

JaCoCo classDirectories
→ 어떤 실제 코드의 커버리지를 계산할지 제한
```


## 1.13.1 이 장에서 필요한 Groovy·Gradle 문법

`build.gradle`은 Java가 아니라 Groovy DSL로 작성되어 있다. DSL은 범용 Groovy 문법을 Gradle 설정을 읽기 좋은 형태로 사용한 것이다.

### 블록과 Closure

```groovy
repositories {
    mavenCentral()
}
```

`repositories` 메서드에 중괄호 안의 Closure를 전달한다. Closure는 JavaScript의 callback이나 Java lambda와 비슷하게 나중에 실행할 코드 묶음이다. Gradle은 Closure 안에서 현재 설정 대상의 메서드와 속성을 바로 사용할 수 있게 한다.

### 메서드 괄호 생략

```groovy
id 'java'
```

Groovy에서는 인자가 명확하면 다음 호출의 괄호를 생략할 수 있다.

```groovy
id('java')
```

따라서 `id 'java'`는 설정 문장이면서 동시에 `id` 메서드 호출이다.

### 속성 대입

```groovy
group = 'kr.adapterz'
html.required = true
```

`=` 왼쪽의 Gradle 속성에 오른쪽 값을 넣는다. 점 표기법은 객체 내부 속성으로 들어간다.

### 문자열

```groovy
'java'
"-javaagent:${configurations.mockitoAgent.asPath}"
```

- 작은따옴표 문자열은 내용을 그대로 사용한다.
- 큰따옴표 문자열은 `${...}` 안의 Groovy 값을 문자열에 삽입할 수 있다.
- 여기의 `${...}`는 YAML 환경변수 문법이 아니라 Groovy 문자열 보간이다.

### List 문법

```groovy
def coverageIncludes = [
    'entity/**',
    'auth/**',
    'config/**'
]
```

대괄호는 여러 값을 순서대로 담는 Groovy List다. `def`는 오른쪽 값을 보고 변수 타입을 추론하게 한다.

### 이름 있는 Task와 타입별 Task

```groovy
tasks.named('test') { ... }
tasks.withType(Test).configureEach { ... }
tasks.register('redisTest', Test) { ... }
```

- `named`: 이미 존재하는 이름의 Task를 설정한다.
- `withType(Test)`: `Test` 타입인 모든 Task를 선택한다.
- `register`: 새로운 Task를 지연 생성한다.
- 두 번째 인자인 `Test`는 새 Task의 타입이다.

### dependency 좌표

```groovy
implementation 'org.springframework.boot:spring-boot-starter-web'
```

문자열은 보통 다음 세 부분의 Maven 좌표다.

```text
group : artifact : version
```

Spring dependency management가 버전을 관리하는 dependency는 마지막 version을 생략할 수 있다.

### `exclude`

```groovy
implementation('starter') {
    exclude group: 'org.redisson', module: 'redisson-spring-data-41'
}
```

`group:`과 `module:`은 Groovy의 이름 있는 인자 문법이다. 해당 dependency가 자동으로 끌고 오는 하위 dependency 하나를 제외한다.

### 실행 의존성과 순서 권장

```groovy
check.dependsOn redisTest
redisTest.shouldRunAfter test
```

- `dependsOn`: 앞 Task가 실행되면 뒤 Task도 반드시 실행 대상이 된다.
- `shouldRunAfter`: 둘 다 실행 대상일 때 순서만 권장하며, 이것만으로 다른 Task를 실행 대상에 추가하지 않는다.

## 1.14 이해 확인

1. `build.gradle`은 브라우저 요청을 직접 처리하는 파일인가?
2. plugin과 dependency는 어떻게 다른가?
3. `implementation`, `runtimeOnly`, `testImplementation`의 차이는 무엇인가?
4. H2와 MySQL Connector가 각각 필요한 환경은 어디인가?
5. 일반 `test`에서 Redis 통합 테스트를 제외하는 이유는 무엇인가?
6. `check`를 실행하면 Redis 통합 테스트도 실행되는가?
7. 현재 JaCoCo 80% 기준이 적용되는 패키지는 어디인가?
8. Service가 JaCoCo 강제 범위에 없다는 것이 Service 테스트가 하나도 없다는 뜻인가?
9. `settings.gradle`의 `rootProject.name`은 무엇을 정하며, `spring.application.name`과 같은 설정인가?
10. `repositories { mavenCentral() }`에서 중괄호 블록과 `mavenCentral()`은 각각 Groovy·Gradle에서 무엇을 의미하는가?
11. `'group:name:version'` 형태의 dependency 좌표 세 부분은 각각 무엇을 가리키는가?
12. `tasks.named('test')`와 `tasks.withType(Test)`는 설정 대상을 어떻게 다르게 선택하는가?
13. `dependsOn`과 `shouldRunAfter`의 가장 중요한 차이는 무엇인가?
14. dependency 안의 `exclude group: ..., module: ...`은 어떤 라이브러리를 제외하는가?

## 1.15 전체 모범 답안

1. 아니다. `build.gradle`은 빌드, dependency, 테스트 Task 등을 설정하는 파일이고 HTTP 요청은 Controller가 처리한다.
2. Plugin은 Gradle이 수행할 수 있는 빌드 기능과 Task를 확장하고, dependency는 애플리케이션이나 테스트 코드가 사용할 라이브러리다.
3. `implementation`은 main 코드의 compile·실행에, `runtimeOnly`는 main 코드 compile에는 직접 쓰지 않고 실행 시에, `testImplementation`은 test 코드의 compile·실행에 사용된다.
4. H2는 local과 일반 test의 memory DB에 필요하고, MySQL Connector는 prod에서 RDS MySQL에 JDBC로 연결할 때 필요하다.
5. Redis 통합 test는 Docker·Testcontainers로 실제 Redis container를 시작하므로 더 느리고 외부 실행 환경이 필요하다. 빠른 일반 test와 분리하되 최종 `check`에서는 둘 다 실행한다.
6. 실행된다. `check` Task가 `redisTest`를 dependency로 가지므로 일반 `test`와 Redis 통합 test가 모두 실행 대상이 된다.
7. JaCoCo 80% 강제 기준은 `entity`, `auth`, `config` package에만 적용된다.
8. 아니다. Service test가 존재할 수 있지만 Service package의 coverage가 현재 80% 강제 계산 범위에 포함되지 않는다는 뜻이다.
9. `rootProject.name`은 Gradle project의 이름을 정한다. `spring.application.name`은 실행 중인 Spring application의 이름이므로 서로 다른 설정이다.
10. `{ ... }`는 `repositories` 설정을 묶는 Groovy closure이고, `mavenCentral()`은 Gradle이 제공하는 method를 호출해 Maven Central repository를 등록한다.
11. `group`은 library를 배포한 조직·namespace, `name`은 module·artifact 이름, `version`은 사용할 release 버전을 가리킨다.
12. `tasks.named('test')`는 이름이 정확히 `test`인 Task 하나를 선택하고, `tasks.withType(Test)`는 `Test` type인 모든 Task를 선택한다.
13. `dependsOn`은 대상 Task를 반드시 실행 대상에 추가하지만, `shouldRunAfter`는 둘 다 이미 실행 대상일 때 순서만 권장한다.
14. 현재 코드는 Redisson starter가 전이적으로 가져오는 `org.redisson:redisson-spring-data-41` module을 제외한다.

다음 학습에서는 `application.yaml`, `application-local.yaml`, `application-prod.yaml`, `application-test.yaml`의 실제 코드를 비교하면서 같은 설정 이름이 환경에 따라 어떻게 다른 값으로 완성되는지 학습한다.

## 1.16 오답노트

### 1-3. `implementation`, `runtimeOnly`, `testImplementation`의 차이는 무엇인가?

- 최초 답: 메인 코드 컴파일, 테스트 코드 작성·실행, 테스트 실행 시점
- 혼동한 부분: 세 dependency 범위와 설명의 대응 순서가 섞였다. 특히 `runtimeOnly`는 테스트 코드 작성용 범위가 아니다.
- 정답:
  - `implementation`: 메인 코드를 컴파일하고 실행할 때 사용한다.
  - `runtimeOnly`: 메인 코드 컴파일에는 직접 사용하지 않고 애플리케이션 실행 시 사용한다.
  - `testImplementation`: 테스트 코드를 컴파일하고 실행할 때 사용한다.
- 상태: 재복습 필요

### 1-5. 일반 `test`에서 Redis 통합 테스트를 제외하는 이유는 무엇인가?

- 최초 답: Redis를 사용해야 하는 것도 있고 아닌 것도 있어서
- 부족했던 부분: 테스트마다 Redis 필요 여부가 다르다는 구분은 맞지만, 별도 작업으로 분리한 직접적인 이유가 빠졌다.
- 정답: Redis 통합 테스트는 Docker와 Testcontainers로 실제 Redis 컨테이너를 시작하므로 일반 단위 테스트보다 무겁고 느리다. 따라서 빠른 일반 테스트와 Redis가 필요한 통합 테스트를 분리하되, 최종 `check`에서는 둘 다 실행한다.
- 상태: 재복습 필요
