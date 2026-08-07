# 16장. Redis 조회수 처리

## 16.0 Redis를 처음 배우기 전에

이 절은 프로젝트 코드를 읽기 전에 알아야 하는 Redis의 기본 개념을 설명합니다. 뒤의 절부터는 이 개념이 실제 코드의 어느 줄에서 사용되는지 연결해서 확인합니다.

### 16.0.1 Redis는 무엇인가

Redis는 애플리케이션 안에 들어 있는 Java 자료구조가 아니라, 별도로 실행되는 Redis 서버입니다. Spring 백엔드는 Redis 명령을 네트워크로 보내고, Redis 서버가 key와 value를 저장·조회·변경한 뒤 결과를 돌려줍니다.

```text
Spring 백엔드 JVM
  └─ StringRedisTemplate ── 네트워크 명령 ──> Redis 서버
                                               ├─ key
                                               └─ value
```

따라서 다음 세 저장 공간은 서로 다릅니다.

| 저장 공간 | 프로젝트에서의 예 | 프로세스가 종료되면 | 주된 책임 |
|---|---|---|---|
| Spring/JVM 메모리 | Java 지역 변수, 객체, `SecurityContext` | 사라짐 | 현재 요청을 처리하는 동안의 값 |
| Redis 서버 | 조회수 count key, dirty set | 설정에 따라 메모리에서 사라질 수 있음. AOF/RDB를 켜면 Redis 재시작 후 복구 가능 | 빠른 임시 누적·공유 상태 |
| MySQL/RDS | `PostViewCount.viewCount` | 데이터베이스에 남음 | 최종 영구 데이터 |

Redis가 빠르다는 말은 단순히 “Java 변수보다 빠르다”는 뜻이 아닙니다. Redis는 데이터를 주로 메모리에 두고, 조회수처럼 단순한 key-value 연산을 전용 명령으로 처리합니다. 다만 Redis도 별도 서버이므로 네트워크 왕복이 필요하며, Redis가 RDS보다 빠르다는 이유만으로 영구 데이터베이스를 대체하는 것은 아닙니다.

### 16.0.2 Redis의 key와 자료형

Redis의 기본 접근 방식은 `key`를 이름으로 사용해 `value`를 읽거나 바꾸는 것입니다. value는 하나의 문자열만 뜻하지 않고 String·Set·List·Hash 같은 Redis 자료형이 될 수 있습니다. 이 프로젝트는 다음 두 자료형을 핵심적으로 사용합니다.

```text
String
  key   = bamboo:{post-view}:count:42
  value = "101"
  의미  = 42번 게시글의 현재 조회수

Set
  key   = bamboo:{post-view}:dirty
  value = {"42", "77", "101"}
  의미  = RDS에 아직 반영할 게시글 ID의 중복 없는 모음
```

조회수 `101`은 Redis가 숫자 타입으로 따로 보관한다는 뜻이 아니라, Redis String으로 저장된 문자열을 `INCR` 명령이 정수처럼 증가시킨다는 뜻입니다. dirty set은 같은 게시글 ID를 여러 요청이 `SADD`해도 한 번만 보관하므로 반영 목록에 중복이 쌓이지 않습니다.

| 명령 | 자료형 | 이 프로젝트의 의미 |
|---|---|---|
| `GET key` | String | 게시글별 Redis 조회수 읽기 |
| `SET key value` | String | Redis 값이 없거나 DB 기준보다 작을 때 기준값 쓰기 |
| `INCR key` | String(정수 문자열) | 조회수 1 증가 후 증가한 값 반환 |
| `SADD key member` | Set | dirty set에 게시글 ID 추가 |
| `SMEMBERS key` | Set | flush 대상 ID 목록 읽기 |
| `SREM key member` | Set | RDS 반영이 끝난 ID를 dirty set에서 제거 |
| `TYPE key` | 모든 key | count key가 String인지, dirty key가 Set인지 확인 |
| `EXISTS key` | 모든 key | count key가 아직 존재하는지 확인 |

뒤의 `RedisViewCountStore`는 이 명령을 Java에서 따로 보내지 않고, 증가·dirty 등록처럼 함께 처리되어야 하는 작업을 Lua 스크립트로 묶어 Redis 서버에서 실행합니다.

### 16.0.3 Redis와 RDS의 책임을 나눈 이유

이 프로젝트의 조회수에는 “빠르게 늘어나는 값”과 “잃어버리면 안 되는 최종 값”이라는 두 요구가 동시에 있습니다.

```text
사용자 조회 요청이 매우 자주 들어옴
        │
        ├─ 매 요청을 RDS row UPDATE로 처리하면 DB 쓰기와 row 경합이 증가
        │
        └─ Redis에서 먼저 누적하고, 주기적으로 RDS에 반영
```

역할은 다음처럼 나뉩니다.

1. Redis의 count key는 여러 조회 요청의 최신 누적값을 빠르게 보관합니다.
2. Redis의 dirty set은 어떤 게시글을 RDS에 반영해야 하는지 표시합니다.
3. Scheduler가 dirty set을 읽어 Redis snapshot을 얻습니다.
4. `ViewCountPersistenceService`가 snapshot을 RDS의 `PostViewCount`에 반영합니다.
5. 저장 중 새로운 조회가 없었을 때만 dirty ID를 제거합니다.

현재 코드의 영구 기준은 `PostViewCount.viewCount`이고 Redis는 빠르게 누적하는 보조 저장소입니다. Redis count가 사라졌을 때도 `PostService`가 전달한 RDS 기준값을 먼저 사용해 조회수가 0이나 1로 갑자기 내려가지 않도록 합니다.

### 16.0.4 왜 이 프로젝트가 Redis를 사용했는가

현재 코드 기준 Redis의 목적은 게시글 조회수 증가입니다.

- 읽기 요청마다 증가할 수 있어 쓰기 빈도가 높습니다.
- 여러 사용자가 동시에 같은 게시글을 조회할 수 있습니다.
- 상세 화면에는 방금 증가한 값이 빠르게 보이는 편이 자연스럽습니다.
- 최종 조회수는 RDS에 남아야 합니다.

RDS만 사용하는 구현도 남아 있습니다. `app.view-count.enabled=false`이면 Spring이 `DatabaseViewCountUpdater`를 Bean으로 선택하고 매 요청에서 RDS를 직접 증가시킵니다. Redis를 켠 환경에서는 `RedisViewCountStore`가 선택됩니다.

다음 두 상황은 다릅니다.

```text
설정으로 Redis 기능을 끔
→ DatabaseViewCountUpdater가 선택됨
→ 조회마다 RDS를 직접 증가

RedisViewCountStore가 선택된 뒤 Redis 통신에 실패
→ 구현체가 DatabaseViewCountUpdater로 자동 교체되지 않음
→ 마지막 RDS baseline을 응답하고 요청을 계속 처리함
→ 장애 구간의 증가분은 현재 코드만으로 RDS에 보존되지 않음
```

따라서 현재 코드의 Redis 오류 처리를 “DB fallback으로 조회수까지 보존한다”고 이해하면 안 됩니다. 정확히는 Redis 장애 때 요청 자체를 실패시키지 않고 마지막 영구 조회수를 응답하는 것입니다.

### 16.0.5 Redis를 사용하면 값이 두 군데에 존재한다

Redis를 도입하면 같은 게시글의 조회수가 잠시 두 곳에 존재합니다.

```text
RDS PostViewCount.viewCount = 100   ← 마지막으로 영구 반영된 값
Redis count key             = 103   ← 아직 RDS에 flush되지 않은 최신 누적값
```

이 차이는 현재 설계가 허용하는 지연입니다. 상세 조회 흐름은 Redis의 증가 결과를 `PostViewResponseDto.viewCount`에 사용하므로 103을 보여줄 수 있습니다. 반면 게시글 목록의 `PostListResponseDto`는 `PostViewCount` Entity 값을 읽으므로 flush 전까지 100을 보여줄 수 있습니다.

Scheduler가 103을 RDS에 저장하고 dirty set에서 해당 ID를 제거하면 두 값이 다시 같아집니다. 저장 중 새로운 조회가 발생해 Redis가 104가 되면 snapshot 103을 저장한 뒤 dirty ID를 제거하지 않고 다음 주기에 104를 다시 반영합니다.

### 16.0.6 분산 락은 무엇이며 왜 필요한가

분산 락은 여러 서버 인스턴스가 공유 자원에 동시에 들어가지 않도록 Redis 같은 외부 저장소에 잠금 상태를 기록하는 장치입니다.

운영 환경의 blue와 green backend가 동시에 Scheduler를 실행한다고 가정합니다.

```text
blue backend  ─┐
               ├─ 같은 Redis dirty set을 읽고 같은 RDS에 flush하려고 함
green backend ─┘
```

두 Scheduler가 동시에 flush하면 같은 snapshot을 중복 저장하거나 저장과 dirty 제거 순서가 얽힐 수 있습니다. 그래서 `RedisViewCountFlushScheduler`는 `bamboo:{post-view}:flush-lock`이라는 공용 lock 이름을 사용합니다.

`synchronized`만으로는 해결되지 않습니다. `synchronized`는 하나의 JVM 안에서만 Java 객체 진입을 막습니다. blue와 green은 서로 다른 JVM이므로 각자의 `synchronized`는 서로 보지 못합니다. Redisson의 `RLock`은 두 JVM이 함께 접근하는 Redis에 잠금 상태를 기록하므로 인스턴스 간에도 같은 락을 공유할 수 있습니다.

분산 락이 필요한 범위는 Scheduler flush입니다. 모든 조회 요청에 분산 락을 거는 구현은 아닙니다.

```text
조회 요청 동시성      → Lua script의 원자성
Scheduler 인스턴스 동시성 → Redisson RLock
```

Lua는 `GET → 기준값 보정 → INCR → SADD`를 Redis에서 하나의 원자 작업으로 실행하고, RLock은 여러 backend 중 한 인스턴스만 flush하도록 합니다.

### 16.0.7 분산 락의 실행 흐름

```text
@Scheduled 주기 도달
→ flushLockKey로 RLock 객체를 얻음
→ tryLock()으로 락 획득 시도
→ 실패하면 이번 주기 종료
→ 성공하면 dirty postId 목록 조회
→ 각 게시글의 Redis snapshot 조회
→ RDS에 snapshot 이상으로 반영
→ 저장 후 Redis 값이 snapshot과 같은지 확인
→ 같으면 dirty 제거, 달라졌으면 dirty 유지
→ finally에서 현재 스레드가 소유한 경우에만 unlock
```

