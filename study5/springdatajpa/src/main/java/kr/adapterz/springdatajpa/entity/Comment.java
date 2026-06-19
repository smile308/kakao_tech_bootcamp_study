package kr.adapterz.springdatajpa.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Getter
@Entity
@Table(name="comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="comment_id")
    private Long comment_id;

    @Column(nullable = false)
    private Long user_id;

    @Column(nullable = false)
    private Long post_id;

    @Column(nullable = false)
    private String comment_content;

    @Column(nullable = false)
    private Long origin_id;

    //대댓글이 아닌 경우
    public Comment(Long user_id, Long post_id, String comment_content)
    {
        this.user_id=user_id;
        this.post_id=post_id;
        this.comment_content=comment_content;
        origin_id=null;
    }

    //대댓글인 경우(현재 미구현)
    public Comment( Long user_id,Long post_id, String comment_content, Long origin_id)
    {
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
