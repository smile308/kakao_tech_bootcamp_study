package kr.adapterz.springdatajpa.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@SQLRestriction("is_deleted = false")
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "user_id")
    private Long user_id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = true, unique = false, length = 255)
    private String password;

    @Column(nullable = false, length = 10)
    private String nickname;

    @Column(nullable = true, unique = false)
    private String profile_image;

    private boolean is_deleted;

    //profile_image가 없는 경우 null 삽입
    public User( String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profile_image = null;
        is_deleted=false;
    }

    //profile_image가 없는 경우 null 삽입
    public User(String email, String password, String nickname, String profile_image) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profile_image = profile_image;
        is_deleted=false;
    }

    public void update(String nickname, String profile_image) {
        this.nickname = nickname;
        this.profile_image = profile_image;
    }

    //유저 삭제
    public void delete(){is_deleted=true;}

    public void setPassword(String password)
    {
        this.password = password;
    }
}