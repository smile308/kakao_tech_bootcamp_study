package kr.adapterz.springdatajpa.repository;

import jakarta.annotation.PostConstruct;
import kr.adapterz.springdatajpa.entity.User;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class UserRepository {

    private final Map<Long, User> store = new HashMap<>();
    private Long sequence = 1L;

    @PostConstruct
    public void initDummyData() {
        save(new User(
                "test1@test.com",
                "1234",
                "tester1",
                "profile1.png"
        ));

        save(new User(
                "test2@test.com",
                "1234",
                "tester2",
                "profile2.png"
        ));
    }

    public User save(User user) {
        Long id = sequence++;

        User savedUser = new User(
                id,
                user.getEmail(),
                user.getPassword(),
                user.getNickname(),
                user.getProfile_image()
        );

        store.put(id, savedUser);

        return savedUser;
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public Optional<User> findByEmailAndPassword(String email, String password) {
        return store.values().stream()
                .filter(user -> user.getEmail().equals(email))
                .filter(user -> user.getPassword().equals(password))
                .findFirst();
    }

    public void deleteById(Long id) {
        store.remove(id);
    }
}