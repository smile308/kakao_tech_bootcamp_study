import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { postApi } from "../../api/postApi.js";
import ConfirmModal from "../../components/common/ConfirmModal.jsx";
import ErrorView from "../../components/common/ErrorView.jsx";
import { ErrorBoundary } from "react-error-boundary";
import PageLayout from "../../components/layout/PageLayout.jsx";
import CommentSection from "../../components/posts/CommentSection.jsx";
import PostDetailCard from "../../components/posts/PostDetailCard.jsx";
import { getErrorMessage } from "../../utils/errorMessage.js";
import "../../styles/posts.css";

const REPORT_BLOCK_THRESHOLD = 5;

function PostDetailPage() {
    const { postId } = useParams();
    const navigate = useNavigate();
    const postRequestRef = useRef(null);
    const [post, setPost] = useState(null);
    const [comments, setComments] = useState([]);
    const [loadError, setLoadError] = useState(null);
    const [retryVersion, setRetryVersion] = useState(0);
    const [isLikeSubmitting, setIsLikeSubmitting] = useState(false);
    const [modal, setModal] = useState(null);
    const likeRequestRef = useRef(false);

    useEffect(() => {
        let active = true;

        if (postRequestRef.current?.postId !== postId) {
            setPost(null);
            setComments([]);
            setLoadError(null);
            postRequestRef.current = {
                postId,
                promise: postApi.getPost(postId),
            };
        }

        const request = postRequestRef.current.promise;

        request
            .then((result) => {
                if (!active) {
                    return;
                }
                setPost(result);
                setComments(result.comments);
                setLoadError(null);
            })
            .catch((requestError) => {
                if (active) {
                    setLoadError(requestError);
                }
                if (postRequestRef.current?.promise === request) {
                    postRequestRef.current = null;
                }
            });

        return () => {
            active = false;
        };
    }, [postId, retryVersion]);

    function retryLoadPost() {
        postRequestRef.current = null;
        setLoadError(null);
        setRetryVersion((previous) => previous + 1);
    }

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
            window.alert(getErrorMessage(actionError));
        }
    }

    async function handleLike() {
        if (likeRequestRef.current) {
            return;
        }

        try {
            likeRequestRef.current = true;
            setIsLikeSubmitting(true);
            const result = post.isLiked
                ? await postApi.unlikePost(post.postId)
                : await postApi.likePost(post.postId);
            setPost((previous) => ({
                ...previous,
                isLiked: !previous.isLiked,
                likeCount: result?.likeCount ?? previous.likeCount + (previous.isLiked ? -1 : 1),
            }));
        } catch (likeError) {
            window.alert(getErrorMessage(likeError, "좋아요 처리에 실패했습니다."));
        } finally {
            likeRequestRef.current = false;
            setIsLikeSubmitting(false);
        }
    }

    function requestReport() {
        openModal(
            "글을 신고하겠습니까?",
            "신고 후에는 취소할 수 없습니다.",
            async () => {
                const result = await postApi.reportPost(post.postId);
                const nextReportCount =
                    result?.reportCount ?? post.reportCount + 1;

                if (nextReportCount >= REPORT_BLOCK_THRESHOLD) {
                    navigate("/posts", { replace: true });
                    return;
                }

                setPost((previous) => ({
                    ...previous,
                    isReported: true,
                    reportCount: nextReportCount,
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
            {loadError ? (
                <ErrorView
                    title="게시글을 불러오지 못했습니다."
                    message={getErrorMessage(loadError, "잠시 후 다시 시도해주세요.")}
                    onRetry={retryLoadPost}
                />
            ) : post ? (
                <>
                    <ErrorBoundary fallback={<ErrorView title="게시글을 표시하지 못했습니다." />}>
                        <PostDetailCard
                            post={post}
                            commentCount={comments.length}
                            isLikeSubmitting={isLikeSubmitting}
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