락을 얻지 못한 backend가 이번 주기 작업을 건너뛰어도 dirty set은 남아 있습니다. 다른 backend가 처리하거나 다음 주기에 다시 처리할 수 있기 때문입니다. 락을 얻은 뒤 RDS 저장에 실패하면 현재 코드는 dirty ID를 유지해 재시도할 수 있게 합니다.

### 16.0.8 Redis 데이터 보존과 AOF

운영 Compose의 Redis는 다음 설정을 사용합니다.

```yaml
command: redis-server --appendonly yes --appendfsync everysec
volumes:
  - redis-data:/data
```

- `--appendonly yes`: Redis 명령을 AOF(Append Only File)에 기록합니다.
- `--appendfsync everysec`: 최대 약 1초 주기로 AOF를 디스크에 동기화하도록 요청합니다.
- `redis-data:/data`: 컨테이너가 교체되어도 Docker volume에 Redis 데이터를 남깁니다.

AOF는 Redis가 재시작했을 때 count와 dirty set을 복구하는 장치입니다. AOF가 RDS를 대신하거나 RDS에 이미 반영되지 않은 값을 영구적으로 보장하는 것은 아닙니다. AOF와 volume이 함께 사라지면 Redis에만 있던 증가분은 잃을 수 있으므로 최종 영구 기준은 여전히 RDS입니다.

### 16.0.9 이 절을 읽은 뒤 코드에서 확인할 연결점

```text
application.yaml
→ app.view-count.enabled와 Redis 접속·key·flush 주기 설정

ViewCountProperties
→ 설정값 binding과 key/주기 검증

ViewCountUpdater
→ PostService가 Redis/DB 구현을 몰라도 같은 increment 계약을 사용

RedisViewCountStore
→ count String 증가와 dirty Set 등록

RedisViewCountFlushScheduler
→ Redisson 분산 락을 얻은 한 backend만 flush

ViewCountPersistenceService / PostViewCountRepository
→ snapshot을 RDS에 최대값으로 반영
```

이제부터는 위 용어를 다시 정의하는 대신 각 실제 코드 블록에서 어떤 Redis key를 어떤 명령으로 바꾸는지, 그 값이 어느 메서드에서 다음 단계로 전달되는지를 확인합니다.


## 16.1 15장에서 Redis로 넘어오는 연결

게시글 조회수를 Redis에서 원자적으로 증가시키고 여러 백엔드 인스턴스가 안전하게 RDS에 반영하는 전체 흐름을 학습한다.

```text
게시글 상세 조회
→ DB의 마지막 영구 조회수 확인
→ Redis에서 증가 + dirty 표시
→ 사용자에게 증가값 응답
→ Scheduler
→ 분산 락
→ Redis snapshot
→ RDS에 최대값 저장
→ 값이 그대로면 dirty 해제
```

### 16.1.1 이 장의 실제 코드 읽기 순서

```text
GET /posts/{postId}
→ PostService가 PostViewCount의 RDS 영구값을 baseline으로 선택
→ ViewCountUpdater interface
→ Redis 활성: RedisViewCountStore.increment()
→ Lua가 count key 초기화·증가와 dirty set 등록을 원자적으로 실행
→ 증가한 값을 상세 response에 즉시 사용

별도 Scheduler tick
→ RedisViewCountFlushScheduler
→ Redisson 분산 lock
→ dirty postId별 snapshot
→ ViewCountPersistenceService
→ PostViewCountRepository의 GREATEST update
→ 저장 중 값이 바뀌지 않았을 때만 dirty acknowledge
```

Redis를 끈 환경에서는 같은 interface에 `DatabaseViewCountUpdater`가 주입되어 RDS를 직접 증가시킨다. 따라서 Controller와 `PostService`는 저장소가 Redis인지 DB인지 알 필요가 없다. 요청 흐름과 Scheduler 흐름은 서로 다른 thread와 transaction에서 실행되므로 반드시 나누어 읽는다.

### 16.1.2 상세 화면이 API 호출을 시작하는 위치

확인 파일: `frontend/src/pages/posts/PostDetailPage.jsx`

연결되는 백엔드 파일: `backend/src/main/java/kr/adapterz/springdatajpa/controller/PostController.java`, `backend/src/main/java/kr/adapterz/springdatajpa/service/PostService.java`

```jsx
useEffect(() => {
    const controller = new AbortController();
    setPost(null);
    setComments([]);
    setLoadError(null);

    postApi.getPost(postId, { signal: controller.signal })
        .then((result) => {
            setPost(result);
            setComments(result.comments);
            setLoadError(null);
        })
        .catch((requestError) => {
            if (controller.signal.aborted || requestError?.name === "AbortError") {
                return;
            }
            setLoadError(requestError);
        });

    return () => {
        controller.abort();
    };
}, [postId, retryVersion]);
```

이 코드는 Redis를 직접 호출하지 않습니다. 화면은 상세 API만 호출하고, 백엔드가 조회수를 Redis에 저장하는지는 알지 못합니다. 응답의 `result.viewCount`가 Redis 증가 결과라면 이 값이 `post` state로 들어갑니다. `AbortController`와 `useEffect`의 일반 문법은 15장에서 이미 설명했으므로 여기서는 Redis 연결 지점만 확인합니다.

### 16.1.3 API module과 Controller의 연결

확인 파일: `frontend/src/api/postApi.js`

```javascript
async getPost(postId, { signal } = {}) {
    return normalizeDetailPost(await request(`/posts/${postId}`, { signal }));
}
```

`postId`는 URL parameter에서 온 값이고 `request`는 공통 HTTP 처리를 담당합니다. `normalizeDetailPost`는 서버 JSON을 화면용 객체로 바꾸며, Redis와 통신하는 코드는 없습니다.

백엔드 Controller의 실제 연결:

```java
@GetMapping("/{postId}")
public PostViewResponseDto getPostView(
        @PathVariable("postId") Long postId,
        @AuthenticationPrincipal CustomUserDetails userDetails
) {
    return postService.getPostView(postId, userDetails.getUserId());
}
```

`@PathVariable`은 URL의 ID를 `Long`으로 바꾸고, `@AuthenticationPrincipal`은 JWT Filter가 SecurityContext에 저장한 로그인 사용자를 받습니다. Controller는 구현체를 선택하지 않고 Service 반환값을 HTTP response body로 돌려줍니다.

### 16.1.4 Service에서 조회수 증가 결과가 DTO로 전달되는 위치

확인 파일: `backend/src/main/java/kr/adapterz/springdatajpa/service/PostService.java`

```java
long baselineViewCount = post.getPostViewCount().getViewCount();
long updatedViewCount = viewCountUpdater.increment(
        postId,
        baselineViewCount
);

return new PostViewResponseDto(
        post,
        post.getPostCounter(),
        updatedViewCount,
        commentResponseDtos,
        isLiked,
        isReported,
        isMine
);
```

`viewCountUpdater`의 선언 타입은 `ViewCountUpdater`이고 실제 Bean은 설정에 따라 달라집니다. `increment`의 반환값을 DTO 생성자에 전달하므로, Redis 활성화 시 상세 응답은 RDS에 아직 flush되지 않은 Redis 최신값을 사용할 수 있습니다.

현재 소스에서 baseline은 `PostViewCount.viewCount` 하나입니다. `PostCounter`에는 조회수 field가 없으므로 두 Entity의 조회수 중 큰 값을 선택하는 흐름은 현재 구현에 없습니다.

### 16.1.5 연결 흐름 요약

```text
PostDetailPage.jsx
→ postApi.getPost(postId)
→ request("/posts/{postId}")
→ GET /posts/{postId}
→ PostController.getPostView()
→ PostService.getPostView()
→ post.getPostViewCount().getViewCount()
→ ViewCountUpdater.increment()
→ PostViewResponseDto.viewCount
→ JSON response
→ PostDetailPage의 post state
```

## 16.2 조회수 저장 구조

확인 파일:

- `backend/src/main/java/kr/adapterz/springdatajpa/entity/Post.java`
- `backend/src/main/java/kr/adapterz/springdatajpa/entity/PostViewCount.java`
- `backend/src/main/java/kr/adapterz/springdatajpa/dto/post/PostViewResponseDto.java`
- `backend/src/main/java/kr/adapterz/springdatajpa/dto/post/PostListResponseDto.java`

### 16.2.1 Post와 PostViewCount 연결

```java
@OneToOne(
        mappedBy = "post",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        optional = false
)
private PostViewCount postViewCount;

public Post(User user, String postTitle, String postContent) {
    this.user = user;
    this.postTitle = postTitle;
    this.postContent = postContent;

    postCounter = new PostCounter(this);
    postViewCount = new PostViewCount(this);
    createdAt = LocalDateTime.now();
    deleted = false;
}
```

Post 생성자에서 `PostViewCount`를 함께 만들기 때문에 게시글과 조회수 row가 함께 생깁니다. `cascade`와 `orphanRemoval`은 이 연관 객체의 생명주기를 Post에 묶습니다.

### 16.2.2 PostViewCount의 영구값

```java
@Entity
@Table(name = "post_view_counts")
public class PostViewCount {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    public PostViewCount(Post post) {
        this.post = post;
        this.viewCount = 0L;
    }
}
```

`@MapsId`는 Post의 ID와 `post_view_counts.post_id`를 같은 shared primary key로 사용하게 합니다. 이 `viewCount`가 RDS에 마지막으로 저장된 영구 기준값이며 Redis의 임시 최신값과 구분해야 합니다.

### 16.2.3 상세 DTO와 목록 DTO의 차이

상세 응답은 Service가 계산한 증가 결과를 받습니다.

```java
public PostViewResponseDto(
        Post post,
        PostCounter counter,
        long viewCount,
        List<CommentResponseDto> comments,
        Boolean isLiked,
        Boolean isReported,
        Boolean isMine
) {
    this.postId = post.getPostId();
    this.version = post.getVersion();
    this.title = post.getPostTitle();
    this.content = post.getPostContent();
    this.imageUrls = getImageUrls(post);
    this.likeCount = counter.getLikeCount();
    this.reportCount = counter.getReportCount();
    this.commentCount = counter.getReplyCount();
    this.viewCount = viewCount;
    this.createdAt = post.getCreatedAt();
    this.isMine = isMine;
    this.isLiked = isLiked;
    this.isReported = isReported;
    this.comments = comments;
}
```

목록 응답은 Entity의 DB 값만 읽습니다.

```java
public PostListResponseDto(Post post, PostCounter counter) {
    this.postId = post.getPostId();
    this.title = post.getPostTitle();
    this.likeCount = counter.getLikeCount();
    this.reportCount = counter.getReportCount();
    this.commentCount = counter.getReplyCount();
    this.viewCount = post.getPostViewCount().getViewCount();
    this.createdAt = post.getCreatedAt();
}
```

