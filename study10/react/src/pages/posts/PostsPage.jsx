import { useCallback, useEffect, useRef, useState } from "react";

import { postApi } from "../../api/postApi.js";
import PageLayout from "../../components/layout/PageLayout.jsx";
import PostBoard from "../../components/posts/PostBoard.jsx";
import "../../styles/posts.css";

function PostsPage() {
    const [posts, setPosts] = useState([]);
    const [currentPage, setCurrentPage] = useState(0);
    const [hasNextPage, setHasNextPage] = useState(true);
    const loadingRef = useRef(false);
    const initialRequestRef = useRef(false);

    const loadPage = useCallback(async (page) => {
        if (loadingRef.current) {
            return;
        }

        try {
            loadingRef.current = true;
            const result = await postApi.getPosts({ page, size: 10 });
            setPosts((previous) => page === 0 ? result.posts : [...previous, ...result.posts]);
            setHasNextPage(result.hasNextPage);
            setCurrentPage(page + 1);
        } catch (requestError) {
            console.error("게시글 목록 조회 실패", requestError);
        } finally {
            loadingRef.current = false;
        }
    }, []);

    useEffect(() => {
        if (!initialRequestRef.current) {
            initialRequestRef.current = true;
            void loadPage(0);
        }
    }, [loadPage]);

    return (
        <PageLayout
            pageClassName="posts-page"
            mainClassName="posts-main"
            title="대나무숲"
        >
            <PostBoard
                posts={posts}
                hasNextPage={hasNextPage}
                onLoadMore={() => loadPage(currentPage)}
            />
        </PageLayout>
    );
}

export default PostsPage;
