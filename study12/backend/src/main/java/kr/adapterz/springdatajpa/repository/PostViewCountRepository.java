package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.PostViewCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostViewCountRepository
        extends JpaRepository<PostViewCount, Long> {
}
