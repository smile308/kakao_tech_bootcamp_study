package kr.adapterz.springdatajpa.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Comment {
    private final Long comment_id;
    private final Long user_id;
    private final Long post_id;
    private String comment_content;
    private final Long origin_id;

    //id없이 먼저 처리하고 후에 DB에서 id를 넣어주기 위한 생성자
    public Comment(Long user_id, Long post_id, String comment_content)
    {
        this.comment_id=null;
        this.user_id=user_id;
        this.post_id=post_id;
        this.comment_content=comment_content;
        origin_id=null;
    }

    //대댓글 기능 현재 미구현
    public Comment( Long user_id,Long post_id, String comment_content, Long origin_id)
    {
        this.comment_id=null;
        this.user_id=user_id;
        this.post_id=post_id;
        this.comment_content=comment_content;
        this.origin_id=origin_id;
    }

    //대댓글이 아닌 경우
    public Comment(Long comment_id, Long user_id, Long post_id, String comment_content)
    {
        this.comment_id=comment_id;
        this.user_id=user_id;
        this.post_id=post_id;
        this.comment_content=comment_content;
        origin_id=null;
    }

    //대댓글인 경우(현재 미구현)
    public Comment(Long comment_id, Long user_id,Long post_id, String comment_content, Long origin_id)
    {
        this.comment_id=comment_id;
        this.user_id=user_id;
        this.post_id=post_id;
        this.comment_content=comment_content;
        this.origin_id=origin_id;
    }

    //댓글 수정
    public void changeComment(String comment_content){
        this.comment_content=comment_content;
    }
}
