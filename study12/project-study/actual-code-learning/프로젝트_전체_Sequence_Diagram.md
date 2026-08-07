# 프로젝트 전체 실행 Sequence Diagram

이 문서는 actual-code-learning의 각 학습 문서를 읽기 전에 프로젝트 전체 실행 관계를
한눈에 확인하기 위한 지도입니다. 각 diagram은 현재 canonical backend·frontend source와
배포 파일에서 확인한 주요 호출 관계를 표현합니다.

이 문서는 코드 설명을 대체하지 않습니다. 실제 method의 매개변수·반환값·예외·상태 변경은
각 기능별 학습 문서에서 원문과 함께 확인합니다.

기준 저장소:

- 백엔드: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back`
- 프론트엔드: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front`
- 상세 학습 자료: `/Users/miles/Documents/GitHub/kakao_tech_bootcamp_study/study12/project-study/actual-code-learning`

---

## 1. 애플리케이션 시작

```mermaid
sequenceDiagram
    participant JVM
    participant App as SpringdatajpaApplication
    participant Context as Spring ApplicationContext
    participant YAML as Profile·YAML
    participant Config as Security/Web/Interceptor Config
    participant Beans as Controller·Service·Repository
    participant Scheduler as Scheduled Tasks

    JVM->>App: main(args)
    App->>Context: SpringApplication.run(...)
    Context->>YAML: profile·property 병합
    Context->>App: @SpringBootApplication component scan
    Context->>Config: @Configuration·@Bean 생성
    Context->>Beans: @Controller·@Service·@Component·Repository 생성
    Context->>Scheduler: @EnableScheduling 등록
    Scheduler-->>Context: AuthSession·Redis scheduler 준비
    Context-->>App: HTTP 요청 대기 상태
```

---

## 2. 프론트 진입과 라우팅

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Browser
    participant Main as main.jsx
    participant Router as BrowserRouter·AppRoutes
    participant Guard as Protected/PublicOnlyRoute
    participant Boundary as PageBoundary
    participant Page as Page Component

    User->>Browser: URL 접속
    Browser->>Main: index.html이 main.jsx 실행
    Main->>Router: App·BrowserRouter 렌더링
    Router->>Guard: 현재 route guard 실행
    alt 보호된 경로
        Guard->>Browser: localStorage Access Token 존재 확인
        Guard->>Boundary: 통과 시 자식 Route 렌더링
    else 공개 전용 경로
        Guard->>Browser: localStorage Access Token 존재 확인
        Guard->>Boundary: 비로그인 상태면 자식 Route 렌더링
    end
    Boundary->>Page: lazy page 로딩·Suspense·ErrorBoundary
    Page-->>Browser: 화면 렌더링
```

Route guard의 token 확인은 token의 서명·만료 검증이 아닙니다. 실제 인증은 API 요청이
backend Security Filter를 통과할 때 수행됩니다.

---

## 3. 일반 API 요청

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Page
    participant API as feature API·api.js
    participant Proxy as Nginx·API Proxy
    participant Security as SecurityFilterChain
    participant Filter as JwtAuthenticationFilter
    participant Interceptor as MVC Interceptor
    participant Controller
    participant Service
    participant Repo as Repository
    participant DB as MySQL·H2

    User->>Page: 목록·상세·작성 동작
    Page->>API: featureApi.method(...)
    API->>API: Access Token·credentials 준비
    API->>Proxy: HTTP request
    Proxy->>Security: backend path 전달
    Security->>Filter: JWT 검증 또는 공개 경로 확인
    Filter->>Security: SecurityContext Authentication 저장
    Security->>Interceptor: MVC preHandle
    Interceptor->>Controller: 요청 계속
    Controller->>Service: DTO·path·userId 전달
    Service->>Repo: 조회·저장·수정 query
    Repo->>DB: SQL·JPA 실행
    DB-->>Repo: Entity·Page·row count
    Repo-->>Service: 결과 반환
    Service-->>Controller: Response DTO
    Controller-->>API: JSON·status·header
    API-->>Page: data 또는 Error
    Page-->>User: state 변경·화면 갱신
```

---

## 4. 인증 요청

상세 인증 흐름은 `04-08_인증_통합_실행흐름.md`의 diagram을 기준으로 읽습니다.

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Page
    participant AuthAPI as authApi·api.js
    participant Security as Security
    participant Session as SessionController
    participant Service as SessionService
    participant UserRepo as UserRepository
    participant SessionRepo as AuthSessionRepository
    participant Browser as localStorage·Cookie

    User->>Page: 로그인 제출
    Page->>AuthAPI: login(email, password)
    AuthAPI->>Security: POST /sessions
    Security->>Session: 공개 session endpoint 통과
    Session->>Service: createSession(request)
    Service->>UserRepo: email로 User 조회·password 검증
    Service->>SessionRepo: Refresh hash 저장
    Service-->>Session: Access·Refresh 결과 DTO
    Session-->>Browser: JSON Access Token + Set-Cookie
    Browser-->>Page: Access Token 저장
