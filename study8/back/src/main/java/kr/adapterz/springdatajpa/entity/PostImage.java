package kr.adapterz.springdatajpa.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "post_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_image_id")
    private Long postImageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Lob
    @Column(name = "image_file", nullable = false, columnDefinition = "TEXT")
    private String imageFile;

    @Column(name = "image_order", nullable = false)
    private int imageOrder;

    public PostImage(Post post, String imageFile, int imageOrder) {
        this.post = post;
        this.imageFile = imageFile;
        this.imageOrder = imageOrder;
    }
}
