// 게시글 상세 조회에 필요한 데이터를 읽기 트랜잭션으로 수집하는 서비스
package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.comment.CommentResponseDto;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.PostCounter;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.LikeRepository;
import kr.adapterz.springdatajpa.repository.PostReportRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostViewReadService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final PostReportRepository postReportRepository;

    @Transactional(readOnly = true)
    public PostViewData read(Long postId, Long loginUserId) {
        Post post = postRepository.findByPostIdAndDeletedFalse(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));

        if (post.isBlockedByReports()) {
            throw new DataNullException("No_Post");
        }

        User loginUser = userRepository.findByUserIdAndDeletedFalse(loginUserId)
                .orElseThrow(() -> new AuthException("No_User"));

        List<Comment> comments = commentRepository.findByPostWithUser(post);
        List<CommentResponseDto> commentResponseDtos = new ArrayList<>();

        for (Comment comment : comments) {
            boolean isMyComment = comment.getUser().getUserId().equals(loginUserId);
            commentResponseDtos.add(
                    new CommentResponseDto(comment, comment.getUser(), isMyComment)
            );
        }

        boolean isLiked = likeRepository.existsByPostAndUser(post, loginUser);
        boolean isReported = postReportRepository.existsByPostAndUser(post, loginUser);
        boolean isMine = post.getUser().getUserId().equals(loginUserId);

        return new PostViewData(
                post,
                post.getPostCounter(),
                post.getPostViewCount().getViewCount(),
                commentResponseDtos,
                isLiked,
                isReported,
                isMine
        );
    }

    public record PostViewData(
            Post post,
            PostCounter counter,
            long baselineViewCount,
            List<CommentResponseDto> comments,
            boolean liked,
            boolean reported,
            boolean mine
    ) {
    }
}
