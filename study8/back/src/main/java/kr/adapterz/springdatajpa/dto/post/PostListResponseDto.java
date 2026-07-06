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
    private int reportCount;
    private String title;
    private int likeCount;
    private int replyCount;
    private int viewCount;
    private LocalDateTime createdAt;
    private String userName;
    private String userProfileImage;
    public PostListResponseDto(Post post, User user){
        this.postId=post.getPostId();
        this.title=post.getPostTitle();
        this.likeCount=post.getLikeCount();
        this.replyCount=post.getReplyCount();
        this.viewCount=post.getViewCount();
        this.createdAt=post.getCreatedAt();
        this.userName=user.getNickname();
        this.userProfileImage=user.getProfileImage();
    }

}
