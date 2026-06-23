package kr.adapterz.springdatajpa.controller;

import kr.adapterz.springdatajpa.dto.comment.*;
import kr.adapterz.springdatajpa.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts/{post_id}/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    //댓글 등록
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentPostResponseDto commentPost(@PathVariable Long post_id, @RequestBody CommentPostRequestDto request){
        return commentService.commentPost(post_id, request);
    }

    //댓글 삭제
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CommentDeleteResponseDto commentDelete(@PathVariable Long post_id,@RequestBody CommentDeleteRequestDto request){
        return commentService.commentDelete(post_id, request);
    }

    //댓글 수정
    @PatchMapping
    public CommentFixResponseDto commentFix(@RequestBody CommentFixRequestDto request){
        return commentService.commentFix(request);
    }
}
