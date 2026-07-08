package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.comment.*;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    //댓글 등록
    public CommentPostResponseDto commentPost(Long postId, Long loginUserId, CommentPostRequestDto request){
        CommentPostResponseDto commentPostResponseDto = new CommentPostResponseDto();
        User user = getLoginUser(loginUserId);
        Post post =postRepository.findById(postId).orElseThrow(()->new DataNullException("No_Post"));
            Comment comment = new Comment(
                    user,
                    post,
                    request.getCommentContent()
            );
        commentRepository.save(comment);
        //댓글 수 증가
        post.addReply();
        return commentPostResponseDto;
    }

    //댓글 수정
    public CommentFixResponseDto commentFix(Long postId, Long loginUserId, CommentFixRequestDto request){
        CommentFixResponseDto commentFixResponseDto = new CommentFixResponseDto();
        Comment comment= commentRepository.findById(request.getCommentId()).orElseThrow(()-> new DataNullException("No_Comment"));

        if (!comment.getPost().getPostId().equals(postId)) {
            throw new DataNullException("No_Comment");
        }

        if(!comment.getUser().getUserId().equals(loginUserId))
        {
            throw new AuthException("No_Auth");
        }
        comment.changeComment(request.getCommentContent());

        return commentFixResponseDto;
    }

    //댓글 삭제
    public CommentDeleteResponseDto commentDelete(Long postId, Long loginUserId, CommentDeleteRequestDto request){
        CommentDeleteResponseDto commentDeleteResponseDto = new CommentDeleteResponseDto();
        Post post =postRepository.findById(postId).orElseThrow(()->new DataNullException("No_Post"));
        Comment comment = commentRepository.findById(request.getCommentId()).orElseThrow(()->new DataNullException("No_Comment"));

        if (!comment.getPost().getPostId().equals(postId)) {
            throw new DataNullException("No_Comment");
        }

        if (!comment.getUser().getUserId().equals(loginUserId)) {
            throw new AuthException("No_Auth");
        }

        commentRepository.delete(comment);
        //댓글 개수 감소
        post.deleteReply();
        return commentDeleteResponseDto;
    }

    private User getLoginUser(Long loginUserId) {
        return userRepository.findByUserIdAndDeletedFalse(loginUserId)
                .orElseThrow(() -> new DataNullException("No_Account"));
    }
}
