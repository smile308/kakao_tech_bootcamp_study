package kr.adapterz.springdatajpa.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long user_id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = true, unique = false, length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 10)
    private String nickname;

    @Column(nullable = true, unique = false)
    private String profile_image;
    //profile_image가 없는 경우 null 삽입
    public User( String email, String password, String nickname) {
        this.user_id = null;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profile_image = null;
    }

    public User(String email, String password, String nickname, String profile_image) {
        this.user_id = null;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profile_image = profile_image;
    }

    //profile_image가 없는 경우 null 삽입
    public User(Long user_id, String email, String password, String nickname) {
        this.user_id = user_id;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profile_image = null;
    }

    public User(Long user_id, String email, String password, String nickname, String profile_image) {
        this.user_id = user_id;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profile_image = profile_image;
    }

    public void update(String nickname, String profile_image) {
        this.nickname = nickname;
        this.profile_image = profile_image;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }
}