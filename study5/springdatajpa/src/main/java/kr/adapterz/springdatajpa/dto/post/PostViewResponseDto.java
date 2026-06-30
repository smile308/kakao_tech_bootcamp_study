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
    private Long postId;
    private boolean isFixed;
    private String postTitle;
    private String postContent;
    private String imageFile;
    private int likeCount;
    private int reportCount;
    private int replyCount;
    private int viewCount;
    private LocalDateTime createdAt;
    private String userName;
    private String userProfileImage;

    //댓글들 리스트
    private List<CommentResponseDto> comments;
    public PostViewResponseDto(Post post, User user, List<CommentResponseDto> comments){
        this.postId=post.getPostId();
        this.isFixed=post.isFixed();
        this.postTitle=post.getPostTitle();
        this.postContent=post.getPostContent();
        this.imageFile=post.getImageFile();
        this.likeCount=post.getLikeCount();
        this.reportCount=post.getReportCount();
        this.replyCount=post.getReplyCount();
        this.viewCount=post.getViewCount();
        this.createdAt=post.getCreatedAt();
        this.userName=user.getNickname();
        this.userProfileImage=user.getProfileImage();
        this.comments=comments;
    }
}
