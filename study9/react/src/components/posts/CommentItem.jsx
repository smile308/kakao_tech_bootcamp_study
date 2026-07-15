import { formatDateTime } from "../../utils/formatter.js";

function CommentItem({ comment, onEdit, onDelete }) {
    return (
        <article className="comment-item">
            <div
                className={`comment-author-image-box ${comment.authorProfileImage ? "" : "is-empty"}`.trim()}
            >
                {comment.authorProfileImage && (
                    <img
                        src={comment.authorProfileImage}
                        className="comment-author-image"
                        alt="댓글 작성자 프로필 이미지"
                    />
                )}
            </div>
            <p className="comment-author-name">{comment.authorNickname}</p>
            <p className="comment-created-at">{formatDateTime(comment.createdAt)}</p>
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