따라서 Redis flush 전에는 상세와 목록의 조회수가 다를 수 있습니다.

```text
상세 조회 → Redis에서 1 증가한 최신값 응답
목록 조회 → RDS PostViewCount의 마지막 영구값만 응답
Scheduler flush 후 → RDS가 최신 snapshot으로 갱신
```

## 16.3 Redis 설정과 Bean 선택

확인 파일:

- `backend/src/main/resources/application.yaml`
- `backend/src/main/resources/application-local.yaml`
- `backend/src/main/resources/application-prod.yaml`
- `backend/src/test/resources/application-test.yaml`
- `backend/src/main/java/kr/adapterz/springdatajpa/config/ViewCountProperties.java`
- `backend/src/main/java/kr/adapterz/springdatajpa/SpringdatajpaApplication.java`

### 16.3.1 base application.yaml

```yaml
spring:
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
```

`${NAME:default}`는 NAME 환경변수가 있으면 그 값을 사용하고, 없으면 콜론 뒤 기본값을 사용합니다. 예를 들어 REDIS_HOST가 없을 때 host는 localhost입니다. connect-timeout의 2초도 입력값이 없을 때 사용하는 기본값입니다.

### 16.3.2 profile별 차이

local의 관련 설정:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
  jpa:
    hibernate:
      ddl-auto: create
  flyway:
    enabled: false
```

local은 H2와 Hibernate schema 생성으로 실행합니다. Redis enabled는 base YAML의 기본값 true를 사용할 수 있습니다.

prod의 관련 설정:

```yaml
spring:
  datasource:
    url: ${DB_URL}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    baseline-on-migrate: false
    locations: classpath:db/migration
    validate-on-migrate: true
```

prod는 MySQL/RDS에 연결하고 Flyway migration을 사용합니다. Redis host와 enabled 값은 Compose가 base YAML의 placeholder에 환경변수로 주입합니다.

test의 관련 설정:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.redisson.spring.starter.RedissonAutoConfigurationV4

app:
  view-count:
    enabled: false
```

test profile은 Redisson 자동 설정을 제외하고 DB 구현체를 선택합니다. 따라서 일반 테스트는 Redis 서버 없이 H2와 `DatabaseViewCountUpdater`로 실행됩니다.

### 16.3.3 애플리케이션 시작과 설정 Bean

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

`@ConfigurationPropertiesScan`이 `ViewCountProperties`를 Bean으로 등록하고, `@EnableScheduling`이 뒤의 `@Scheduled` flush 메서드를 활성화합니다. 설정이 병합된 뒤 `@ConditionalOnProperty` 조건에 따라 enabled 값에 맞는 구현체가 생성됩니다.

### 16.3.4 enabled에 따른 구현체 선택

```java
@ConditionalOnProperty(
        prefix = "app.view-count",
        name = "enabled",
        havingValue = "true"
)
public class RedisViewCountStore implements ViewCountUpdater {
}

@ConditionalOnProperty(
        prefix = "app.view-count",
        name = "enabled",
        havingValue = "false"
)
public class DatabaseViewCountUpdater implements ViewCountUpdater {
}
```

`enabled=false`이면 DatabaseViewCountUpdater만, `enabled=true`이면 RedisViewCountStore만 조건을 만족합니다. 이 설정은 애플리케이션 시작 시 Bean 선택을 결정하는 것이며 Redis 장애 때 두 Bean 사이를 동적으로 전환하는 기능은 아닙니다.

## 16.4 DB 조회수 구현과 Redis 구현의 공통 계약

확인 파일: `backend/src/main/java/kr/adapterz/springdatajpa/service/ViewCountUpdater.java`, `backend/src/main/java/kr/adapterz/springdatajpa/service/DatabaseViewCountUpdater.java`, `backend/src/main/java/kr/adapterz/springdatajpa/repository/PostViewCountRepository.java`

### 16.4.1 DB 구현이 호출하는 조회수 query

실제 코드:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(
        value = """
                UPDATE post_view_counts
                SET view_count = GREATEST(
                        view_count,
                        :baselineViewCount
                    ) + 1
                WHERE post_id = :postId
                """,
        nativeQuery = true
)
int incrementViewCount(
        @Param("postId") Long postId,
        @Param("baselineViewCount") long baselineViewCount
);
```

이 query는 `view_count`와 Service가 읽은 `baselineViewCount` 중 큰 값에 1을 더합니다. DB 구현이 Redis 구현과 같은 `increment(postId, baselineViewCount)` 계약을 구현하는 이유는 Controller·Service가 저장 기술에 따라 분기하지 않게 하기 위해서입니다. `@Modifying`은 SELECT가 아닌 UPDATE query임을 표시하고, 반환값은 실제 변경된 row 수입니다. row 수가 1이 아니면 해당 게시글의 조회수 row가 없거나 비정상 상태이므로 `CounterUpdateException`으로 처리합니다.

### 16.4.2 이 단계의 데이터 전달

```text
PostService.getPostView()
→ ViewCountUpdater.increment(postId, baselineViewCount)
→ enabled=false: DatabaseViewCountUpdater
→ PostViewCountRepository.incrementViewCount()
→ updated row count 확인
→ findById()로 증가된 값 반환

enabled=true:
→ RedisViewCountStore.increment()
→ Redis Lua 결과 반환
```

이 절의 인터페이스와 `DatabaseViewCountUpdater` 전체 구조는 10·11장에서 이미 설명한 내용이므로, 여기서는 Redis 구현이 같은 계약을 사용해야 하는 이유와 반환값이 상세 DTO로 전달되는 지점만 다시 확인합니다.


실제 코드:

```java
public interface ViewCountUpdater {

    long increment(Long postId, long baselineViewCount);
}
```

`PostService`는 Redis나 DB 구현을 직접 알지 않고 `ViewCountUpdater`만 의존한다.

Redis 활성화:

```java
@ConditionalOnProperty(
        prefix = "app.view-count",
        name = "enabled",
        havingValue = "true"
)
public class RedisViewCountStore
        implements ViewCountUpdater {
}
```

Redis 비활성화:

```java
@ConditionalOnProperty(
        prefix = "app.view-count",
        name = "enabled",
        havingValue = "false"
)
public class DatabaseViewCountUpdater
        implements ViewCountUpdater {
}
```

설정값에 따라 둘 중 하나만 Spring Bean이 되므로 `PostService` 코드를 바꾸지 않고 구현을 교체할 수 있다.

### 구현 교체 인터페이스와 조건

```java
public interface ViewCountUpdater { // PostService가 구현 기술과 분리되어 의존할 공통 계약이다.
    long increment(Long postId, long baselineViewCount); // 게시글 ID와 DB 기준값을 받아 증가 결과를 반환하도록 정한다.
}
```

```java
@ConditionalOnProperty( // 특정 설정값이 맞을 때만 이 구현을 Spring Bean으로 만든다.
        prefix = "app.view-count", // 검사할 설정의 공통 앞부분이다.
        name = "enabled", // app.view-count.enabled 값을 검사한다.
        havingValue = "true" // 값이 true일 때 Redis 구현을 활성화한다.
)
public class RedisViewCountStore implements ViewCountUpdater { // Redis 기반 증가 구현이 공통 인터페이스를 구현한다.
}
```

## 16.5 Redis count key와 dirty set

확인 파일: `backend/src/main/java/kr/adapterz/springdatajpa/config/ViewCountProperties.java`, `backend/src/main/java/kr/adapterz/springdatajpa/service/RedisViewCountStore.java`

공통 설정:

```yaml
count-key-prefix: "bamboo:{post-view}:count:"
dirty-set-key: "bamboo:{post-view}:dirty"
flush-lock-key: "bamboo:{post-view}:flush-lock"
```

예:

```text
bamboo:{post-view}:count:42
→ 42번 게시글의 현재 조회수 string

bamboo:{post-view}:dirty
→ RDS 반영이 필요한 게시글 ID set

bamboo:{post-view}:flush-lock
→ 여러 백엔드 중 하나만 flush하도록 하는 분산 락
```

`{post-view}`는 Redis Cluster에서 관련 키를 같은 hash slot에 배치하기 위한 hash tag다. 여러 키를 함께 사용하는 Lua 스크립트에서 중요하다.

설정을 실제 key 문자열로 바꾸고 잘못된 설정을 시작 시 거부하는 `ViewCountProperties` 원문:

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

record의 compact constructor인 `public ViewCountProperties { ... }`는 parameter 목록을 다시 적지 않고 생성 시 검증을 수행한다. 설정 binding 중 prefix·lock key가 비었거나 flush interval이 0 이하이면 application 시작이 실패한다. `countKey(42L)`는 `countKeyPrefix + 42`를 반환하며 post ID도 양수인지 검사한다.


### DB 대체 구현 조건과 Redis key 설정 라인별 주석본

```java
@ConditionalOnProperty( // Redis 비활성 설정일 때만 아래 구현을 Spring Bean으로 만든다.
        prefix = "app.view-count", // 검사 대상 설정의 앞부분은 app.view-count다.
        name = "enabled", // enabled 값을 검사한다.
        havingValue = "false" // 값이 false일 때 DatabaseViewCountUpdater를 선택한다.
)
public class DatabaseViewCountUpdater implements ViewCountUpdater { // Redis 없이 RDS update로 조회수를 증가시키는 구현이다.
}
```

```yaml
count-key-prefix: "bamboo:{post-view}:count:" # 뒤에 postId를 붙여 게시글별 string 조회수 key를 만든다.
dirty-set-key: "bamboo:{post-view}:dirty" # 아직 RDS에 반영할 변경이 있는 postId들을 set으로 저장한다.
flush-lock-key: "bamboo:{post-view}:flush-lock" # 여러 backend Scheduler가 공유하는 Redisson 분산 락 이름이다.
```

### `ViewCountProperties`

```java
@ConfigurationProperties(prefix = "app.view-count") // YAML의 app.view-count 하위 값을 record component에 binding한다.
public record ViewCountProperties( // 조회수 관련 설정을 immutable data 묶음으로 선언한다.
        boolean enabled, // Redis 조회수 기능 활성 여부다.
        String countKeyPrefix, // 게시글 ID 앞에 붙일 count key prefix다.
        String dirtySetKey, // dirty ID set의 완성 key다.
        String flushLockKey, // Redisson 분산 lock key다.
        Duration flushInterval // Scheduler 주기다.
) {

    public ViewCountProperties { // record component 값을 암묵적으로 받는 compact constructor다.
        requireText(countKeyPrefix, "countKeyPrefix"); // count prefix가 비지 않았는지 검사한다.
        requireText(dirtySetKey, "dirtySetKey"); // dirty key가 비지 않았는지 검사한다.
        requireText(flushLockKey, "flushLockKey"); // lock key가 비지 않았는지 검사한다.

        if (flushInterval == null || flushInterval.isZero() || flushInterval.isNegative()) { // interval이 없거나 0 이하인지 검사한다.
            throw new IllegalArgumentException("flushInterval must be positive"); // 잘못된 설정이면 application 시작을 실패시킨다.
        }
    }

    public String countKey(Long postId) { // 특정 게시글의 완성된 Redis count key를 만든다.
        if (postId == null || postId <= 0) { // ID가 없거나 양수가 아닌지 검사한다.
            throw new IllegalArgumentException("postId must be positive"); // 잘못된 key 생성을 거부한다.
        }
        return countKeyPrefix + postId; // prefix 뒤에 숫자 ID를 붙여 반환한다.
    }

    private static void requireText(String value, String propertyName) { // 문자열 설정 공통 검증 method다.
        if (value == null || value.isBlank()) { // null이거나 공백뿐인지 검사한다.
            throw new IllegalArgumentException(propertyName + " must not be blank"); // 어떤 property가 잘못됐는지 포함해 실패시킨다.
        }
    }
}
```

## 16.6 Redis 조회수 증가와 Lua script

확인 파일: `backend/src/main/java/kr/adapterz/springdatajpa/service/RedisViewCountStore.java`

```lua
local count_type = redis.call('TYPE', KEYS[1]).ok
local dirty_type = redis.call('TYPE', KEYS[2]).ok

