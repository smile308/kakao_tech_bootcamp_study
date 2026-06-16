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
    //user_id에 쓰일 변수
    private Long sequence = 1L;

    //더미 데이터
    @PostConstruct
    public void initDummyData() {
        save(new User("test1@test.com", "Password1!", "tester1", "profile1.png"));
        save(new User("test2@test.com", "Password2!", "tester2", "profile2.png"));
    }

    //데이터 저장
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

    //아이디로 유저 정보 찾기
    @Override
    public Optional<User> findId(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    //로그인에 사용될 이메일과 비밀번호로 유저 정보 찾기
    @Override
    public Optional<User> findByEmailAndPassword(String email, String password) {
        for (User user : store.values()) {
            if (user.getEmail().equals(email) && user.getPassword().equals(password)) {
                return Optional.of(user);
            }
        }

        return Optional.empty();
    }

    // 이메일 중복여부 판단
    @Override
    public boolean existsEmail(String email) {
        for (User user : store.values()) {
            if (user.getEmail().equals(email)) {
                return true;
            }
        }

        return false;
    }

    //닉네임 중복여부 판단
    @Override
    public boolean existsNickname(String nickname) {
        for (User user : store.values()) {
            if (user.getNickname().equals(nickname)) {
                return true;
            }
        }

        return false;
    }

    //유저 삭제
    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}