INSERT INTO users
(user_id, email, password, nickname, profile_image, received_report_count, deleted)
VALUES
    (1, 'test@example.com', '$2a$10$44njE6/sAzptDjUjNFaG5.60QwzzVBijbgYr6v9IeFxoav7cWbRj6', '더미작성자1', NULL, 0,false),
    (2, 'second@example.com', '$2a$10$44njE6/sAzptDjUjNFaG5.60QwzzVBijbgYr6v9IeFxoav7cWbRj6', '두번째작성자', NULL, 0,false),
    (3, 'admin@example.com', '$2a$10$44njE6/sAzptDjUjNFaG5.60QwzzVBijbgYr6v9IeFxoav7cWbRj6', '관리자', NULL, 0,false),
    (4, 'suspended@example.com', '$2a$10$44njE6/sAzptDjUjNFaG5.60QwzzVBijbgYr6v9IeFxoav7cWbRj6', '정지계정', NULL, 10,false);

INSERT INTO posts
(post_id, user_id, post_title, post_content, is_fixed, like_count, report_count, reply_count, view_count, created_at, deleted)
VALUES
    (1, 1, '더미 게시글 1', '게시글 상세조회 테스트용 본문입니다.', false, 999, 0,2, 999, TIMESTAMP '2026-06-26 10:00:00', false),
    (2, 1, '더미 게시글 2', '좋아요와 조회수 k 단위 테스트용 게시글입니다.', false, 1000, 0,1, 1000, TIMESTAMP '2026-06-26 10:01:00', false),
    (3, 2, '더미 게시글 3', '10000 이상 숫자 표기 테스트용 게시글입니다.', false, 10000, 0,0, 10000, TIMESTAMP '2026-06-26 10:02:00', false),
    (4, 2, '더미 게시글 4', '100000 이상 숫자 표기 테스트용 게시글입니다.', false, 100000, 0,0, 100000, TIMESTAMP '2026-06-26 10:03:00', false),
    (5, 1, '더미 게시글 5', '인피니티 스크롤 테스트용 게시글입니다.', false, 350, 0,0, 1200, TIMESTAMP '2026-06-26 10:04:00', false),
    (6, 1, '더미 게시글 6', '인피니티 스크롤 테스트용 게시글입니다.', false, 700, 0,0, 2400, TIMESTAMP '2026-06-26 10:05:00', false),
    (7, 2, '더미 게시글 7', '인피니티 스크롤 테스트용 게시글입니다.', false, 1050, 0,0, 3600, TIMESTAMP '2026-06-26 10:06:00', false),
    (8, 2, '더미 게시글 8', '인피니티 스크롤 테스트용 게시글입니다.', false, 1400, 0,0, 4800, TIMESTAMP '2026-06-26 10:07:00', false),
    (9, 1, '더미 게시글 9', '인피니티 스크롤 테스트용 게시글입니다.', false,  1750, 0,0, 6000, TIMESTAMP '2026-06-26 10:08:00', false),
    (10, 1, '더미 게시글 10', '첫 화면 10개 조회 확인용 게시글입니다.', false, 2100, 0,0, 7200, TIMESTAMP '2026-06-26 10:09:00', false),
    (11, 2, '더미 게시글 11', '추가 조회 테스트용 게시글입니다.', false, 2450, 0,0, 8400, TIMESTAMP '2026-06-26 10:10:00', false),
    (12, 2, '더미 게시글 12', '추가 조회 테스트용 게시글입니다.', false,  2800, 0,0, 9600, TIMESTAMP '2026-06-26 10:11:00', false),
    (13, 1, '더미 게시글 13', '추가 조회 테스트용 게시글입니다.', false,  3150, 0,0, 10800, TIMESTAMP '2026-06-26 10:12:00', false),
    (14, 1, '더미 게시글 14', '추가 조회 테스트용 게시글입니다.', false, 3500, 0,0, 12000, TIMESTAMP '2026-06-26 10:13:00', false),
    (15, 2, '더미 게시글 15', '추가 조회 테스트용 게시글입니다.', false, 3850, 0,0, 13200, TIMESTAMP '2026-06-26 10:14:00', false),
    (16, 2, '더미 게시글 16', '추가 조회 테스트용 게시글입니다.', false,  4200, 0,0, 14400, TIMESTAMP '2026-06-26 10:15:00', false),
    (17, 1, '더미 게시글 17', '추가 조회 테스트용 게시글입니다.', false,  4550, 0,0, 15600, TIMESTAMP '2026-06-26 10:16:00', false),
    (18, 1, '더미 게시글 18', '추가 조회 테스트용 게시글입니다.', false, 4900, 0,0, 16800, TIMESTAMP '2026-06-26 10:17:00', false),
    (19, 2, '더미 게시글 19', '추가 조회 테스트용 게시글입니다.', false, 5250, 0,0, 18000, TIMESTAMP '2026-06-26 10:18:00', false),
    (20, 2, '더미 게시글 20', '두 번째 페이지 마지막 테스트용 게시글입니다.', false, 5600, 0,0, 19200, TIMESTAMP '2026-06-26 10:19:00', false),
    (21, 1, '더미 게시글 21', '세 번째 페이지 테스트용 게시글입니다.', false, 5950, 0,0, 20400, TIMESTAMP '2026-06-26 10:20:00', false),
    (22, 1, '더미 게시글 22', '세 번째 페이지 테스트용 게시글입니다.', false, 6300, 0,0, 21600, TIMESTAMP '2026-06-26 10:21:00', false),
    (23, 2, '더미 게시글 23', '세 번째 페이지 테스트용 게시글입니다.', false, 6650, 0,0, 22800, TIMESTAMP '2026-06-26 10:22:00', false),
    (24, 2, '더미 게시글 24', '세 번째 페이지 테스트용 게시글입니다.', false, 7000, 0,0, 24000, TIMESTAMP '2026-06-26 10:23:00', false),
    (25, 1, '더미 게시글 25', '세 번째 페이지 테스트용 게시글입니다.', false, 7350, 0,0, 25200, TIMESTAMP '2026-06-26 10:24:00', false);

INSERT INTO comments
(comment_id, user_id, post_id, comment_content, created_at)
VALUES
    (1, 1, 1, '첫 번째 댓글입니다.', TIMESTAMP '2026-06-26 10:10:00'),
    (2, 2, 1, '다른 사용자의 댓글입니다.', TIMESTAMP '2026-06-26 10:11:00'),
    (3, 1, 2, '두 번째 게시글 댓글입니다.', TIMESTAMP '2026-06-26 10:12:00');

ALTER TABLE users ALTER COLUMN user_id RESTART WITH 100;
ALTER TABLE posts ALTER COLUMN post_id RESTART WITH 100;
ALTER TABLE comments ALTER COLUMN comment_id RESTART WITH 100;
