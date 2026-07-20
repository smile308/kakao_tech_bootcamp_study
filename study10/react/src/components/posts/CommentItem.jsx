import AuthorSummary from "./AuthorSummary.jsx";

function CommentItem({ comment, onEdit, onDelete }) {
    return (
        <article className="comment-item">
            <AuthorSummary
                variant="comment"
                profileImage={comment.authorProfileImage}
                nickname={comment.authorNickname}
                createdAt={comment.createdAt}
            />
            <p className="comment-content">{comment.content}</p>
            {comment.isMine && (
                <div className="comment-actions">
                    <button
                        type="button"
                        className="comment-action-button"
                        onClick={() => onEdit(comment)}
                    >
                        수정
                    </button>
                    <button
                        type="button"
                        className="comment-action-button"
                        onClick={() => onDelete(comment.commentId)}
                    >
                        삭제
                    </button>
                </div>
            )}
        </article>
    );
}

export default CommentItem;
