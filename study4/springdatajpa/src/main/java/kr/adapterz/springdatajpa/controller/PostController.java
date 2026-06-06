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
    public List<PostListResponseDto> getPostList(){
        return postService.getPostList();
    }
    //게시글 추가
    @PostMapping
    public PostResponseDto createPost(@Valid @RequestBody PostRequestDto request){
        return postService.createPost(request);
    }
    //게시글 상세조회
    @GetMapping("/{post_id}")
    public PostViewResponseDto getPostView(@PathVariable Long post_id) {
        return postService.getPostView(post_id);
    }
    //게시글 수정
    @PatchMapping("/{post_id}")
    public PostFixResponseDto fixPost(@Valid @RequestBody PostFixRequestDto request){
        return postService.fixPost(request);
    }
    //게시글 삭제
    @DeleteMapping("/{post_id}")
    public PostDeleteResponseDto deletePost(@RequestBody PostDeleteRequestDto request){
        return postService.deletePost(request);
    }
    //좋아요
    @PostMapping("/{post_id}/likes")
    public LikeResponseDto likePost(@RequestBody LikeRequestDto request){
        return postService.likePost(request);
    }

    //좋아요 취소
    @DeleteMapping("/{post_id}/cancel")
    public LikeCancelResponseDto cancelLike(@RequestBody LikeCancelRequestDto request){
        return postService.cancelLike(request);
    }
}
