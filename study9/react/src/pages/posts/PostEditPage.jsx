import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { postApi } from "../../api/postApi.js";
import ErrorView from "../../components/common/ErrorView.jsx";
import ErrorBoundary from "../../components/common/error/ErrorBoundary.jsx";
import PageLayout from "../../components/layout/PageLayout.jsx";
import PostEditor from "../../components/posts/PostEditor.jsx";
import "../../styles/posts.css";

const REPORT_BLOCK_THRESHOLD = 5;

function PostEditPage() {
    const { postId } = useParams();
    const navigate = useNavigate();
    const postRequestRef = useRef(null);
    const [originalPost, setOriginalPost] = useState(null);

    useEffect(() => {
        let active = true;
        if (postRequestRef.current?.postId !== postId) {
            postRequestRef.current = {
                postId,
                promise: postApi.getPost(postId),
            };
        }

        postRequestRef.current.promise
            .then((post) => {
                if (!active) {
                    return;
                }
                if (!post.isMine || post.reportCount >= REPORT_BLOCK_THRESHOLD) {
                    navigate(`/posts/${postId}`, { replace: true });
                    return;
                }
                setOriginalPost(post);
            })
            .catch((requestError) => {
                if (active) {
                    console.error("수정할 게시글 조회 실패", requestError);
                }
            });

        return () => {
            active = false;
        };
    }, [navigate, postId]);

    return (
        <PageLayout
            pageClassName="post-edit-page"
            mainClassName="post-edit-main"
            title="글 수정"
            showBack
            backTo={`/posts/${postId}`}
        >
            <h2 className="post-edit-title">대나무숲 글 수정</h2>
            {originalPost ? (
                <ErrorBoundary fallback={<ErrorView title="게시글 수정 양식을 표시하지 못했습니다." />}>
                    <PostEditor
                        mode="edit"
                        initialValues={originalPost}
                        onSubmit={async (payload) => {
                            await postApi.updatePost(postId, payload);
                            navigate(`/posts/${postId}`, { replace: true });
                        }}
                    />
                </ErrorBoundary>
            ) : null}
        </PageLayout>
    );
}

export default PostEditPage;
