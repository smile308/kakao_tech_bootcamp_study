package kr.adapterz.springdatajpa.controller;

import kr.adapterz.springdatajpa.auth.CustomUserDetails;
import kr.adapterz.springdatajpa.dto.comment.*;
import kr.adapterz.springdatajpa.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponseDto commentPost(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CommentPostRequestDto request
    ){
        return commentService.commentPost(postId, userDetails.getUserId(), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public CommentDeleteResponseDto commentDelete(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CommentDeleteRequestDto request
    ){
        return commentService.commentDelete(postId, userDetails.getUserId(), request);
    }

    @PatchMapping
    public CommentResponseDto commentFix(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CommentFixRequestDto request
    ){
        return commentService.commentFix(postId, userDetails.getUserId(), request);
    }
}
