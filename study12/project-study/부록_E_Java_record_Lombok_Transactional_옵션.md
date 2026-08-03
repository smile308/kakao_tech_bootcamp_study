# 부록 E. Java `record`, Lombok, `@Transactional` 옵션

> 1장, 2장, 5장에 흩어져 등장한 Java/Spring 문법을 한 곳에 모은다. `record`, `Optional`, Lombok annotation, `@Transactional` 옵션의 정확한 의미를 본다.

## E.1 학습 목표

```text
record의 자동 생성과 compact constructor
→ @Getter / @NoArgsConstructor / @RequiredArgsConstructor가 만드는 것
→ @Transactional의 propagation / isolation / readOnly
→ Optional.orElseThrow의 두 형태
→ @MappedSuperclass, @JsonIgnore, @JsonProperty
```

## E.2 `record` (Java 16+)

`ViewCountProperties`가 record로 선언돼 있다.

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
        // ...
    }
}
```

### record가 자동 생성하는 것

```text
record Foo(int x, String y) { }

자동 생성
→ private final int x;
→ private final String y;
→ public Foo(int x, String y) { this.x = x; this.y = y; }
→ public int x() { return x; }
→ public String y() { return y; }
→ equals(Object)
→ hashCode()
→ toString()
```

### 일반 class와 비교

```java
// record 한 줄
public record ViewCountProperties(boolean enabled, ...) { }

// 동일한 class
public final class ViewCountProperties {
    private final boolean enabled;
    // ...
    public ViewCountProperties(boolean enabled, ...) { ... }
    public boolean enabled() { return enabled; }
    // equals, hashCode, toString
}
```

### compact constructor

```java
public record ViewCountProperties(
        boolean enabled,
        String countKeyPrefix,
        String dirtySetKey,
        String flushLockKey,
        Duration flushInterval
) {
    public ViewCountProperties {
        // ↑ 매개변수 목록을 다시 적지 않음
        requireText(countKeyPrefix, "countKeyPrefix");
        requireText(dirtySetKey, "dirtySetKey");
        requireText(flushLockKey, "flushLockKey");
        if (flushInterval == null || flushInterval.isZero() || flushInterval.isNegative()) {
            throw new IllegalArgumentException("flushInterval must be positive");
        }
    }
}
```

`public ViewCountProperties { ... }` 형태는 **compact constructor**다. 매개변수 목록을 다시 적지 않는 대신, 컴파일러가 자동 생성한 필드 대입 직전에 이 블록을 실행한다. 즉 **유효성 검사를 record에 강제**할 수 있다.

### record의 한계

```text
불변 (immutable)
→ final field만 가지므로 setter로 변경 불가
→ 값을 바꾸려면 새 record instance를 만들어야 함

상속 불가
→ record는 암묵적으로 final
→ 다른 record를 extends 할 수 없음
→ implements는 가능

Spring Entity로 사용 불가
→ @Entity는 인자 없는 protected constructor를 요구
→ record는 final이라 상속 못 함 → JPA Entity로 쓸 수 없음
```

## E.3 Lombok

`build.gradle`에 `org.projectlombok:lombok`이 들어가 있다. Lombok은 annotation processor로 컴파일 시점에 boilerplate 코드를 자동 생성한다.

### `@Getter`

```java
@Getter
public class Post {
    private Long postId;
    private String postTitle;
}
```

컴파일 후:

```java
public class Post {
    private Long postId;
    private String postTitle;

    public Long getPostId() { return postId; }
    public String getPostTitle() { return postTitle; }
}
```

`@Getter`를 class에 붙이면 모든 non-static 필드에 대해 getter를 자동 생성한다. 필드 단위로도 붙일 수 있다.

### `@NoArgsConstructor`

```java
@NoArgsConstructor
public class Post { ... }
```

컴파일 후:

```java
public Post() { }
```

JPA Entity는 인자 없는 constructor를 요구한다 (리플렉션으로 instantiate). `@NoArgsConstructor`는 그것을 만들어 준다.

### `@NoArgsConstructor(access = AccessLevel.PROTECTED)`

```java
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post { ... }
```

protected constructor만 생성된다. 즉 **외부에서 `new Post()`로 만들 수 없다**. Entity가 항상 의미 있는 초기값을 가진 상태로 생성되도록 강제한다.

```java
// 외부에서
new Post();  // 컴파일 에러

