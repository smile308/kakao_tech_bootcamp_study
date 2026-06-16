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

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
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
            User user = userRepository.findId(post.getUser_id())
                    .orElse(new User(null, null, null, "삭제된 계정"));

            PostListResponseDto dto = new PostListResponseDto(post, user);

            result.add(dto);
        }

        return result;
    }

    // 게시물 추가
    public PostResponseDto createPost(PostRequestDto request) {
        PostResponseDto postResponseDto= new PostResponseDto();
        userRepository.findId(request.getUser_id()).orElseThrow(()->new AuthException("No_User"));
        Post post = new Post(
                request.getUser_id(),
                request.getTitle(),
                request.getContents(),
                request.getImage_file()
        );
        postRepository.save(post);

        return postResponseDto;
    }

    //게시물 상세조회
    public PostViewResponseDto getPostView(Long post_id) {

        Post post = postRepository.findId(post_id)
                .orElseThrow(() -> new DataNullException("No_Post"));

        User user = userRepository.findId(post.getUser_id())
                .orElse(new User(null, null, null, "삭제된 계정"));
        //postid에 해당하는 comment들의 리스트, 아직 유저 정보가 없음
        List<Comment> comments = commentRepository.findByPostId(post.getPost_id());

        List<CommentResponseDto> commentResponseDtos = new ArrayList<>();

        //각 코멘트 별로 유저의 정보를 찾아서 commentResponseDtos라는 배열에 하나하나 추가함
        for (Comment comment : comments) {
            User commentWriter = userRepository.findId(comment.getUser_id())
                    .orElse(new User(null, null, null, "삭제된 계정"));

            CommentResponseDto commentResponseDto =
                    new CommentResponseDto(comment, commentWriter);

            commentResponseDtos.add(commentResponseDto);
        }
        //조회수 상승
        post.view();
        return new PostViewResponseDto(post, user, commentResponseDtos);
    }

    //게시물 수정
    public PostFixResponseDto fixPost(Long post_id, PostFixRequestDto request) {
        PostFixResponseDto postFixResponseDto = new PostFixResponseDto();
        Post post = postRepository.findId(post_id)
                .orElseThrow(()->new DataNullException("No_Post"));
        //실제 작성자가 맞는지 확인
        if (!post.getUser_id().equals(request.getUser_id())) {
            throw new AuthException("No_Auth");
        }
        post.update(
                request.getTitle(),
                request.getContents(),
                request.getImage_file()
        );
        return postFixResponseDto;
    }
    //게시글 삭제
    public PostDeleteResponseDto deletePost(Long post_id, PostDeleteRequestDto request){
        PostDeleteResponseDto postDeleteResponseDto = new PostDeleteResponseDto();
        Post post =postRepository.findId(post_id).orElseThrow(()->new DataNullException("No_Post"));
        //게시물 작성자가 아닐경우 권한이 없다는걸 알림
        if(!post.getUser_id().equals(request.getUser_id())) {
            throw new AuthException("No_Auth");
        }
        postRepository.deleteById(post_id);
        return postDeleteResponseDto;
    }

    //게시물 좋아요
    public LikeResponseDto likePost(Long post_id, LikeRequestDto request){
        Post post = postRepository.findId(post_id).orElseThrow(()->new DataNullException("No_Post"));
        userRepository.findId(request.getUser_id()).orElseThrow(()->new AuthException("No_User"));
        post.like();
        LikeResponseDto likeResponseDto = new LikeResponseDto(post.getLike_count());

        return likeResponseDto;
    }

    //좋아요 취소
    public LikeCancelResponseDto cancelLike(Long post_id, LikeCancelRequestDto request){
        Post post = postRepository.findId(post_id).orElseThrow(()->new DataNullException("No_Post"));
        userRepository.findId(request.getUser_id()).orElseThrow(()->new AuthException("No_User"));
        post.likeCancle();
        LikeCancelResponseDto likeCancelResponseDto = new LikeCancelResponseDto(post.getLike_count());

        return likeCancelResponseDto;
    }
}
