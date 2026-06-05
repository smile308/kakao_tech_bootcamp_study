package kr.adapterz.springdatajpa.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class User {
    private Long user_id;
    private String email;
    private String password;
    private String nickname;
    private String profile_image;

    //DB가 없어서 id초기 값 설정에 문제가 발생해 id가 없는 경우의 생성자를 만들어 둠
    public User(String email, String password, String nickname) {
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