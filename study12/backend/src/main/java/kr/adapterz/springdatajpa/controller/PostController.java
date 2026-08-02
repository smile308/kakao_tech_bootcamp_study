package kr.adapterz.springdatajpa.controller;

import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.auth.CustomUserDetails;
import kr.adapterz.springdatajpa.dto.post.*;

import kr.adapterz.springdatajpa.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    @GetMapping
    public PostPageResponseDto getPostList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return postService.getPostList(page, size);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponseDto createPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PostRequestDto request
    ){
        return postService.createPost(userDetails.getUserId(), request);
    }
    @GetMapping("/{postId}")
    public PostViewResponseDto getPostView(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return postService.getPostView(postId, userDetails.getUserId());
    }
    @PatchMapping("/{postId}")
    public PostFixResponseDto fixPost(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PostFixRequestDto request
    ){
        return postService.fixPost(postId, userDetails.getUserId(), request);
    }
    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public PostDeleteResponseDto deletePost(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PostDeleteRequestDto request
    ){
        return postService.deletePost(postId, userDetails.getUserId(), request);
    }
    @PostMapping("/{postId}/likes")
    @ResponseStatus(HttpStatus.CREATED)
    public LikeResponseDto likePost(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ){
        return postService.likePost(postId, userDetails.getUserId());
    }

    @DeleteMapping("/{postId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public LikeCancelResponseDto cancelLike(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ){
        return postService.cancelLike(postId, userDetails.getUserId());
    }

    @PostMapping("/{postId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public PostReportResponseDto reportPost(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails){
        return postService.reportPost(postId, userDetails.getUserId());
    }
}