```

---

## 5. 게시글 Entity·DTO·Repository 흐름

```mermaid
sequenceDiagram
    actor User as 사용자
    participant Page
    participant API as postApi
    participant Controller as PostController
    participant Service as PostService
    participant DTO as Request·Response DTO
    participant Entity as Post·Counter·Image
    participant Repo as PostRepository
    participant DB as Database

    User->>Page: 게시글 작성 또는 목록 요청
    Page->>API: createPost 또는 getPosts
    API->>Controller: HTTP request
    Controller->>DTO: JSON binding·validation
    DTO-->>Controller: typed input
    Controller->>Service: DTO·page·userId 전달
    alt 게시글 작성
        Service->>Entity: Post 생성
        Entity->>Entity: PostCounter·PostViewCount·PostImage 연결
        Service->>Repo: save(post)
    else 게시글 목록
        Service->>Repo: Page query(Pageable)
        Repo-->>Service: Page<Post>
    end
    Repo->>DB: JPA query 또는 INSERT
    DB-->>Repo: 결과
    Repo-->>Service: 저장·조회 결과
    Service->>DTO: Response DTO·Factory 생성
    DTO-->>Controller: response object
    Controller-->>API: JSON response
    API-->>Page: normalized data
```

---

## 6. 댓글·좋아요·신고 상호작용

```mermaid
sequenceDiagram
    actor User as 로그인 사용자
    participant Page as 상세 Page
    participant API as postApi
    participant Controller as Post/CommentController
    participant Service as Post/CommentService
    participant Repo as 관련 Repository
    participant Counter as PostCounterRepository
    participant DB as Database

    User->>Page: 댓글·좋아요·신고 클릭
    Page->>API: endpoint method 호출
    API->>Controller: path·body·Authorization
    Controller->>Service: userId·postId·DTO 전달
    Service->>Repo: Post·User·Comment·Like·Report 조회
    Repo->>DB: 소유권·존재·중복 query
    DB-->>Repo: Entity·Optional·boolean
    alt 댓글 작성
        Service->>Counter: replyCount 증가
        Service->>Repo: Comment save
    else 댓글 수정
        Service->>Repo: Comment 조회
        Service->>Repo: managed Entity content 변경
    else 댓글 삭제
        Service->>Counter: replyCount 감소
        Service->>Repo: Comment delete
    else 좋아요
        Service->>Repo: Like save/delete
        Service->>Counter: likeCount bulk update
    else 신고
        Service->>Repo: PostReport save
        Service->>Counter: reportCount 변경
    end
    DB-->>Service: transaction 결과
    Service-->>Controller: Response DTO
    Controller-->>API: JSON response
    API-->>Page: 필요한 state만 갱신
```

---

## 7. Redis 조회수와 DB 반영

Redis 상세 흐름은 `98_Redis_조회수_처리.md`에 보관되어 있습니다.

```mermaid
sequenceDiagram
    participant Detail as PostService.getPostView
    participant Updater as ViewCountUpdater
    participant Redis as RedisViewCountStore
    participant Lua as Redis Lua Script
    participant Scheduler as Flush Scheduler
    participant Lock as Redisson Lock
    participant Persist as ViewCountPersistenceService
    participant Repo as PostViewCountRepository
    participant DB as post_view_counts

    Detail->>Updater: increment(postId, baseline)
    Updater->>Redis: count key·dirty key·baseline
    Redis->>Lua: KEYS·ARGV 전달
    Lua->>Lua: baseline 보정·INCR·SADD
    Lua-->>Redis: updated count
    Redis-->>Updater: viewCount
    Updater-->>Detail: response용 viewCount

    Scheduler->>Lock: tryLock()
    alt lock 획득
        Scheduler->>Redis: dirty postId·snapshot 조회
        Scheduler->>Persist: persist(postId, snapshot)
        Persist->>Repo: persistMaxViewCount
        Repo->>DB: GREATEST update
        DB-->>Repo: row count
        Repo-->>Persist: 저장 결과
        Persist-->>Scheduler: transaction 완료
        Scheduler->>Redis: 현재 count와 snapshot 비교
        alt 값이 같음
            Redis-->>Scheduler: dirty 제거
        else 값이 증가함
            Redis-->>Scheduler: dirty 유지
        end
    else lock 획득 실패
        Scheduler-->>Scheduler: 이번 주기 건너뜀
    end
