package kr.adapterz.springdatajpa.dto.post;

import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class PostListResponseDto {
    private Long postId;
    private String title;
    private int likeCount;
    private int reportCount;
    private int commentCount;
    private int viewCount;
    private LocalDateTime createdAt;
    private String authorNickname;
    private String authorProfileImage;

    public PostListResponseDto(Post post, User user) {
        this.postId = post.getPostId();
        this.title = post.getPostTitle();
        this.likeCount = post.getLikeCount();
        this.reportCount = post.getReportCount();
        this.commentCount = post.getReplyCount();
        this.viewCount = post.getViewCount();
        this.createdAt = post.getCreatedAt();
        this.authorNickname = user.getNickname();
        this.authorProfileImage = user.getProfileImage();
    }
}
