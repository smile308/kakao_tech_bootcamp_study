SET @posts_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
);

SET @create_post_counters_sql = IF(
    @posts_exists = 1,
    'CREATE TABLE IF NOT EXISTS post_counters (
        post_id BIGINT NOT NULL,
        like_count INT NOT NULL DEFAULT 0,
        report_count INT NOT NULL DEFAULT 0,
        reply_count INT NOT NULL DEFAULT 0,
        view_count INT NOT NULL DEFAULT 0,
        PRIMARY KEY (post_id),
        CONSTRAINT fk_post_counters_post
            FOREIGN KEY (post_id) REFERENCES posts (post_id)
    ) ENGINE=InnoDB',
    'SELECT 1'
);

PREPARE create_post_counters_statement FROM @create_post_counters_sql;
EXECUTE create_post_counters_statement;
DEALLOCATE PREPARE create_post_counters_statement;

SET @legacy_counter_column_count = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
      AND column_name IN (
          'like_count',
          'report_count',
          'reply_count',
          'view_count'
      )
);

SET @backfill_post_counters_sql = IF(
    @posts_exists = 1 AND @legacy_counter_column_count = 4,
    'INSERT INTO post_counters (
        post_id,
        like_count,
        report_count,
        reply_count,
        view_count
    )
    SELECT
        post.post_id,
        post.like_count,
        post.report_count,
        post.reply_count,
        post.view_count
    FROM posts post
    LEFT JOIN post_counters counter
        ON counter.post_id = post.post_id
    WHERE counter.post_id IS NULL',
    'SELECT 1'
);

PREPARE backfill_post_counters_statement FROM @backfill_post_counters_sql;
EXECUTE backfill_post_counters_statement;
DEALLOCATE PREPARE backfill_post_counters_statement;

SET @like_count_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
      AND column_name = 'like_count'
);
SET @alter_like_count_sql = IF(
    @like_count_exists = 1,
    'ALTER TABLE posts MODIFY COLUMN like_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE alter_like_count_statement FROM @alter_like_count_sql;
EXECUTE alter_like_count_statement;
DEALLOCATE PREPARE alter_like_count_statement;

SET @report_count_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
      AND column_name = 'report_count'
);
SET @alter_report_count_sql = IF(
    @report_count_exists = 1,
    'ALTER TABLE posts MODIFY COLUMN report_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE alter_report_count_statement FROM @alter_report_count_sql;
EXECUTE alter_report_count_statement;
DEALLOCATE PREPARE alter_report_count_statement;

SET @reply_count_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
      AND column_name = 'reply_count'
);
SET @alter_reply_count_sql = IF(
    @reply_count_exists = 1,
    'ALTER TABLE posts MODIFY COLUMN reply_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE alter_reply_count_statement FROM @alter_reply_count_sql;
EXECUTE alter_reply_count_statement;
DEALLOCATE PREPARE alter_reply_count_statement;

SET @view_count_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'posts'
      AND column_name = 'view_count'
);
SET @alter_view_count_sql = IF(
    @view_count_exists = 1,
    'ALTER TABLE posts MODIFY COLUMN view_count INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE alter_view_count_statement FROM @alter_view_count_sql;
EXECUTE alter_view_count_statement;
DEALLOCATE PREPARE alter_view_count_statement;
