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
    private boolean is_fixed;
    private int report_count;
    private int like_count;
    private int reply_count;
    private int view_count;
    private String date;
    private String time;
    //DB없이 초기화를 위한 생성자
    public Post( Long user_id, String post_title, String post_content)
    {
        this.post_id=null;
        this.user_id=user_id;
        this.post_title=post_title;
        this.post_content=post_content;

        is_fixed=false;
        report_count=0;
        like_count=0;
        reply_count=0;
        view_count=0;
        date= String.valueOf(LocalDate.now());
        time=String.valueOf(LocalTime.now());
    }

    //초기값 설정
    public Post(Long post_id, Long user_id, String post_title, String post_content)
    {
        this.post_id=post_id;
        this.user_id=user_id;
        this.post_title=post_title;
        this.post_content=post_content;

        is_fixed=false;
        report_count=0;
        like_count=0;
        reply_count=0;
        view_count=0;
        date= String.valueOf(LocalDate.now());
        time=String.valueOf(LocalTime.now());
    }

    public void changeTitle(String post_title){
        this.post_title=post_title;
    }

    public void changeContent(String post_content){
        this.post_content=post_content;
    }

    public void fixed(){
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
