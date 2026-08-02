# 8장. Redis 조회수 처리

## 8.1 학습 목표

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

## 8.2 구현체를 바꾸는 인터페이스

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

## 8.3 Redis 키 구조

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

## 8.4 실제 코드 발췌: 증가 Lua 스크립트

```lua
local count_type = redis.call('TYPE', KEYS[1]).ok
local dirty_type = redis.call('TYPE', KEYS[2]).ok

if count_type ~= 'none' and count_type ~= 'string' then
    return redis.error_reply(
        'view count key must be a string'
    )
end

if dirty_type ~= 'none' and dirty_type ~= 'set' then
    return redis.error_reply(
        'dirty key must be a set'
    )
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

## 8.5 실제 코드 발췌: Java에서 Lua 실행

```java
Long updatedViewCount = redisTemplate.execute(
        INCREMENT_AND_MARK_DIRTY_SCRIPT,
        List.of(
                properties.countKey(postId),
                properties.dirtySetKey()
        ),
        Long.toString(baselineViewCount),
        Long.toString(postId)
);
```

```text
KEYS[1] → 게시글 조회수 key
KEYS[2] → dirty set key
ARGV[1] → DB 기준 조회수
ARGV[2] → 게시글 ID
```

Redis가 값을 반환하지 않거나 `DataAccessException`이 발생하면 DB 기준값을 응답한다. 조회 요청 전체를 실패시키지 않는 가용성 우선 fallback이다.

## 8.6 DB 기준값이 필요한 이유

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

## 8.7 실제 코드 발췌: Flush Scheduler

```java
@Scheduled(
        initialDelayString =
                "${app.view-count.flush-interval}",
        fixedDelayString =
                "${app.view-count.flush-interval}"
)
public void flushDirtyViewCounts() {
    RLock lock =
            redissonClient.getLock(
                    properties.flushLockKey()
            );

    boolean acquired = false;

    try {
        acquired = lock.tryLock();

        if (!acquired) {
            return;
        }

        flushWhileHoldingLock();
    } catch (RedisException exception) {
        log.warn(
                "Redis view count flush lock failed."
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

## 8.8 Snapshot 저장과 dirty 확인

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

## 8.9 실제 코드 발췌: RDS 최대값 반영

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

Transaction이 commit된 후에만 dirty 해제가 실행되어야 한다. 테스트에서는 저장 호출과 acknowledge 호출의 순서를 검증한다.

## 8.10 Redis AOF와 Docker volume

Compose 실제 설정:

```yaml
redis:
  image: redis:7.4-alpine
  command:
    - redis-server
    - --appendonly
    - "yes"
    - --appendfsync
    - everysec
  volumes:
    - redis-data:/data
```

```text
AOF
→ Redis 변경 명령을 파일에 기록

appendfsync everysec
→ 대략 매초 디스크에 동기화

Docker volume
→ 컨테이너를 다시 만들어도 /data 보존
```

Redis가 작업 저장소라고 해서 항상 재시작 시 모든 값이 사라지도록 구성된 것은 아니다.

## 8.11 장애별 동작

| 장애 | 현재 동작 |
|---|---|
| Redis 증가 실패 | DB의 마지막 영구 조회수를 응답 |
| Redis가 null 반환 | DB 기준값 응답 |
| 분산 락 획득 실패 | 이번 flush 건너뜀 |
| RDS 저장 실패 | dirty 유지, 다음 주기 재시도 |
| 저장 중 조회수 증가 | snapshot 불일치로 dirty 유지 |
| Redis 재시작 | AOF와 volume으로 복구 시도 |

## 8.12 핵심 축약본

```text
요청 경로:
DB 기준값 → Redis 원자적 INCR → dirty 표시

반영 경로:
분산 락 → dirty 조회 → snapshot → RDS max 저장
→ Redis가 snapshot 그대로일 때만 dirty 제거
```


## 8.12.1 전체 원문 코드 라인별 주석본

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
Long updatedViewCount = redisTemplate.execute( // Redis 서버에 미리 만든 Lua script 실행을 요청한다.
        INCREMENT_AND_MARK_DIRTY_SCRIPT, // 증가와 dirty 표시가 함께 있는 script 객체다.
        List.of( // Lua의 KEYS 배열로 전달할 Redis key 목록이다.
                properties.countKey(postId), // KEYS[1]인 게시글별 조회수 key다.
                properties.dirtySetKey() // KEYS[2]인 공통 dirty set key다.
        ),
        Long.toString(baselineViewCount), // ARGV[1]로 전달할 DB 기준 조회수다.
        Long.toString(postId) // ARGV[2]로 dirty set에 넣을 게시글 ID다.
);
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

### Redis Compose 영속성

```yaml
redis:                         # Redis 서비스 정의를 시작한다.
  image: redis:7.4-alpine      # 가벼운 Redis 7.4 Alpine 이미지를 사용한다.
  command:                     # 기본 Redis 실행 명령에 AOF 옵션을 추가한다.
    - redis-server             # Redis 서버 프로세스를 시작한다.
    - --appendonly             # AOF 기록 기능의 옵션 이름이다.
    - "yes"                    # AOF를 활성화한다.
    - --appendfsync            # AOF 내용을 디스크와 동기화할 주기 옵션이다.
    - everysec                 # 최대 대략 1초 단위로 fsync한다.
  volumes:                     # 컨테이너 밖에 보존할 저장 경로를 연결한다.
    - redis-data:/data         # Redis /data를 이름 있는 Docker volume에 저장한다.
```

## 8.13 스킵할 코드

- 로그 메시지 문구
- `OptionalLong`의 Java 문법 세부
- 락 해제 중 예외 로그의 반복 구조
- DB fallback 구현의 단순 조회 부분

다만 두 Lua 스크립트, 분산 락, snapshot 이후 조건부 dirty 해제는 스킵하지 않는다.


## 8.13.1 이 장에서 필요한 Redis·Lua·다형성 문법

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

## 8.14 이해 확인

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

## 8.15 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.
