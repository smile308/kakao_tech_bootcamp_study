package kr.adapterz.springdatajpa.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import kr.adapterz.springdatajpa.dto.comment.CommentResponseDto;
import kr.adapterz.springdatajpa.dto.post.*;
import kr.adapterz.springdatajpa.entity.*;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.CounterUpdateException;
import kr.adapterz.springdatajpa.exception.ForbiddenException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.exception.PostVersionConflictException;
import kr.adapterz.springdatajpa.repository.*;
import kr.adapterz.springdatajpa.validation.ImageDataUrlValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final PostReportRepository postReportRepository;
    private final PostCounterRepository postCounterRepository;
    private final ViewCountUpdater viewCountUpdater;
    private final EntityManager entityManager;

    public PostPageResponseDto getPostList(int page, int size) {
        validatePagination(page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Post> posts = postRepository
                .findByDeletedFalseAndReportCountLessThanOrderByPostIdDesc(
                        Post.REPORT_BLOCK_THRESHOLD,
                        pageable
                );

        List<PostListResponseDto> result = new ArrayList<>();

        for (Post post : posts.getContent()) {
            result.add(PostResponseFactory.createListResponse(post));
        }

        return PostResponseFactory.createPageResponse(result, posts.hasNext());
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("Invalid_Page");
        }
        if (size < 1) {
            throw new InvalidRequestException("Invalid_Page_Size");
        }
    }

    @Transactional
    public PostResponseDto createPost(Long loginUserId, PostRequestDto request) {
        ImageDataUrlValidator.validatePostImages(request.getImageFiles());

        PostResponseDto postResponseDto= new PostResponseDto();
        User user = getLoginUser(loginUserId);
        Post post = new Post(
                user,
                request.getTitle(),
                request.getContent()
        );
        post.replaceImages(request.getImageFiles());
        postRepository.save(post);

        return postResponseDto;
    }

    @Transactional
    public PostViewResponseDto getPostView(Long postId, Long loginUserId) {
        Post post = getViewablePost(postId);
        User loginUser = getLoginUser(loginUserId);

        List<Comment> comments = commentRepository.findByPostWithUser(post);
        List<CommentResponseDto> commentResponseDtos = new ArrayList<>();

        for (Comment comment : comments) {
            boolean isMyComment=comment.getUser().getUserId().equals(loginUserId);
            CommentResponseDto commentResponseDto =
                    new CommentResponseDto(comment, comment.getUser(),isMyComment);
            commentResponseDtos.add(commentResponseDto);
        }

        boolean isLiked = likeRepository.existsByPostAndUser(post, loginUser);
        boolean isReported = postReportRepository.existsByPostAndUser(post, loginUser);
        boolean isMine = post.getUser().getUserId().equals(loginUserId);

        long baselineViewCount = Math.max(
                post.getPostCounter().getViewCount(),
                post.getPostViewCount().getViewCount()
        );
        long updatedViewCount = viewCountUpdater.increment(
                postId,
                baselineViewCount
        );

        return new PostViewResponseDto(
                post,
                post.getPostCounter(),
                updatedViewCount,
                commentResponseDtos,
                isLiked,
                isReported,
                isMine
        );
    }

    @Transactional
    public PostFixResponseDto fixPost(Long postId, Long loginUserId, PostFixRequestDto request) {
        PostFixResponseDto postFixResponseDto = new PostFixResponseDto();
        Post post = getActivePost(postId);

        validatePostModificationPermission(post, loginUserId);
        validatePostVersion(post, request.getVersion());
        if (requiresForcedVersionIncrement(post, request)) {
            entityManager.lock(post, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        ImageDataUrlValidator.validatePostImages(request.getImageFiles());

        post.update(
                request.getTitle(),
                request.getContent(),
                request.getImageFiles()
        );
        return postFixResponseDto;
    }
    @Transactional
    public PostDeleteResponseDto deletePost(
            Long postId,
            Long loginUserId,
            PostDeleteRequestDto request
    ){
        PostDeleteResponseDto postDeleteResponseDto = new PostDeleteResponseDto();
        Post post = getActivePost(postId);

        validatePostModificationPermission(post, loginUserId);
        validatePostVersion(post, request.getVersion());
        getPostCounterForUpdate(postId);

        post.delete();
        return postDeleteResponseDto;
    }

    @Transactional
    public LikeResponseDto likePost(Long postId, Long loginUserId) {
        Post post = getActivePostForInteraction(postId);

        User user = getLoginUser(loginUserId);

        getPostCounterForUpdate(postId);
        validateActivePostAfterCounterUpdate(postId);

        try {
            likeRepository.saveAndFlush(new Like(post, user));
        } catch (DataIntegrityViolationException e) {
            throw new InvalidRequestException("Already_Liked");
        }
        validateCounterUpdate(
                postCounterRepository.incrementLikeCount(postId)
        );

        return new LikeResponseDto(getPostCounter(postId).getLikeCount());
    }

    @Transactional
    public LikeCancelResponseDto cancelLike(Long postId, Long loginUserId) {
        getActivePostForInteraction(postId);
        getLoginUser(loginUserId);

        getPostCounterForUpdate(postId);
        validateActivePostAfterCounterUpdate(postId);

        int deletedRowCount = likeRepository.deleteByPostIdAndUserId(
                postId,
                loginUserId
        );
        if (deletedRowCount != 1) {
            throw new InvalidRequestException("Not_Liked");
        }
        validateCounterUpdate(
                postCounterRepository.decrementLikeCount(postId)
        );

        return new LikeCancelResponseDto(getPostCounter(postId).getLikeCount());
    }

    @Transactional
    public PostReportResponseDto reportPost(
            Long postId,
            Long loginUserId
    ) {
        Post post = getActivePostForUpdate(postId);
        User reporter = getLoginUser(loginUserId);
        User writer = getUserForUpdate(post.getUser().getUserId());

        if (writer.getUserId().equals(reporter.getUserId())) {
            throw new InvalidRequestException("Cannot_Report_Own_Post");
        }

        if (postReportRepository.existsByPostAndUser(post, reporter)) {
            throw new InvalidRequestException("Already_Reported");
        }


        postReportRepository.save(new PostReport(post, reporter));

        post.report();
        writer.receiveReport();

        return new PostReportResponseDto(
                post.getPostId(),
                post.getPostCounter().getReportCount()
        );
    }


    private User getLoginUser(Long loginUserId) {
        return userRepository.findByUserIdAndDeletedFalse(loginUserId)
                .orElseThrow(() -> new AuthException("No_User"));
    }

    private Post getActivePost(Long postId) {
        return postRepository.findByPostIdAndDeletedFalse(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));
    }

    private Post getViewablePost(Long postId) {
        Post post = getActivePost(postId);

        if (post.isBlockedByReports()) {
            throw new DataNullException("No_Post");
        }

        return post;
    }

    private Post getActivePostForUpdate(Long postId) {
        return postRepository.findActivePostForUpdate(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));
    }

    private Post getActivePostForInteraction(Long postId) {
        return postRepository.findActivePostForInteraction(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));
    }

    private void validateActivePostAfterCounterUpdate(Long postId) {
        postRepository.findActivePostForInteractionCheck(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));
    }

    private User getUserForUpdate(Long userId) {
        return userRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new DataNullException("No_User"));
    }

    private PostCounter getPostCounter(Long postId) {
        return postCounterRepository.findById(postId)
                .orElseThrow(CounterUpdateException::new);
    }

    private PostCounter getPostCounterForUpdate(Long postId) {
        return postCounterRepository.findByPostIdForUpdate(postId)
                .orElseThrow(CounterUpdateException::new);
    }

    private void validateCounterUpdate(int updatedRowCount) {
        if (updatedRowCount != 1) {
            throw new CounterUpdateException();
        }
    }

    private void validatePostModificationPermission(Post post, Long loginUserId) {
        boolean isWriter = post.getUser().getUserId().equals(loginUserId);

        if (!isWriter || post.isBlockedByReports()) {
            throw new ForbiddenException("Forbidden_Access");
        }
    }

    private void validatePostVersion(Post post, Long requestVersion) {
        if (!Objects.equals(post.getVersion(), requestVersion)) {
            throw new PostVersionConflictException();
        }
    }

    private boolean requiresForcedVersionIncrement(
            Post post,
            PostFixRequestDto request
    ) {
        return post.isFixed()
                && Objects.equals(post.getPostTitle(), request.getTitle())
                && Objects.equals(post.getPostContent(), request.getContent());
    }
}
