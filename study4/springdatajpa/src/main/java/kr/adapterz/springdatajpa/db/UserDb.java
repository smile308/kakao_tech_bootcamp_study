package kr.adapterz.springdatajpa.db;

import jakarta.annotation.PostConstruct;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class UserDb implements UserRepository {

    private final Map<Long, User> store = new HashMap<>();
    private Long sequence = 1L;

    @PostConstruct
    public void initDummyData() {
        save(new User("test1@test.com", "Password1!", "tester1", "profile1.png"));
        save(new User("test2@test.com", "Password2!", "tester2", "profile2.png"));
    }

    @Override
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

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<User> findByEmailAndPassword(String email, String password) {
        for (User user : store.values()) {
            if (user.getEmail().equals(email) && user.getPassword().equals(password)) {
                return Optional.of(user);
            }
        }

        return Optional.empty();
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}