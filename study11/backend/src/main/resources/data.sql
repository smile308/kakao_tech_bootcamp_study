INSERT INTO users
(user_id, email, password, nickname, profile_image, received_report_count, deleted)
VALUES
    (1, 'test@example.com', '$2a$10$44njE6/sAzptDjUjNFaG5.60QwzzVBijbgYr6v9IeFxoav7cWbRj6', '더미작성자1', NULL, 0,false),
    (2, 'second@example.com', '$2a$10$44njE6/sAzptDjUjNFaG5.60QwzzVBijbgYr6v9IeFxoav7cWbRj6', '두번째작성자', NULL, 4,false),
    (3, 'admin@example.com', '$2a$10$44njE6/sAzptDjUjNFaG5.60QwzzVBijbgYr6v9IeFxoav7cWbRj6', '관리자', NULL, 0,false),
    (4, 'suspended@example.com', '$2a$10$44njE6/sAzptDjUjNFaG5.60QwzzVBijbgYr6v9IeFxoav7cWbRj6', '정지계정', NULL, 10,false),
    (5, 'reporter1@example.com', '$2a$10$44njE6/sAzptDjUjNFaG5.60QwzzVBijbgYr6v9IeFxoav7cWbRj6', '신고테스터1', NULL, 0,false),
    (6, 'reporter2@example.com', '$2a$10$44njE6/sAzptDjUjNFaG5.60QwzzVBijbgYr6v9IeFxoav7cWbRj6', '신고테스터2', NULL, 0,false),
    (7, 'reporter3@example.com', '$2a$10$44njE6/sAzptDjUjNFaG5.60QwzzVBijbgYr6v9IeFxoav7cWbRj6', '신고테스터3', NULL, 0,false);

INSERT INTO posts
(post_id, user_id, post_title, post_content, is_fixed, created_at, deleted, version)
VALUES
    (1, 1, '더미 게시글 1', '게시글 상세조회 테스트용 본문입니다.', false, TIMESTAMP '2026-06-26 10:00:00', false, 0),
    (2, 1, '더미 게시글 2', '좋아요와 조회수 k 단위 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:01:00', false, 0),
    (3, 2, '더미 게시글 3', '10000 이상 숫자 표기 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:02:00', false, 0),
    (4, 2, '더미 게시글 4', '100000 이상 숫자 표기 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:03:00', false, 0),
    (5, 1, '더미 게시글 5', '인피니티 스크롤 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:04:00', false, 0),
    (6, 1, '더미 게시글 6', '인피니티 스크롤 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:05:00', false, 0),
    (7, 2, '더미 게시글 7', '인피니티 스크롤 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:06:00', false, 0),
    (8, 2, '더미 게시글 8', '인피니티 스크롤 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:07:00', false, 0),
    (9, 1, '더미 게시글 9', '인피니티 스크롤 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:08:00', false, 0),
    (10, 1, '더미 게시글 10', '첫 화면 10개 조회 확인용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:09:00', false, 0),
    (11, 2, '더미 게시글 11', '추가 조회 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:10:00', false, 0),
    (12, 2, '더미 게시글 12', '추가 조회 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:11:00', false, 0),
    (13, 1, '더미 게시글 13', '추가 조회 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:12:00', false, 0),
    (14, 1, '더미 게시글 14', '추가 조회 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:13:00', false, 0),
    (15, 2, '더미 게시글 15', '추가 조회 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:14:00', false, 0),
    (16, 2, '더미 게시글 16', '추가 조회 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:15:00', false, 0),
    (17, 1, '더미 게시글 17', '추가 조회 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:16:00', false, 0),
    (18, 1, '더미 게시글 18', '추가 조회 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:17:00', false, 0),
    (19, 2, '더미 게시글 19', '추가 조회 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:18:00', false, 0),
    (20, 2, '더미 게시글 20', '두 번째 페이지 마지막 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:19:00', false, 0),
    (21, 1, '더미 게시글 21', '세 번째 페이지 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:20:00', false, 0),
    (22, 1, '더미 게시글 22', '세 번째 페이지 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:21:00', false, 0),
    (23, 2, '더미 게시글 23', '세 번째 페이지 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:22:00', false, 0),
    (24, 2, '더미 게시글 24', '세 번째 페이지 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:23:00', false, 0),
    (25, 1, '더미 게시글 25', '세 번째 페이지 테스트용 게시글입니다.', false, TIMESTAMP '2026-06-26 10:24:00', false, 0),
    (26, 2, '신고 4회 게시글', 'test@example.com 계정으로 5번째 신고를 확인하는 예시입니다.', false, TIMESTAMP '2026-06-26 10:25:00', false, 0);

INSERT INTO post_counters
(post_id, like_count, report_count, reply_count, view_count)
VALUES
    (1, 999, 0, 2, 999),
    (2, 1000, 0, 1, 1000),
    (3, 10000, 0, 0, 10000),
    (4, 100000, 0, 0, 100000),
    (5, 350, 0, 0, 1200),
    (6, 700, 0, 0, 2400),
    (7, 1050, 0, 0, 3600),
    (8, 1400, 0, 0, 4800),
    (9, 1750, 0, 0, 6000),
    (10, 2100, 0, 0, 7200),
    (11, 2450, 0, 0, 8400),
    (12, 2800, 0, 0, 9600),
    (13, 3150, 0, 0, 10800),
    (14, 3500, 0, 0, 12000),
    (15, 3850, 0, 0, 13200),
    (16, 4200, 0, 0, 14400),
    (17, 4550, 0, 0, 15600),
    (18, 4900, 0, 0, 16800),
    (19, 5250, 0, 0, 18000),
    (20, 5600, 0, 0, 19200),
    (21, 5950, 0, 0, 20400),
    (22, 6300, 0, 0, 21600),
    (23, 6650, 0, 0, 22800),
    (24, 7000, 0, 0, 24000),
    (25, 7350, 0, 0, 25200),
    (26, 0, 4, 0, 0);

INSERT INTO comments
(comment_id, user_id, post_id, comment_content, created_at)
VALUES
    (1, 1, 1, '첫 번째 댓글입니다.', TIMESTAMP '2026-06-26 10:10:00'),
    (2, 2, 1, '다른 사용자의 댓글입니다.', TIMESTAMP '2026-06-26 10:11:00'),
    (3, 1, 2, '두 번째 게시글 댓글입니다.', TIMESTAMP '2026-06-26 10:12:00');

INSERT INTO post_reports
(post_report_id, post_id, user_id, created_at)
VALUES
    (1, 26, 3, TIMESTAMP '2026-06-26 10:26:00'),
    (2, 26, 5, TIMESTAMP '2026-06-26 10:27:00'),
    (3, 26, 6, TIMESTAMP '2026-06-26 10:28:00'),
    (4, 26, 7, TIMESTAMP '2026-06-26 10:29:00');

ALTER TABLE users ALTER COLUMN user_id RESTART WITH 100;
ALTER TABLE posts ALTER COLUMN post_id RESTART WITH 100;
ALTER TABLE comments ALTER COLUMN comment_id RESTART WITH 100;
ALTER TABLE post_reports ALTER COLUMN post_report_id RESTART WITH 100;
