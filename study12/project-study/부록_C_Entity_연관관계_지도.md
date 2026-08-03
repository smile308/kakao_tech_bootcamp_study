# 부록 C. Entity 연관관계 지도

> 6장과 9장에 흩어져 있는 7개 Entity 사이의 FK·Cascade·fetch 전략을 한 장에 모은다. 부록 F의 `Post` 매핑 옵션 설명과 짝을 이룬다.

## C.1 학습 목표

```text
7개 Entity 사이의 관계를 다이어그램으로 본다
→ 어느 Entity가 owner(외래키 보유)인지 구분한다
→ fetch 전략이 LAZY인 곳과 EAGER인 곳을 본다
→ CascadeType과 orphanRemoval이 어디에 적용됐는지 본다
→ Repository method가 @EntityGraph로 무엇을 함께 끌고 오는지 본다
```

## C.2 ER 다이어그램 (텍스트)

```text
┌─────────────────────┐
│       User          │
│─────────────────────│
│ user_id (PK)        │
│ email               │
│ password (nullable) │
│ nickname            │
│ profile_image       │
│ received_report_cnt │
│ deleted             │
│ auth_version        │
└──────────┬──────────┘
           │ 1
           │
           │ N
┌──────────┴──────────┐         ┌─────────────────────┐
│       Post          │ 1     1 │   PostCounter       │
│─────────────────────│◄────────│─────────────────────│
│ post_id (PK)        │         │ post_id (PK, FK)    │
│ version (@Version)  │         │ like_count          │
│ user_id (FK)        │         │ report_count        │
│ post_title          │         │ reply_count         │
│ post_content        │         │ view_count          │
│ is_fixed            │         └─────────────────────┘
│ created_at          │
│ deleted             │         ┌─────────────────────┐
└─┬─────┬─────┬───────┘  1   1 │  PostViewCount      │
  │     │     │         ◄──────│─────────────────────│
  │ 1   │ 1   │ 1              │ post_id (PK, FK)    │
  │     │     │                │ view_count (long)   │
  │     │     │                └─────────────────────┘
  │ N   │ N   │ N
  ▼     ▼     ▼
┌────────────────┐ ┌────────────────┐ ┌──────────────────┐
│   PostImage    │ │   Comment      │ │      Like        │
│────────────────│ │────────────────│ │──────────────────│
│ post_image_id  │ │ comment_id (PK)│ │ post_like_id (PK)│
│ post_id (FK)   │ │ user_id (FK)   │ │ post_id (FK)     │
│ image_file     │ │ post_id (FK)   │ │ user_id (FK)     │
│ image_order    │ │ comment_content│ │ UK(post,user)    │
│ (LONGTEXT)     │ │ created_at     │ └──────────────────┘
└────────────────┘ └────────────────┘ ┌──────────────────┐
                                        │   PostReport     │
                                        │──────────────────│
                                        │ post_report_id   │
                                        │ post_id (FK)     │
                                        │ user_id (FK)     │
                                        │ created_at       │
                                        │ UK(post,user)    │
                                        └──────────────────┘

┌─────────────────────┐
│    AuthSession      │ N ── 1 User
│─────────────────────│
│ auth_session_id(PK) │
│ user_id (FK)        │
│ refresh_token_hash  │ (UNIQUE, length=64)
│ refresh_expires_at  │
│ created_at          │
│ revoked_at          │
│ idx(user_id)        │
│ idx(refresh_expires)│
└─────────────────────┘
```

## C.3 owner / mappedBy 정리

JPA 연관관계에서 **외래키를 가진 쪽이 owner**, 반대편은 `mappedBy`로 지정한다.

| 관계 | Owner (FK 보유) | mappedBy (비-owner) | 양방향? |
|---|---|---|---|
| `Post.user` | `Post.user_id` | 없음 (단방향 ManyToOne) | 아니오 |
| `Post.postImages` | `PostImage.post_id` | `Post.postImages` | 예 |
| `Post.postCounter` | `PostCounter.post_id` (MapsId) | `Post.postCounter` | 예 |
| `Post.postViewCount` | `PostViewCount.post_id` (MapsId) | `Post.postViewCount` | 예 |
| `Comment.user` | `Comment.user_id` | 없음 (단방향) | 아니오 |
| `Comment.post` | `Comment.post_id` | 없음 (단방향) | 아니오 |
| `Like.post` | `Like.post_id` | 없음 (단방향) | 아니오 |
| `Like.user` | `Like.user_id` | 없음 (단방향) | 아니오 |
| `PostReport.post` | `PostReport.post_id` | 없음 (단방향) | 아니오 |
| `PostReport.user` | `PostReport.user_id` | 없음 (단방향) | 아니오 |
| `AuthSession.user` | `AuthSession.user_id` | 없음 (단방향) | 아니오 |