```

---

## 8. Flyway·RDS 시작 흐름

```mermaid
sequenceDiagram
    participant App as Spring Boot
    participant Profile as application-prod.yaml
    participant Flyway
    participant History as flyway_schema_history
    participant MySQL as RDS MySQL
    participant Hibernate

    App->>Profile: prod property 해석
    App->>Flyway: migration 위치·옵션 전달
    Flyway->>History: 현재 적용 version 확인
    Flyway->>MySQL: B3·V1·V2·V3·V4 순서 적용
    MySQL-->>History: migration version 기록
    Flyway-->>App: migration 완료
    App->>Hibernate: ddl-auto=validate
    Hibernate->>MySQL: Entity와 schema 일치 여부 확인
    MySQL-->>Hibernate: validation 결과
    Hibernate-->>App: 애플리케이션 시작 계속
```

실제 production DB·migration 실행 결과는 이 저장소의 정적 diagram만으로 확인하지
않았으므로, 운영 상태로 단정하지 않습니다.

---

## 9. 테스트 실행 흐름

```mermaid
sequenceDiagram
    participant Developer as 개발자·CI
    participant Gradle
    participant Test as JUnit Test
    participant Spring as Spring Test Context
    participant H2
    participant Redis as Redis Container
    participant MySQL as MySQL Container
    participant Assert as Assertion

    Developer->>Gradle: ./gradlew test
    Gradle->>Test: 일반·Mockito·H2 테스트 선택
    Test->>Spring: 필요한 Context 로딩
    Spring->>H2: local/test DB 사용
    H2-->>Test: query 결과
    Test->>Assert: 상태·반환값 검증

    Developer->>Gradle: ./gradlew redisTest
    Gradle->>Redis: Testcontainers Redis 시작
    Gradle->>Test: redis-integration 선택
    Test->>Redis: Lua·lock·AOF 동작 검증
    Redis-->>Assert: 결과

    Developer->>Gradle: ./gradlew mysqlTest
    Gradle->>MySQL: Testcontainers MySQL 시작
    Gradle->>Test: mysql-integration 선택
    Test->>MySQL: Flyway·validate·lock 검증
    MySQL-->>Assert: 결과
```

현재 frontend에는 별도 browser 자동화 test runner가 없습니다.

---

## 10. CI/CD와 Blue-Green 배포

```mermaid
sequenceDiagram
    actor Developer as 개발자
    participant GitHub
    participant Actions as GitHub Actions
    participant Build as Gradle·npm
    participant Registry as GHCR
    participant AWS as OIDC·SSM
    participant Server as 배포 서버
    participant Compose
    participant Nginx

    Developer->>GitHub: push 또는 pull request
    GitHub->>Actions: workflow 실행
    Actions->>Build: backend clean check
    Actions->>Build: frontend npm ci·lint·build
    Build-->>Actions: test·build 결과
    Actions->>Registry: Docker image push
    opt DEPLOY_ENABLED=true
        Actions->>AWS: OIDC role·SSM command
        AWS->>Server: deploy script 실행
        Server->>Compose: inactive blue/green container 시작
        Compose-->>Server: health 확인
        Server->>Nginx: active upstream 전환
        Nginx-->>Server: 외부 health 확인
        Server-->>AWS: deploy 결과
        AWS-->>Actions: SSM 상태 반환
    end
```

---

## 11. 전체 프로젝트 요청 요약

```text
브라우저
→ React route·Page state
→ feature API·공통 request
→ frontend Nginx·backend Nginx
→ Security Filter
→ MVC Interceptor
→ Controller
→ Service transaction
→ Repository·Entity
→ H2·MySQL·Redis
→ DTO·Jackson·HTTP response
→ React state·화면
```

배포 변경은 별도 흐름입니다.

```text
GitHub push
→ GitHub Actions
→ test·lint·build
→ Docker image
→ GHCR
→ AWS OIDC·SSM
→ Compose
→ health check
→ Nginx blue-green 전환
```

---

## 진행 상태

- 이 보조 다이어그램은 새 파일을 집계하지 않습니다. 현재 파일별 진행률은 `00_실제코드_파일목록과_학습지도.md`의 표를 기준으로 합니다.
- 이번 문서에서 새 source 파일을 추가로 학습하지 않음
- 프로젝트 전체 sequence diagram 작성: 완료
- 다음 학습 시작점: `actual-code-learning/10_게시글_댓글_DTO_Repository_실제흐름.md`의 checkpoint 이후
- 테스트·실제 배포·Redis/MySQL runtime 결과: 실행하지 않음
