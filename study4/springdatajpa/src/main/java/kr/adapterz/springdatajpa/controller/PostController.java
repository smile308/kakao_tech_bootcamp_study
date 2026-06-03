package kr.adapterz.springdatajpa.controller;

import kr.adapterz.springdatajpa.dto.post.PostListResponseDto;
import kr.adapterz.springdatajpa.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    @GetMapping
    public List<PostListResponseDto> getPostList(){
        return postService.getPostList();
    }
}
