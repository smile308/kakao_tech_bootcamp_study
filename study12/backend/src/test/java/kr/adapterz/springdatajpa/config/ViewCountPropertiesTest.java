package kr.adapterz.springdatajpa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ViewCountPropertiesTest {

    @Autowired
    private ViewCountProperties boundProperties;

    @Test
    void application_YAML의_조회수_설정을_객체로_변환한다() {
        assertThat(boundProperties.enabled()).isFalse();
        assertThat(boundProperties.countKey(42L))
                .isEqualTo("bamboo:{post-view}:count:42");
        assertThat(boundProperties.dirtySetKey())
                .isEqualTo("bamboo:{post-view}:dirty");
        assertThat(boundProperties.flushLockKey())
                .isEqualTo("bamboo:{post-view}:flush-lock");
        assertThat(boundProperties.flushInterval())
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void 게시글_ID를_조회수_키로_변환한다() {
        ViewCountProperties properties = properties();

        assertThat(properties.countKey(42L))
                .isEqualTo("bamboo:{post-view}:count:42");
    }

    @Test
    void 게시글_ID는_양수여야_한다() {
        ViewCountProperties properties = properties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.countKey(0L))
                .withMessage("postId must be positive");
    }

    @Test
    void MySQL_반영_주기는_양수여야_한다() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ViewCountProperties(
                        true,
                        "bamboo:{post-view}:count:",
                        "bamboo:{post-view}:dirty",
                        "bamboo:{post-view}:flush-lock",
                        Duration.ZERO
                ))
                .withMessage("flushInterval must be positive");
    }

    private ViewCountProperties properties() {
        return new ViewCountProperties(
                true,
                "bamboo:{post-view}:count:",
                "bamboo:{post-view}:dirty",
                "bamboo:{post-view}:flush-lock",
                Duration.ofSeconds(5)
        );
    }
}
