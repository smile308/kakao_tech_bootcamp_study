# 6장. 게시글 API와 JPA

## 6.1 학습 목표

게시글 API를 통해 JPA 연관관계, 읽기 전용 Transaction, DTO 변환, soft delete, 낙관적·비관적 락을 학습한다.

일반 흐름:

```text
PostController
→ PostService
→ PostRepository와 관련 Repository
→ Post·PostCounter·PostViewCount
→ Response DTO
```

### 6.1.1 이 장의 실제 코드 읽기 순서

```text
PostController endpoint
→ PostService의 public Transaction method
→ 권한·삭제·신고 차단 검사
→ PostRepository와 보조 Repository 조회
→ 필요한 row lock 또는 version 검사
→ Entity 상태 변경 / count query
→ Transaction commit과 dirty checking
→ Response DTO 또는 PostResponseFactory
```

상세 조회는 `Post → 댓글·좋아요·신고 여부 → 조회수 증가 → PostViewResponseDto` 순서다. 작성·수정·삭제는 `요청 DTO → 작성자 확인 → image 검증 → Entity 변경`을 따른다. 좋아요·신고·댓글은 같은 `PostCounter`를 함께 수정하므로 단순 CRUD가 아니라 어떤 row를 먼저 잠그는지까지 읽어야 한다. 6장에 없는 화면 상태 갱신과 댓글 UI 연결은 8장에서 이어진다.

## 6.2 게시글 Entity 묶음

게시글 한 행만으로 모든 정보를 표현하지 않는다.

```text
Post
→ 제목, 내용, 작성자, 삭제 여부, version

PostImage
→ 게시글 이미지 여러 개

PostCounter
→ 좋아요·신고·댓글 카운터

PostViewCount
→ BIGINT 조회수

Like / PostReport / Comment
→ 사용자와 게시글 사이의 행동
```

카운터를 별도 Entity로 분리하면 자주 변경되는 카운터가 게시글 본문 version과 불필요하게 충돌하는 것을 줄일 수 있다.

## 6.2.1 실제 Entity 연관관계

파일: `entity/Post.java`의 실제 연관관계 field 원문:

```java
@Version
@Column(name = "version", nullable = false)
private Long version;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User user;

@Column(name = "post_title", nullable = false, length = 26)
private String postTitle;

@Column(name = "post_content", nullable = false)
private String postContent;

@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("imageOrder ASC")
private List<PostImage> postImages = new ArrayList<>();

@Column(name = "is_fixed", nullable = false)
private boolean isFixed;

@OneToOne(
        mappedBy = "post",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        optional = false
)
private PostCounter postCounter;

@OneToOne(
        mappedBy = "post",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        optional = false
)
private PostViewCount postViewCount;
```

`mappedBy = "post"`는 foreign key를 실제 관리하는 주인이 상대 Entity의 `post` field라는 뜻이다. `PostCounter` 쪽 실제 원문은 다음과 같다.

```java
@Id
@Column(name = "post_id")
private Long postId;

@MapsId
@OneToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "post_id")
private Post post;
```

`@MapsId`는 이 연관관계의 foreign key인 `post_id`를 `PostCounter` 자신의 primary key로도 함께 사용한다. 따라서 Post와 PostCounter는 같은 ID를 공유하는 1:1 구조다. `PostViewCount`도 같은 mapping을 사용한다.

## 6.3 실제 코드 발췌: Controller

```java
@GetMapping("/{postId}")
public PostViewResponseDto getPostView(
        @PathVariable("postId") Long postId,
        @AuthenticationPrincipal CustomUserDetails userDetails
) {
    return postService.getPostView(postId, userDetails.getUserId());
}

@PatchMapping("/{postId}")
public PostFixResponseDto fixPost(
        @PathVariable("postId") Long postId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody PostFixRequestDto request
){
    return postService.fixPost(postId, userDetails.getUserId(), request);
}
```

중요한 줄:

```java
@PathVariable("postId")
// /posts/15의 15를 Java Long 값으로 받는다.

@AuthenticationPrincipal
// JWT Filter가 SecurityContext에 저장한 로그인 사용자 정보를 받는다.
```

## 6.4 실제 코드 발췌: 상세 조회 Service

