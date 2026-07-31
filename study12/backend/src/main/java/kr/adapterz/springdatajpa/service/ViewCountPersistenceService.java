package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.exception.CounterUpdateException;
import kr.adapterz.springdatajpa.repository.PostViewCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ViewCountPersistenceService {

    private final PostViewCountRepository postViewCountRepository;

    @Transactional
    public void persist(Long postId, long snapshotViewCount) {
        int updatedRowCount =
                postViewCountRepository.persistMaxViewCount(
                        postId,
                        snapshotViewCount
                );

        if (updatedRowCount != 1) {
            throw new CounterUpdateException();
        }
    }
}