// 같은 패키지나 상속 class에서
new Post();  // 가능
```

### `@RequiredArgsConstructor`

```java
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
}
```

컴파일 후:

```java
public UserService(
        UserRepository userRepository,
        AuthSessionRepository authSessionRepository,
        PasswordEncoder passwordEncoder
) {
    this.userRepository = userRepository;
    this.authSessionRepository = authSessionRepository;
    this.passwordEncoder = passwordEncoder;
}
```

**final 필드를 받는 생성자**를 자동 생성한다. 의존성 주입을 명시적 생성자 코드로 작성하지 않아도 된다.

### 의존성 주입 패턴 비교

```java
// 패턴 1: @Autowired 필드 주입
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
}
// 단점: final 불가, 테스트 어려움

// 패턴 2: @RequiredArgsConstructor (이 프로젝트)
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
}
// 장점: final 보장, 테스트에서 직접 new 가능

// 패턴 3: 명시적 생성자
@Service
public class UserService {
    private final UserRepository userRepository;
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}
// 장점: 명시적이지만 보일러플레이트
```

## E.4 `@Transactional` 옵션

`CommentService`:

```java
@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {
    // ...
}
```

class 레벨에 `@Transactional`이 붙으면 **모든 public method**가 기본 propagation으로 트랜잭션 안에서 실행된다.

### `propagation` (트랜잭션 전파)

| 값 | 의미 |
|---|---|
| `REQUIRED` (기본) | 기존 트랜잭션이 있으면 합류, 없으면 새로 시작 |
| `REQUIRES_NEW` | 기존 트랜잭션을 잠시 중단하고 새 트랜잭션 시작 |
| `NESTED` | 기존 트랜잭션 안에 savepoint로 분리 |
| `MANDATORY` | 기존 트랜잭션이 반드시 있어야 함 |
| `SUPPORTS` | 기존 트랜잭션이 있으면 합류, 없어도 진행 |
| `NOT_SUPPORTED` | 기존 트랜잭션을 일시 중단 |
| `NEVER` | 기존 트랜잭션이 있으면 예외 |

`RedisViewCountFlushScheduler`처럼 외부 시스템(RDS) 작업이 메인 트랜잭션과 분리돼야 하는 경우 `REQUIRES_NEW`가 필요하다.

### `isolation` (격리 수준)

| 값 | 회피하는 문제 |
|---|---|
| `DEFAULT` (DB 기본) | DB에 위임 (MySQL: REPEATABLE_READ) |
| `READ_UNCOMMITTED` | 없음 (모든 문제 발생) |
| `READ_COMMITTED` | dirty read |
| `REPEATABLE_READ` | non-repeatable read (MySQL 기본) |
| `SERIALIZABLE` | phantom read |

`@Version` 기반 낙관적 락은 isolation과 무관하게 동작한다. application code에서 version mismatch를 검사하기 때문이다.

### `readOnly`

```java
@Transactional(readOnly = true)
public PostResponseDto getPost(Long postId) { ... }
```

`readOnly = true`의 효과:

```text
DB driver에 read-only hint 전달
→ MySQL: SELECT만 보내고 write 시도 안 함
→ Hibernate: dirty checking 비활성화
→ 약간의 성능 이점 (실제 DB가 hint를 활용하는 경우만)

틀린 사용
→ SELECT와 INSERT가 같은 method에 있으면 readOnly=true는 위험
→ INSERT는 정상 commit되지만 readOnly hint로 인해 flush 단계에서 문제 가능
```

### `rollbackFor`

기본은 `RuntimeException`만 rollback. checked exception은 자동 commit된다.

```java
@Transactional(rollbackFor = Exception.class)
public void foo() throws IOException { ... }
```

`Exception.class`를 지정하면 모든 예외에서 rollback. checked exception도 트랜잭션을 롤백시키고 싶을 때 사용한다.

### method 레벨 vs class 레벨

```java
@Service
@Transactional  // class 레벨: 모든 public method
public class CommentService {

    @Transactional(readOnly = true)  // method 레벨이 우선
    public PostResponseDto getPost(Long postId) { ... }
}
```

method 레벨 annotation이 class 레벨을 덮어쓴다.

## E.5 `Optional<T>`

`PostRepository.findByPostIdAndDeletedFalse`는 `Optional<Post>`를 반환한다.

```java
public Optional<Post> findByPostIdAndDeletedFalse(@Param("postId") Long postId);
```

`Optional`은 "값이 있을 수도 없을 수도"를 표현하는 wrapper다. Java 8+ 표준.

### `Optional.orElseThrow` 두 형태

```java
// 형태 1: supplier
.orElseThrow(() -> new DataNullException("No_Post"));