```java
@Transactional
public PostViewResponseDto getPostView(Long postId, Long loginUserId) {
    Post post = getViewablePost(postId);
    User loginUser = getLoginUser(loginUserId);

    List<Comment> comments = commentRepository.findByPostWithUser(post);
    List<CommentResponseDto> commentResponseDtos = new ArrayList<>();

    for (Comment comment : comments) {
        boolean isMyComment=comment.getUser().getUserId().equals(loginUserId);
        CommentResponseDto commentResponseDto =
                new CommentResponseDto(comment, comment.getUser(),isMyComment);
        commentResponseDtos.add(commentResponseDto);
    }

    boolean isLiked = likeRepository.existsByPostAndUser(post, loginUser);
    boolean isReported = postReportRepository.existsByPostAndUser(post, loginUser);
    boolean isMine = post.getUser().getUserId().equals(loginUserId);

    long baselineViewCount = Math.max(
            post.getPostCounter().getViewCount(),
            post.getPostViewCount().getViewCount()
    );
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
}
```

클래스에는 기본적으로 `@Transactional(readOnly = true)`가 있지만 조회수 증가는 상태를 변경하므로 이 메서드에 `@Transactional`을 다시 지정했다.

상세 조회는 단순 SELECT 하나가 아니다. 화면이 요구하는 댓글, 좋아요 여부, 신고 여부, 작성자 여부와 조회수를 조합한다.

## 6.5 실제 코드 발췌: Repository와 EntityGraph

```java
@EntityGraph(
        attributePaths = {
                "user",
                "postImages",
                "postCounter",
                "postViewCount"
        }
)
@Query("""
        SELECT post
        FROM Post post
        WHERE post.postId = :postId
          AND post.deleted = false
        """)
Optional<Post> findByPostIdAndDeletedFalse(
        @Param("postId") Long postId
);
```

중요한 줄:

```java
@EntityGraph(...)
// 기본 LAZY 연관관계 중 이번 응답에 필요한 값을 게시글과 함께 조회한다.

post.deleted = false
// DB 행을 삭제하지 않고 삭제 표시만 하는 soft delete를 조회 조건에 반영한다.
```

`EntityGraph`가 없으면 DTO 변환 중 연관관계마다 추가 쿼리가 발생하는 N+1 문제가 생길 수 있다.

## 6.6 수정과 낙관적 락

실제 코드:

```java
private void validatePostVersion(Post post, Long requestVersion) {
    if (!Objects.equals(post.getVersion(), requestVersion)) {
        throw new PostVersionConflictException();
    }
}
```

수정 method에는 같은 값으로 수정해도 version을 올려야 하는 경우를 위한 다음 실제 코드도 있다.

```java
if (requiresForcedVersionIncrement(post, request)) {
    entityManager.lock(post, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
}
```

```java
private boolean requiresForcedVersionIncrement(
        Post post,
        PostFixRequestDto request
) {
    return post.isFixed()
            && Objects.equals(post.getPostTitle(), request.getTitle())
            && Objects.equals(post.getPostContent(), request.getContent());
}
```

JPA는 `Post` 자체의 column 값이 바뀌지 않으면 Post UPDATE가 필요 없다고 판단할 수 있다. 하지만 image collection만 교체되거나 이미 같은 제목·내용으로 다시 수정하는 요청도 성공한 수정으로 version을 진행시켜야 이후 오래된 요청을 구분할 수 있다. `OPTIMISTIC_FORCE_INCREMENT`는 해당 Post의 version 증가를 강제한다. 조건에 `post.isFixed()`가 있으므로 최초 수정에서는 `update()`가 `isFixed`를 false에서 true로 바꾸며 자연스럽게 Post가 dirty 상태가 된다.

```text
사용자 A와 B가 version 3 게시글을 함께 열음
→ A가 version 3으로 수정 성공
→ DB version 4
→ B가 오래된 version 3으로 수정 요청
→ 충돌 응답 409
```

`version`은 “마지막에 저장한 사람이 무조건 앞선 변경을 덮어쓰기”를 방지한다.

여기에는 두 겹의 방어가 있다. `validatePostVersion()`은 client가 보낸 version이 현재 영속 Entity와 맞는지 일찍 검사하고, Entity의 `@Version`은 조회 이후 commit 사이에 다른 Transaction이 먼저 수정했는지도 UPDATE 조건으로 검사한다.

## 6.7 좋아요와 비관적 락

먼저 `PostCounterRepository`가 lock을 얻고 count를 바꾸는 실제 원문:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        SELECT counter
        FROM PostCounter counter
        WHERE counter.postId = :postId
        """)
Optional<PostCounter> findByPostIdForUpdate(
        @Param("postId") Long postId
);

@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
        UPDATE PostCounter counter
        SET counter.likeCount = counter.likeCount + 1
        WHERE counter.postId = :postId
        """)
