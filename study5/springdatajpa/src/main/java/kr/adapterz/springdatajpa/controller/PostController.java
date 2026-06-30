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
    public List<PostListResponseDto> getPostList(){
        return postService.getPostList();
    }
    //게시글 추가
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponseDto createPost(@Valid @RequestBody PostRequestDto request){
        return postService.createPost(request);
    }
    //게시글 상세조회
    @GetMapping("/{postId}")
    public PostViewResponseDto getPostView(@PathVariable("postId") Long postId) {
        return postService.getPostView(postId);
    }
    //게시글 수정
    @PatchMapping("/{postId}")
    public PostFixResponseDto fixPost(@PathVariable("postId") Long postId,@Valid @RequestBody PostFixRequestDto request){
        return postService.fixPost(postId,request);
    }
    //게시글 삭제
    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public PostDeleteResponseDto deletePost(@PathVariable("postId") Long postId,@RequestBody PostDeleteRequestDto request){
        return postService.deletePost(postId,request);
    }
    //좋아요
    @PostMapping("/{postId}/likes")
    @ResponseStatus(HttpStatus.CREATED)
    public LikeResponseDto likePost(@PathVariable("postId") Long postId,@RequestBody LikeRequestDto request){
        return postService.likePost(postId,request);
    }

    //좋아요 취소
    @DeleteMapping("/{postId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public LikeCancelResponseDto cancelLike(@PathVariable("postId") Long postId, @RequestBody LikeCancelRequestDto request){
        return postService.cancelLike(postId,request);
    }

    //게시글 신고
    @PostMapping("/{postId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponseDto reportPost(@PathVariable("postId") Long postId, @RequestBody ReportRequestDto request){
        return postService.reportPost(postId,request);
    }
}
