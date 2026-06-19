package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.comment.*;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    //댓글 등록
    public CommentPostResponseDto commentPost(Long post_id, CommentPostRequestDto request){
        CommentPostResponseDto commentPostResponseDto = new CommentPostResponseDto();
        Post post =postRepository.findById(post_id).orElseThrow(()->new DataNullException("No_Post"));
            Comment comment = new Comment(
                    request.getUser_id(),
                    post_id,
                    request.getComment_content()
            );
        commentRepository.save(comment);
        //댓글 수 증가
        post.addReply();
        return commentPostResponseDto;
    }

    //댓글 수정
    public CommentFixResponseDto commentFix(Long post_id, CommentFixRequestDto request){
        CommentFixResponseDto commentFixResponseDto = new CommentFixResponseDto();
        Comment comment= commentRepository.findById(request.getComment_id()).orElseThrow(()-> new DataNullException("No_Comment"));
        comment.changeComment(request.getComment_content());

        return commentFixResponseDto;
    }

    //댓글 삭제
    public CommentDeleteResponseDto commentDelete(Long post_id, CommentDeleteRequestDto request){
        CommentDeleteResponseDto commentDeleteResponseDto = new CommentDeleteResponseDto();
        Post post =postRepository.findById(post_id).orElseThrow(()->new DataNullException("No_Post"));
        commentRepository.findById(request.getComment_id()).orElseThrow(()->new DataNullException("No_Comment"));
        commentRepository.deleteById(request.getComment_id());
        //댓글 개수 감소
        post.deleteReply();
        return commentDeleteResponseDto;
    }
}