int incrementLikeCount(@Param("postId") Long postId);
```

첫 JPQL은 해당 counter row를 조회하면서 DB write lock을 요청한다. 두 번째 JPQL은 Entity를 읽고 Java에서 `+ 1` 하는 대신 DB UPDATE 식으로 현재 값에 1을 더하고, 수정된 row 수를 반환한다. `@Modifying`은 SELECT가 아닌 변경 JPQL임을 Spring Data JPA에 알린다. `flushAutomatically`는 query 전에 pending 변경을 flush하고, `clearAutomatically`는 query 뒤 persistence context를 비워 bulk update와 이미 관리 중인 Entity 값이 어긋나는 것을 줄인다.

Service의 실제 원문:

```java
@Transactional
public LikeResponseDto likePost(Long postId, Long loginUserId) {
    Post post = getActivePostForInteraction(postId);

    User user = getLoginUser(loginUserId);

    getPostCounterForUpdate(postId);
    validateActivePostAfterCounterUpdate(postId);

    try {
        likeRepository.saveAndFlush(new Like(post, user));
    } catch (DataIntegrityViolationException e) {
        throw new InvalidRequestException("Already_Liked");
    }
    validateCounterUpdate(
            postCounterRepository.incrementLikeCount(postId)
    );

    return new LikeResponseDto(getPostCounter(postId).getLikeCount());
}
```

비관적 락은 Transaction이 끝날 때까지 다른 Transaction의 충돌 접근을 기다리게 한다.

좋아요는 다음 두 사실이 함께 유지되어야 한다.

```text
Like 행 존재
↔ PostCounter.likeCount 증가
```

중간에 실패하면 `@Transactional`이 전체 변경을 rollback한다.

DB의 사용자·게시글 unique 제약과 `DataIntegrityViolationException` 처리는 같은 사용자의 중복 좋아요 경쟁도 막는다. `saveAndFlush()`를 사용하는 이유는 unique 제약 검사를 method 뒤 commit까지 미루지 않고 이 `try/catch` 안에서 발생시키기 위해서다.

## 6.8 신고 흐름

Service의 실제 원문:

```java
@Transactional
public PostReportResponseDto reportPost(
        Long postId,
        Long loginUserId
) {
    Post post = getActivePostForUpdate(postId);
    User reporter = getLoginUser(loginUserId);
    User writer = getUserForUpdate(post.getUser().getUserId());

    if (writer.getUserId().equals(reporter.getUserId())) {
        throw new InvalidRequestException("Cannot_Report_Own_Post");
    }

    if (postReportRepository.existsByPostAndUser(post, reporter)) {
        throw new InvalidRequestException("Already_Reported");
    }


    postReportRepository.save(new PostReport(post, reporter));

    post.report();
    writer.receiveReport();

    return new PostReportResponseDto(
            post.getPostId(),
            post.getPostCounter().getReportCount()
    );
}
```

```text
활성 게시글을 쓰기 락으로 조회
→ 신고자 조회
→ 작성자 User를 쓰기 락으로 조회
→ 자기 글 신고 금지
→ 중복 신고 금지
→ PostReport 저장
→ 게시글 신고 카운터 증가
→ 작성자 누적 신고 증가
→ 이후 `User.isSuspended()`가 누적 수와 기준을 비교해 정지 상태로 판단
```

작성자 User에 락을 거는 이유는 서로 다른 게시글에서 같은 작성자를 동시에 신고해도 누적 신고 수가 유실되지 않게 하기 위해서다.

게시글에는 `PESSIMISTIC_WRITE`, 작성자 User에도 `PESSIMISTIC_WRITE` lock을 건다. 같은 게시글 신고 카운터와 같은 작성자의 누적 신고 수를 각각 직렬화한다.

같은 사용자가 같은 게시글에 동시에 두 번 신고해도 두 요청은 `getActivePostForUpdate(postId)`에서 같은 게시글 row의 write lock을 경쟁한다. 첫 요청이 Transaction을 끝낼 때까지 두 번째 요청은 기다리고, 락을 얻은 뒤 `existsByPostAndUser()`를 실행하므로 첫 요청이 저장한 신고를 확인해 `Already_Reported`로 거부된다.

```text
요청 A가 게시글 write lock 획득
→ 요청 B는 같은 게시글 lock에서 대기
→ 요청 A가 신고 저장 후 commit
→ 요청 B가 lock 획득
→ 중복 신고 조회 결과 true
→ Already_Reported
```

`post_reports(post_id, user_id)` unique constraint도 중복 row 자체를 막는 최종 DB 제약으로 남아 있다. 하지만 현재 Service 흐름에서 동시 중복 신고를 순서대로 처리하게 만드는 핵심은 unique 예외 변환이 아니라 중복 검사보다 먼저 획득하는 게시글 write lock이다.

## 6.9 핵심 축약본

```java
Controller {
    HTTP 값을 받아 Service 호출
}

