import { useNavigate } from "react-router-dom";

import { postApi } from "../../api/postApi.js";
import ErrorView from "../../components/common/ErrorView.jsx";
import ErrorBoundary from "../../components/common/error/ErrorBoundary.jsx";
import PageLayout from "../../components/layout/PageLayout.jsx";
import PostEditor from "../../components/posts/PostEditor.jsx";
import "../../styles/posts.css";

function PostCreatePage() {
    const navigate = useNavigate();

    return (
        <PageLayout
            pageClassName="post-create-page"
            mainClassName="post-create-main"
            title="글 작성"
            showBack
            backTo="/posts"
        >
            <h2 className="post-create-title">대나무숲 글 작성</h2>
            <ErrorBoundary fallback={<ErrorView title="게시글 작성 양식을 표시하지 못했습니다." />}>
                <PostEditor
                    mode="create"
                    onSubmit={async (payload) => {
                        await postApi.createPost(payload);
                        navigate("/posts", { replace: true });
                    }}
                />
            </ErrorBoundary>
        </PageLayout>
    );
}

export default PostCreatePage;