`Post`가 부모인 연관관계 3개(`postImages`, `postCounter`, `postViewCount`)만 양방향이다. 나머지 자식 Entity는 Post를 참조만 하고 Post는 자식 컬렉션을 가지지 않는다 (단, PostImage와 PostCounter/PostViewCount는 양방향).

## C.4 `Post` 안의 양방향 연관관계 3종

### `postImages` (OneToMany)

```java
@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("imageOrder ASC")
private List<PostImage> postImages = new ArrayList<>();
```

- `mappedBy = "post"`: `PostImage.post`가 owner. Post 쪽은 비-owner.
- `CascadeType.ALL`: Post 저장/삭제 시 PostImage도 함께 저장/삭제.
- `orphanRemoval = true`: Post에서 빠진 PostImage는 자동으로 삭제. `postImages.clear()`만 해도 DB에서 삭제됨.
- `@OrderBy("imageOrder ASC")`: 항상 imageOrder 오름차순으로 정렬해 가져옴.

### `postCounter` (OneToOne)

```java
@OneToOne(
        mappedBy = "post",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        optional = false
)
private PostCounter postCounter;
```

- `optional = false`: Post는 PostCounter 없이 존재할 수 없다. inner join 강제.
- `CascadeType.ALL + orphanRemoval = true`: Post 생성 시 PostCounter도 함께 생성.

### `postViewCount` (OneToOne)

`postCounter`와 같은 구조. `PostViewCount`는 Redis로 이전된 후 현재 거의 사용되지 않지만 호환을 위해 유지.

## C.5 `MapsId` 패턴

`PostCounter` / `PostViewCount`는 post_id를 **자신의 PK로도 사용**한다.

```java
@Id
@Column(name = "post_id")
private Long postId;

@MapsId
@OneToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "post_id")
private Post post;
```

이걸 `@MapsId` 패턴이라 한다. 의미는:

```text
post_id 컬럼이 PostCounter의 PK이면서 동시에 Post를 가리키는 FK
→ PostCounter의 PK를 따로 만들지 않고 Post의 PK를 그대로 빌려 쓴다
→ Post와 PostCounter가 1:1로 강하게 묶인다
```

`Post.postCounter = new PostCounter(this)`로 Post 생성 시 PostCounter를 함께 만들면, 둘이 같은 post_id를 공유한다.

## C.6 fetch 전략

| 연관관계 | fetch | 이유 |
|---|---|---|
| `Post.user` | `LAZY` | 작성자 닉네임은 상세 조회 시점에 필요 |
| `Post.postImages` | `LAZY` (OneToMany 기본) | 목록 조회에서는 이미지가 불필요 |
| `Post.postCounter` | `LAZY` | 목록에서는 카운트만 필요할 때 별도 쿼리로 가져옴 |
| `Post.postViewCount` | `LAZY` | 동일 |
| `Comment.user` | `LAZY` | 댓글 목록 표시 시점에 닉네임 필요 |
| `Comment.post` | `LAZY` | 댓글에서 게시글 정보는 거의 안 봄 |
| `Like.post`, `Like.user` | `LAZY` | Like는 ID만으로 카운트 변경 |
| `PostReport.post`, `PostReport.user` | `LAZY` | 동일 |
| `AuthSession.user` | `LAZY` | 회전/만료 검사 시 user_id만 사용 |

모든 연관관계가 `LAZY`다. EAGER가 단 하나도 없다. 이건 의도적이다:

```text
EAGER의 문제
→ JPQL 작성 시 N+1을 예측하기 어렵다
→ 의도하지 않은 join이 발생한다
→ 성능 이슈가 늦게 드러난다

LAZY + @EntityGraph
→ 명시적으로 "이 쿼리에서는 같이 가져와" 라고 지정
→ 그 외에는 select 직전에 접근할 때만 추가 쿼리
```

## C.7 `@EntityGraph`로 함께 가져오기

