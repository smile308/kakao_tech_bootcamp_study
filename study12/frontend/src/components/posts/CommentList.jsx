import { ErrorBoundary } from "react-error-boundary";
import CommentItem from "./CommentItem.jsx";

function CommentList({ comments, onEdit, onDelete }) {
    return (
        <section className="comment-list">
            {comments.map((comment) => (
                <ErrorBoundary
                    key={comment.commentId}
                    resetKeys={[comment.commentId]}
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
