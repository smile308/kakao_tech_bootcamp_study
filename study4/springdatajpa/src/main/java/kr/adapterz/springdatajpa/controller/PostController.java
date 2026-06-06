package kr.adapterz.springdatajpa.controller;

import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.dto.post.*;

import kr.adapterz.springdatajpa.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    //게시글 목록 조회
    @GetMapping
    public List<PostListResponseDto> getPostList(@Valid @RequestBody PostListRequestDto request){
        return postService.getPostList(request);
    }
    //게시글 추가
    @PostMapping
    public PostResponseDto createPost(@Valid @RequestBody PostRequestDto request){
        return postService.createPost(request);
    }
    //게시글 상세조회
    @GetMapping("/{postId}")
    public PostViewResponseDto getPostView(@RequestBody PostViewRequestDto request) {
        return postService.getPostView(request);
    }
    //게시글 수정
    @PatchMapping("/{postId}")
    public PostFixResponseDto fixPost(@Valid @RequestBody PostFixRequestDto request){
        return postService.fixPost(request);
    }
    //게시글 삭제
    @DeleteMapping("/{postId}")
    public PostDeleteResponseDto deletePost(@RequestBody PostDeleteRequestDto request){
        return postService.deletePost(request);
    }
    //좋아요
    @PatchMapping("/{postId}/like")
    public LikeResponseDto likePost(@RequestBody LikeRequestDto request){
        return postService.likePost(request);
    }

    //좋아요 취소
    @PatchMapping("/{postId}/cancel")
    public LikeCancelResponseDto cancelLike(@RequestBody LikeCancelRequestDto request){
        return postService.cancelLike(request);
    }
}
