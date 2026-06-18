package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.UserInfoDto;
import org.springframework.transaction.annotation.Transactional;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public User create(String email, String password, String nickname){
        User user = new User(email, password, nickname);
        return userRepository.save(user);
    }

    public User findById(Long id){
        return userRepository.findById(id).orElseThrow(()->new IllegalArgumentException("user not found"));
    }

    public User getReferenceById(Long id){
        return userRepository.getReferenceById(id);
    }

    @Transactional
    public User update(Long id, String nickname){
        User user = findById(id);
        if(nickname!=null)
        {
            user.changeNickname(nickname);
        }
        return user;
    }

    @Transactional
    public void delete(Long id) {
        userRepository.delete(findById(id));
    }

    public List<User> findByNicknameKeyword(String keyword){
        //return userRepository.findByNicknameContainingIgnoreCaseOrderByIdDesc(keyword);
        return userRepository.searchByNickname(keyword);
    }

    public List<String> findEmailsByNickname(String nickname) {
        return userRepository.findEmailsByNickname(nickname);
    }

    public List<UserInfoDto> findUserByNicknameWithDto(String keyword) {
        return userRepository.findUserByNicknameWithDto(keyword);
    }

    public boolean existsByEmail(String email){
        return userRepository.existsByEmail(email);
    }

    public long countByNickname(String nickname){
        return userRepository.countByNickname(nickname);
    }

}
