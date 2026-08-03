# 부록 F. `Post` Entity와 JPA 매핑 옵션

> 6장의 흐름 설명에서 빠져 있던 `Post` Entity의 매핑 옵션을 한 곳에 정리한다. `@Id`, `@Version`, `@OneToOne`, `@MapsId` 등 JPA 핵심 annotation의 정확한 의미를 본다.

## F.1 학습 목표

```text
@Id / @GeneratedValue의 전략별 차이
→ @Version이 만드는 낙관적 락의 원리
→ @ManyToOne의 fetch 전략
→ @OneToOne(mappedBy, optional, cascade, orphanRemoval)
→ @Lob + columnDefinition이 만드는 DB 타입
→ @OrderBy와 정렬
```

## F.2 `Post` Entity 전체 원문

```java
@Getter
@Entity
@Table(name="posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class Post {
    public static final int REPORT_BLOCK_THRESHOLD = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="post_id")
    private Long postId;

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

    @Column(name ="created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name ="deleted", nullable = false)
    private boolean deleted;

    public Post(User user, String postTitle, String postContent, String imageFile) { ... }
    public Post(User user, String postTitle, String postContent) { ... }
    public void update(String title, String contents, String imageFile) { ... }
    public void update(String title, String contents, List<String> imageFiles) { ... }
    public String getImageFile() { ... }
    public void replaceImages(String imageFile) { ... }
    public void replaceImages(List<String> imageFiles) { ... }
    public void addImage(String imageFile, int imageOrder) { ... }
    public void addReply() { postCounter.addReply(); }
    public void deleteReply() { postCounter.deleteReply(); }
    public void like() { postCounter.like(); }
    public void likeCancle() { postCounter.cancelLike(); }
    public void view() { postCounter.view(); }
    public void delete() { deleted = true; }
    public void report() { postCounter.report(); }
    public boolean isBlockedByReports() { ... }
    public int getLikeCount() { return postCounter.getLikeCount(); }
    public int getReportCount() { return postCounter.getReportCount(); }
    public int getReplyCount() { return postCounter.getReplyCount(); }
    public int getViewCount() { return postCounter.getViewCount(); }
}
```

## F.3 `@Id` 와 `@GeneratedValue`

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name="post_id")
private Long postId;
```

### `@Id`

이 필드가 Entity의 PK임을 표시한다. JPA는 이 필드로 Entity를 식별한다. `equals`/`hashCode`도 이 필드 기반으로 자동 생성된다 (Lombok `@Getter`만으로는 안 됨, 별도 `@EqualsAndHashCode` 필요).

### `@GeneratedValue(strategy = ...)` 전략 비교

| 전략 | DB 책임 | JPA 책임 | 비고 |
|---|---|---|---|
| `IDENTITY` | AUTO_INCREMENT | INSERT 후 ID 받음 | MySQL 기본 |
| `SEQUENCE` | DB 시퀀스 객체 | INSERT 전 시퀀스로부터 ID 받음 | Oracle, PostgreSQL |
| `TABLE` | 별도 키 테이블 | SELECT로 다음 ID 계산 후 INSERT | 모든 DB 호환, 가장 느림 |
| `AUTO` | DB에 맞춰 자동 선택 | DB dialect가 결정 | 기본값 |

`IDENTITY`는 INSERT 직후 DB가 AUTO_INCREMENT로 만든 ID를 반환한다. JPA는 그 ID를 받아서 영속성 컨텍스트에 저장한다.

## F.4 `@Version` (낙관적 락)

```java
@Version
@Column(name = "version", nullable = false)
private Long version;
```

### 동작 원리

```text
[1] User A가 Post를 읽음. version=3
[2] User B가 같은 Post를 읽음. version=3
[3] User A가 수정 후 commit. UPDATE posts SET ..., version=4 WHERE post_id=? AND version=3
    → affected rows = 1
[4] User B가 수정 후 commit. UPDATE posts SET ..., version=4 WHERE post_id=? AND version=3
    → affected rows = 0
    → ObjectOptimisticLockingFailureException
