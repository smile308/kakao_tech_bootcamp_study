package kr.adapterz.springdatajpa.entity;

import java.time.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Post {
    private final Long post_id;
    private final Long user_id;
    private String post_title;
    private String post_content;
    private String image_file;
    private boolean is_fixed;
    private int report_count;
    private int like_count;
    private int reply_count;
    private int view_count;
    private String date;
    private String time;
    //DB없이 초기화를 위한 생성자
    public Post( Long user_id, String post_title, String post_content, String image_file)
    {
        this.post_id=null;
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
    public Post( Long user_id, String post_title, String post_content)
    {
        this.post_id=null;
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

    public void report(){
        report_count++;
    }

    public void like(){
        like_count++;
    }

    public void likeCancle(){
        like_count--;
    }

    public void view(){
        view_count++;
    }
}