Service {
    조회와 검증 순서를 조합
    Transaction 경계 설정
    응답 DTO 생성
}

Repository {
    필요한 Entity와 연관관계 조회
    락과 수정 쿼리 실행
}

Entity {
    상태와 상태 변경 규칙 보유
}
```


## 6.9.1 전체 원문 코드 라인별 주석본

아래 주석은 학습을 위해 추가한 것이며 실제 프로젝트 파일에는 없다. 줄 끝 주석을 붙인 주석본은 의미를 따라가기 위한 설명용이므로 그대로 복사해 실행하지 않는다.

### `Post`와 공유 primary key 카운터

```java
@Version // JPA가 concurrent update 충돌을 확인할 version field로 지정한다.
@Column(name = "version", nullable = false) // posts.version column에 null 없이 mapping한다.
private Long version; // client가 수정 요청에 다시 보내는 현재 version 값이다.

@ManyToOne(fetch = FetchType.LAZY) // 여러 Post가 한 User 작성자를 참조하며 필요할 때 조회한다.
@JoinColumn(name = "user_id", nullable = false) // posts.user_id foreign key column을 이 field가 관리한다.
private User user; // 게시글 작성자 Entity다.

@Column(name = "post_title", nullable = false, length = 26) // 제목 column과 DB 길이 제한을 지정한다.
private String postTitle; // 게시글 제목이다.

@Column(name = "post_content", nullable = false) // 본문 column을 null 불가로 mapping한다.
private String postContent; // 게시글 본문이다.

@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true) // PostImage.post가 FK 주인이며 저장·삭제를 전파하고 관계에서 빠진 image를 삭제한다.
@OrderBy("imageOrder ASC") // collection을 읽을 때 imageOrder field 오름차순으로 정렬한다.
private List<PostImage> postImages = new ArrayList<>(); // 게시글 image Entity 목록을 빈 mutable list로 초기화한다.

@Column(name = "is_fixed", nullable = false) // 한 번 수정됐는지 기록하는 column이다.
private boolean isFixed; // version 강제 증가 판단에도 사용한다.

@OneToOne( // Post 하나와 PostCounter 하나의 1:1 관계다.
        mappedBy = "post", // FK는 PostCounter.post field가 관리한다.
        fetch = FetchType.LAZY, // 실제 접근할 때 조회하는 전략이다.
        cascade = CascadeType.ALL, // Post의 영속 동작을 counter에도 전파한다.
        orphanRemoval = true, // 관계에서 분리된 counter를 삭제 대상으로 본다.
        optional = false // counter가 반드시 존재하는 관계임을 나타낸다.
)
private PostCounter postCounter; // 좋아요·신고·댓글과 기존 조회수 counter다.

@OneToOne( // Post 하나와 BIGINT 조회수 Entity 하나의 1:1 관계다.
        mappedBy = "post", // FK는 PostViewCount.post field가 관리한다.
        fetch = FetchType.LAZY, // 필요할 때 조회한다.
        cascade = CascadeType.ALL, // Post 영속 동작을 조회수 Entity에도 전파한다.
        orphanRemoval = true, // 관계에서 빠진 조회수 Entity를 삭제한다.
        optional = false // 반드시 존재하는 관계다.
)
private PostViewCount postViewCount; // long 조회수를 저장하는 별도 Entity다.
```

```java
@Id // PostCounter의 primary key다.
@Column(name = "post_id") // post_counters.post_id column에 mapping한다.
private Long postId; // Post ID와 같은 값을 공유한다.

