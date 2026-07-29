package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.comment.*;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.CounterUpdateException;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.ForbiddenException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.PostCounterRepository;
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
    private final PostCounterRepository postCounterRepository;
    private final UserRepository userRepository;

    //댓글 등록
    public CommentResponseDto commentPost(Long postId, Long loginUserId, CommentPostRequestDto request){
        User user = getLoginUser(loginUserId);
        Post post = getActivePostForInteraction(postId);
            Comment comment = new Comment(
                    user,
                    post,
                    request.getCommentContent()
            );
        validateCounterUpdate(
                postCounterRepository.incrementReplyCount(postId)
        );
        validateActivePostWithLock(postId);
        commentRepository.save(comment);
        return new CommentResponseDto(comment, user, true);
    }

    //댓글 수정
    public CommentResponseDto commentFix(Long postId, Long loginUserId, CommentFixRequestDto request){
        validateActivePostWithLock(postId);
        Comment comment= commentRepository.findById(request.getCommentId()).orElseThrow(()-> new DataNullException("No_Comment"));

        if (!comment.getPost().getPostId().equals(postId)) {
            throw new DataNullException("No_Comment");
        }

        if(!comment.getUser().getUserId().equals(loginUserId))
        {
            throw new ForbiddenException("Forbidden_Access");
        }
        comment.changeComment(request.getCommentContent());

        return new CommentResponseDto(comment, comment.getUser(), true);
    }

    //댓글 삭제
    public CommentDeleteResponseDto commentDelete(Long postId, Long loginUserId, CommentDeleteRequestDto request){
        CommentDeleteResponseDto commentDeleteResponseDto = new CommentDeleteResponseDto();
        getActivePostForInteraction(postId);
        Comment comment = commentRepository.findById(request.getCommentId()).orElseThrow(()->new DataNullException("No_Comment"));

        if (!comment.getPost().getPostId().equals(postId)) {
            throw new DataNullException("No_Comment");
        }

        if (!comment.getUser().getUserId().equals(loginUserId)) {
            throw new ForbiddenException("Forbidden_Access");
        }

        validateCounterUpdate(
                postCounterRepository.decrementReplyCount(postId)
        );
        validateActivePostWithLock(postId);
        commentRepository.delete(comment);
        return commentDeleteResponseDto;
    }

    private User getLoginUser(Long loginUserId) {
        return userRepository.findByUserIdAndDeletedFalse(loginUserId)
                .orElseThrow(() -> new DataNullException("No_Account"));
    }

    private Post getActivePostForInteraction(Long postId) {
        return postRepository.findActivePostForInteraction(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));
    }

    private void validateActivePostWithLock(Long postId) {
        postRepository.findActivePostForInteractionCheck(postId)
                .orElseThrow(() -> new DataNullException("No_Post"));
    }

    private void validateCounterUpdate(int updatedRowCount) {
        if (updatedRowCount != 1) {
            throw new CounterUpdateException();
        }
    }
}
