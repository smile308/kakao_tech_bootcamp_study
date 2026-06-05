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

    //DB없이 생성하기 위한 생성자
    public Comment(Long user_id, Long post_id, String comment_content)
    {
        this.comment_id=null;
        this.user_id=user_id;
        this.post_id=post_id;
        this.comment_content=comment_content;
        origin_id=null;
    }


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

    //대댓글인 경우
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
