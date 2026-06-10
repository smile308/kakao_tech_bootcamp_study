package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.comment.*;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    //댓글 등록
    public CommentPostResponseDto commentPost(CommentPostRequestDto request){
        CommentPostResponseDto commentPostResponseDto = new CommentPostResponseDto();
        Post post =postRepository.findId(request.getPost_id()).orElseThrow(()->new DataNullException("No_Post"));
            Comment comment = new Comment(
                    request.getUser_id(),
                    request.getPost_id(),
                    request.getComment_content()
            );
        commentRepository.save(comment);
        //댓글 수 증가
        post.addReply();
        return commentPostResponseDto;
    }

    //댓글 수정
    public CommentFixResponseDto commentFix(CommentFixRequestDto request){
        CommentFixResponseDto commentFixResponseDto = new CommentFixResponseDto();
        Comment comment= commentRepository.findId(request.getComment_id()).orElseThrow(()-> new DataNullException("No_Comment"));
        comment.changeComment(request.getComment_content());

        return commentFixResponseDto;
    }

    //댓글 삭제
    public CommentDeleteResponseDto commentDelete(CommentDeleteRequestDto request){
        CommentDeleteResponseDto commentDeleteResponseDto = new CommentDeleteResponseDto();
        Post post =postRepository.findId(request.getPost_id()).orElseThrow(()->new DataNullException("No_Post"));
        commentRepository.findId(request.getComment_id()).orElseThrow(()->new DataNullException("No_Comment"));
        commentRepository.deleteById(request.getComment_id());
        //댓글 개수 감소
        post.deleteReply();
        return commentDeleteResponseDto;
    }
}
