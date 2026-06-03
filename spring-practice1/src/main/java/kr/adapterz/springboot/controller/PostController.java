package kr.adapterz.springboot.controller;

import jakarta.validation.Valid;
import kr.adapterz.springboot.dto.PostRequestDto;
import kr.adapterz.springboot.dto.PostResponseDto;
import kr.adapterz.springboot.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping("/users/{userId}")
    public PostResponseDto createPost(
            @PathVariable Long userId,
            @Valid @RequestBody PostRequestDto request
    ) {
        return postService.createPost(userId, request);
    }

    @GetMapping("/{postId}")
    public PostResponseDto getPost(@PathVariable Long postId) {
        return postService.getPost(postId);
    }

    @PutMapping("/{postId}")
    public PostResponseDto updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostRequestDto request
    ) {
        return postService.updatePost(postId, request);
    }

    @DeleteMapping("/{postId}")
    public void deletePost(@PathVariable Long postId) {
        postService.deletePost(postId);
    }
}