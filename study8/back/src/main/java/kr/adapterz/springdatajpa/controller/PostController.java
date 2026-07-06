package kr.adapterz.springdatajpa.controller;

import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.dto.post.*;

import kr.adapterz.springdatajpa.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    //게시글 목록 조회
    @GetMapping
    public PostPageResponseDto getPostList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return postService.getPostList(page, size);
    }
    //게시글 추가
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponseDto createPost(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody PostRequestDto request
    ){
        return postService.createPost(authorizationHeader, request);
    }
    //게시글 상세조회
    @GetMapping("/{postId}")
    public PostViewResponseDto getPostView(@PathVariable("postId") Long postId) {
        return postService.getPostView(postId);
    }
    //게시글 수정
    @PatchMapping("/{postId}")
    public PostFixResponseDto fixPost(
            @PathVariable("postId") Long postId,
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody PostFixRequestDto request
    ){
        return postService.fixPost(postId, authorizationHeader, request);
    }
    //게시글 삭제
    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public PostDeleteResponseDto deletePost(
            @PathVariable("postId") Long postId,
            @RequestHeader("Authorization") String authorizationHeader
    ){
        return postService.deletePost(postId, authorizationHeader);
    }
    //좋아요
    @PostMapping("/{postId}/likes")
    @ResponseStatus(HttpStatus.CREATED)
    public LikeResponseDto likePost(
            @PathVariable("postId") Long postId,
            @RequestHeader("Authorization") String authorizationHeader
    ){
        return postService.likePost(postId, authorizationHeader);
    }

    //좋아요 취소
    @DeleteMapping("/{postId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public LikeCancelResponseDto cancelLike(
            @PathVariable("postId") Long postId,
            @RequestHeader("Authorization") String authorizationHeader
    ){
        return postService.cancelLike(postId, authorizationHeader);
    }
}
