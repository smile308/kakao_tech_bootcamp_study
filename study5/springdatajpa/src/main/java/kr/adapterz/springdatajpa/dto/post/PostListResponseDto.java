package kr.adapterz.springdatajpa.dto.post;

import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class PostListResponseDto {
    private Long post_id;
    private int report_count;
    private String title;
    private int like_count;
    private int reply_count;
    private int view_count;
    private LocalDateTime created_at;
    private String user_name;
    private String user_profile_image;
    public PostListResponseDto(Post post, User user){
        this.post_id=post.getPostId();
        this.report_count=post.getReportCount();
        this.title=post.getPostTitle();
        this.like_count=post.getLikeCount();
        this.reply_count=post.getReplyCount();
        this.view_count=post.getViewCount();
        this.created_at=post.getCreatedAt();
        this.user_name=user.getNickname();
        this.user_profile_image=user.getProfileImage();
    }

}
