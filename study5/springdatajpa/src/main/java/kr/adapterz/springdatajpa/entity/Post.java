package kr.adapterz.springdatajpa.entity;

import java.time.*;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Getter
@Entity
@Table(name="posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="post_id")
    private Long post_id;

    @Column(nullable = false)
    private Long user_id;

    @Column(nullable = false, length = 26)
    private String post_title;

    @Column(nullable = false)
    private String post_content;

    private String image_file;
    private boolean is_fixed;
    private int report_count;
    private int like_count;
    private int reply_count;
    private int view_count;
    private String date;
    private String time;

    //초기값 설정
    public Post(Long post_id, Long user_id, String post_title, String post_content, String image_file)
    {
        this.post_id=post_id;
        this.user_id=user_id;
        this.post_title=post_title;
        this.post_content=post_content;
        this.image_file=image_file;

        is_fixed=false;
        report_count=0;
        like_count=0;
        reply_count=0;
        view_count=0;
        date= String.valueOf(LocalDate.now());
        time=String.valueOf(LocalTime.now());
    }
    //이미지가 없는 경우

    public Post(Long post_id, Long user_id, String post_title, String post_content)
    {
        this.post_id=post_id;
        this.user_id=user_id;
        this.post_title=post_title;
        this.post_content=post_content;
        this.image_file=null;

        is_fixed=false;
        report_count=0;
        like_count=0;
        reply_count=0;
        view_count=0;
        date= String.valueOf(LocalDate.now());
        time=String.valueOf(LocalTime.now());
    }
    //게시물 수정
    public void update(String title, String contents, String image_file) {
        this.post_title = title;
        this.post_content = contents;
        this.image_file = image_file;
        is_fixed=true;
    }

    //신고 기능(미구현)
    public void report(){
        report_count++;
    }
    //댓글 추가
    public void addReply(){
        reply_count++;
    }
    //댓글 삭제
    public void deleteReply(){
        reply_count--;
    }

    //좋아요
    public void like(){
        like_count++;
    }

    //좋아요 취소
    public void likeCancle(){
        like_count--;
    }

    //조회수 기능
    public void view(){
        view_count++;
    }
}
