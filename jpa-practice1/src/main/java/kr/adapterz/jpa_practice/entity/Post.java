package kr.adapterz.jpa_practice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
public class Post {

    @Id @GeneratedValue
    @Column(name = "post_id")
    private Long id;

    private String title;
    private String content;

    @Enumerated(EnumType.STRING)
    private PostType postType;

    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // FK(post.user_id) → user.user_id
    private User author;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public Post() {}

    public Post(String title, String content, PostType postType, User author) {
        this.title = title;
        this.content = content;
        this.postType = postType;
        this.author = author;
    }
}