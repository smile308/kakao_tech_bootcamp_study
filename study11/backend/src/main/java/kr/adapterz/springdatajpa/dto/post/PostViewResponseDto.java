package kr.adapterz.springdatajpa.dto.post;

import kr.adapterz.springdatajpa.dto.comment.CommentResponseDto;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.PostImage;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class PostViewResponseDto {

    private Long postId;
    private boolean isFixed;
    private String title;
    private String content;
    private List<String> imageUrls;
    private int likeCount;
    private int reportCount;
    private int commentCount;
    private int viewCount;
    private LocalDateTime createdAt;

    private Boolean isMine;
    private Boolean isReported;
    private Boolean isLiked;

    private List<CommentResponseDto> comments;

    public PostViewResponseDto(
            Post post,
            List<CommentResponseDto> comments,
            Boolean isLiked,
            Boolean isReported,
            Boolean isMine
    ) {
        this.postId = post.getPostId();
        this.isFixed = post.isFixed();
        this.title = post.getPostTitle();
        this.content = post.getPostContent();
        this.imageUrls = getImageUrls(post);
        this.likeCount = post.getLikeCount();
        this.reportCount = post.getReportCount();
        this.commentCount = post.getReplyCount();
        this.viewCount = post.getViewCount();
        this.createdAt = post.getCreatedAt();

        this.isMine = isMine;
        this.isLiked = isLiked;
        this.isReported = isReported;

        this.comments = comments;
    }

    private List<String> getImageUrls(Post post) {
        List<String> result = new ArrayList<>();

        for (PostImage postImage : post.getPostImages()) {
            result.add(postImage.getImageFile());
        }

        return result;
    }
}
