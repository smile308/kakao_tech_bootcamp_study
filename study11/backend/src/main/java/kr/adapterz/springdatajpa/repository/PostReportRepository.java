package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.PostReport;
import kr.adapterz.springdatajpa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    boolean existsByPostAndUser(Post post, User user);
}