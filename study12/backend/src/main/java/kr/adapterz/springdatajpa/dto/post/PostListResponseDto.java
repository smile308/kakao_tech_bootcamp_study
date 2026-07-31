package kr.adapterz.springdatajpa.dto.post;

import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.PostCounter;
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
    private long viewCount;
    private LocalDateTime createdAt;

    public PostListResponseDto(Post post, PostCounter counter) {
        this.postId = post.getPostId();
        this.title = post.getPostTitle();
        this.likeCount = counter.getLikeCount();
        this.reportCount = counter.getReportCount();
        this.commentCount = counter.getReplyCount();
        this.viewCount = post.getPostViewCount().getViewCount();
        this.createdAt = post.getCreatedAt();
    }
}
