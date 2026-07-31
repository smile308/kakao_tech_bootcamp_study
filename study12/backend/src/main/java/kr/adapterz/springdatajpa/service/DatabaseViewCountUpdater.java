package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.entity.PostViewCount;
import kr.adapterz.springdatajpa.exception.CounterUpdateException;
import kr.adapterz.springdatajpa.repository.PostViewCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.view-count",
        name = "enabled",
        havingValue = "false"
)
public class DatabaseViewCountUpdater implements ViewCountUpdater {

    private final PostViewCountRepository postViewCountRepository;

    @Override
    @Transactional
    public long increment(Long postId, long baselineViewCount) {
        int updatedRowCount =
                postViewCountRepository.incrementViewCount(
                        postId,
                        baselineViewCount
                );

        if (updatedRowCount != 1) {
            throw new CounterUpdateException();
        }

        return postViewCountRepository.findById(postId)
                .map(PostViewCount::getViewCount)
                .orElseThrow(CounterUpdateException::new);
    }
}