@MapsId // 아래 연관관계의 ID를 위 primary key 값으로 사용한다.
@OneToOne(fetch = FetchType.LAZY, optional = false) // counter 하나가 반드시 Post 하나와 연결된다.
@JoinColumn(name = "post_id") // 같은 post_id column을 foreign key로도 사용한다.
private Post post; // counter가 속한 Post다.
```

### 게시글 상세·수정 Controller

```java
@GetMapping("/{postId}") // GET /posts/{postId} 요청을 상세 조회 메서드에 연결한다.
public PostViewResponseDto getPostView( // 상세 화면에 필요한 응답 DTO를 반환한다.
        @PathVariable("postId") Long postId, // URL의 postId 부분을 Long으로 변환해 받는다.
        @AuthenticationPrincipal CustomUserDetails userDetails // JWT Filter가 저장한 로그인 principal을 받는다.
) {
    return postService.getPostView( // 실제 조회 규칙과 Transaction은 Service에 맡긴다.
            postId, // 조회할 게시글 ID를 전달한다.
            userDetails.getUserId() // 로그인 사용자별 좋아요·소유권 판단을 위해 사용자 ID를 전달한다.
    );
}

@PatchMapping("/{postId}") // PATCH /posts/{postId} 요청을 수정 메서드에 연결한다.
public PostFixResponseDto fixPost( // 수정 완료 응답 DTO를 반환한다.
        @PathVariable("postId") Long postId, // 수정할 게시글 ID를 URL에서 받는다.
        @AuthenticationPrincipal CustomUserDetails userDetails, // 현재 로그인 사용자를 받는다.
        @Valid @RequestBody PostFixRequestDto request // JSON 수정값을 DTO로 바꾸고 형식을 검증한다.
) {
    return postService.fixPost( // 소유권, version과 이미지 검증을 Service에 맡긴다.
            postId, // 수정 대상 ID다.
            userDetails.getUserId(), // 작성자와 비교할 로그인 사용자 ID다.
            request // 제목, 내용, 이미지, version이 담긴 요청이다.
    );
}
```

### 상세 조회 Service

```java
@Transactional // 조회수 증가도 포함하므로 읽기 전용이 아닌 Transaction을 시작한다.
public PostViewResponseDto getPostView(Long postId, Long loginUserId) { // 게시글 상세 응답 전체를 조립한다.
    Post post = getViewablePost(postId); // 존재하고 삭제·신고 차단되지 않은 게시글을 조회한다.
    User loginUser = getLoginUser(loginUserId); // 요청 사용자가 현재 유효한 계정인지 조회한다.

    List<Comment> comments = commentRepository.findByPostWithUser(post); // 댓글과 댓글 작성자를 함께 조회한다.
    List<CommentResponseDto> commentResponseDtos = new ArrayList<>(); // 화면에 전달할 댓글 DTO 목록을 만든다.

    for (Comment comment : comments) { // 조회한 댓글을 하나씩 응답 모양으로 변환한다.
        boolean isMyComment = comment.getUser().getUserId().equals(loginUserId); // 댓글 작성자와 로그인 사용자가 같은지 판단한다.
        CommentResponseDto commentResponseDto = new CommentResponseDto( // 댓글 Entity에서 응답 DTO를 만든다.
                comment, // 댓글 본문과 작성 시간을 제공한다.
                comment.getUser(), // 작성자 닉네임·프로필 정보를 제공한다.
                isMyComment // 화면에서 수정·삭제 버튼 표시 여부에 사용한다.
        );
        commentResponseDtos.add(commentResponseDto); // 완성한 댓글 DTO를 응답 목록에 추가한다.
    }

    boolean isLiked = likeRepository.existsByPostAndUser(post, loginUser); // 로그인 사용자의 좋아요 존재 여부를 조회한다.
    boolean isReported = postReportRepository.existsByPostAndUser(post, loginUser); // 신고 존재 여부를 조회한다.
    boolean isMine = post.getUser().getUserId().equals(loginUserId); // 게시글 작성자 본인인지 판단한다.

    long baselineViewCount = Math.max( // 마이그레이션 전후 두 DB 카운터 중 안전한 큰 값을 선택한다.
            post.getPostCounter().getViewCount(), // 기존 PostCounter의 조회수다.
            post.getPostViewCount().getViewCount() // 새 BIGINT PostViewCount의 조회수다.
    );

    long updatedViewCount = viewCountUpdater.increment( // 설정에 따라 Redis 또는 DB 구현으로 조회수를 증가시킨다.
            postId, // 증가할 게시글 ID다.
            baselineViewCount // Redis가 비어 있을 때 사용할 DB 기준값이다.
    );

    return new PostViewResponseDto( // 모든 조회 결과를 하나의 상세 응답으로 조립한다.
            post, // 제목, 내용, 이미지와 작성자 정보의 기반이다.
            post.getPostCounter(), // 좋아요·신고·댓글 카운터를 제공한다.
            updatedViewCount, // 이번 요청으로 증가한 조회수다.
            commentResponseDtos, // 화면에 표시할 댓글 목록이다.
            isLiked, // 좋아요 버튼 상태다.
            isReported, // 신고 버튼 상태다.
            isMine // 수정·삭제 버튼 상태다.
    );
}
```

### 상세 Repository

```java
@EntityGraph( // 이번 조회에서 함께 불러올 LAZY 연관관계를 지정한다.
        attributePaths = { // fetch 대상 필드 이름 목록이다.
                "user", // 게시글 작성자를 함께 조회한다.
                "postImages", // 게시글 이미지 목록을 함께 조회한다.
                "postCounter", // 좋아요·신고·댓글 카운터를 함께 조회한다.
                "postViewCount" // 새 조회수 Entity를 함께 조회한다.
        }
)
@Query(""" // 메서드에서 실행할 JPQL을 직접 작성한다.
        SELECT post
        FROM Post post
        WHERE post.postId = :postId
          AND post.deleted = false
        """)
