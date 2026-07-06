package kr.adapterz.springdatajpa.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@SQLRestriction("deleted = false")
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
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
    @Column(name = "profile_image", columnDefinition = "TEXT")
    private String profileImage;
    @Column(name ="deleted", nullable = false)
    private boolean deleted;

    //profile_image가 없는 경우 null 삽입
    public User( String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImage = null;
        deleted=false;
    }

    //profile_image가 없는 경우 null 삽입
    public User(String email, String password, String nickname, String profileImage) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImage = profileImage;
        deleted=false;
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

    public void setPassword(String password)
    {
        this.password = password;
    }
}