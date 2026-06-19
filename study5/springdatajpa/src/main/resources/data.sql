INSERT INTO users (user_id, email, password, nickname, profile_image)
VALUES
    (1, 'test1@test.com', 'Password1!', 'tester1', 'profile1.png'),
    (2, 'test2@test.com', 'Password2!', 'tester2', 'profile2.png');

INSERT INTO posts (
    post_id, user_id, post_title, post_content, image_file,
    is_fixed, report_count, like_count, reply_count, view_count, date, time
)
VALUES
    (1, 1, '첫 번째 게시글', '첫 번째 게시글 내용입니다.', 'image.png1',
     false, 0, 0, 2, 0, '2026-06-16', '10:00:00'),
    (2, 2, '두 번째 게시글', '두 번째 게시글 내용입니다.', 'image.png2',
     false, 0, 0, 1, 0, '2026-06-16', '10:00:00');

INSERT INTO comments (comment_id, user_id, post_id, comment_content, origin_id)
VALUES
    (1, 1, 1, '첫 번째 게시글의 첫 번째 댓글입니다.', null),
    (2, 1, 2, '두 번째 게시글의 첫 번째 댓글입니다.', null),
    (3, 2, 1, '첫 번째 게시글의 두 번째 댓글입니다.', null);