// 형태 2: 미리 만든 예외
.orElseThrow(new DataNullException("No_Post"));
```

형태 1은 supplier(lambda) 안의 표현이 **실제 예외가 필요할 때**만 실행된다. `Optional`이 비어 있을 때만 supplier가 호출되므로, 비어 있지 않으면 `new DataNullException(...)` 자체가 실행되지 않는다.

형태 2는 `new`가 메서드 호출 시점에 무조건 실행된다. `orElseThrow`의 인자가 평가되어야 하므로. 예외 객체는 비쌀 수 있어 형태 1이 더 효율적이다.

### `Optional`의 다른 method

```java
optional.isPresent()             // 값 있는지 boolean
optional.ifPresent(value -> ...) // 값 있을 때만 실행
optional.map(value -> value.x()) // 값 변환
optional.filter(value -> ...)    // 조건 필터
optional.orElse(defaultValue)    // 없으면 기본값
optional.orElseGet(() -> ...)    // 없으면 supplier로 만든 값
```

## E.6 `@JsonIgnore`

`SessionResponseDto`에서:

```java
@Getter
public class SessionResponseDto {
    private String accessToken;
    @JsonIgnore
    private String refreshToken;
    // ...
}
```

Jackson이 JSON으로 직렬화할 때 `refreshToken` 필드를 제외한다. 즉:

```json
{
    "accessToken": "eyJ..."
}
```

`refreshToken`은 body에 안 들어가고 별도 `Set-Cookie` 헤더로만 전달된다.

## E.7 `@JsonProperty`

요청 DTO에서 snake_case → camelCase 매핑이 필요할 때:

```java
@PostMapping
public ... createUser(@RequestBody UserRequestDto request) {
    // JSON의 "profile_image" → request.getProfileImage()
}
```

Jackson 기본 동작이 snake_case를 camelCase로 자동 변환한다. 별도 annotation 없이 동작한다.

## E.8 `@AuthenticationPrincipal`

`CustomUserDetails`는 `UserDetails`를 구현한 래퍼다.

```java
@AuthenticationPrincipal CustomUserDetails userDetails
```

Spring Security가 `SecurityContextHolder`에서 `Authentication`을 꺼내고, 그 안의 `principal`을 형변환해 주입한다. 이 프로젝트에서는 `JwtAuthenticationFilter`가 `CustomUserDetails`를 직접 만들어 넣는다.

## E.9 `Map.of` / `Set.of` / `List.of`

Java 9+ 불변 collection 팩토리:

```java
Set<String> set = Set.of("a", "b", "c");
Map<String, Integer> map = Map.of("k1", 1, "k2", 2);
List<String> list = List.of("x", "y");
```

장점:

```text
불변 — 외부에서 add/remove 불가
→ thread-safe
→ 실수로 변경하는 버그 방지

간결 — new HashSet<>() + add() 5줄 → Set.of() 1줄
```

`api.js`의 `NO_REFRESH_REQUESTS = new Set([...])`와 같은 역할을 Java에서 한다.

## E.10 `@Transactional`이 안 먹는 흔한 실수

```java
@Service
public class UserService {

    @Transactional
    public void createUser() { ... }

    public void someMethod() {
        this.createUser();  // ⚠️ 트랜잭션 안에서 호출되는데
                            //    createUser의 @Transactional이 무시됨
    }
}
```

**Spring의 AOP 기반 @Transactional은 외부 호출에서만 동작한다.** 내부 `this.method()` 호출은 AOP 프록시를 거치지 않아 annotation이 무시된다. 이걸 self-invocation 문제라고 한다.

해결:

```java
// 방법 1: method를 다른 class로 분리
@Service
public class UserHelper {
    @Transactional
    public void createUser() { ... }
}

// 방법 2: 자기 자신의 프록시를 주입
@Service
public class UserService {
    private final UserService self;  // @Lazy
    // ...
}

// 방법 3: TransactionTemplate 사용
```

## E.11 이해 확인

1. `ViewCountProperties`를 record로 만든 이유를 immutable object의 관점에서 설명하라.
2. `@NoArgsConstructor(access = AccessLevel.PROTECTED)`가 Entity에서 필요한 이유와 외부에서 `new`를 막는 목적을 답하라.
3. `@Transactional(readOnly = true)`가 적절한 method 종류와 부적절한 method 종류를 각각 하나씩 답하라.
4. `@Transactional`의 self-invocation 문제가 무엇이고, 현재 프로젝트의 `CommentService`에서 발생할 수 있는지 답하라.
5. `@JsonIgnore`가 붙은 필드는 `getter`로 읽을 수 있는가? 답할 때 Jackson의 동작을 함께 설명하라.
6. `Optional.orElseThrow(() -> ...)`와 `Optional.orElseThrow(new ...())`의 성능 차이를 답하라.
