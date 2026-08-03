# 부록 A. Comment API 원문과 검증 부재 이슈

> 8장에서 흐름만 다뤘던 Comment API의 실제 코드를 원문 + 라인별 주석으로 본다. 동시에 **현재 코드에 `@Valid`가 빠져 있는 의도/실수 이슈**를 별도 절로 분리해 학습 포인트로 만든다.

## A.1 학습 목표

3장과 4장에서 본 회원가입·검증 패턴이 댓글 API에는 빠져 있다. 같은 DTO 패턴을 그대로 따라야 마땅한데 그게 누락된 것은 회고할 가치가 있는 학습 포인트다.

```text
CommentController 원문
→ CommentService의 세 method
→ Comment DTO 원문
→ @Valid 부재가 만드는 보안 경계의 차이
```

## A.2 `CommentController` 실제 원문

파일: `controller/CommentController.java`

```java
@RestController
@RequestMapping("/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponseDto commentPost(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CommentPostRequestDto request
    ){
        return commentService.commentPost(postId, userDetails.getUserId(), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CommentDeleteResponseDto commentDelete(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CommentDeleteRequestDto request
    ){
        return commentService.commentDelete(postId, userDetails.getUserId(), request);
    }

    @PatchMapping
    public CommentResponseDto commentFix(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CommentFixRequestDto request
    ){
        return commentService.commentFix(postId, userDetails.getUserId(), request);
    }
}
```

### 라인별 주석본

```java
@RestController // method 반환값을 JSON 응답으로 직렬화하는 Controller로 등록한다.
@RequestMapping("/posts/{postId}/comments") // 모든 endpoint의 공통 경로를 /posts/{게시글ID}/comments로 지정한다.
@RequiredArgsConstructor // final field로 선언한 의존성을 생성자 주입으로 받는다.
public class CommentController { // 댓글 관련 HTTP 요청을 받는 Controller class다.
    private final CommentService commentService; // 댓글 업무 로직을 가진 Service를 주입받는다.

    // 댓글 작성
    @PostMapping // POST /posts/{postId}/comments 요청을 이 method에 연결한다.
    @ResponseStatus(HttpStatus.CREATED) // 성공 시 HTTP 상태를 201 Created로 고정한다.
    public CommentResponseDto commentPost(
            @PathVariable("postId") Long postId, // URL의 {postId} 값을 Long으로 받는다.
            @AuthenticationPrincipal CustomUserDetails userDetails, // Security Filter가 저장한 인증 객체를 주입받는다.
            @RequestBody CommentPostRequestDto request // 요청 JSON을 이 DTO로 역직렬화한다.
    ){
        return commentService.commentPost(postId, userDetails.getUserId(), request);
    }

    // 댓글 삭제
    @DeleteMapping // DELETE 요청을 받는다.
    @ResponseStatus(HttpStatus.NO_CONTENT) // 성공 시 204 No Content로 응답한다. body는 비어 있다.
    public CommentDeleteResponseDto commentDelete(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CommentDeleteRequestDto request
    ){
        return commentService.commentDelete(postId, userDetails.getUserId(), request);
    }

    // 댓글 수정
    @PatchMapping // PATCH 요청을 받는다. 기본 성공 상태는 200 OK.
    public CommentResponseDto commentFix(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CommentFixRequestDto request
    ){
        return commentService.commentFix(postId, userDetails.getUserId(), request);
    }
}
```

### 다른 Controller와 다른 점

| 항목 | UserController / PostController | CommentController |
|---|---|---|
| `@Valid` | `@Valid @RequestBody` | **없음** |
| 인증 annotation | `@AuthenticationPrincipal` | `@AuthenticationPrincipal` |
| DTO 검증 annotation | `@NotBlank`, `@Size`, `@Pattern` 등 | **없음** |
| DTO 검증 의존 | `MethodArgumentNotValidException` handler | **동작하지 않음** |

## A.3 Comment DTO 원문

### `CommentPostRequestDto`

```java
@Getter
@NoArgsConstructor
public class CommentPostRequestDto {
    private String commentContent;
}
```

비교 대상: 3장 `UserRequestDto`

```java
@NotBlank
@Pattern(regexp = "...")
private String password;
```

댓글 DTO에는 `@NotBlank` 한 줄도 없다. 필드도 `commentContent` 단 하나.

### `CommentFixRequestDto`

