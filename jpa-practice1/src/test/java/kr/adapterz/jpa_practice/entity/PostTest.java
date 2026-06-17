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
    @Test
    @Rollback(false)
    void bidirectionalOnetoManyTest() {
        // 유저 생성 및 저장
        User user = new User("tester@adapterz.kr", "123aS!", "tester", UserRole.ADMIN);
        entityManager.persist(user);
        // flush 시 INSERT 발생

        // 게시글 3개 생성
        Post noticePost = new Post("공지사항", "공지 내용", PostType.NOTICE, user);
        Post freePost1 = new Post("자유게시판 글1", "자유 내용", PostType.FREE, user);
        Post freePost2 = new Post("자유게시판 글2", "자유 내용", PostType.FREE, user);

        // 연관관계 설정 (편의 메서드 사용)
        user.addPost(noticePost);
        user.addPost(freePost1);
        user.addPost(freePost2);

        // 게시글 저장
        entityManager.persist(noticePost);
        entityManager.persist(freePost1);
        entityManager.persist(freePost2);
        // post.author 에 FK(user_id) 값이 들어가므로 persist 시 INSERT 쿼리에 포함됨

        // flush 로 DB 반영, clear 로 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();

        // 유저 다시 조회 후 posts 컬렉션으로 연관 게시글 확인
        User findUser = entityManager.find(User.class, user.getId());
        System.out.println("조회된 유저 닉네임 = " + findUser.getNickname());
        System.out.println("연관된 게시글 수 = " + findUser.getPosts().size());
        findUser.getPosts().forEach(post ->
                System.out.println("post.id = " + post.getId() + ", title = " + post.getTitle())
        );

        // 특정 게시글 다시 조회 후 author 로 유저 확인
        Post findPost = entityManager.find(Post.class, freePost1.getId());
        System.out.println("조회된 게시글 제목 = " + findPost.getTitle());
        System.out.println("작성자 닉네임 = " + findPost.getAuthor().getNickname());
    }
}

