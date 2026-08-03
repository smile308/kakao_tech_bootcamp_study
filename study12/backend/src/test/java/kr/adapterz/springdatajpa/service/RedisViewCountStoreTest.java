package kr.adapterz.springdatajpa.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import kr.adapterz.springdatajpa.config.ViewCountProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisViewCountStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisViewCountStore store;

    @BeforeEach
    void setUp() {
        ViewCountProperties properties = new ViewCountProperties(
                true,
                "bamboo:{post-view}:count:",
                "bamboo:{post-view}:dirty",
                "bamboo:{post-view}:flush-lock",
                Duration.ofSeconds(5)
        );
        store = new RedisViewCountStore(redisTemplate, properties);
    }

    @Test
    void DB_기준값으로_초기화하고_조회수를_증가시킨다() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(
                        "bamboo:{post-view}:count:42",
                        "bamboo:{post-view}:dirty"
                )),
                eq("100"),
                eq("42")
        )).thenReturn(101L);

        long updatedViewCount = store.increment(42L, 100L);

        assertThat(updatedViewCount).isEqualTo(101L);
        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(
                        "bamboo:{post-view}:count:42",
                        "bamboo:{post-view}:dirty"
                )),
                eq("100"),
                eq("42")
        );
    }

    @Test
    void DB_기준값은_음수일_수_없다() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.increment(42L, -1L))
                .withMessage("baselineViewCount must not be negative");

        verifyNoInteractions(redisTemplate);
    }

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

    @Test
    void 조회수_키가_없으면_dirty_표시를_제거한다() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(
                        "bamboo:{post-view}:count:42",
                        "bamboo:{post-view}:dirty"
                )),
                eq("42")
        )).thenReturn(1L);

        boolean removed = store.removeDirtyIfCountMissing(42L);

        assertThat(removed).isTrue();
    }
}
