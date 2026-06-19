package kr.adapterz.springdatajpa.dto.post;

import kr.adapterz.springdatajpa.dto.comment.CommentResponseDto;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
    private LocalDateTime created_at;
    private String user_name;
    private String user_profile_image;

    //댓글들 리스트
    private List<CommentResponseDto> comments;
    public PostViewResponseDto(Post post, User user, List<CommentResponseDto> comments){
        this.post_id=post.getPostId();
        this.is_fixed=post.is_fixed();
        this.post_title=post.getPostTitle();
        this.post_content=post.getPostContent();
        this.image_file=post.getImageFile();
        this.like_count=post.getLikeCount();
        this.report_count=post.getReportCount();
        this.reply_count=post.getReplyCount();
        this.view_count=post.getViewCount();
        this.created_at=post.getCreatedAt();
        this.user_name=user.getNickname();
        this.user_profile_image=user.getProfileImage();
        this.comments=comments;
    }
}
