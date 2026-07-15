import { useState } from "react";

import ErrorView from "../common/ErrorView.jsx";
import ErrorBoundary from "../common/error/ErrorBoundary.jsx";
import CommentEditor from "./CommentEditor.jsx";
import CommentList from "./CommentList.jsx";

function CommentSection({ postId, comments, onRefresh, onRequestDelete }) {
    const [editingComment, setEditingComment] = useState(null);

    return (
        <>
            <ErrorBoundary fallback={<ErrorView title="댓글 입력창을 표시하지 못했습니다." />}>
                <CommentEditor
                    postId={postId}
                    editingComment={editingComment}
                    onSaved={onRefresh}
                    onCancelEdit={() => setEditingComment(null)}
                />
            </ErrorBoundary>
            <ErrorBoundary fallback={<ErrorView title="댓글 목록을 표시하지 못했습니다." />}>
                <CommentList
                    comments={comments}
                    onEdit={setEditingComment}
                    onDelete={onRequestDelete}
                />
            </ErrorBoundary>
        </>
    );
}

export default CommentSection;
