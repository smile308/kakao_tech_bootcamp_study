package kr.adapterz.springdatajpa.entity;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@Entity
@Table(name="posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class Post {
    public static final int REPORT_BLOCK_THRESHOLD = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="post_id")
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "post_title", nullable = false, length = 26)
    private String postTitle;

    @Column(name = "post_content", nullable = false)
    private String postContent;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("imageOrder ASC")
    private List<PostImage> postImages = new ArrayList<>();

    @Column(name = "is_fixed", nullable = false)
    private boolean isFixed;

    @Column(name ="like_count", nullable = false)
    private int likeCount;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name ="reply_count", nullable = false)
    private int replyCount;

    @Column(name ="view_count", nullable = false)
    private int viewCount;

    @Column(name ="created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name ="deleted", nullable = false)
    private boolean deleted;

    //초기값 설정
    public Post(User user, String postTitle, String postContent, String imageFile)
    {
        this(user, postTitle, postContent);
        replaceImages(imageFile);
    }

    //이미지가 없는 경우
    public Post(User user, String postTitle, String postContent)
    {
        this.user=user;
        this.postTitle=postTitle;
        this.postContent=postContent;

        isFixed=false;
        likeCount=0;
        reportCount=0;
        replyCount=0;
        viewCount=0;
        createdAt = LocalDateTime.now();
        deleted=false;
    }


    //게시물 수정
    public void update(String title, String contents, String imageFile) {
        this.postTitle = title;
        this.postContent = contents;
        replaceImages(imageFile);
        isFixed=true;
    }

    //게시물 수정
    public void update(String title, String contents, List<String> imageFiles) {
        this.postTitle = title;
        this.postContent = contents;
        replaceImages(imageFiles);
        isFixed=true;
    }

    //기존 imageFile 이름을 유지하기 위한 대표 이미지 조회
    public String getImageFile() {
        if (postImages == null || postImages.isEmpty()) {
            return null;
        }

        return postImages.get(0).getImageFile();
    }

    //이미지 1개 교체
    public void replaceImages(String imageFile) {
        postImages.clear();

        if (imageFile == null || imageFile.isBlank()) {
            return;
        }

        addImage(imageFile, 0);
    }

    //이미지 여러 개 교체
    public void replaceImages(List<String> imageFiles) {
        postImages.clear();

        if (imageFiles == null) {
            return;
        }

        for (int i = 0; i < imageFiles.size(); i++) {
            String imageFile = imageFiles.get(i);

            if (imageFile == null || imageFile.isBlank()) {
                continue;
            }

            addImage(imageFile, i);
        }
    }

    //이미지 추가
    public void addImage(String imageFile, int imageOrder) {
        PostImage postImage = new PostImage(this, imageFile, imageOrder);
        postImages.add(postImage);
    }

    //댓글 추가
    public void addReply(){
        replyCount++;
    }
    //댓글 삭제
    public void deleteReply(){
        replyCount--;
    }

    //좋아요
    public void like(){
        likeCount++;
    }

    //좋아요 취소
    public void likeCancle(){
        likeCount--;
    }

    //조회수 기능
    public void view(){
        viewCount++;
    }

    //삭제
    public void delete(){deleted=true;}

    //신고
    public void report() {
        reportCount++;
    }

    public boolean isBlockedByReports() {
        return reportCount >= REPORT_BLOCK_THRESHOLD;
    }
}
