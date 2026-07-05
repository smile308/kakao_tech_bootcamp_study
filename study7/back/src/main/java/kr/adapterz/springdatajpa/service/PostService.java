package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.auth.JwtProvider;
import kr.adapterz.springdatajpa.dto.comment.CommentResponseDto;
import kr.adapterz.springdatajpa.dto.post.*;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.Like;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.LikeRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final JwtProvider jwtProvider;

    //게시물 목록 조회
    public PostPageResponseDto getPostList(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Post> posts = postRepository.findByDeletedFalseOrderByPostIdDesc(pageable);

        List<PostListResponseDto> result = new ArrayList<>();

        for (Post post : posts.getContent()) {
            result.add(new PostListResponseDto(post, post.getUser()));
        }

        return new PostPageResponseDto(result, posts.hasNext());
    }

    // 게시물 추가
    @Transactional
    public PostResponseDto createPost(String authorizationHeader, PostRequestDto request) {
        PostResponseDto postResponseDto= new PostResponseDto();
        User user = getLoginUser(authorizationHeader);
        Post post = new Post(
                user,
                request.getTitle(),
                request.getContents()
        );
        post.replaceImages(request.getPostImageFiles());
        postRepository.save(post);

        return postResponseDto;
    }

    //게시물
    @Transactional
    public PostViewResponseDto getPostView(Long postId) {
        Post post = getActivePost(postId);

        List<Comment> comments = commentRepository.findByPostWithUser(post);
        List<CommentResponseDto> commentResponseDtos = new ArrayList<>();

        for (Comment comment : comments) {
            CommentResponseDto commentResponseDto =
                    new CommentResponseDto(comment, comment.getUser());

            commentResponseDtos.add(commentResponseDto);
        }

        post.view();

        return new PostViewResponseDto(post, post.getUser(), commentResponseDtos);
    }

    //게시물 수정
    @Transactional
    public PostFixResponseDto fixPost(Long postId, String authorizationHeader, PostFixRequestDto request) {
        PostFixResponseDto postFixResponseDto = new PostFixResponseDto();
        Long loginUserId = jwtProvider.getUserIdFromAuthorizationHeader(authorizationHeader);
        Post post = getActivePost(postId);

        //실제 작성자가 맞는지 확인
        if (!post.getUser().getUserId().equals(loginUserId)) {
            throw new AuthException("No_Auth");
        }
        post.update(
                request.getTitle(),
                request.getContents(),
                request.getPostImageFiles()
        );
        return postFixResponseDto;
    }
    //게시글 삭제
    @Transactional
    public PostDeleteResponseDto deletePost(Long postId, String authorizationHeader){
        PostDeleteResponseDto postDeleteResponseDto = new PostDeleteResponseDto();
        Long loginUserId = jwtProvider.getUserIdFromAuthorizationHeader(authorizationHeader);
        Post post = getActivePost(postId);

        //게시물 작성자가 아닐경우 권한이 없다는걸 알림
        if(!post.getUser().getUserId().equals(loginUserId)) {
            throw new AuthException("No_Auth");
        }
        post.delete();
        return postDeleteResponseDto;
    }

    //게시물 좋아요
    @Transactional
    public LikeResponseDto likePost(Long postId, String authorizationHeader) {
        Post post = getActivePost(postId);

        User user = getLoginUser(authorizationHeader);

        if (likeRepository.existsByPostAndUser(post, user)) {
            throw new InvalidRequestException("Already_Liked");
        }

        likeRepository.save(new Like(post, user));
        post.like();

        return new LikeResponseDto(post.getLikeCount());
    }

    //좋아요 취소
    @Transactional
    public LikeCancelResponseDto cancelLike(Long postId, String authorizationHeader) {
        Post post = getActivePost(postId);

        User user = getLoginUser(authorizationHeader);

        Like postLike = likeRepository.findByPostAndUser(post, user)
                .orElseThrow(() -> new InvalidRequestException("Not_Liked"));

        likeRepository.delete(postLike);
        post.likeCancle();

        return new LikeCancelResponseDto(post.getLikeCount());
    }

    //게시글 신고
    @Transactional
    public ReportResponseDto reportPost (Long postId, String authorizationHeader){
        getLoginUser(authorizationHeader);
        Post post = getActivePost(postId);

        post.report();
        ReportResponseDto reportResponseDto = new ReportResponseDto(post.getReportCount());
        return reportResponseDto;
    }

    private User getLoginUser(String authorizationHeader) {
        Long loginUserId = jwtProvider.getUserIdFromAuthorizationHeader(authorizationHeader);
        return userRepository.findByUserIdAndDeletedFalse(loginUserId)
                .orElseThrow(() -> new AuthException("No_User"));
    }

    private Post getActivePost(Long postId) {
        return postRepository.findByPostIdAndDeletedFalse(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));
    }
}
