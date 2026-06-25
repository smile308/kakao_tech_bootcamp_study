package kr.adapterz.springdatajpa.service;

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

    //게시물 목록 조회
    public List<PostListResponseDto> getPostList() {
        List<Post> posts = postRepository.findAllByOrderByPostIdDesc();
        List<PostListResponseDto> result = new ArrayList<>();

        //각 게시물의 user_id로 작성자 정보 붙이기
        for (Post post : posts) {
            PostListResponseDto dto = new PostListResponseDto(post, post.getUser());

            result.add(dto);
        }

        return result;
    }

    // 게시물 추가
    @Transactional
    public PostResponseDto createPost(PostRequestDto request) {
        PostResponseDto postResponseDto= new PostResponseDto();
        User user = userRepository.findById(request.getUserId()).orElseThrow(()->new AuthException("No_User"));
        Post post = new Post(
                user,
                request.getTitle(),
                request.getContents(),
                request.getImageFile()
        );
        postRepository.save(post);

        return postResponseDto;
    }

    //게시물
    @Transactional
    public PostViewResponseDto getPostView(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));

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
    public PostFixResponseDto fixPost(Long postId, PostFixRequestDto request) {
        PostFixResponseDto postFixResponseDto = new PostFixResponseDto();
        Post post = postRepository.findById(postId)
                .orElseThrow(()->new DataNullException("No_Post"));

        //실제 작성자가 맞는지 확인
        if (!post.getUser().getUserId().equals(request.getUserId())) {
            throw new AuthException("No_Auth");
        }
        post.update(
                request.getTitle(),
                request.getContents(),
                request.getImageFile()
        );
        return postFixResponseDto;
    }
    //게시글 삭제
    @Transactional
    public PostDeleteResponseDto deletePost(Long postId, PostDeleteRequestDto request){
        PostDeleteResponseDto postDeleteResponseDto = new PostDeleteResponseDto();
        Post post =postRepository.findById(postId).orElseThrow(()->new DataNullException("No_Post"));

        //게시물 작성자가 아닐경우 권한이 없다는걸 알림
        if(!post.getUser().getUserId().equals(request.getUserId())) {
            throw new AuthException("No_Auth");
        }
        post.delete();
        return postDeleteResponseDto;
    }

    //게시물 좋아요
    @Transactional
    public LikeResponseDto likePost(Long postId, LikeRequestDto request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));

        User user = userRepository.findByUserIdAndDeletedFalse(request.getUserId())
                .orElseThrow(() -> new AuthException("No_User"));

        if (likeRepository.existsByPostAndUser(post, user)) {
            throw new InvalidRequestException("Already_Liked");
        }

        likeRepository.save(new Like(post, user));
        post.like();

        return new LikeResponseDto(post.getLikeCount());
    }

    //좋아요 취소
    @Transactional
    public LikeCancelResponseDto cancelLike(Long postId, LikeCancelRequestDto request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));

        User user = userRepository.findByUserIdAndDeletedFalse(request.getUserId())
                .orElseThrow(() -> new AuthException("No_User"));

        Like postLike = likeRepository.findByPostAndUser(post, user)
                .orElseThrow(() -> new InvalidRequestException("Not_Liked"));

        likeRepository.delete(postLike);
        post.likeCancle();

        return new LikeCancelResponseDto(post.getLikeCount());
    }

    //게시글 신고
    @Transactional
    public ReportResponseDto reportPost (Long postId, ReportRequestDto request){
        Post post = postRepository.findById(postId).orElseThrow(()->new DataNullException("No_Post"));

        post.report();
        ReportResponseDto reportResponseDto = new ReportResponseDto(post.getReportCount());
        return reportResponseDto;
    }

}
