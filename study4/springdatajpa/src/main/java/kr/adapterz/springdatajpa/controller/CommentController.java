package kr.adapterz.springdatajpa.controller;

import kr.adapterz.springdatajpa.dto.comment.*;
import kr.adapterz.springdatajpa.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    //댓글 등록
    @PostMapping
    public CommentPostResponseDto commentPost(@RequestBody CommentPostRequestDto request){
        return commentService.commentPost(request);
    }

    //댓글 삭제
    @DeleteMapping
    public CommentDeleteResponseDto commentDelete(@RequestBody CommentDeleteRequestDto request){
        return commentService.commentDelete(request);
    }

    //댓글 수정
    @PatchMapping
    public CommentFixResponseDto commentFix(@RequestBody CommentFixRequestDto request){
        return commentService.commentFix(request);
    }
}
