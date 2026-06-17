package kr.adapterz.jpa_practice.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class ProxyTest {
    @Autowired
    EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @Rollback(false)
    void proxyTest_Hibernate() {
        // 데이터 준비
        User user = new User("tester@adapterz.kr", "123aS!", "tester", UserRole.ADMIN);
        entityManager.persist(user);

        Post post = new Post("프록시 테스트", "내용", PostType.FREE, user);
        entityManager.persist(post);

        entityManager.flush();
        entityManager.clear();

        // Post 재조회 (author는 LAZY 프록시)
        Post findPost = entityManager.find(Post.class, post.getId());
        User authorProxy = findPost.getAuthor(); // 프록시 객체 리턴

        // 1. 초기화 여부 확인 (JPA 표준)
        boolean loadedBefore = entityManagerFactory.getPersistenceUnitUtil().isLoaded(authorProxy);
        System.out.println("초기화 전 isLoaded = " + loadedBefore); // false 예상

        // 2. 프록시 클래스 확인
        System.out.println("프록시 실제 클래스 = " + authorProxy.getClass().getName());

        // 3. 강제 초기화 (Hibernate 전용)
        Hibernate.initialize(authorProxy);

        boolean loadedAfter = entityManagerFactory.getPersistenceUnitUtil().isLoaded(authorProxy);
        System.out.println("실제 DB SELECT을 통해 엔티티 데이터 로딩");
        System.out.println("강제 초기화 후 isLoaded = " + loadedAfter); // true

        // 4. 강제 초기화 없이도 게터 호출 시 초기화됨 (JPA 표준은 강제 초기화 API 없음)
        System.out.println("작성자 닉네임 = " + authorProxy.getNickname()); // 접근 시 초기화
    }
    @Test
    @Rollback(false)
    void proxyTest_OriginJPA() {
        // 데이터 준비
        User user = new User("tester@adapterz.kr", "123aS!", "tester", UserRole.ADMIN);
        entityManager.persist(user);

        Post post = new Post("프록시 테스트", "내용", PostType.FREE, user);
        entityManager.persist(post);

        entityManager.flush();
        entityManager.clear();

        // Post 재조회 (author는 LAZY 프록시)
        Post findPost = entityManager.find(Post.class, post.getId());
        User authorProxy = findPost.getAuthor(); // 프록시 객체 리턴

        // 1. 초기화 여부 확인 (JPA 표준)
        boolean loadedBefore = entityManagerFactory.getPersistenceUnitUtil().isLoaded(authorProxy);
        System.out.println("초기화 전 isLoaded = " + loadedBefore); // false 예상

        // 2. 프록시 클래스 확인
        System.out.println("프록시 실제 클래스 = " + authorProxy.getClass().getName());

        // 3. 강제 초기화 없이도 게터 호출 시 초기화됨 (JPA 표준은 강제 초기화 API 없음)
        String initializeUserNickname = authorProxy.getNickname();
        System.out.println("실제 DB SELECT을 통해 엔티티 데이터 로딩");
        System.out.println("작성자 닉네임 = " + initializeUserNickname); // 접근 시 초기화
    }
}