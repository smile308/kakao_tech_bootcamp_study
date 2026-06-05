package kr.adapterz.springdatajpa.controller;

import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.dto.post.PostListRequestDto;
import kr.adapterz.springdatajpa.dto.post.PostListResponseDto;

import kr.adapterz.springdatajpa.dto.post.PostRequestDto;
import kr.adapterz.springdatajpa.dto.post.PostResponseDto;
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

    @PostMapping
    public PostResponseDto createPost(@Valid @RequestBody PostRequestDto request){
        return postService.createPost(request);
    }
}
