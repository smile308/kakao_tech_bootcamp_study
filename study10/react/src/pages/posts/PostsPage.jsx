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
    const [retryVersion, setRetryVersion] = useState(0);
    const loadingRef = useRef(false);
    const initialRequestRef = useRef(null);
    const nextPageRef = useRef(0);

    const loadPage = useCallback(async (page) => {
        if (loadingRef.current) {
            return;
        }

        try {
            loadingRef.current = true;
            setLoadError(null);
            const result = await postApi.getPosts({ page, size: 10 });
            setPosts((previous) => page === 0 ? result.posts : [...previous, ...result.posts]);
            setHasNextPage(result.hasNextPage);
            nextPageRef.current = page + 1;
            setFailedPage(null);
        } catch (requestError) {
            setLoadError(requestError);
            setFailedPage(page);
        } finally {
            loadingRef.current = false;
        }
    }, []);

    const loadNextPage = useCallback(() => {
        void loadPage(nextPageRef.current);
    }, [loadPage]);

    useEffect(() => {
        let active = true;

        if (!initialRequestRef.current) {
            loadingRef.current = true;
            initialRequestRef.current = postApi.getPosts({ page: 0, size: 10 });
        }

        const request = initialRequestRef.current;

        request
            .then((result) => {
                if (!active) {
                    return;
                }
                setPosts(result.posts);
                setHasNextPage(result.hasNextPage);
                nextPageRef.current = 1;
                setLoadError(null);
                setFailedPage(null);
            })
            .catch((requestError) => {
                if (active) {
                    setLoadError(requestError);
                    setFailedPage(0);
                }
                if (initialRequestRef.current === request) {
                    initialRequestRef.current = null;
                }
            })
            .finally(() => {
                loadingRef.current = false;
            });

        return () => {
            active = false;
        };
    }, [retryVersion]);

    function retryLoadPage() {
        const page = failedPage ?? 0;
        setLoadError(null);

        if (page === 0) {
            initialRequestRef.current = null;
            nextPageRef.current = 0;
            setPosts([]);
            setHasNextPage(true);
            setRetryVersion((previous) => previous + 1);
            return;
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