Optional<Post> findByPostIdAndDeletedFalse( // 없을 수 있으므로 Optional<Post>를 반환한다.
        @Param("postId") Long postId // Java 인자를 JPQL의 :postId에 연결한다.
);
```

### version 검증

```java
private void validatePostVersion(Post post, Long requestVersion) { // DB Entity와 클라이언트가 본 version을 비교한다.
    if (!Objects.equals(post.getVersion(), requestVersion)) { // null 안전 비교로 두 version이 다른지 확인한다.
        throw new PostVersionConflictException(); // 오래된 화면의 수정이면 409로 변환될 예외를 던진다.
    }
}
```

```java
if (requiresForcedVersionIncrement(post, request)) { // Post 자체 column 변경이 없을 수 있는 후속 수정인지 검사한다.
    entityManager.lock(post, LockModeType.OPTIMISTIC_FORCE_INCREMENT); // commit 때 Post version을 강제로 증가시킨다.
}
```

```java
private boolean requiresForcedVersionIncrement( // 강제 version 증가가 필요한지 계산한다.
        Post post, // 현재 영속 Post다.
        PostFixRequestDto request // client가 보낸 수정 값이다.
) {
    return post.isFixed() // 이미 한 번 수정되어 update()의 isFixed 변경이 더는 일어나지 않고
            && Objects.equals(post.getPostTitle(), request.getTitle()) // 제목도 현재 값과 같고
            && Objects.equals(post.getPostContent(), request.getContent()); // 내용도 현재 값과 같을 때 true다.
}
```

### 좋아요 Transaction 핵심

```java
@Lock(LockModeType.PESSIMISTIC_WRITE) // 조회할 counter row에 DB write lock을 요청한다.
@Query(""" // Entity와 field 이름을 사용하는 JPQL 조회를 선언한다.
        SELECT counter
        FROM PostCounter counter
        WHERE counter.postId = :postId
        """)
Optional<PostCounter> findByPostIdForUpdate( // row가 없을 가능성을 Optional로 반환한다.
        @Param("postId") Long postId // method 인자를 JPQL의 :postId parameter에 연결한다.
);

@Modifying(clearAutomatically = true, flushAutomatically = true) // 변경 query 전 flush하고 실행 뒤 persistence context를 비운다.
@Query(""" // DB에서 직접 likeCount를 증가시키는 JPQL update다.
        UPDATE PostCounter counter
        SET counter.likeCount = counter.likeCount + 1
        WHERE counter.postId = :postId
        """)
