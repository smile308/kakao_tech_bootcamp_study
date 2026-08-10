package kr.adapterz.springdatajpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "post_counters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostCounter {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Column(name = "reply_count", nullable = false)
    private int replyCount;

    public PostCounter(Post post) {
        this.post = post;
        this.likeCount = 0;
        this.reportCount = 0;
        this.replyCount = 0;
    }

    public void report() {
        reportCount++;
    }
}
