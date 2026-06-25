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
    private Long commentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "comment_content", nullable = false)
    private String commentContent;

    @Column(name = "origin_id", nullable = true)
    private Long originId;

    //대댓글이 아닌 경우
    public Comment(User user, Post post, String commentContent)
    {
        this.user=user;
        this.post=post;
        this.commentContent=commentContent;
        originId=null;
    }

    //대댓글인 경우(현재 미구현)
    public Comment(User user,Post post, String commentContent, Long originId)
    {
        this.user=user;
        this.post=post;
        this.commentContent=commentContent;
        this.originId=originId;
    }

    //댓글 수정
    public void changeComment(String commentContent){
        this.commentContent=commentContent;
    }
}
