package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.auth.SessionCheck;
import kr.adapterz.springdatajpa.dto.comment.CommentResponseDto;
import kr.adapterz.springdatajpa.dto.post.*;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.SessionException;
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
    private final SessionCheck sessionCheck;
    private final CommentRepository commentRepository;

    //게시물 목록 조회
    public List<PostListResponseDto> getPostList(PostListRequestDto request) {
        sessionCheck.check(request.getAccess_session());
        List<Post> posts = postRepository.findAll();
        List<PostListResponseDto> result = new ArrayList<>();

        //각 게시물의 user_id로 작성자 정보 붙이기
        for (Post post : posts) {
            User user = userRepository.findId(post.getUser_id())
                    .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

            PostListResponseDto dto = new PostListResponseDto(post, user);

            result.add(dto);
        }

        return result;
    }

    // 게시물 추가
    public PostResponseDto createPost(PostRequestDto request) {
        sessionCheck.check(request.getAccess_session());
        PostResponseDto postResponseDto= new PostResponseDto();
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
    public PostViewResponseDto getPostView(PostViewRequestDto request) {
        sessionCheck.check(request.getAccess_session());

        Post post = postRepository.findId(request.getPost_id())
                .orElseThrow(() -> new DataNullException());

        User postWriter = userRepository.findId(post.getUser_id())
                .orElseThrow(() -> new DataNullException());

        //postid에 해당하는 comment들의 리스트, 아직 유저 정보가 없음
        List<Comment> comments = commentRepository.findByPostId(post.getPost_id());

        List<CommentResponseDto> commentResponseDtos = new ArrayList<>();

        //각 코멘트 별로 유저의 정보를 찾아서 commentResponseDtos라는 배열에 하나하나 추가함
        for (Comment comment : comments) {
            User commentWriter = userRepository.findId(comment.getUser_id())
                    .orElseThrow(() -> new DataNullException());

            CommentResponseDto commentResponseDto =
                    new CommentResponseDto(comment, commentWriter);

            commentResponseDtos.add(commentResponseDto);
        }

        return new PostViewResponseDto(post, postWriter, commentResponseDtos);
    }

    //게시물 수정
    public PostFixResponseDto fixPost(PostFixRequestDto request) {
        sessionCheck.check(request.getAccess_session());
        PostFixResponseDto postFixResponseDto = new PostFixResponseDto();
        Post post = postRepository.findId(request.getPost_id())
                .orElseThrow(()->new DataNullException());
        //실제 작성자가 맞는지 확인
        if (!post.getUser_id().equals(request.getUser_id())) {
            throw new SessionException();
        }
        post.update(
                request.getTitle(),
                request.getContents(),
                request.getImage_file()
        );
        return postFixResponseDto;
    }
    //게시글 삭제
    public PostDeleteResponseDto deletePost(PostDeleteRequestDto request){
        sessionCheck.check(request.getAccess_session());
        PostDeleteResponseDto postDeleteResponseDto = new PostDeleteResponseDto();
        postRepository.findId(request.getPost_id()).orElseThrow(()->new DataNullException());
        postRepository.deleteById(request.getPost_id());
        return postDeleteResponseDto;
    }
}
