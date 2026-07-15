import { useEffect, useState } from "react";

import { postApi } from "../../api/postApi.js";

function CommentEditor({ postId, editingComment, onSaved, onCancelEdit }) {
    const [content, setContent] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        setContent(editingComment?.content ?? "");
    }, [editingComment]);

    async function handleSubmit(event) {
        event.preventDefault();
        const trimmedContent = content.trim();
        if (!trimmedContent || isSubmitting) {
            return;
        }

        try {
            setIsSubmitting(true);
            setError("");
            if (editingComment) {
                await postApi.updateComment(
                    postId,
                    editingComment.commentId,
                    trimmedContent,
                );
            } else {
                await postApi.createComment(postId, trimmedContent);
            }
            setContent("");
            onCancelEdit();
            await onSaved();
        } catch (submitError) {
            setError(submitError.message || "댓글 처리에 실패했습니다.");
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <form className="comment-form" noValidate onSubmit={handleSubmit}>
            <textarea
                className="comment-input"
                value={content}
                placeholder="댓글을 남겨주세요!"
                onChange={(event) => setContent(event.target.value)}
            />
            <div className="comment-form-divider" />
            {error && <p className="comment-form-error">{error}</p>}
            <button
                type="submit"
                className="comment-submit-button"
                disabled={!content.trim() || isSubmitting}
            >
                {editingComment ? "댓글 수정" : "댓글 등록"}
            </button>
        </form>
    );
}

export default CommentEditor;
