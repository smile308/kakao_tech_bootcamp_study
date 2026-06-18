package kr.adapterz.springdatajpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;


@Entity
@Getter
@RequiredArgsConstructor
public class User extends AbstractAuditable{
    @Id
    @GeneratedValue
    @Column(name="user_id")
    private Long id;

    private String email;
    private String password;
    private String nickname;

    @OneToMany(mappedBy="author")
    List<Post> posts = new ArrayList<>();

    public User(String email, String password, String nickname){
        this.email=email;
        this.password=password;
        this.nickname=nickname;
    }

    public void changeNickname(String nickname){
        this.nickname=nickname;
    }


}