`PostRepository.findByPostIdAndDeletedFalse`:

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
Optional<Post> findByPostIdAndDeletedFalse(@Param("postId") Long postId);
```

이 쿼리는 Post를 1번 가져오면서 user/postImages/postCounter/postViewCount를 **fetch join 없이** 함께 가져온다. `@EntityGraph`는 내부적으로 `left join fetch`로 변환하지만, 컬렉션(`postImages`)은 `Set`이나 `List` size=1이 아니면 MultipleBagFetchException이 날 수 있어 JPA가 in-memory distinct로 풀어낸다.

`PostService.getPost`는 이 메서드를 호출해 단일 쿼리에 가까운 비용으로 상세 정보를 구성한다.

## C.8 CascadeType 정리

| Cascade 옵션 | Post → 자식에 적용? | 의미 |
|---|---|---|
| `PERSIST` | yes | Post 저장 시 PostImage도 저장 |
| `MERGE` | yes | Post merge 시 자식도 merge |
| `REMOVE` | yes | Post 삭제 시 자식도 삭제 |
| `REFRESH` | yes | Post refresh 시 자식도 DB에서 다시 읽음 |
| `DETACH` | yes | Post detach 시 자식도 detach |
| `ALL` | yes | 위 5개 모두 |

Cascade는 **부모의 lifecycle 이벤트를 자식에게 전파**한다. 단순 조회(Lazy loading)와는 무관하다.

### `orphanRemoval = true` 와의 차이

```text
CascadeType.REMOVE
→ 부모 Entity 자체가 삭제될 때 자식도 함께 삭제

orphanRemoval = true
→ 부모가 컬렉션에서 자식 참조를 제거할 때 (postImages.remove(child)) DB에서도 삭제
```

둘은 비슷하지만 시점이 다르다. `orphanRemoval`이 켜진 컬렉션은 컬렉션 조작만으로 자식을 정리할 수 있어 `PostImage`처럼 "게시글 수정 = 이미지 전체 교체" 패턴에 잘 맞는다.

## C.9 Unique 제약과 동시성

| Entity | Unique 제약 | 효과 |
|---|---|---|
| `Like` | `(post_id, user_id)` | 같은 사용자가 같은 게시글에 두 번 좋아요 불가 |
| `PostReport` | `(post_id, user_id)` | 같은 사용자가 같은 게시글에 두 번 신고 불가 |
| `AuthSession` | `refresh_token_hash` | 같은 Refresh Token hash가 두 row에 존재 불가 |
| `User` | (없음) | 같은 email 중복은 application code(`existsByEmailAndDeletedFalse`)로 검사 |

DB unique는 **마지막 방어선**이다. application code 검사를 통과한 후 DB insert 직전에 두 요청이 동시에 들어와도 unique 위배로 한쪽이 실패한다. 이게 `LikeService.likePost`가 `DataIntegrityViolationException`을 처리하는 이유다.

## C.10 `Soft Delete` 패턴

`User.deleted`, `Post.deleted` boolean 컬럼으로 soft delete를 표현한다. `DELETE FROM users` 같은 hard delete는 사용하지 않는다.

```text
장점
→ 같은 email로 재가입 가능 (이전 user row의 soft delete + 새 user row 생성)
→ deleted=false 조건으로 "활성 사용자"만 조회 가능
→ 신고 누적 계정의 이력 추적 가능 (deleted row의 receivedReportCount 유지)

단점
→ 조회 쿼리에 항상 deleted=false 조건 필요
→ DB row 수가 계속 증가
```

이 패턴이 가능하려면 `User.email`이 unique가 아니어야 한다. 실제 코드를 보면:

```java
@Column(nullable = false)
private String email;
```

`@Column`에는 `unique = true`가 없다. 이메일 중복 검사는 Service layer에서 `existsByEmailAndDeletedFalse`로만 수행한다. 같은 email을 가진 deleted=true row가 여러 개 존재할 수 있다.

## C.11 이해 확인

1. `Post.postImages`는 `CascadeType.ALL`과 `orphanRemoval = true`를 둘 다 가진다. 두 옵션이 동시에 발동하는 시나리오를 각각 하나씩 만들어 보라.
2. `PostCounter`가 `Long postId` 필드와 `Post post` 필드를 둘 다 가지는 이유는 무엇인가?
3. `Comment`는 양방향 매핑 없이 단방향 ManyToOne으로만 Post를 참조한다. 이 설계가 가져오는 한 가지 한계는 무엇인가?
4. `Like`와 `PostReport`에 `uniqueConstraints`가 필요한 이유를 두 가지 이상의 동시성 시나리오로 설명하라.
5. `User.email` 컬럼에 `unique = true`가 없는 이유는 무엇인가? 그러면 email 중복은 어떻게 막는가?
6. `PostRepository.findByPostIdAndDeletedFalse`에 `@EntityGraph`가 필요한 이유를 N+1 시나리오로 설명하라.