```java
@Getter
@NoArgsConstructor
public class CommentFixRequestDto {
    private Long commentId;
    private String commentContent;
}
```

`commentId`도 단순 Long. `@NotNull`, `@Positive` 같은 검사 없음.

### `CommentDeleteRequestDto`

```java
@Getter
@NoArgsConstructor
public class CommentDeleteRequestDto {
    private Long commentId;
}
```

마찬가지로 검증 annotation 없음.

## A.4 `CommentService` 전체 원문

```java
@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostCounterRepository postCounterRepository;
    private final UserRepository userRepository;

    public CommentResponseDto commentPost(Long postId, Long loginUserId, CommentPostRequestDto request){
        User user = getLoginUser(loginUserId);
        Post post = getActivePostForInteraction(postId);
            Comment comment = new Comment(
                    user,
                    post,
                    request.getCommentContent()
            );
        validateCounterUpdate(
                postCounterRepository.incrementReplyCount(postId)
        );
        validateActivePostWithLock(postId);
        commentRepository.save(comment);
        return new CommentResponseDto(comment, user, true);
    }

    public CommentResponseDto commentFix(Long postId, Long loginUserId, CommentFixRequestDto request){
        validateActivePostWithLock(postId);
        Comment comment= commentRepository.findById(request.getCommentId()).orElseThrow(()-> new DataNullException("No_Comment"));

        if (!comment.getPost().getPostId().equals(postId)) {
            throw new DataNullException("No_Comment");
        }

        if(!comment.getUser().getUserId().equals(loginUserId))
        {
            throw new ForbiddenException("Forbidden_Access");
        }
        comment.changeComment(request.getCommentContent());

        return new CommentResponseDto(comment, comment.getUser(), true);
    }

    public CommentDeleteResponseDto commentDelete(Long postId, Long loginUserId, CommentDeleteRequestDto request){
        CommentDeleteResponseDto commentDeleteResponseDto = new CommentDeleteResponseDto();
        getActivePostForInteraction(postId);
        Comment comment = commentRepository.findById(request.getCommentId()).orElseThrow(()->new DataNullException("No_Comment"));

        if (!comment.getPost().getPostId().equals(postId)) {
            throw new DataNullException("No_Comment");
        }

        if (!comment.getUser().getUserId().equals(loginUserId)) {
            throw new ForbiddenException("Forbidden_Access");
        }

        validateCounterUpdate(
                postCounterRepository.decrementReplyCount(postId)
        );
        validateActivePostWithLock(postId);
        commentRepository.delete(comment);
        return commentDeleteResponseDto;
    }

    private User getLoginUser(Long loginUserId) {
        return userRepository.findByUserIdAndDeletedFalse(loginUserId)
                .orElseThrow(() -> new DataNullException("No_Account"));
    }

    private Post getActivePostForInteraction(Long postId) {
        return postRepository.findActivePostForInteraction(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));
    }

    private void validateActivePostWithLock(Long postId) {
        postRepository.findActivePostForInteractionCheck(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));
    }

    private void validateCounterUpdate(int updatedRowCount) {
        if (updatedRowCount != 1) {
            throw new CounterUpdateException();
        }
    }
}
```

## A.5 method별 흐름

### 댓글 작성 `commentPost`

```text
로그인 사용자 조회
→ 활성 게시글 조회
→ Comment Entity 생성
→ PostCounter.replyCount +1 (UPDATE)
→ row count가 1인지 검증 (없거나 두 개였으면 CounterUpdateException)
→ 활성 게시글 재확인 (이 사이에 삭제됐을 수 있음)
→ commentRepository.save
→ CommentResponseDto 반환
```

### 댓글 수정 `commentFix`

```text
활성 게시글 조회
→ 댓글 ID로 댓글 조회 (없으면 DataNullException)
→ 댓글이 URL의 postId와 같은 게시글에 속한 댓글인지 검사
→ 작성자 본인인지 검사 (아니면 ForbiddenException)
→ comment.changeComment(content)
→ CommentResponseDto 반환
```

### 댓글 삭제 `commentDelete`

```text
활성 게시글 조회
→ 댓글 ID로 댓글 조회
→ 댓글이 URL의 postId와 같은 게시글에 속한 댓글인지 검사
→ 작성자 본인인지 검사
→ PostCounter.replyCount -1
→ 활성 게시글 재확인
→ commentRepository.delete
→ 빈 응답 DTO 반환
```

## A.6 핵심 학습 포인트: `@Valid` 부재가 만드는 차이

