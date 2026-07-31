package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.PostViewCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostViewCountRepository
        extends JpaRepository<PostViewCount, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    UPDATE post_view_counts
                    SET view_count = GREATEST(
                            view_count,
                            :baselineViewCount
                        ) + 1
                    WHERE post_id = :postId
                    """,
            nativeQuery = true
    )
    int incrementViewCount(
            @Param("postId") Long postId,
            @Param("baselineViewCount") long baselineViewCount
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    UPDATE post_view_counts
                    SET view_count = GREATEST(
                            view_count,
                            :snapshotViewCount
                        )
                    WHERE post_id = :postId
                    """,
            nativeQuery = true
    )
    int persistMaxViewCount(
            @Param("postId") Long postId,
            @Param("snapshotViewCount") long snapshotViewCount
    );
}