```

version이 WHERE 절에 포함돼 **다른 트랜잭션이 이미 version을 올렸다면** affected row가 0이 되고 JPA가 예외를 던진다.

### `@Version` 없는 객체는?

낙관적 락이 동작하지 않는다. **마지막 commit이 이긴다** (lost update). JPA는 `@Version`이 달린 필드만 WHERE 절에 자동 추가한다.

## F.5 `@ManyToOne(fetch = FetchType.LAZY)`

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

### `ManyToOne` vs `OneToMany`

```text
ManyToOne
→ "나(Post) 다수 → 너(User) 하나"
→ Post가 User를 참조 (FK 보유)
→ @JoinColumn으로 user_id 컬럼을 가짐

OneToMany
→ "나(Post) 하나 → 너(PostImage) 다수"
→ Post가 List<PostImage>를 가짐
→ @JoinColumn 없음. 자식(PostImage)이 FK를 가짐
```

### fetch 전략

```text
EAGER
→ Post를 조회할 때 User도 JOIN해서 함께 가져옴
→ 추가 쿼리 없음
→ N+1 위험 (컬렉션이면 더 위험)

LAZY
→ Post만 SELECT
→ user.getNickname()을 호출하는 시점에 User SELECT
→ 추가 쿼리 1회 발생
```

현재 프로젝트는 모든 연관관계를 LAZY로 둔다. EAGER가 필요한 경우는 거의 없다.

## F.6 `@OneToOne` (Post ↔ PostCounter)

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

### `mappedBy = "post"`

`PostCounter` 측의 다음 매핑을 가리킨다.

```java
@OneToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "post_id")
private Post post;
```

PostCounter가 FK(`post_id`)를 가진다. Post는 `mappedBy`로 비-owner가 된다. **Post 측에는 `@JoinColumn`이 없다.**

### `optional = false`

JPA 관점에서 Post는 PostCounter 없이 존재할 수 없다. `SELECT Post LEFT JOIN PostCounter ...` 대신 `JOIN`이 강제된다. `@EntityGraph`로 함께 가져올 때 inner join으로 동작한다.

### `cascade = CascadeType.ALL + orphanRemoval = true`

```text
Post 저장
→ PostCounter도 함께 저장
Post 삭제
→ PostCounter도 함께 삭제
postCounter = null; postRepository.save(post)
→ PostCounter가 DB에서 삭제 (orphan)
```

## F.7 `@MapsId` (PostCounter가 post_id를 PK로 사용)

```java
@Id
@Column(name = "post_id")
private Long postId;

@MapsId
@OneToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "post_id")
private Post post;
```

`@MapsId`는 `post_id`가 PostCounter의 PK이면서 동시에 Post의 FK임을 표시한다. PostCounter를 만들 때:

```java
Post post = new Post(...);
PostCounter counter = new PostCounter(post);
post.setPostCounter(counter);
```

`PostCounter.postId`는 Post의 `postId`와 자동으로 같은 값이 된다. 별도 `setPostId(post.getPostId())` 호출이 필요 없다.

DB에서는:

```sql
CREATE TABLE post_counters (
    post_id     BIGINT PRIMARY KEY,        -- Post의 PK를 그대로 차용
    like_count  INT NOT NULL DEFAULT 0,
    report_count INT NOT NULL DEFAULT 0,
    reply_count INT NOT NULL DEFAULT 0,
    view_count  INT NOT NULL DEFAULT 0,
    FOREIGN KEY (post_id) REFERENCES posts(post_id)
);
```

`post_id` 컬럼이 PK이면서 FK인 **1:1 강결합** 구조. Post와 PostCounter는 항상 함께 생성·삭제된다.

## F.8 `@Lob` + `columnDefinition`

```java
@Lob
@Column(name = "image_file", nullable = false, columnDefinition = "LONGTEXT")
private String imageFile;
```

### `@Lob`

DB의 LOB(Large Object) 타입을 매핑한다. JPA는 다음을 자동 매핑한다.

```text
String + @Lob
→ DB가 TEXT/BLOB/CLOB 등 large object로 저장

byte[] + @Lob
→ DB가 BLOB으로 저장
```

### `columnDefinition = "LONGTEXT"`

Hibernate의 자동 매핑을 무시하고 직접 SQL DDL을 지정한다. MySQL에서는:

```text
@Column 생략 또는 TEXT
→ MySQL: TEXT (최대 64KB)

