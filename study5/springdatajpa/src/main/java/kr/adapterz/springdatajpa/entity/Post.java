package kr.adapterz.springdatajpa.entity;

import java.time.*;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@Entity
@Table(name="posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="post_id")
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "post_title", nullable = false, length = 26)
    private String postTitle;

    @Column(name = "post_content", nullable = false)
    private String postContent;
    @Lob
    @Column(name = "image_file", columnDefinition = "TEXT")
    private String imageFile;
    @Column(name = "is_fixed", nullable = false)
    private boolean isFixed;

    @Column(name ="report_count", nullable = false)
    private int reportCount;

    @Column(name ="like_count", nullable = false)
    private int likeCount;

    @Column(name ="reply_count", nullable = false)
    private int replyCount;

    @Column(name ="view_count", nullable = false)
    private int viewCount;

    @Column(name ="created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name ="deleted", nullable = false)
    private boolean deleted;

    //초기값 설정
    public Post(User user, String postTitle, String postContent, String imageFile)
    {
        this.user=user;
        this.postTitle=postTitle;
        this.postContent=postContent;
        this.imageFile=imageFile;

        isFixed=false;
        reportCount=0;
        likeCount=0;
        replyCount=0;
        viewCount=0;
        createdAt = LocalDateTime.now();
        deleted=false;
    }

    //이미지가 없는 경우
    public Post(User user, String postTitle, String postContent)
    {
        this.user=user;
        this.postTitle=postTitle;
        this.postContent=postContent;
        this.imageFile=null;

        isFixed=false;
        reportCount=0;
        likeCount=0;
        replyCount=0;
        viewCount=0;
        createdAt = LocalDateTime.now();
        deleted=false;
    }


    //게시물 수정
    public void update(String title, String contents, String imageFile) {
        this.postTitle = title;
        this.postContent = contents;
        this.imageFile = imageFile;
        isFixed=true;
    }

    //신고 기능
    public void report(){
        reportCount++;
    }
    //댓글 추가
    public void addReply(){
        replyCount++;
    }
    //댓글 삭제
    public void deleteReply(){
        replyCount--;
    }

    //좋아요
    public void like(){
        likeCount++;
    }

    //좋아요 취소
    public void likeCancle(){
        likeCount--;
    }

    //조회수 기능
    public void view(){
        viewCount++;
    }

    //삭제
    public void delete(){deleted=true;}
}