int incrementLikeCount(@Param("postId") Long postId); // 변경된 row 수를 int로 반환한다.
```

```java
@Transactional // Like row와 counter 변경을 한 업무 단위로 묶는다.
public LikeResponseDto likePost(Long postId, Long loginUserId) { // 게시글 ID와 로그인 사용자 ID로 좋아요를 추가한다.
    Post post = getActivePostForInteraction(postId); // 삭제되지 않은 상호작용 대상 게시글을 조회한다.

    User user = getLoginUser(loginUserId); // 삭제되지 않은 로그인 사용자를 조회한다.

    getPostCounterForUpdate(postId); // 카운터 row에 비관적 write lock을 걸어 동시 counter 변경을 직렬화한다.
    validateActivePostAfterCounterUpdate(postId); // lock을 기다리는 사이 게시글이 삭제되지 않았는지 read lock 조회로 다시 확인한다.

    try { // DB unique 제약 위반을 프로젝트 오류로 바꿀 범위를 시작한다.
        likeRepository.saveAndFlush(new Like(post, user)); // Like row를 즉시 flush해 같은 사용자 중복을 현재 위치에서 확인한다.
    } catch (DataIntegrityViolationException e) { // post_id·user_id unique 제약 위반 등을 잡는다.
        throw new InvalidRequestException("Already_Liked"); // 중복 좋아요용 400 업무 예외로 바꾼다.
    }
    validateCounterUpdate( // update query가 정확히 한 row를 바꿨는지 검사한다.
            postCounterRepository.incrementLikeCount(postId) // DB에서 like_count를 원자적으로 1 증가시킨다.
    );

    return new LikeResponseDto(getPostCounter(postId).getLikeCount()); // 갱신된 counter를 다시 읽어 응답 DTO에 넣는다.
}
```

### 신고 Transaction

```java
@Transactional // 신고 row·게시글 counter·작성자 누적 신고 수를 한 Transaction으로 묶는다.
public PostReportResponseDto reportPost( // 신고 처리 결과를 반환한다.
        Long postId, // 신고할 게시글 ID다.
        Long loginUserId // 신고하는 로그인 사용자 ID다.
) {
    Post post = getActivePostForUpdate(postId); // 활성 게시글을 조회하며 게시글 row에 write lock을 건다.
    User reporter = getLoginUser(loginUserId); // 신고자가 현재 유효한 계정인지 조회한다.
    User writer = getUserForUpdate(post.getUser().getUserId()); // 작성자 row를 write lock으로 조회한다.

    if (writer.getUserId().equals(reporter.getUserId())) { // 작성자와 신고자가 같은지 검사한다.
        throw new InvalidRequestException("Cannot_Report_Own_Post"); // 자기 글 신고를 거부한다.
    }

    if (postReportRepository.existsByPostAndUser(post, reporter)) { // 이미 같은 사용자의 신고 row가 있는지 조회한다.
        throw new InvalidRequestException("Already_Reported"); // 게시글 lock 뒤 조회하므로 기다리던 동시 중복 요청도 여기서 거부한다.
    }


    postReportRepository.save(new PostReport(post, reporter)); // 신고 이력을 영속화한다.

    post.report(); // PostCounter의 신고 수를 1 증가시킨다.
    writer.receiveReport(); // 작성자의 전체 누적 신고 수를 1 증가시킨다.

    return new PostReportResponseDto( // 변경 결과 응답을 만든다.
            post.getPostId(), // 신고된 게시글 ID다.
            post.getPostCounter().getReportCount() // 증가 후 게시글 신고 수다.
    );
}
```

## 6.10 스킵할 코드

게시글 상세·수정·좋아요·신고를 이해한 뒤 다음은 차이만 확인한다.

- 댓글 CRUD: 대상 게시글 일치와 댓글 소유권 검증
- 좋아요 취소: Like 삭제 후 카운터 감소
- 게시글 삭제: soft delete와 version 검증
- 단순 응답 DTO: 화면에 전달할 필드 모양
- `ImageDataUrlValidator` 내부: 4장에서 실제 원문과 signature 검사까지 이미 학습했으므로 여기서는 Service 호출 위치만 확인


## 6.10.1 이 장에서 필요한 JPA·Transaction 문법

### JPA Entity와 annotation

```java
@Entity
@Table(name = "posts")
@Id
@GeneratedValue
@Column
```

- `@Entity`: 이 클래스를 JPA 영속 객체로 등록
- `@Table`: 연결할 DB 테이블 정보
- `@Id`: 기본키 필드
- `@GeneratedValue`: 기본키 생성 전략
- `@Column`: 컬럼 이름, null, 길이 등 mapping 설정

### 연관관계

```java
@ManyToOne
@OneToOne
@OneToMany
@JoinColumn
```

- `ManyToOne`: 여러 댓글이 한 게시글을 참조하는 관계
- `OneToOne`: 게시글 하나와 카운터 하나의 관계
- `OneToMany`: 게시글 하나가 여러 이미지를 가지는 관계
- `JoinColumn`: foreign key 컬럼을 지정

연관관계의 주인은 foreign key를 실제로 관리하는 쪽이다.

### LAZY와 EntityGraph

`fetch = FetchType.LAZY`는 연관 객체를 즉시 조회하지 않고 실제 접근이 필요할 때 조회한다. `@EntityGraph`는 특정 query에서 필요한 LAZY 관계를 함께 가져오도록 예외적으로 지정한다.

### cascade와 orphan removal

- cascade는 부모 저장·삭제 동작을 연관 Entity에 전파한다.
- orphan removal은 부모 컬렉션에서 제거되어 관계를 잃은 자식을 삭제한다.
- 정확한 동작은 Entity에 지정된 옵션을 확인해야 한다.

### `Optional`

```java
Optional<Post>
```

결과가 있을 수도 없을 수도 있음을 타입으로 표현한다.

```java
repository.findById(id)
    .orElseThrow(() -> new DataNullException("No_Post"));
