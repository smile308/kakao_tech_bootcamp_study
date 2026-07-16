import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { postApi } from "../../api/postApi.js";
import ConfirmModal from "../../components/common/ConfirmModal.jsx";
import ErrorView from "../../components/common/ErrorView.jsx";
import { ErrorBoundary } from "react-error-boundary";
import PageLayout from "../../components/layout/PageLayout.jsx";
import CommentSection from "../../components/posts/CommentSection.jsx";
import PostDetailCard from "../../components/posts/PostDetailCard.jsx";
import "../../styles/posts.css";

function PostDetailPage() {
    const { postId } = useParams();
    const navigate = useNavigate();
    const initialRequestRef = useRef(false);
    const [post, setPost] = useState(null);
    const [comments, setComments] = useState([]);
    const [modal, setModal] = useState(null);

    const loadPost = useCallback(async () => {
        try {
            const result = await postApi.getPost(postId);
            setPost(result);
            setComments(result.comments);
        } catch (requestError) {
            console.error("게시글 상세 조회 실패", requestError);
        }
    }, [postId]);

    useEffect(() => {
        if (!initialRequestRef.current) {
            initialRequestRef.current = true;
            void loadPost();
        }
    }, [loadPost]);

    function openModal(title, description, onConfirm) {
        setModal({ title, description, onConfirm });
    }

    async function confirmModal() {
        const action = modal?.onConfirm;
        setModal(null);
        if (!action) {
            return;
        }

        try {
            await action();
        } catch (actionError) {
            window.alert(actionError.message || "요청 처리에 실패했습니다.");
        }
    }

    async function handleLike() {
        try {
            const result = post.isLiked
                ? await postApi.unlikePost(post.postId)
                : await postApi.likePost(post.postId);
            setPost((previous) => ({
                ...previous,
                isLiked: !previous.isLiked,
                likeCount: result?.likeCount ?? previous.likeCount + (previous.isLiked ? -1 : 1),
            }));
        } catch (likeError) {
            window.alert(likeError.message || "좋아요 처리에 실패했습니다.");
        }
    }

    function requestReport() {
        openModal(
            "글을 신고하겠습니까?",
            "신고 후에는 취소할 수 없습니다.",
            async () => {
                const result = await postApi.reportPost(post.postId);
                setPost((previous) => ({
                    ...previous,
                    isReported: true,
                    reportCount: result?.reportCount ?? previous.reportCount + 1,
                }));
            },
        );
    }

    function requestPostDelete() {
        openModal(
            "글을 삭제하겠습니까?",
            "삭제한 내용은 복구할 수 없습니다.",
            async () => {
                await postApi.deletePost(post.postId);
                navigate("/posts", { replace: true });
            },
        );
    }

    function requestCommentDelete(commentId) {
        openModal(
            "댓글을 삭제하겠습니까?",
            "삭제한 내용은 복구할 수 없습니다.",
            async () => {
                await postApi.deleteComment(post.postId, commentId);
                setComments((previous) => (
                    previous.filter((comment) => comment.commentId !== commentId)
                ));
            },
        );
    }

    function handleCommentCreated(comment) {
        setComments((previous) => [...previous, comment]);
    }

    function handleCommentUpdated(comment) {
        setComments((previous) => previous.map((item) => (
            item.commentId === comment.commentId ? comment : item
        )));
    }

    return (
        <PageLayout
            pageClassName="post-detail-page"
            mainClassName="post-detail-main"
            title="글 상세"
            showBack
            backTo="/posts"
        >
            {post ? (
                <>
                    <ErrorBoundary fallback={<ErrorView title="게시글을 표시하지 못했습니다." />}>
                        <PostDetailCard
                            post={post}
                            commentCount={comments.length}
                            onDelete={requestPostDelete}
                            onReport={requestReport}
                            onLike={handleLike}
                        />
                    </ErrorBoundary>
                    <ErrorBoundary fallback={<ErrorView title="댓글 영역을 표시하지 못했습니다." />}>
                        <CommentSection
                            postId={post.postId}
                            comments={comments}
                            onCommentCreated={handleCommentCreated}
                            onCommentUpdated={handleCommentUpdated}
                            onRequestDelete={requestCommentDelete}
                        />
                    </ErrorBoundary>
                </>
            ) : null}

            <ConfirmModal
                isOpen={Boolean(modal)}
                title={modal?.title}
                description={modal?.description}
                onCancel={() => setModal(null)}
                onConfirm={confirmModal}
            />
        </PageLayout>
    );
}

export default PostDetailPage;
