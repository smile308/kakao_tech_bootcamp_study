CREATE TABLE IF NOT EXISTS post_view_counts (
    post_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_view_counts_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;

INSERT INTO post_view_counts (
    post_id,
    view_count
)
SELECT
    legacy_counter.post_id,
    legacy_counter.view_count
FROM post_counters legacy_counter
LEFT JOIN post_view_counts view_counter
    ON view_counter.post_id = legacy_counter.post_id
WHERE view_counter.post_id IS NULL;

UPDATE post_view_counts view_counter
INNER JOIN post_counters legacy_counter
    ON legacy_counter.post_id = view_counter.post_id
SET view_counter.view_count = GREATEST(
    view_counter.view_count,
    legacy_counter.view_count
);
