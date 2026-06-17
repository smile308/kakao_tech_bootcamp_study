package kr.adapterz.jpa_practice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;


@Entity
@Getter @Setter
public class User {
    @Id @GeneratedValue
    @Column(name = "user_id")
    private Long id;
    private String email;
    private String password;
    private String nickname;

    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    @OneToMany(mappedBy = "author")
    private List<Post> posts = new ArrayList<>();

    @OneToOne
    @JoinColumn(name = "profile_id", unique = true)
    private UserProfile userProfile;


    protected User() {}

    public User(String email, String password, String nickname, UserRole userRole) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.userRole = userRole;
    }

    // 편의 메서드: 양쪽 동기화
    public void addPost(Post post) {
        this.posts.add(post);
        post.setAuthor(this);
    }

    public void removePost(Post post) {
        this.posts.remove(post);
        post.setAuthor(null);
    }
}