package kr.adapterz.springdatajpa.dto.post;

import kr.adapterz.springdatajpa.dto.comment.CommentResponseDto;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PostViewResponseDto {
    private Long post_id;
    private boolean is_fixed;
    private String post_title;
    private String post_content;
    private String image_file;
    private int like_count;
    private int report_count;
    private int reply_count;
    private int view_count;
    private String date;
    private String time;
    private String user_name;
    private String user_profile_image;

    //댓글들 리스트
    private List<CommentResponseDto> comments;
    public PostViewResponseDto(Post post, User user, List<CommentResponseDto> comments){
        this.post_id=post.getPost_id();
        this.is_fixed=post.is_fixed();
        this.post_title=post.getPost_title();
        this.post_content=post.getPost_content();
        this.image_file=post.getImage_file();
        this.like_count=post.getLike_count();
        this.report_count=post.getReport_count();
        this.reply_count=post.getReply_count();
        this.view_count=post.getView_count();
        this.date=post.getDate();
        this.time=post.getTime();
        this.user_name=user.getNickname();
        this.user_profile_image=user.getProfile_image();
        this.comments=comments;
    }
}
