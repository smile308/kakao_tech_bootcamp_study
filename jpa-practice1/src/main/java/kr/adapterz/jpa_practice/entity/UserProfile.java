package kr.adapterz.jpa_practice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class UserProfile {

    @Id @GeneratedValue
    @Column(name = "profile_id")
    private Long id;
    private String profileImagePath;

    @OneToOne(mappedBy = "userProfile")
    private User user;
}