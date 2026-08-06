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

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    @OneToOne(
            mappedBy = "post",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            optional = false
    )
    private PostCounter postCounter;

    @OneToOne(
            mappedBy = "post",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            optional = false
    )
    private PostViewCount postViewCount;

    @Column(name ="created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name ="deleted", nullable = false)
    private boolean deleted;

    public Post(User user, String postTitle, String postContent)
    {
        this.user=user;
        this.postTitle=postTitle;
        this.postContent=postContent;

        postCounter = new PostCounter(this);
        postViewCount = new PostViewCount(this);
        createdAt = LocalDateTime.now();
        deleted=false;
    }


    public void update(String title, String contents, List<String> imageFiles) {
        this.postTitle = title;
        this.postContent = contents;
        replaceImages(imageFiles);
    }

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

    public void addImage(String imageFile, int imageOrder) {
        PostImage postImage = new PostImage(this, imageFile, imageOrder);
        postImages.add(postImage);
    }

    public void addReply(){
        postCounter.addReply();
    }
    public void deleteReply(){
        postCounter.deleteReply();
    }

    public void like(){
        postCounter.like();
    }

    public void likeCancle(){
        postCounter.cancelLike();
    }

    public void delete(){deleted=true;}

    public void report() {
        postCounter.report();
    }

    public boolean isBlockedByReports() {
        return postCounter.getReportCount() >= REPORT_BLOCK_THRESHOLD;
    }

    public int getLikeCount() {
        return postCounter.getLikeCount();
    }

    public int getReportCount() {
        return postCounter.getReportCount();
    }

    public int getReplyCount() {
        return postCounter.getReplyCount();
    }

}
