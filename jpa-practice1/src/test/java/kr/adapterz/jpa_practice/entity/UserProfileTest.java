package kr.adapterz.jpa_practice.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UserProfileTest {

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @Rollback(false)
    void oneToOneTest() {
        // 프로필 생성
        UserProfile profile = new UserProfile();
        profile.setProfileImagePath("/images/tester.png");

        // 유저 생성
        User user = new User("tester@adapterz.kr", "123aS!", "tester", UserRole.ADMIN);

        // 연관관계 설정 (User → UserProfile)
        user.setUserProfile(profile);

        // 저장
        entityManager.persist(profile); // Profile 먼저 저장
        entityManager.persist(user);    // User 저장

        entityManager.flush();
        entityManager.clear();

        // 다시 조회
        User findUser = entityManager.find(User.class, user.getId());
        System.out.println("조회된 유저 이메일 = " + findUser.getEmail());
        System.out.println("유저 프로필 이미지 경로 = " + findUser.getUserProfile().getProfileImagePath());

        UserProfile findProfile = entityManager.find(UserProfile.class, profile.getId());
        System.out.println("조회된 프로필 id = " + findProfile.getId());
        System.out.println("프로필에 연결된 유저 닉네임 = " + findProfile.getUser().getNickname());
    }
}