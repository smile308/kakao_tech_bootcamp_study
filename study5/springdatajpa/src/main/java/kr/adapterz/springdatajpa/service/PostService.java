package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.comment.CommentResponseDto;
import kr.adapterz.springdatajpa.dto.post.*;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
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

    //게시물 목록 조회
    public List<PostListResponseDto> getPostList() {
        List<Post> posts = postRepository.findAll();
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
        User postWriter = getDisplayUser(post.getUser().getUserId());

        //postid에 해당하는 comment들의 리스트, 아직 유저 정보가 없음
        List<Comment> comments = commentRepository.findByPost(post);

        List<CommentResponseDto> commentResponseDtos = new ArrayList<>();

        //각 코멘트 별로 유저의 정보를 찾아서 commentResponseDtos라는 배열에 하나하나 추가함
        for (Comment comment : comments) {
            User commentWriter = getDisplayUser(comment.getUser().getUserId());

            CommentResponseDto commentResponseDto =
                    new CommentResponseDto(comment, commentWriter);

            commentResponseDtos.add(commentResponseDto);
        }
        //조회수 상승
        post.view();
        return new PostViewResponseDto(post, postWriter, commentResponseDtos);
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
    public LikeResponseDto likePost(Long postId, LikeRequestDto request){
        Post post = postRepository.findById(postId).orElseThrow(()->new DataNullException("No_Post"));

        userRepository.findById(request.getUserId()).orElseThrow(()->new AuthException("No_User"));
        post.like();
        LikeResponseDto likeResponseDto = new LikeResponseDto(post.getLikeCount());

        return likeResponseDto;
    }

    //좋아요 취소
    @Transactional
    public LikeCancelResponseDto cancelLike(Long postId, LikeCancelRequestDto request){
        Post post = postRepository.findById(postId).orElseThrow(()->new DataNullException("No_Post"));

        userRepository.findById(request.getUserId()).orElseThrow(()->new AuthException("No_User"));
        post.likeCancle();
        LikeCancelResponseDto likeCancelResponseDto = new LikeCancelResponseDto(post.getLikeCount());

        return likeCancelResponseDto;
    }

    //게시글 신고
    @Transactional
    public ReportResponseDto reportPost (Long postId, ReportRequestDto request){
        Post post = postRepository.findById(postId).orElseThrow(()->new DataNullException("No_Post"));

        post.report();
        ReportResponseDto reportResponseDto = new ReportResponseDto(post.getReportCount());
        return reportResponseDto;
    }

    //삭제된 계정 처리
    private User getDisplayUser(Long userId) {
        return userRepository.findById(userId)
                .orElse(new User(null, null, "삭제된 계정", null));
    }
}
