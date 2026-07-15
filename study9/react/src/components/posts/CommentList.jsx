import ErrorBoundary from "../common/error/ErrorBoundary.jsx";
import CommentItem from "./CommentItem.jsx";

function CommentList({ comments, onEdit, onDelete }) {
    return (
        <section className="comment-list">
            {comments.map((comment) => (
                <ErrorBoundary
                    key={comment.commentId}
                    resetKey={comment.commentId}
                    fallback={<div className="item-error-view">이 댓글을 표시할 수 없습니다.</div>}
                >
                    <CommentItem
                        comment={comment}
                        onEdit={onEdit}
                        onDelete={onDelete}
                    />
                </ErrorBoundary>
            ))}
        </section>
    );
}

export default CommentList;