```

값이 있으면 Post를 반환하고, 없으면 lambda로 만든 예외를 던진다.

### Collection과 for-each

```java
List<Comment> comments = ...;

for (Comment comment : comments) {
    ...
}
```

`List<Comment>`는 순서 있는 댓글 모음이다. for-each는 목록 원소를 하나씩 `comment` 변수에 넣어 블록을 반복한다.

### `@Transactional`

메서드가 정상 종료되면 commit하고 처리되지 않은 RuntimeException이 밖으로 나가면 rollback한다. 여러 Repository 변경이 하나의 업무 단위로 함께 성공하거나 함께 취소된다.

`readOnly = true`는 조회 의도를 알리고 구현체가 최적화할 기회를 준다. 쓰기 작업이 있는 메서드는 일반 Transaction을 사용한다.

### Dirty checking

Transaction 안에서 JPA가 관리 중인 Entity의 필드를 변경하면 별도 `save` 호출이 없어도 commit 시 변경을 감지해 UPDATE할 수 있다.

### Optimistic lock

`@Version` 필드를 UPDATE 조건에 포함하여 읽었던 version이 그대로인 경우에만 수정한다. 충돌 가능성이 낮다고 보고 DB row를 미리 잠그지 않는다.

### Pessimistic lock

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

조회할 때 DB row lock을 획득한다. 다른 Transaction은 lock 해제까지 기다리므로 동시 변경을 직렬화할 수 있다.

### `@MapsId`

자식 Entity의 foreign key를 자신의 primary key로도 사용하는 shared primary key mapping이다. 이 프로젝트에서는 `PostCounter.post_id`와 `PostViewCount.post_id`가 각각 연결된 `Post.post_id`와 같은 값이다.

### `@Modifying`

Repository의 `@Query`가 SELECT가 아니라 UPDATE·DELETE 같은 변경 query임을 표시한다. 반환 `int`는 영향받은 row 수이며, 이 프로젝트의 `validateCounterUpdate()`는 정확히 1인지 검사한다. Bulk update는 persistence context를 거치지 않고 DB를 직접 바꾸므로 자동 flush·clear option의 의미도 함께 확인해야 한다.

### `saveAndFlush`

`save`는 영속 상태로 만들지만 SQL이 commit까지 미뤄질 수 있다. `saveAndFlush`는 즉시 flush하여 unique 제약 위반 같은 DB 오류를 현재 코드 위치에서 확인하게 한다.

### `Objects.equals`

```java
Objects.equals(a, b)
```

한쪽이 null이어도 NullPointerException 없이 두 객체의 동등성을 비교한다.

### `Math.max`

두 숫자 중 큰 값을 반환한다. 여기서는 마이그레이션 전후 조회수 중 더 안전한 기준값을 선택한다.

## 6.11 이해 확인

1. `@PathVariable`과 `@AuthenticationPrincipal`은 각각 어디에서 값을 얻는가?
2. 상세 조회가 읽기만 하는 것처럼 보여도 일반 Transaction인 이유는 무엇인가?
3. `EntityGraph`가 해결하려는 문제는 무엇인가?
4. soft delete된 게시글을 제외하는 조건은 어디에 있는가?
5. 낙관적 락의 version은 어떤 동시 수정 문제를 막는가?
6. 비관적 락은 다른 Transaction에 어떤 영향을 주는가?
7. Like 행 저장과 카운터 증가가 한 Transaction이어야 하는 이유는 무엇인가?
8. 신고에서 작성자 User를 잠그는 이유는 무엇인가?
9. `validatePostVersion()`과 Entity의 `@Version`은 각각 어느 시점의 충돌을 막는가?
10. `OPTIMISTIC_FORCE_INCREMENT`가 필요한 수정 상황은 무엇인가?
11. `@MapsId`가 적용된 PostCounter의 `post_id`는 어떤 두 역할을 동시에 하는가?
12. 같은 게시글에 동시에 들어온 중복 신고가 `Already_Reported` 검사까지 순서대로 처리되는 이유는 무엇인가?

## 6.12 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.
