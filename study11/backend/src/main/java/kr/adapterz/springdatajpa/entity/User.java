package kr.adapterz.springdatajpa.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    public static final int SUSPENSION_REPORT_THRESHOLD = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = true, unique = false, length = 255)
    private String password;

    @Column(nullable = false, length = 10)
    private String nickname;

    @Lob
    @Column(name = "profile_image", columnDefinition = "LONGTEXT")
    private String profileImage;

    @Column(name = "received_report_count", nullable = false)
    private int receivedReportCount;

    @Column(name ="deleted", nullable = false)
    private boolean deleted;

    public User(
            String email,
            String password,
            String nickname,
            String profileImage,
            int receivedReportCount
    ) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.receivedReportCount = receivedReportCount;
        this.deleted = false;
    }

    public User(
            String email,
            String password,
            String nickname,
            int receivedReportCount
    ) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImage = null;
        this.receivedReportCount = receivedReportCount;
        this.deleted = false;
    }

    public void update(String nickname, String profileImage) {
        this.nickname = nickname;
        this.profileImage = profileImage;
    }

    //유저 삭제
    public void delete(){
        this.deleted = true;
        this.nickname = "삭제된 유저";
        this.profileImage = null;
    }

    //신고 카운
    public void receiveReport() {
        receivedReportCount++;
    }

    public boolean isSuspended() {
        return receivedReportCount >= SUSPENSION_REPORT_THRESHOLD;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }
}
