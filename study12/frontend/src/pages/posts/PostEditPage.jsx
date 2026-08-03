import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { postApi } from "../../api/postApi.js";
import ErrorView from "../../components/common/ErrorView.jsx";
import { ErrorBoundary } from "react-error-boundary";
import PageLayout from "../../components/layout/PageLayout.jsx";
import PostEditor from "../../components/posts/PostEditor.jsx";
import { getErrorMessage } from "../../utils/errorMessage.js";
import "../../styles/posts.css";

const REPORT_BLOCK_THRESHOLD = 5;

function PostEditPage() {
    const { postId } = useParams();
    const navigate = useNavigate();
    const [originalPost, setOriginalPost] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [retryVersion, setRetryVersion] = useState(0);

    useEffect(() => {
        const controller = new AbortController();
        setOriginalPost(null);
        setLoadError(null);

        postApi.getPost(postId, { signal: controller.signal })
            .then((post) => {
                if (!post.isMine || post.reportCount >= REPORT_BLOCK_THRESHOLD) {
                    navigate(`/posts/${postId}`, { replace: true });
                    return;
                }
                setOriginalPost(post);
                setLoadError(null);
            })
            .catch((requestError) => {
                if (controller.signal.aborted || requestError?.name === "AbortError") {
                    return;
                }
                setLoadError(requestError);
            });

        return () => {
            controller.abort();
        };
    }, [navigate, postId, retryVersion]);

    function retryLoadPost() {
        setLoadError(null);
        setRetryVersion((previous) => previous + 1);
    }

    return (
        <PageLayout
            pageClassName="post-edit-page"
            mainClassName="post-edit-main"
            title="글 수정"
            showBack
            backTo={`/posts/${postId}`}
        >
            <h2 className="post-edit-title">대나무숲 글 수정</h2>
            {loadError ? (
                <ErrorView
                    title="수정할 게시글을 불러오지 못했습니다."
                    message={getErrorMessage(loadError, "잠시 후 다시 시도해주세요.")}
                    onRetry={retryLoadPost}
                />
            ) : originalPost ? (
                <ErrorBoundary fallback={<ErrorView title="게시글 수정 양식을 표시하지 못했습니다." />}>
                    <PostEditor
                        mode="edit"
                        initialValues={originalPost}
                        onSubmit={async (payload) => {
                            await postApi.updatePost(postId, {
                                ...payload,
                                version: originalPost.version,
                            });
                            navigate(`/posts/${postId}`, { replace: true });
                        }}
                    />
                </ErrorBoundary>
            ) : null}
        </PageLayout>
    );
}

export default PostEditPage;
