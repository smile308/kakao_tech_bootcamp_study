package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.dto.UserInfoDto;
import kr.adapterz.springdatajpa.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    //List<User> findByNicknameContainingIgnoreCaseOrderByIdDesc(String keyword);

    @Query("""
           select u
           from User u
           where lower(u.nickname) like lower(concat('%', :keyword, '%'))
           order by u.id desc
            """)
    List<User>  searchByNickname(String keyword);

    @Query("select u.email from User u where u.nickname = :nickname")
    List<String> findEmailsByNickname(String nickname);


    @Query("""
           select new kr.adapterz.springdatajpa.dto.UserInfoDto(u.id, u.email, u.nickname)
           from User u
           where lower(u.nickname) like lower(concat('%', :keyword, '%'))
           order by u.id desc
           """)
    List<UserInfoDto> findUserByNicknameWithDto(String keyword);

    boolean existsByEmail(String email);

    long countByNickname(String nickname);

    @EntityGraph(attributePaths = "posts")
    List<User> findAllBy();
}
