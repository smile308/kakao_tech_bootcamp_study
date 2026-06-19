package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.dto.PostSummaryDto;
import kr.adapterz.springdatajpa.entity.Post;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    //List<Post> findByTitleContainingIgnoreCase(String keyword);
    @Query("""
           select p
           from Post p
           where lower(p.title) like lower(concat('%', :keyword, '%'))
           order by p.id desc
           """)
    List<Post> searchByTitle(String keyword);
    //List<Post> findByAuthor_Nickname(String nickname);
    @Query("""
           select p
           from Post p
           where p.author.nickname = :nickname
           order by p.id desc
           """)
    List<Post> findByAuthorNickname(String nickname);

    @Query("select p.title from Post p where p.author.id = :authorId order by p.id desc")
    List<String> findTitlesByAuthorId(Long authorId);


    @Query("""
           select new kr.adapterz.springdatajpa.dto.PostSummaryDto(
                    p.id, p.title, p.author.nickname)
           from Post p
           where lower(p.title) like lower(concat('%', :keyword, '%'))
           order by p.id desc
           """)
    List<PostSummaryDto> findPostSummaries(String keyword);

    @EntityGraph(attributePaths = "author") // author만 즉시 로딩
    List<Post> findAllBy();
}