if count_type ~= 'none' and count_type ~= 'string' then
    return redis.error_reply('view count key must be a string')
end

if dirty_type ~= 'none' and dirty_type ~= 'set' then
    return redis.error_reply('dirty key must be a set')
end

local current = redis.call('GET', KEYS[1])
local baseline = tonumber(ARGV[1])

if not current or tonumber(current) < baseline then
    redis.call('SET', KEYS[1], ARGV[1])
end

local updated = redis.call('INCR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[2])

return updated
```

핵심 순서:

```text
키 타입 검증
→ Redis 현재값 조회
→ Redis가 없거나 DB 기준값보다 작으면 기준값으로 복구
→ INCR
→ 게시글 ID를 dirty set에 SADD
→ 증가된 값 반환
```

Lua 스크립트 전체는 Redis 서버 안에서 하나의 원자적 작업으로 실행된다. 다른 요청이 중간 단계에 끼어들지 못하므로 동시에 조회수가 증가해도 값을 잃지 않는다.

여기서 원자적이라는 말은 “다른 Redis command가 script 중간에 끼어들지 않는다”는 뜻이다. Redis Lua는 DB Transaction처럼 script 실행 중 이미 수행된 write를 오류 발생 시 자동 rollback하지 않는다. 현재 증가 script는 write 전에 두 key type을 먼저 검사하여 예상 가능한 type 오류가 중간 write 뒤 발생할 가능성을 줄인다.

### 증가 Lua

```lua
local count_type = redis.call('TYPE', KEYS[1]).ok -- 첫 key가 조회수에 맞는 string 타입인지 확인한다.
local dirty_type = redis.call('TYPE', KEYS[2]).ok -- 두 번째 key가 dirty ID에 맞는 set 타입인지 확인한다.

if count_type ~= 'none' and count_type ~= 'string' then -- key가 없거나 string인 정상 상태가 아닌지 검사한다.
    return redis.error_reply('view count key must be a string') -- 잘못된 타입이면 숫자 증가 전에 오류로 중단한다.
end

if dirty_type ~= 'none' and dirty_type ~= 'set' then -- dirty key가 없거나 set인 정상 상태가 아닌지 검사한다.
    return redis.error_reply('dirty key must be a set') -- 다른 타입이면 SADD 전에 오류로 중단한다.
end

local current = redis.call('GET', KEYS[1]) -- Redis에 저장된 현재 게시글 조회수를 읽는다.
local baseline = tonumber(ARGV[1]) -- Java가 전달한 DB 기준값 문자열을 숫자로 바꾼다.

if not current or tonumber(current) < baseline then -- Redis 값이 없거나 DB보다 뒤처졌는지 확인한다.
    redis.call('SET', KEYS[1], ARGV[1]) -- 증가 전에 Redis를 DB 기준값까지 복구한다.
end

local updated = redis.call('INCR', KEYS[1]) -- 현재 값을 원자적으로 1 증가시키고 결과를 받는다.
redis.call('SADD', KEYS[2], ARGV[2]) -- RDS 반영 대상 set에 게시글 ID를 추가한다.

return updated -- Java와 사용자 응답에 사용할 증가된 조회수를 반환한다.
```

### 16.6.1 Java에서 Lua 실행

```java
@Override
public long increment(Long postId, long baselineViewCount) {
    if (baselineViewCount < 0) {
        throw new IllegalArgumentException(
                "baselineViewCount must not be negative"
        );
    }

    try {
        Long updatedViewCount = redisTemplate.execute(
                INCREMENT_AND_MARK_DIRTY_SCRIPT,
                List.of(
                        properties.countKey(postId),
                        properties.dirtySetKey()
                ),
                Long.toString(baselineViewCount),
                Long.toString(postId)
        );

        if (updatedViewCount == null) {
            log.warn(
                    "Redis returned no view count. "
                            + "Serving the last persisted count. postId={}",
                    postId
            );
            return baselineViewCount;
        }

        return updatedViewCount;
    } catch (DataAccessException exception) {
        log.warn(
                "Redis view count update failed. "
                        + "Serving the last persisted count. postId={}, cause={}",
                postId,
                exception.getClass().getSimpleName()
        );
        return baselineViewCount;
    }
}
```

```text
KEYS[1] → 게시글 조회수 key
KEYS[2] → dirty set key
ARGV[1] → DB 기준 조회수
ARGV[2] → 게시글 ID
```

Redis가 값을 반환하지 않거나 Redis 접근 계층의 `DataAccessException`이 발생하면 DB 기준값을 응답한다. 조회 요청 전체를 실패시키지 않는 가용성 우선 fallback이다. 그러나 음수 baseline은 `try` 전에 `IllegalArgumentException`을 던지고, Redis와 무관한 모든 RuntimeException까지 fallback하는 것은 아니다.

fallback으로 DB 기준값만 응답하는 요청은 조회수를 RDS에서 직접 증가시키지 않는다. 따라서 Redis 장애 동안 들어온 조회 횟수는 영구 저장되지 않고 사용자에게도 마지막 영구값이 반복되어 보일 수 있다. “가용성 우선”은 요청 성공을 우선한다는 뜻이지 증가분까지 보존한다는 뜻은 아니다.

### Java의 Lua 실행

```java
@Override // ViewCountUpdater의 증가 계약을 구현한다.
public long increment(Long postId, long baselineViewCount) { // 게시글 ID와 DB 기준값으로 Redis 조회수를 증가시킨다.
    if (baselineViewCount < 0) { // 조회수 기준값이 음수인지 검사한다.
        throw new IllegalArgumentException( // caller 계약 위반이므로 Redis fallback 전에 예외를 던진다.
                "baselineViewCount must not be negative"
        );
    }

    try { // Redis 접근 오류에 한해 DB 기준값을 반환할 범위다.
        Long updatedViewCount = redisTemplate.execute( // Redis server에 미리 만든 Lua script 실행을 요청한다.
                INCREMENT_AND_MARK_DIRTY_SCRIPT, // 증가와 dirty 표시가 함께 있는 script 객체다.
                List.of( // Lua의 KEYS 배열로 전달할 Redis key 목록이다.
                        properties.countKey(postId), // KEYS[1]인 게시글별 조회수 key다.
                        properties.dirtySetKey() // KEYS[2]인 공통 dirty set key다.
                ),
                Long.toString(baselineViewCount), // ARGV[1]로 전달할 DB 기준 조회수다.
                Long.toString(postId) // ARGV[2]로 dirty set에 넣을 게시글 ID다.
        );

        if (updatedViewCount == null) { // Redis template이 결과를 돌려주지 않았는지 확인한다.
            log.warn( // server log에 fallback 사실과 게시글 ID를 남긴다.
                    "Redis returned no view count. "
                            + "Serving the last persisted count. postId={}",
                    postId
            );
            return baselineViewCount; // 증가시키지 못한 마지막 DB 영구값을 응답한다.
        }

        return updatedViewCount; // Lua가 반환한 증가 후 값을 응답한다.
    } catch (DataAccessException exception) { // Spring Redis 접근 계층 예외를 잡는다.
        log.warn( // 요청을 실패시키지 않고 장애 class를 log로 남긴다.
                "Redis view count update failed. "
                        + "Serving the last persisted count. postId={}, cause={}",
                postId,
                exception.getClass().getSimpleName()
        );
        return baselineViewCount; // Redis 장애 시 증가분을 기록하지 못하고 마지막 DB 값을 반환한다.
    }
}
```

## 16.7 Redis 오류와 DB 비활성화의 차이

Redis가 재시작되거나 키가 사라질 수 있다.

```text
RDS 조회수 100
Redis 키 없음

기준값 없이 INCR
→ 1부터 시작하여 조회수 감소

DB 기준값 100으로 SET 후 INCR
→ 101
```

`PostService`는 현재 `PostViewCount.viewCount` 하나를 baseline으로 전달한다. 현재 `PostCounter`에는 조회수 field가 없으므로 두 Entity 중 큰 값을 선택하는 로직은 존재하지 않는다.

설정으로 Redis 기능 자체를 끈 경우에는 “Redis 오류 fallback”과 달리 `DatabaseViewCountUpdater`가 RDS를 직접 증가시킨다. 실제 원문:

```java
@Override
@Transactional
public long increment(Long postId, long baselineViewCount) {
    int updatedRowCount =
            postViewCountRepository.incrementViewCount(
                    postId,
                    baselineViewCount
            );

    if (updatedRowCount != 1) {
        throw new CounterUpdateException();
    }

    return postViewCountRepository.findById(postId)
            .map(PostViewCount::getViewCount)
            .orElseThrow(CounterUpdateException::new);
}
```

```text
VIEW_COUNT_REDIS_ENABLED=false
→ DatabaseViewCountUpdater Bean 선택
→ 매 조회마다 RDS max 기준 + 1

RedisViewCountStore가 선택된 상태에서 Redis 통신만 실패
→ 구현체를 즉시 DB 구현으로 교체하는 것이 아님
→ 마지막 DB baseline을 응답하고 증가분은 기록하지 못함
```

### Redis 비활성 시 DB 구현

```java
@Override // 같은 ViewCountUpdater 계약을 DB 방식으로 구현한다.
@Transactional // DB update와 이어지는 조회를 한 Transaction으로 묶는다.
public long increment(Long postId, long baselineViewCount) { // RDS에서 기준값 보정과 증가를 수행한다.
    int updatedRowCount = // native update가 바꾼 row 수를 받는다.
            postViewCountRepository.incrementViewCount( // GREATEST(DB 값, baseline) + 1 query를 실행한다.
                    postId, // 수정할 게시글 ID다.
                    baselineViewCount // migration 중 더 큰 값을 보존할 기준이다.
            );

    if (updatedRowCount != 1) { // 정확히 한 row가 바뀌었는지 확인한다.
        throw new CounterUpdateException(); // row가 없거나 비정상 결과면 Transaction을 실패시킨다.
    }

    return postViewCountRepository.findById(postId) // 증가된 row를 다시 조회한다.
            .map(PostViewCount::getViewCount) // Entity가 있으면 long 조회수로 변환한다.
            .orElseThrow(CounterUpdateException::new); // 사라졌다면 counter 갱신 실패 예외를 만든다.
}
```

## 16.8 Scheduler와 Redis 분산 락

확인 파일: `backend/src/main/java/kr/adapterz/springdatajpa/service/RedisViewCountFlushScheduler.java`, `backend/src/main/java/kr/adapterz/springdatajpa/config/ViewCountProperties.java`

```java
@Scheduled(
        initialDelayString = "${app.view-count.flush-interval}",
        fixedDelayString = "${app.view-count.flush-interval}"
)
public void flushDirtyViewCounts() {
    RLock lock = redissonClient.getLock(properties.flushLockKey());
    boolean acquired = false;

    try {
        acquired = lock.tryLock();

        if (!acquired) {
            return;
        }

        flushWhileHoldingLock();
    } catch (RedisException exception) {
        log.warn(
                "Redis view count flush lock failed. cause={}",
                exception.getClass().getSimpleName()
        );
    } finally {
        releaseIfOwned(lock, acquired);
    }
}
```

여러 backend 인스턴스에서 Scheduler가 동시에 실행되므로 Redisson 분산 락을 사용한다.

```text
blue backend lock 성공 → flush 실행
green backend lock 실패 → 이번 주기 건너뜀
```

락을 얻은 뒤 dirty ID를 처리하고 락을 해제하는 실제 원문:

```java
private void flushWhileHoldingLock() {
    try {
        for (Long postId : redisViewCountStore.findDirtyPostIds()) {
            flushOne(postId);
        }
    } catch (DataAccessException exception) {
        log.warn(
                "Redis view count flush was skipped. cause={}",
                exception.getClass().getSimpleName()
        );
    }
}

private void flushOne(Long postId) {
    try {
        OptionalLong snapshot =
                redisViewCountStore.findViewCountSnapshot(postId);

        if (snapshot.isEmpty()) {
            redisViewCountStore.removeDirtyIfCountMissing(postId);
            return;
        }

        long snapshotViewCount = snapshot.getAsLong();

        persistenceService.persist(postId, snapshotViewCount);

        redisViewCountStore.acknowledgeIfUnchanged(
                postId,
                snapshotViewCount
        );
    } catch (RuntimeException exception) {
        log.warn(
                "Failed to persist Redis view count. "
                        + "It will be retried. postId={}, cause={}",
                postId,
                exception.getClass().getSimpleName()
        );
    }
}

private void releaseIfOwned(RLock lock, boolean acquired) {
    if (!acquired) {
        return;
    }

    try {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    } catch (RedisException exception) {
        log.warn(
                "Redis view count flush lock release failed. cause={}",
                exception.getClass().getSimpleName()
        );
    }
}
```

각 게시글의 `flushOne()`은 RuntimeException을 개별적으로 잡으므로 한 게시글 저장 실패가 뒤의 다른 게시글 flush까지 중단시키지 않는다. 반면 dirty set 전체 조회에서 `DataAccessException`이 나면 이번 주기 전체를 건너뛴다. snapshot key가 없으면 `removeDirtyIfCountMissing()`가 count key의 부재를 Redis 안에서 다시 확인한 뒤 고아 dirty member를 제거한다.

### Flush Scheduler

```java
@Scheduled( // Spring Scheduler가 이 메서드를 주기적으로 호출하게 한다.
        initialDelayString = "${app.view-count.flush-interval}", // 시작 후 첫 실행까지 설정 시간만큼 기다린다.
        fixedDelayString = "${app.view-count.flush-interval}" // 이전 실행 종료 후 다음 실행까지 같은 시간만큼 기다린다.
)
public void flushDirtyViewCounts() { // dirty 조회수를 RDS에 반영하는 진입 메서드다.
    RLock lock = redissonClient.getLock(properties.flushLockKey()); // 모든 backend가 공유하는 Redis 분산 락 객체를 얻는다.
    boolean acquired = false; // 현재 실행이 락을 얻었는지 추적한다.

    try { // 락 획득·flush 중 Redis 예외를 처리한다.
        acquired = lock.tryLock(); // 기다리지 않고 현재 락을 얻을 수 있는지 시도한다.
        if (!acquired) { // 다른 backend가 이미 flush 중인지 확인한다.
            return; // 중복 저장하지 않고 이번 주기를 건너뛴다.
        }
        flushWhileHoldingLock(); // 락 소유자만 dirty 목록을 순회해 저장한다.
    } catch (RedisException exception) { // 락 자체의 Redis 통신 실패를 받는다.
        log.warn("Redis view count flush lock failed."); // 전체 Scheduler를 죽이지 않고 장애를 기록한다.
    } finally { // 성공·실패와 관계없이 락 정리를 시도한다.
        releaseIfOwned(lock, acquired); // 실제 획득했고 현재 Thread 소유일 때만 안전하게 해제한다.
    }
}
```

```java
private void flushWhileHoldingLock() { // 분산 락 소유 중 dirty set 전체를 처리한다.
    try { // dirty set 조회 자체의 Redis 접근 실패를 처리한다.
        for (Long postId : redisViewCountStore.findDirtyPostIds()) { // dirty 게시글 ID를 하나씩 순회한다.
            flushOne(postId); // 게시글 하나의 snapshot 저장을 시도한다.
        }
    } catch (DataAccessException exception) { // dirty set을 읽지 못한 Redis 접근 오류를 잡는다.
        log.warn( // 이번 전체 주기를 건너뛴 이유를 기록한다.
                "Redis view count flush was skipped. cause={}",
                exception.getClass().getSimpleName()
        );
    }
}

private void flushOne(Long postId) { // dirty 게시글 하나를 RDS에 반영한다.
    try { // 한 게시글 실패가 다음 게시글 순회를 막지 않도록 개별 처리한다.
        OptionalLong snapshot = // Redis count key가 없을 가능성을 담는다.
                redisViewCountStore.findViewCountSnapshot(postId); // 현재 조회수를 snapshot으로 읽는다.

        if (snapshot.isEmpty()) { // dirty ID는 있지만 count key가 없는지 확인한다.
            redisViewCountStore.removeDirtyIfCountMissing(postId); // count key가 지금도 없을 때만 Lua로 고아 dirty를 제거한다.
            return; // RDS에 저장할 snapshot이 없으므로 이 ID 처리를 끝낸다.
        }

        long snapshotViewCount = snapshot.getAsLong(); // 존재하는 primitive long 값을 꺼낸다.

        persistenceService.persist(postId, snapshotViewCount); // 별도 Service proxy의 DB Transaction으로 max 값을 저장한다.

        redisViewCountStore.acknowledgeIfUnchanged( // DB commit 후 현재 Redis 값이 snapshot과 같을 때만 dirty를 지운다.
                postId, // 확인할 게시글 ID다.
                snapshotViewCount // 방금 영구 저장한 비교값이다.
        );
    } catch (RuntimeException exception) { // Redis parsing·DB 저장·acknowledge 등 한 ID의 RuntimeException을 잡는다.
        log.warn( // dirty를 남긴 채 다음 주기 재시도 사실을 기록한다.
                "Failed to persist Redis view count. "
                        + "It will be retried. postId={}, cause={}",
                postId,
                exception.getClass().getSimpleName()
        );
    }
}

private void releaseIfOwned(RLock lock, boolean acquired) { // 현재 실행이 소유한 lock만 해제한다.
    if (!acquired) { // 처음부터 lock을 얻지 못했는지 확인한다.
        return; // 다른 실행의 lock을 건드리지 않는다.
    }

    try { // lock 상태 확인과 unlock의 Redis 오류를 처리한다.
        if (lock.isHeldByCurrentThread()) { // 현재 Thread가 아직 이 lock 소유자인지 확인한다.
            lock.unlock(); // 소유권이 있을 때만 분산 lock을 해제한다.
        }
    } catch (RedisException exception) { // lock 해제 통신 실패를 잡는다.
        log.warn( // Scheduler를 죽이지 않고 해제 실패를 기록한다.
                "Redis view count flush lock release failed. cause={}",
                exception.getClass().getSimpleName()
        );
    }
}
```

## 16.9 Snapshot과 dirty 제거 조건

```java
long snapshotViewCount = snapshot.getAsLong();

persistenceService.persist(
        postId,
        snapshotViewCount
);

redisViewCountStore.acknowledgeIfUnchanged(
        postId,
        snapshotViewCount
);
```

RDS 저장 후 무조건 dirty를 제거하면 안 된다.

```text
snapshot 100 읽음
→ 저장 중 새 조회 발생
→ Redis 101
→ RDS에 100 저장
→ dirty 무조건 제거
→ 101을 다시 저장할 기회 유실
```

그래서 다음 Lua가 현재 Redis 값이 저장한 snapshot과 같을 때만 dirty set에서 제거한다.

```lua
local current = redis.call('GET', KEYS[1])

if current and current == ARGV[1] then
    return redis.call(
        'SREM',
        KEYS[2],
        ARGV[2]
    )
end

return 0
```

값이 증가했다면 dirty를 유지하여 다음 Scheduler 주기에 다시 저장한다.

count key가 없는데 dirty ID만 남은 경우에는 저장할 snapshot 자체가 없다. dirty를 계속 보관해도 사라진 값을 복구할 수 없으므로 다음 Lua로 고아 dirty를 정리한다.

```lua
if redis.call('EXISTS', KEYS[1]) == 0 then
    return redis.call('SREM', KEYS[2], ARGV[1])
end

return 0
```

Java에서 `GET → SREM`을 별도 명령으로 실행하면 두 명령 사이에 새 조회가 count key와 dirty를 다시 만들 수 있다. 그러고도 이전 Scheduler가 `SREM`하면 새 증가분을 저장할 기회를 잃는다. Lua는 count key 부재 확인과 dirty 제거 사이에 다른 Redis 명령이 끼어들지 못하게 한다.

```text
Lua 실행 전에 새 조회가 들어옴
→ count key가 존재하므로 dirty 유지

Lua가 먼저 고아 dirty를 제거함
→ 이후 새 조회가 count key를 만들고 dirty를 다시 추가
```

### Snapshot 저장과 조건부 acknowledge

```java
long snapshotViewCount = snapshot.getAsLong(); // 현재 Redis 조회수를 변경 여부 비교 기준으로 고정한다.
persistenceService.persist(postId, snapshotViewCount); // snapshot을 별도 Transaction으로 RDS에 저장한다.
redisViewCountStore.acknowledgeIfUnchanged( // 저장 뒤 Redis 값이 그대로일 때 dirty 제거를 시도한다.
        postId, // 확인할 게시글 ID다.
        snapshotViewCount // 방금 DB에 저장한 조회수다.
);
```

```lua
local current = redis.call('GET', KEYS[1]) -- DB 저장이 끝난 현재 시점의 Redis 조회수를 다시 읽는다.

if current and current == ARGV[1] then -- key가 존재하며 저장한 snapshot과 정확히 같은지 확인한다.
    return redis.call('SREM', KEYS[2], ARGV[2]) -- 추가 조회가 없을 때만 dirty set에서 게시글 ID를 제거한다.
end

return 0 -- 값이 달라졌으면 새 증가분을 다음 주기에 저장하도록 dirty를 유지한다.
```

## 16.10 Redis 값을 DB에 반영

확인 파일: `backend/src/main/java/kr/adapterz/springdatajpa/service/ViewCountPersistenceService.java`, `backend/src/main/java/kr/adapterz/springdatajpa/repository/PostViewCountRepository.java`

```java
@Transactional
public void persist(
        Long postId,
        long snapshotViewCount
) {
    int updatedRowCount =
            postViewCountRepository.persistMaxViewCount(
                    postId,
                    snapshotViewCount
            );

    if (updatedRowCount != 1) {
        throw new CounterUpdateException();
    }
}
```

DB 쿼리는 기존 조회수와 snapshot 중 더 큰 값을 저장한다. 늦게 도착한 작은 snapshot이 DB 값을 감소시키지 못하게 한다.

`persistMaxViewCount()`의 실제 native SQL:

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(
        value = """
                UPDATE post_view_counts
                SET view_count = GREATEST(
                        view_count,
                        :snapshotViewCount
                    )
                WHERE post_id = :postId
                """,
        nativeQuery = true
)
int persistMaxViewCount(
        @Param("postId") Long postId,
        @Param("snapshotViewCount") long snapshotViewCount
);
```

이것은 Entity 이름을 사용하는 JPQL이 아니라 실제 table·column 이름을 사용하는 native SQL이다. MySQL의 `GREATEST(a, b)`가 두 값 중 큰 값을 반환한다. WHERE에 맞는 row가 없으면 반환 row 수가 0이므로 Service가 `CounterUpdateException`을 던진다.

Transaction이 commit된 후에만 dirty 해제가 실행되어야 한다. 테스트에서는 저장 호출과 acknowledge 호출의 순서를 검증한다.

### count key가 사라진 경우의 고아 dirty 정리

```java
public boolean removeDirtyIfCountMissing(Long postId) { // 저장할 count가 없는 dirty ID를 경쟁 조건 없이 정리한다.
    Long removed = redisTemplate.execute( // Redis 서버에서 확인과 제거를 하나의 Lua 작업으로 실행한다.
            REMOVE_DIRTY_IF_COUNT_MISSING_SCRIPT, // count 부재일 때만 SREM하는 script다.
            List.of( // Lua의 KEYS 배열에 전달할 두 key다.
                    properties.countKey(postId), // KEYS[1]은 게시글의 조회수 count key다.
                    properties.dirtySetKey() // KEYS[2]는 RDS 반영 대기 ID set이다.
            ),
            Long.toString(postId) // ARGV[1]은 dirty set에서 제거할 게시글 ID다.
    );

    return Long.valueOf(1L).equals(removed); // 실제 한 member가 제거됐을 때만 true를 반환한다.
}
```

```lua
if redis.call('EXISTS', KEYS[1]) == 0 then -- 실행 시점에도 count key가 없는지 Redis 안에서 확인한다.
    return redis.call('SREM', KEYS[2], ARGV[1]) -- 없을 때만 복구 불가능한 고아 dirty ID를 제거한다.
end

return 0 -- 새 조회가 count key를 만들었다면 새 증가분 보존을 위해 dirty를 유지한다.
```

## 16.11 Redis Docker Compose와 AOF

확인 파일:

- `backend/deploy/compose.yaml`
- `backend/deploy/.env.example`
- `tools/loadtest/compose.yaml`

`backend/deploy/.env.example`에서 Compose가 backend container에 전달할 Redis 관련 값을 확인합니다.

```dotenv
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_CONNECT_TIMEOUT=2s
REDIS_COMMAND_TIMEOUT=1s
VIEW_COUNT_REDIS_ENABLED=true
VIEW_COUNT_FLUSH_INTERVAL=5s
```

Compose 실제 설정:

```yaml
redis:
  image: redis:7.4-alpine
  restart: unless-stopped
  command:
    - redis-server
    - --appendonly
    - "yes"
    - --appendfsync
    - everysec
  volumes:
    - redis-data:/data
  healthcheck:
    test:
      - CMD
      - redis-cli
      - ping
    interval: 5s
    timeout: 3s
    retries: 10
    start_period: 5s
  networks:
    - backend-network
```

blue/green backend의 Redis 연결 환경변수 발췌:

```yaml
backend-blue:
  environment:
    REDIS_HOST: ${REDIS_HOST:-redis}
    REDIS_PORT: ${REDIS_PORT:-6379}
    REDIS_CONNECT_TIMEOUT: ${REDIS_CONNECT_TIMEOUT:-2s}
    REDIS_COMMAND_TIMEOUT: ${REDIS_COMMAND_TIMEOUT:-1s}
    VIEW_COUNT_REDIS_ENABLED: ${VIEW_COUNT_REDIS_ENABLED:-true}
    VIEW_COUNT_FLUSH_INTERVAL: ${VIEW_COUNT_FLUSH_INTERVAL:-5s}

backend-green:
  environment:
    REDIS_HOST: ${REDIS_HOST:-redis}
    REDIS_PORT: ${REDIS_PORT:-6379}
    REDIS_CONNECT_TIMEOUT: ${REDIS_CONNECT_TIMEOUT:-2s}
    REDIS_COMMAND_TIMEOUT: ${REDIS_COMMAND_TIMEOUT:-1s}
    VIEW_COUNT_REDIS_ENABLED: ${VIEW_COUNT_REDIS_ENABLED:-true}
    VIEW_COUNT_FLUSH_INTERVAL: ${VIEW_COUNT_FLUSH_INTERVAL:-5s}
```

두 backend는 서로 다른 컨테이너이지만 같은 Compose network에서 service 이름 `redis`로 동일한 Redis 컨테이너에 접근합니다. 따라서 두 Scheduler가 같은 dirty set과 flush lock을 공유하고, `RLock`이 인스턴스 사이에서 동작할 수 있습니다. `tools/loadtest/compose.yaml`의 Redis는 `profiles: [redis]`로 선택적으로 실행되며, 실제 load test 환경에서도 같은 AOF 설정과 `redis-data` volume을 사용합니다.

```text
AOF
→ Redis 변경 명령을 파일에 기록

appendfsync everysec
→ 대략 매초 디스크에 동기화

Docker volume
→ 컨테이너를 다시 만들어도 /data 보존

healthcheck
→ 컨테이너 안에서 redis-cli ping을 주기적으로 실행해 Redis 응답 상태 판정
```

Redis가 작업 저장소라고 해서 항상 재시작 시 모든 값이 사라지도록 구성된 것은 아니다.

`restart: unless-stopped`는 process가 비정상 종료되거나 Docker daemon이 재시작될 때 container 재시작을 시도하되 사용자가 명시적으로 중지한 상태는 유지하는 정책이다. AOF `everysec`는 보통 성능과 내구성의 절충이며, 장애 시 최근 약 1초 범위의 write가 유실될 가능성까지 없애는 설정은 아니다.

### RDS 저장

```java
@Transactional // max update와 행 수 검증을 하나의 DB Transaction으로 처리한다.
public void persist(Long postId, long snapshotViewCount) { // 특정 게시글 snapshot을 영구 반영한다.
    int updatedRowCount = postViewCountRepository.persistMaxViewCount( // 기존 값과 snapshot 중 큰 값을 저장하는 update를 실행한다.
            postId, // 수정할 post_view_counts의 기본키다.
            snapshotViewCount // Redis에서 읽은 저장 후보 값이다.
    );

    if (updatedRowCount != 1) { // 정확히 한 게시글 행이 수정되었는지 확인한다.
        throw new CounterUpdateException(); // 없거나 비정상 수정이면 Transaction을 실패시킨다.
    }
}
```

```java
@Modifying(clearAutomatically = true, flushAutomatically = true) // native update 전 pending 변경을 flush하고 뒤에 persistence context를 비운다.
@Query( // 실제 table과 column 이름을 사용할 query를 선언한다.
        value = """
                UPDATE post_view_counts
                SET view_count = GREATEST(
                        view_count,
                        :snapshotViewCount
                    )
                WHERE post_id = :postId
                """, // 현재 DB 값과 snapshot 중 큰 값을 저장하는 MySQL SQL이다.
        nativeQuery = true // JPQL이 아니라 native SQL임을 지정한다.
)
int persistMaxViewCount( // 변경된 row 수를 반환한다.
        @Param("postId") Long postId, // :postId parameter에 게시글 ID를 연결한다.
        @Param("snapshotViewCount") long snapshotViewCount // :snapshotViewCount에 Redis snapshot을 연결한다.
);
```

### 16.11.1 장애별 동작

| 장애 | 현재 동작 |
|---|---|
| Redis 증가 `DataAccessException` | DB의 마지막 영구 조회수를 응답하지만 해당 증가분은 기록하지 못함 |
| Redis가 null 반환 | DB 기준값을 응답하지만 해당 증가분은 기록하지 못함 |
| 분산 락 획득 실패 | 이번 flush 건너뜀 |
| RDS 저장 실패 | dirty 유지, 다음 주기 재시도 |
| 저장 중 조회수 증가 | snapshot 불일치로 dirty 유지 |
| snapshot count key 없음 | count key가 여전히 없을 때만 Lua로 고아 dirty ID 제거 |
| Redis 재시작 | AOF와 volume으로 복구 시도. `everysec` 특성상 최근 write 유실 가능성은 남음 |

이 절은 앞의 실제 코드 원문을 읽은 직후 확인한다. 원문과 같은 순서의 설명용 주석본이며 실제 실행 파일에는 주석이 없다.

### Redis Compose 영속성

```yaml
redis:                         # Redis 서비스 정의를 시작한다.
  image: redis:7.4-alpine      # 가벼운 Redis 7.4 Alpine 이미지를 사용한다.
  restart: unless-stopped      # 명시적으로 중지하지 않았다면 종료·daemon 재시작 뒤 container를 다시 시작한다.
  command:                     # 기본 Redis 실행 명령에 AOF 옵션을 추가한다.
    - redis-server             # Redis 서버 프로세스를 시작한다.
    - --appendonly             # AOF 기록 기능의 옵션 이름이다.
    - "yes"                    # AOF를 활성화한다.
    - --appendfsync            # AOF 내용을 디스크와 동기화할 주기 옵션이다.
    - everysec                 # 최대 대략 1초 단위로 fsync한다.
  volumes:                     # 컨테이너 밖에 보존할 저장 경로를 연결한다.
    - redis-data:/data         # Redis /data를 이름 있는 Docker volume에 저장한다.
  healthcheck:                 # Redis가 실제 command에 응답하는지 container 상태 검사를 정의한다.
    test:                      # 실행할 검사 command를 배열 형식으로 적는다.
      - CMD                    # shell 해석 없이 뒤 인자들을 직접 실행하는 형식이다.
      - redis-cli              # Redis command line client를 실행한다.
      - ping                   # server 응답이 정상이면 PONG과 성공 exit code를 반환한다.
    interval: 5s               # 정상 상태에서 5초마다 검사한다.
    timeout: 3s                # 한 검사 응답을 최대 3초 기다린다.
    retries: 10                # 연속 10번 실패하면 unhealthy로 판단한다.
    start_period: 5s           # 시작 직후 5초 동안의 실패에는 초기 유예를 적용한다.
  networks:                    # Redis가 참여할 Compose network 목록이다.
    - backend-network          # backend container가 service 이름 redis로 접근할 공유 network다.
```

## 16.12 Redis 테스트 실행 구조

확인할 실제 파일:

- `backend/build.gradle`
- `backend/src/test/resources/application-test.yaml`

### 16.12.1 Gradle task의 분리

실제 코드:

```groovy
tasks.named('test') {
    systemProperty 'spring.profiles.active', 'test'
    useJUnitPlatform {
        excludeTags 'redis-integration', 'mysql-integration'
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

tasks.register('mysqlTest', Test) {
    description = 'Runs schema and locking integration tests against a Testcontainers MySQL server.'
    group = 'verification'
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
    useJUnitPlatform {
        includeTags 'mysql-integration'
    }
    shouldRunAfter test
}

check.dependsOn jacocoTestCoverageVerification
check.dependsOn tasks.named('redisTest')
check.dependsOn tasks.named('mysqlTest')
```

실행 관계:

```text
./gradlew test
→ test profile 활성화
→ redis-integration·mysql-integration 태그 제외
→ 일반 테스트 실행
→ jacocoTestReport 실행

./gradlew redisTest
→ redis-integration 태그만 실행
→ Redis Testcontainers 필요

./gradlew check
→ 일반 test와 JaCoCo 검증
→ redisTest
→ mysqlTest
```

따라서 `./gradlew test`만 성공해도 실제 Redis container 테스트까지 통과했다는 뜻은 아닙니다. `check`까지 실행해야 Redis·MySQL integration task가 의존 관계에 포함됩니다. 이 문서에서는 task 설정을 확인했으며, 문서 작성 시 Gradle task 자체를 실행했다고 주장하지 않습니다.

### 16.12.2 test profile이 Redis를 끄는 이유

```yaml
app:
  view-count:
    enabled: false
```

일반 테스트는 외부 Redis 서버의 실행 여부에 좌우되지 않도록 DB 구현체를 선택합니다. Redis 동작 자체는 별도 `redis-integration` 태그와 Testcontainers 테스트에서 실제 Redis를 띄워 확인합니다.

## 16.13 Redis 단위 테스트

단위 테스트는 실제 Redis 서버 대신 Mockito mock을 주입해 Java 코드가 올바른 명령·인자·fallback을 선택하는지 확인합니다.

이 단계에서 확인할 범위는 다음과 같습니다.

- `ViewCountPropertiesTest`: YAML binding, key 생성, 양수 postId, 양수 flush interval
- `RedisViewCountStoreTest`: baseline 전달, 음수 baseline 거부, null 결과, Redis 연결 오류, 고아 dirty 제거
- `RedisViewCountFlushSchedulerTest`: 락 획득 실패, snapshot 저장 순서, DB 저장 실패 시 dirty 유지, count key 부재 처리

### 16.13.1 ViewCountPropertiesTest

파일: `backend/src/test/java/kr/adapterz/springdatajpa/config/ViewCountPropertiesTest.java`

실제 코드 발췌:

```java
@ActiveProfiles("test")
@SpringBootTest
class ViewCountPropertiesTest {

    @Autowired
    private ViewCountProperties boundProperties;

    @Test
    void application_YAML의_조회수_설정을_객체로_변환한다() {
        assertThat(boundProperties.enabled()).isFalse();
        assertThat(boundProperties.countKey(42L))
                .isEqualTo("bamboo:{post-view}:count:42");
        assertThat(boundProperties.flushInterval())
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void 게시글_ID는_양수여야_한다() {
        ViewCountProperties properties = properties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.countKey(0L))
                .withMessage("postId must be positive");
    }
}
```

`@SpringBootTest`가 YAML binding 결과를 실제 ApplicationContext에서 읽고, `countKey(42L)`와 양수 검증을 확인합니다. 이 테스트는 Redis 서버에 명령을 보내지 않습니다.

### 16.13.2 RedisViewCountStoreTest

파일: `backend/src/test/java/kr/adapterz/springdatajpa/service/RedisViewCountStoreTest.java`

```java
@ExtendWith(MockitoExtension.class)
class RedisViewCountStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void Redis가_결과를_반환하지_않으면_DB_기준값을_반환한다() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                ArgumentMatchers.<List<String>>any(),
                any(String.class),
                any(String.class)
        )).thenReturn(null);

        long viewCount = store.increment(42L, 100L);

        assertThat(viewCount).isEqualTo(100L);
    }

    @Test
    void Redis_연결에_실패하면_DB_기준값을_반환한다() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                ArgumentMatchers.<List<String>>any(),
                any(String.class),
                any(String.class)
        )).thenThrow(new RedisConnectionFailureException(
                "Redis is unavailable"
        ));

        long viewCount = store.increment(42L, 100L);

        assertThat(viewCount).isEqualTo(100L);
    }
}
```

이 테스트는 `redisTemplate.execute()`가 null을 반환하거나 Redis 연결 예외를 던졌을 때 baseline을 반환하는 현재 코드를 증명합니다. “Redis 장애 시 DB 구현체로 전환한다”는 것을 검증하는 테스트가 아닙니다.

### 16.13.3 RedisViewCountFlushSchedulerTest

파일: `backend/src/test/java/kr/adapterz/springdatajpa/service/RedisViewCountFlushSchedulerTest.java`

```java
@Test
void 분산_락을_얻지_못하면_반영_작업을_건너뛴다() {
    when(redissonClient.getLock(
            "bamboo:{post-view}:flush-lock"
    )).thenReturn(lock);
    when(lock.tryLock()).thenReturn(false);

    scheduler.flushDirtyViewCounts();

    verifyNoInteractions(
            redisViewCountStore,
            persistenceService
    );
    verify(lock, never()).unlock();
}
```

Mock scheduler 테스트는 락 실패 시 Redis dirty 조회와 DB 저장이 실행되지 않는지 확인합니다. 다른 테스트는 snapshot 저장 후 acknowledge 순서, DB 저장 실패 시 dirty 유지, count key가 없을 때 persistence service를 호출하지 않는지를 검증합니다.

## 16.14 실제 Redis·AOF·동시성 테스트

이 절은 Mockito가 아니라 Docker/Testcontainers와 H2를 사용해 여러 객체가 실제로 상호작용하는지를 확인합니다.

### 16.14.1 RedisViewCountStoreIntegrationTest

파일: `backend/src/test/java/kr/adapterz/springdatajpa/service/RedisViewCountStoreIntegrationTest.java`

```java
@Tag("redis-integration")
@Testcontainers
class RedisViewCountStoreIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4-alpine")
            )
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());
}
```

`@Tag` 때문에 일반 `test` task에서는 제외되고 `redisTest`에서 선택됩니다. 이 테스트는 실제 Redis에서 baseline 보정·200회 동시 증가·snapshot dirty 유지·고아 dirty 정리·분산 락을 확인합니다.

200회 동시성 테스트의 핵심 부분:

```java
int requestCount = 200;
CountDownLatch startSignal = new CountDownLatch(1);

try (ExecutorService executor =
             Executors.newFixedThreadPool(20)) {
    for (int index = 0; index < requestCount; index++) {
        results.add(executor.submit(() -> {
            startSignal.await();
            return store.increment(42L, 100L);
        }));
    }

    startSignal.countDown();
}
```

`CountDownLatch`는 모든 작업이 같은 시점에 증가를 시작하도록 하고, `ExecutorService`는 20개 worker thread를 사용합니다. 최종 Redis String이 `300`인지 확인하므로 100 baseline에서 200번 증가가 유실되지 않았는지 검증합니다.

### 16.14.2 RedisAofRestartIntegrationTest

파일: `backend/src/test/java/kr/adapterz/springdatajpa/service/RedisAofRestartIntegrationTest.java`

```java
.withCommand(
        "redis-server",
        "--appendonly",
        "yes",
        "--appendfsync",
        "everysec"
)
```

테스트는 조회수 101과 dirty member를 만든 뒤 Redis container를 재시작하고 `redis-cli GET`과 `SISMEMBER`로 두 값이 복구되는지 확인합니다. 이는 AOF 복구를 검증하는 테스트이지 RDS에 이미 flush된 값을 검증하는 테스트가 아닙니다.

### 16.14.3 PostViewCountRepositoryIntegrationTest

파일: `backend/src/test/java/kr/adapterz/springdatajpa/repository/PostViewCountRepositoryIntegrationTest.java`

이 테스트는 `@ActiveProfiles("test")`, `@SpringBootTest`, `@Transactional`로 H2에서 실행됩니다.

```java
postViewCountRepository.incrementViewCount(post.getPostId(), 100L);
postViewCountRepository.incrementViewCount(post.getPostId(), 50L);

PostViewCount savedViewCount =
        postViewCountRepository.findById(post.getPostId())
                .orElseThrow();

assertThat(savedViewCount.getViewCount()).isEqualTo(102L);
```

첫 호출은 `max(0,100)+1=101`, 두 번째 호출은 `max(101,50)+1=102`가 되는지 확인합니다. 별도의 snapshot 저장 테스트는 150을 저장한 뒤 120을 저장해도 DB가 150으로 유지되는지 확인합니다.

### 16.14.4 PostConcurrencyIntegrationTest

파일: `backend/src/test/java/kr/adapterz/springdatajpa/service/PostConcurrencyIntegrationTest.java`

이 테스트는 `application-test.yaml`의 `enabled=false`를 사용하므로 Redis integration 테스트가 아닙니다. `PostService.getPostView()`가 DB updater를 사용할 때도 30개 동시 요청의 증가가 유실되지 않는지를 H2에서 확인합니다.

```java
@Test
void 동시에_게시글을_조회해도_조회수가_요청_수만큼_증가한다()
        throws Exception {
    int requestCount = 30;

    runConcurrently(
            requestCount,
            ignored -> postService.getPostView(postId, viewerId)
    );

    long separatedViewCount = postViewCountRepository.findById(postId)
            .orElseThrow()
            .getViewCount();

    assertThat(separatedViewCount).isEqualTo(requestCount);
}
```

같은 파일의 좋아요·신고 동시성 테스트는 조회수 Redis를 검증하는 테스트가 아니라, 같은 counter row를 수정하는 다른 업무의 동시성 보장도 함께 확인합니다. 게시글 수정 테스트는 같은 내용이면 version을 증가시키지 않고, 다른 내용이면 한 요청만 성공하는 현재 Post optimistic locking 규칙을 검증합니다.

### 16.14.5 PostServiceTest

파일: `backend/src/test/java/kr/adapterz/springdatajpa/service/PostServiceTest.java`

```java
@Mock
private ViewCountUpdater viewCountUpdater;

@Test
void 게시글_상세_조회_시_본인_게시글과_댓글의_isMine이_true로_반환된다() {
    when(viewCountUpdater.increment(postId, 0L))
            .thenReturn(1L);

    PostViewResponseDto response =
            postService.getPostView(postId, loginUserId);

    assertThat(response.getViewCount()).isEqualTo(1);
    verify(viewCountUpdater).increment(postId, 0L);
}
```

이 테스트는 실제 Redis·DB 구현을 선택하지 않고 ViewCountUpdater mock이 반환한 값을 Service가 PostViewResponseDto.viewCount에 전달하는지를 검증합니다. 따라서 Redis Lua의 원자성은 이 테스트가 아니라 RedisViewCountStoreIntegrationTest가 검증합니다.


## 16.15 핵심 축약본

```text
요청 경로:
DB 기준값 → Redis 원자적 INCR → dirty 표시

반영 경로:
분산 락 → dirty 조회 → snapshot → RDS max 저장
→ Redis가 snapshot 그대로일 때만 dirty 제거
→ count key가 사라졌다면 Lua 재확인 후 고아 dirty 제거
```


## 16.16 스킵할 코드

- 로그 메시지 문구
- `OptionalLong`의 Java 문법 세부
- 락 해제 중 예외 로그의 반복 구조
- `findDirtyPostIds()`의 stream 변환과 단순 Redis GET wrapper 내부

다만 두 Lua 스크립트, 분산 락, snapshot 이후 조건부 dirty 해제는 스킵하지 않는다.


## 16.16.1 이 장에서 필요한 Redis·Lua·다형성 문법

### interface 다형성

```java
private final ViewCountUpdater viewCountUpdater;
```

변수 타입은 interface지만 실제 주입 객체는 설정에 따라 `RedisViewCountStore` 또는 `DatabaseViewCountUpdater`다. 호출자는 같은 `increment` 계약만 알고 구현 기술을 몰라도 된다.

### 조건부 Bean

`@ConditionalOnProperty`는 설정 조건이 맞는 클래스만 Bean 등록 대상으로 만든다. 두 구현이 동시에 등록되면 같은 interface 후보가 둘이라 주입이 모호해질 수 있으므로 조건을 반대로 구성한다.

### Redis 자료형

- string: 조회수처럼 하나의 문자열·숫자 값
- set: 중복 없는 게시글 ID 모음
- lock: Redisson이 여러 Redis key와 명령으로 구현하는 분산 동기화 도구

Redis `INCR`는 string에 저장된 정수 문자열을 원자적으로 증가시킨다.

### Lua 기본 문법

```lua
local current = ...
if condition then
    ...
end
return value
```

- `local`: 현재 script 안의 지역 변수
- `if ... then ... end`: 조건문
- `not`: 논리 부정
- `or`, `and`: 논리 결합
- `~=`: 같지 않음
- `==`: 같음
- `return`: Java 호출자에게 결과 반환

### `KEYS`와 `ARGV`

Redis가 Lua에 제공하는 배열이다.

- `KEYS`: script가 접근할 Redis key
- `ARGV`: key가 아닌 일반 인자
- Lua 배열 index는 JavaScript와 달리 1부터 시작한다.

### `redis.call`

```lua
redis.call('GET', KEYS[1])
```

Redis 서버 내부에서 GET, SET, INCR, SADD 같은 명령을 실행한다. 명령이 실패하면 script 전체가 오류로 중단된다.

### Lua 원자성

Redis는 실행 중인 Lua script 사이에 다른 명령을 끼워 넣지 않는다. 따라서 “기준값 복구 → 증가 → dirty 표시”를 하나의 논리 작업으로 유지한다. 다만 긴 script는 다른 요청도 기다리게 하므로 짧게 유지해야 한다.

### Java Generic method 힌트

```java
ArgumentMatchers.<RedisScript<Long>>any()
```

`<RedisScript<Long>>`은 컴파일러에게 `any()`가 어떤 Generic 타입을 반환하는지 명시한다. overload와 타입 추론이 모호할 때 사용한다.

### `OptionalLong`

primitive `long` 값이 있을 수도 없을 수도 있음을 표현한다. 값이 없으면 0이라고 임의 해석하지 않고 `isEmpty()`로 구분한다.

### `@Scheduled`

- `initialDelay`: 애플리케이션 시작 뒤 첫 실행까지 대기
- `fixedDelay`: 이전 실행이 끝난 뒤 다음 실행까지 대기
- `fixedRate`와 달리 작업 실행 시간이 겹치도록 주기를 계산하지 않는다.

### `tryLock`

락을 얻을 수 없을 때 무기한 기다리지 않고 즉시 false를 반환한다. 다른 backend가 flush 중이면 이번 주기를 건너뛰고 다음 Scheduler 실행을 기다린다.

### `finally`

예외 발생 여부와 상관없이 실행되는 블록이다. 락·파일·연결처럼 반드시 정리해야 하는 자원에 사용한다.

### Snapshot

문법이 아니라 동시성 개념이다. 계속 변할 수 있는 Redis 값을 특정 순간에 읽어 고정한 비교 기준을 뜻한다. 저장 뒤 현재값과 snapshot을 비교하여 중간 변경 여부를 판단한다.

### record compact constructor

```java
public ViewCountProperties {
    검증 코드
}
```

record의 canonical constructor를 component parameter 목록 없이 작성하는 문법이다. component 값이 field에 최종 대입되기 전에 검증하거나 정규화할 수 있다. 일반 class의 parameter 없는 constructor와 다른 문법이다.

### `GREATEST`와 native query

```sql
SET view_count = GREATEST(view_count, :snapshotViewCount)
```

MySQL 함수 `GREATEST`는 인자 중 큰 값을 반환한다. `nativeQuery = true`이므로 `post_view_counts`, `view_count`는 Entity·field 이름이 아니라 실제 DB table·column 이름이다.

### Redis 장애 fallback과 DB 구현의 차이

- 설정이 `false`여서 `DatabaseViewCountUpdater`가 선택되면 매 요청이 RDS 조회수를 실제로 증가시킨다.
- Redis 구현이 선택된 상태에서 `DataAccessException`이 발생하면 구현체를 교체하지 않는다. 그 요청은 baseline만 반환하므로 조회 증가분이 보존되지 않는다.
- `@ConditionalOnProperty`는 application 시작 시 Bean 구성을 결정하며 요청 중 장애를 감지해 동적으로 다른 Bean으로 전환하는 기능이 아니다.

## 16.17 이해 확인

1. `ViewCountUpdater` 인터페이스를 둔 이유는 무엇인가?
2. Redis 활성 여부에 따라 구현체는 어떻게 선택되는가?
3. count key와 dirty set은 각각 무엇을 저장하는가?
4. 조회수 증가와 dirty 표시를 Lua 하나로 실행하는 이유는 무엇인가?
5. DB 기준값보다 Redis 값이 작을 때 복구하는 이유는 무엇인가?
6. 여러 backend에서 flush할 때 분산 락이 필요한 이유는 무엇인가?
7. RDS 저장 후 dirty를 무조건 제거하면 어떤 조회수가 유실될 수 있는가?
8. `acknowledgeIfUnchanged`는 어떤 경우에만 dirty를 제거하는가?
9. RDS에는 왜 기존 값과 snapshot 중 큰 값을 저장하는가?
10. AOF와 Docker volume은 각각 무엇을 보존하는가?
11. Redis 구현이 활성화된 상태의 통신 장애와 `VIEW_COUNT_REDIS_ENABLED=false`는 DB 처리 방식이 어떻게 다른가?
12. Redis Lua의 원자성이 script 오류 시 이전 write까지 rollback한다는 뜻인가?
13. snapshot count key가 사라졌지만 dirty ID가 남아 있으면 왜 Java에서 바로 제거하지 않고 Lua로 다시 확인하는가?
14. AOF `everysec`와 volume을 사용하면 최근 조회수 유실 가능성이 완전히 사라지는가?

## 16.18 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.

## 16.19 진행률

- 이 문서까지 확인한 고유 파일: **162/213개**
- 진행률: **76.1%**
- 이번 문서에서 새로 집계한 구현 파일: `ViewCountProperties.java`, `RedisViewCountStore.java`, `RedisViewCountFlushScheduler.java`, `ViewCountPersistenceService.java`
- `Post`, `PostService`, `PostViewCountRepository`와 Redis 테스트 파일은 이미 다른 단계에서 집계했거나 18번 테스트 문서에서 집계하므로 중복하지 않습니다.
