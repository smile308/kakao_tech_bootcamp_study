package kr.adapterz.jpa_practice.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import kr.adapterz.jpa_practice.entity.User;
import kr.adapterz.jpa_practice.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PostTest {
    @PersistenceContext
    EntityManager entityManager;

    @Test
    @Rollback(false)
    void unidirectionalManyToOneTest() {
        // 유저 저장
        User user = new User("tester@adapterz.kr", "123aS!", "tester", UserRole.ADMIN);
        entityManager.persist(user);
        entityManager.flush();

        // 게시글 저장
        Post post = new Post("공지 글", "내용", PostType.NOTICE, user);
        entityManager.persist(post);
        entityManager.flush();

        // 1차 캐시 초기화 후 조회
        entityManager.clear();
        Post findPost = entityManager.find(Post.class, post.getId());
        System.out.println("findPost.getId() : " + findPost.getId());
        System.out.println("findPost.getTitle() : " + findPost.getTitle());
        System.out.println("findPost.getauthor().getNickname() : " + findPost.getAuthor().getNickname());
    }
}