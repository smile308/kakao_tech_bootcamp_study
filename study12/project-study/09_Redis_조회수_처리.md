# 9장. Redis 조회수 처리

## 9.1 학습 목표

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

### 9.1.1 이 장의 실제 코드 읽기 순서

```text
GET /posts/{postId}
→ PostService가 RDS의 두 조회수 값 중 큰 값을 baseline으로 선택
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

## 9.2 구현체를 바꾸는 인터페이스

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

## 9.3 Redis 키 구조

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

## 9.4 실제 코드 발췌: 증가 Lua 스크립트

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

## 9.5 실제 코드 발췌: Java에서 Lua 실행

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

## 9.6 DB 기준값이 필요한 이유

Redis가 재시작되거나 키가 사라질 수 있다.

```text
RDS 조회수 100
Redis 키 없음

기준값 없이 INCR
→ 1부터 시작하여 조회수 감소

DB 기준값 100으로 SET 후 INCR
→ 101
```

`PostService`는 기존 `PostCounter`와 새 `PostViewCount` 중 큰 값을 기준으로 전달한다. 마이그레이션 중 두 저장 위치의 값이 다를 수 있기 때문이다.

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

## 9.7 실제 코드 발췌: Flush Scheduler

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

## 9.8 Snapshot 저장과 dirty 확인

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

## 9.9 실제 코드 발췌: RDS 최대값 반영

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

## 9.10 Redis AOF와 Docker volume

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

## 9.11 장애별 동작

| 장애 | 현재 동작 |
|---|---|
| Redis 증가 `DataAccessException` | DB의 마지막 영구 조회수를 응답하지만 해당 증가분은 기록하지 못함 |
| Redis가 null 반환 | DB 기준값을 응답하지만 해당 증가분은 기록하지 못함 |
| 분산 락 획득 실패 | 이번 flush 건너뜀 |
| RDS 저장 실패 | dirty 유지, 다음 주기 재시도 |
| 저장 중 조회수 증가 | snapshot 불일치로 dirty 유지 |
| snapshot count key 없음 | count key가 여전히 없을 때만 Lua로 고아 dirty ID 제거 |
| Redis 재시작 | AOF와 volume으로 복구 시도. `everysec` 특성상 최근 write 유실 가능성은 남음 |

## 9.12 핵심 축약본

```text
요청 경로:
DB 기준값 → Redis 원자적 INCR → dirty 표시

반영 경로:
분산 락 → dirty 조회 → snapshot → RDS max 저장
→ Redis가 snapshot 그대로일 때만 dirty 제거
→ count key가 사라졌다면 Lua 재확인 후 고아 dirty 제거
```


## 9.12.1 전체 원문 코드 라인별 주석본

아래 주석은 학습을 위해 추가한 것이며 실제 프로젝트 파일에는 없다. 줄 끝 주석을 붙인 주석본은 의미를 따라가기 위한 설명용이므로 그대로 복사해 실행하지 않는다.

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

## 9.13 스킵할 코드

- 로그 메시지 문구
- `OptionalLong`의 Java 문법 세부
- 락 해제 중 예외 로그의 반복 구조
- `findDirtyPostIds()`의 stream 변환과 단순 Redis GET wrapper 내부

다만 두 Lua 스크립트, 분산 락, snapshot 이후 조건부 dirty 해제는 스킵하지 않는다.


## 9.13.1 이 장에서 필요한 Redis·Lua·다형성 문법

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

## 9.14 이해 확인

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

## 9.15 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.
