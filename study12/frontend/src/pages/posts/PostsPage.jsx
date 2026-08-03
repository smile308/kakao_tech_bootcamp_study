import { useCallback, useEffect, useRef, useState } from "react";

import { postApi } from "../../api/postApi.js";
import ErrorView from "../../components/common/ErrorView.jsx";
import PageLayout from "../../components/layout/PageLayout.jsx";
import PostBoard from "../../components/posts/PostBoard.jsx";
import { getErrorMessage } from "../../utils/errorMessage.js";
import "../../styles/posts.css";

function PostsPage() {
    const [posts, setPosts] = useState([]);
    const [hasNextPage, setHasNextPage] = useState(true);
    const [loadError, setLoadError] = useState(null);
    const [failedPage, setFailedPage] = useState(null);
    const loadingRef = useRef(false);
    const requestControllerRef = useRef(null);
    const nextPageRef = useRef(0);

    const loadPage = useCallback(async (page) => {
        if (loadingRef.current) {
            return;
        }

        const controller = new AbortController();
        requestControllerRef.current = controller;
        loadingRef.current = true;

        try {
            setLoadError(null);
            const result = await postApi.getPosts({
                page,
                size: 10,
                signal: controller.signal,
            });

            if (controller.signal.aborted) {
                return;
            }

            setPosts((previous) => page === 0 ? result.posts : [...previous, ...result.posts]);
            setHasNextPage(result.hasNextPage);
            nextPageRef.current = page + 1;
            setFailedPage(null);
        } catch (requestError) {
            if (controller.signal.aborted || requestError?.name === "AbortError") {
                return;
            }
            if (requestControllerRef.current !== controller) {
                return;
            }
            setLoadError(requestError);
            setFailedPage(page);
        } finally {
            if (requestControllerRef.current === controller) {
                requestControllerRef.current = null;
                loadingRef.current = false;
            }
        }
    }, []);

    const cancelCurrentRequest = useCallback(() => {
        requestControllerRef.current?.abort();
        requestControllerRef.current = null;
        loadingRef.current = false;
    }, []);

    const loadNextPage = useCallback(() => {
        void loadPage(nextPageRef.current);
    }, [loadPage]);

    useEffect(() => {
        void loadPage(0);

        return () => {
            cancelCurrentRequest();
        };
    }, [cancelCurrentRequest, loadPage]);

    function retryLoadPage() {
        const page = failedPage ?? 0;
        setLoadError(null);

        if (page === 0) {
            nextPageRef.current = 0;
            setPosts([]);
            setHasNextPage(true);
        }

        void loadPage(page);
    }

    return (
        <PageLayout
            pageClassName="posts-page"
            mainClassName="posts-main"
            title="대나무숲"
        >
            {loadError ? (
                <ErrorView
                    title="게시글 목록을 불러오지 못했습니다."
                    message={getErrorMessage(loadError, "잠시 후 다시 시도해주세요.")}
                    onRetry={retryLoadPage}
                />
            ) : (
                <PostBoard
                    posts={posts}
                    hasNextPage={hasNextPage}
                    onLoadMore={loadNextPage}
                />
            )}
        </PageLayout>
    );
}

export default PostsPage;