`@Valid`가 없는 Controller는 다음 두 가지를 보장하지 못한다.

### 1. 빈 문자열, null, 공백 문자열을 통과시킨다

```http
POST /posts/1/comments
Content-Type: application/json

{ "commentContent": "" }
```

- 게시글 Controller였다면: `@NotBlank` + `@Valid` → 400 invalid_request
- 댓글 Controller는: `@RequestBody`만 → `commentContent = ""`로 Service까지 진입 → DB에 빈 댓글 저장

`Comment` Entity도 다음 검사만 한다.

```java
@Column(name = "comment_content", nullable = false)
private String commentContent;
```

DB `NOT NULL`만 보장할 뿐 빈 문자열은 통과. 결과적으로 화면에는 진짜 빈 댓글이 보일 수 있다.

### 2. 너무 긴 댓글도 그대로 들어간다

DB schema는 `comment_content`를 `TEXT`/`LONGTEXT`로 두어 길이 제한이 사실상 없다. 3장의 `UserRequestDto`처럼 `@Size(max = 1000)` 같은 상한이 없으므로 10MB 짜리 텍스트도 들어간다.

### 3. 직접 API 호출로 검증을 우회할 수 있다

프론트엔드의 `PostEditor`는 `getPostImageFilesError`로 1차 검증을 하지만, 이건 사용성을 위한 것이다. 공격자가 curl이나 Postman으로 직접 호출하면:

```bash
curl -X POST http://server/posts/1/comments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"commentContent":""}'
```

→ 빈 댓글이 그대로 저장된다.

## A.7 학습 회고 정리

이 이슈는 **두 가지로 해석할 수 있다**.

```text
해석 1 (실수)
→ DTO 검증 annotation을 깜빡한 것
→ 향후 수정 시 UserRequestDto처럼 @NotBlank @Size를 추가해야 함

해각 2 (의도)
→ 댓글은 자유로운 자기 표현이 우선
→ 1차 검증을 프론트에만 두고 백엔드는 형식 자유
→ 단, 이 경우에도 @Size(max = ...) 정도는 있어야 DB 보호가 됨
```

현재 코드만 봐서는 어느 의도인지 단정할 수 없다. 둘 다 가능성 있는 학습 포인트이며, 새로운 기능을 만들 때 **"이 DTO는 검증을 어디서 보장하는가"** 를 항상 자문하는 습관이 필요하다.

## A.8 이 부록에서 필요한 문법 정리

### `@AuthenticationPrincipal`

```java
@AuthenticationPrincipal CustomUserDetails userDetails
```

Spring Security가 `SecurityContextHolder`에 저장한 `Authentication` 객체에서 `getPrincipal()`을 꺼낸다. `CustomUserDetails`는 `UserDetails` 구현체로 `User` Entity를 감싼 객체다. `userDetails.getUserId()`로 인증된 사용자의 ID를 얻는다.

### `@PathVariable`

```java
@PathVariable("postId") Long postId
```

URL의 `{postId}` 자리에 들어온 문자열을 `Long`으로 변환해 받는다. URL은 원래 문자열이므로 타입 변환은 Spring이 한다.

### `Optional<T>.orElseThrow`

```java
userRepository.findByUserIdAndDeletedFalse(loginUserId)
        .orElseThrow(() -> new DataNullException("No_Account"));
```

`Optional`이 비어 있을 때만 supplier가 만든 예외를 던진다. supplier는 `() -> 예외` 형태의 람다다. **호출되지 않으면 예외 객체도 만들지 않으므로** 미리 객체를 만드는 `orElseThrow(new ...)`보다 약간 효율적이다.

## A.9 이해 확인

1. `CommentController`의 세 method 중 `@Valid`가 빠진 결과로 어떤 값이 그대로 저장될 수 있는가?
2. `commentPost`가 `getActivePostForInteraction`을 두 번 호출하는 것처럼 보이는 이유를 `validateActivePostWithLock` 호출 위치로 설명하라.
3. `commentDelete`에서 `validateCounterUpdate`가 replyCount 0이 되는 시나리오를 어떻게 막는지 답하라.
4. `@Valid`가 없는 DTO에 대해 직접 API 호출로 검증을 우회할 수 있는 공격 시나리오를 한 가지 만들어 보라.
5. 현재 `Comment` Entity의 `commentContent`는 어떤 DB 제약만 보장하는가?
