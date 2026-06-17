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
class UserTest {

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @Rollback(false)
    void idTest() {
        User user = new User("tester@adapterz.kr", "123aS!", "Adapterz", UserRole.ADMIN);
        entityManager.persist(user);
    }
    @Test
    @Rollback(false)
    void idStrategyTest(){
        // 5개의 더미데이터 추가
        for (int i = 1; i <= 5; i++) {
            User user = new User(
                    "tester" + i + "@adapterz.kr",
                    "123aS!" + i,
                    "Adapterz" + i,
                    UserRole.ADMIN
            );
            entityManager.persist(user);
        }
    }

    @Test
    @Rollback(false)
    void flushTest() {
        User user = new User("tester@adapterz.kr", "123aS!", "Adapterz", UserRole.ADMIN);

        System.out.println("=== Flush (아무것도 없음) ===");
        entityManager.flush(); // 아무 것도 없음 (정상)
        System.out.println("==============");

        System.out.println("=== Persist ===");
        entityManager.persist(user);     // 영속화 (아직 INSERT 미발행)
        System.out.println("==============");

        System.out.println("=== Flush (INSERT 발생) ===");
        entityManager.flush();           // 여기서 INSERT 발생
        System.out.println("==============");
    }

    @Test
    @Rollback(false)
    void removeTest() {
        User user = new User("delete@adapterz.kr", "123aS!", "DeleteUser", UserRole.ADMIN);
        entityManager.persist(user);

        entityManager.flush(); // INSERT 실행
        System.out.println("=== INSERT 쿼리 실행됨 ===");

        entityManager.remove(user);
        System.out.println("=== remove 호출 (아직 DELETE 쿼리 안 나감) ===");

        entityManager.flush();
        System.out.println("=== DELETE 쿼리 실행됨 ===");
    }
}