@Lob + columnDefinition = "LONGTEXT"
→ MySQL: LONGTEXT (최대 4GB)
```

이 프로젝트는 이미지 Data URL을 DB에 직접 저장하므로 LONGTEXT가 필요하다. 3MB 이미지 × 3개 × 1.33 (base64 overhead) ≈ 12MB.

## F.9 `@OrderBy`

```java
@OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("imageOrder ASC")
private List<PostImage> postImages = new ArrayList<>();
```

`postImages`를 collection으로 가져올 때 항상 `imageOrder ASC`로 정렬한다. SQL에 `ORDER BY image_order ASC`가 자동으로 붙는다.

```text
@OrderBy 없음
→ DB가 반환한 순서 또는 HashSet/HashMap의 순서
→ 일관성 없음

@OrderBy("imageOrder ASC")
→ 매번 imageOrder 오름차순
→ 일관성 보장
```

## F.10 `@Column` 옵션

```java
@Column(name = "post_title", nullable = false, length = 26)
private String postTitle;
```

| 옵션 | 의미 |
|---|---|
| `name` | DB 컬럼명 (생략 시 필드명 그대로) |
| `nullable` | NOT NULL 여부. false면 NOT NULL |
| `length` | VARCHAR 길이. 생략 시 255 |
| `unique` | UNIQUE 제약. true면 UNIQUE |
| `updatable` | UPDATE에 포함 여부. false면 INSERT만 |
| `insertable` | INSERT에 포함 여부. false면 INSERT 제외 |
| `columnDefinition` | DDL 직접 지정 (예: LONGTEXT) |

```java
@Column(name ="created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;
```

`updatable = false`로 두면 JPA가 UPDATE SQL에 `created_at`을 포함하지 않는다. `dirty checking`이 작동해도 created_at은 절대 안 바뀐다.

## F.11 `equals` / `hashCode` 와 JPA

이 프로젝트는 `User`, `Post` 등에 `@EqualsAndHashCode`나 `equals`/`hashCode`를 직접 구현하지 않았다. **Lombok `@Getter`만으로는 `equals`/`hashCode`가 자동 생성되지 않는다.** 이건 의도적이지만 한 가지 함정이 있다.

```text
JPA는 영속성 컨텍스트에서 Entity를 Set으로 관리
→ equals/hashCode가 구현되지 않으면 같은 PK를 가진 두 인스턴스를 다른 객체로 인식
→ 같은 PK 조회 시 매번 새 객체처럼 다룰 수 있음
```

현재 코드는 `@Id` 기반 `equals`/`hashCode`를 명시적으로 작성하지 않았다. 대부분의 경우 ID만 다르면 `setPost(previous)` 패턴이 정상 동작해 큰 문제가 없다. 다만 `Set<Post>` 같은 컬렉션에 Entity를 넣을 때는 주의가 필요하다.

## F.12 정적 상수

```java
public static final int REPORT_BLOCK_THRESHOLD = 5;
```

`Post.isBlockedByReports`에서 사용:

```java
public boolean isBlockedByReports() {
    return postCounter.getReportCount() >= REPORT_BLOCK_THRESHOLD;
}
```

비즈니스 임계값을 Entity 자체에 두는 패턴. Service가 아니라 Entity가 본인의 차단 기준을 안다. **임계값 변경 시 한 곳만 고치면 된다.**

## F.13 이해 확인

1. `@Version` 필드가 WHERE 절에 자동으로 추가되는 메커니즘을 UPDATE SQL로 보여라.
2. `@MapsId`를 사용한 PostCounter가 Post와 다른 `@OneToOne` 패턴(예: User ↔ UserProfile)과 다른 점은 무엇인가?
3. `@OneToOne(optional = false)`가 JPA 쿼리에 미치는 영향을 `LEFT JOIN`과 `INNER JOIN`으로 설명하라.
4. `@Lob` + `columnDefinition = "LONGTEXT"` 조합이 `@Lob`만 단독으로 쓰는 것과 어떻게 다른가?
5. `@OrderBy`가 빠진 `OneToMany` 컬렉션이 가져오는 잠재적 버그를 한 가지 예로 들어라.
6. `updatable = false`인 `createdAt`이 JPA dirty checking에 의해 실수로 갱신되지 않는 이유를 설명하라.
