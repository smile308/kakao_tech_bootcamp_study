package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.auth.SessionCheck;
import kr.adapterz.springdatajpa.dto.comment.*;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor

public class CommentService {
    private final SessionCheck sessionCheck;
    private final CommentRepository commentRepository;

    //댓글 등록
    public CommentPostResponseDto commentPost(CommentPostRequestDto request){
    //세션 검사
    sessionCheck.check(request.getAccess_session());
        CommentPostResponseDto commentPostResponseDto = new CommentPostResponseDto();
            Comment comment = new Comment(
                    request.getUser_id(),
                    request.getPost_id(),
                    request.getComment_content()
            );
        commentRepository.save(comment);
        return commentPostResponseDto;
    }

    //댓글 수정
    public CommentFixResponseDto commentFix(CommentFixRequestDto request){
        sessionCheck.check(request.getAccess_session());
        CommentFixResponseDto commentFixResponseDto = new CommentFixResponseDto();
        Comment comment= commentRepository.findId(request.getComment_id()).orElseThrow(()-> new DataNullException());
        comment.changeComment(request.getComment_content());

        return commentFixResponseDto;
    }

    //댓글 삭제
    public CommentDeleteResponseDto commentDelete(CommentDeleteRequestDto request){
        sessionCheck.check(request.getAccess_session());
        CommentDeleteResponseDto commentDeleteResponseDto = new CommentDeleteResponseDto();
        commentRepository.deleteById(request.getComment_id());
        return commentDeleteResponseDto;
    }
}
