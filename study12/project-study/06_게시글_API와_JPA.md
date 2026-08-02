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

## 6.3 실제 코드 발췌: Controller

```java
@GetMapping("/{postId}")
public PostViewResponseDto getPostView(
        @PathVariable("postId") Long postId,
        @AuthenticationPrincipal CustomUserDetails userDetails
) {
    return postService.getPostView(
            postId,
            userDetails.getUserId()
    );
}

@PatchMapping("/{postId}")
public PostFixResponseDto fixPost(
        @PathVariable("postId") Long postId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody PostFixRequestDto request
) {
    return postService.fixPost(
            postId,
            userDetails.getUserId(),
            request
    );
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
public PostViewResponseDto getPostView(
        Long postId,
        Long loginUserId
) {
    Post post = getViewablePost(postId);
    User loginUser = getLoginUser(loginUserId);

    List<Comment> comments =
            commentRepository.findByPostWithUser(post);

    List<CommentResponseDto> commentResponseDtos =
            new ArrayList<>();

    for (Comment comment : comments) {
        boolean isMyComment =
                comment.getUser().getUserId()
                        .equals(loginUserId);

        CommentResponseDto commentResponseDto =
                new CommentResponseDto(
                        comment,
                        comment.getUser(),
                        isMyComment
                );

        commentResponseDtos.add(commentResponseDto);
    }

    boolean isLiked =
            likeRepository.existsByPostAndUser(post, loginUser);

    boolean isReported =
            postReportRepository.existsByPostAndUser(
                    post,
                    loginUser
            );

    boolean isMine =
            post.getUser().getUserId().equals(loginUserId);

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
private void validatePostVersion(
        Post post,
        Long requestVersion
) {
    if (!Objects.equals(
            post.getVersion(),
            requestVersion
    )) {
        throw new PostVersionConflictException();
    }
}
```

```text
사용자 A와 B가 version 3 게시글을 함께 열음
→ A가 version 3으로 수정 성공
→ DB version 4
→ B가 오래된 version 3으로 수정 요청
→ 충돌 응답 409
```

`version`은 “마지막에 저장한 사람이 무조건 앞선 변경을 덮어쓰기”를 방지한다.

## 6.7 좋아요와 비관적 락

실제 코드:

```java
getPostCounterForUpdate(postId);
validateActivePostAfterCounterUpdate(postId);

likeRepository.saveAndFlush(new Like(post, user));

validateCounterUpdate(
        postCounterRepository.incrementLikeCount(postId)
);
```

비관적 락은 Transaction이 끝날 때까지 다른 Transaction의 충돌 접근을 기다리게 한다.

좋아요는 다음 두 사실이 함께 유지되어야 한다.

```text
Like 행 존재
↔ PostCounter.likeCount 증가
```

중간에 실패하면 `@Transactional`이 전체 변경을 rollback한다.

DB의 사용자·게시글 unique 제약과 `DataIntegrityViolationException` 처리는 같은 사용자의 중복 좋아요 경쟁도 막는다.

## 6.8 신고 흐름

```text
활성 게시글을 쓰기 락으로 조회
→ 신고자 조회
→ 작성자 User를 쓰기 락으로 조회
→ 자기 글 신고 금지
→ 중복 신고 금지
→ PostReport 저장
→ 게시글 신고 카운터 증가
→ 작성자 누적 신고 증가
→ 기준 도달 시 정지 상태
```

작성자 User에 락을 거는 이유는 서로 다른 게시글에서 같은 작성자를 동시에 신고해도 누적 신고 수가 유실되지 않게 하기 위해서다.

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

### 좋아요 Transaction 핵심

```java
getPostCounterForUpdate(postId); // 카운터 행에 비관적 쓰기 락을 걸어 동시 변경을 직렬화한다.
validateActivePostAfterCounterUpdate(postId); // 락을 기다리는 동안 게시글이 삭제되지 않았는지 다시 확인한다.
likeRepository.saveAndFlush(new Like(post, user)); // Like 행을 즉시 DB에 반영해 unique 중복도 이 Transaction 안에서 확인한다.
validateCounterUpdate( // 수정된 행 수가 정확한지 공통 검증한다.
        postCounterRepository.incrementLikeCount(postId) // DB update query로 like_count를 1 증가시킨다.
);
```

## 6.10 스킵할 코드

게시글 상세·수정·좋아요·신고를 이해한 뒤 다음은 차이만 확인한다.

- 댓글 CRUD: 대상 게시글 일치와 댓글 소유권 검증
- 좋아요 취소: Like 삭제 후 카운터 감소
- 게시글 삭제: soft delete와 version 검증
- 단순 응답 DTO: 화면에 전달할 필드 모양
- ImageDataUrlValidator 내부의 이미지 signature 검사: 입력 보안 장에서 별도 심화 가능


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

## 6.12 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.
