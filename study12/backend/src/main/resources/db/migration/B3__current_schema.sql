CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    nickname VARCHAR(10) NOT NULL,
    profile_image LONGTEXT,
    received_report_count INT NOT NULL,
    deleted BIT NOT NULL,
    auth_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB;

CREATE TABLE posts (
    post_id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    post_title VARCHAR(26) NOT NULL,
    post_content VARCHAR(255) NOT NULL,
    is_fixed BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    deleted BIT NOT NULL,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_posts_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE TABLE auth_sessions (
    auth_session_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    refresh_expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    PRIMARY KEY (auth_session_id),
    CONSTRAINT uk_auth_sessions_refresh_token_hash
        UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE INDEX idx_auth_sessions_user_id
    ON auth_sessions (user_id);

CREATE INDEX idx_auth_sessions_refresh_expires_at
    ON auth_sessions (refresh_expires_at);

CREATE TABLE comments (
    comment_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    comment_content VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (comment_id),
    CONSTRAINT fk_comments_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_comments_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;

CREATE TABLE post_counters (
    post_id BIGINT NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    report_count INT NOT NULL DEFAULT 0,
    reply_count INT NOT NULL DEFAULT 0,
    view_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_counters_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;

CREATE TABLE post_images (
    post_image_id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    image_file LONGTEXT NOT NULL,
    image_order INT NOT NULL,
    PRIMARY KEY (post_image_id),
    CONSTRAINT fk_post_images_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;

CREATE TABLE post_likes (
    post_like_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (post_like_id),
    CONSTRAINT uk_post_like_post_user
        UNIQUE (post_id, user_id),
    CONSTRAINT fk_post_likes_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id),
    CONSTRAINT fk_post_likes_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE TABLE post_likes_seq (
    next_val BIGINT
) ENGINE=InnoDB;

INSERT INTO post_likes_seq (next_val) VALUES (1);

CREATE TABLE post_reports (
    post_report_id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_report_id),
    CONSTRAINT uk_post_report_post_user
        UNIQUE (post_id, user_id),
    CONSTRAINT fk_post_reports_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id),
    CONSTRAINT fk_post_reports_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE TABLE post_view_counts (
    post_id BIGINT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_view_counts_post
        FOREIGN KEY (post_id) REFERENCES posts (post_id)
) ENGINE=InnoDB;
