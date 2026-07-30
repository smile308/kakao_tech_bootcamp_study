import { useEffect, useRef } from "react";

import { ErrorBoundary } from "react-error-boundary";
import PostListItem from "./PostListItem.jsx";

function PostItemFallback() {
    return <div className="item-error-view">이 게시글을 표시할 수 없습니다.</div>;
}

function PostList({ posts, hasNextPage, onLoadMore }) {
    const observerTargetRef = useRef(null);

    useEffect(() => {
        const target = observerTargetRef.current;
        if (!target || !hasNextPage) {
            return;
        }

        const observer = new IntersectionObserver((entries) => {
            if (entries[0]?.isIntersecting) {
                onLoadMore();
            }
        });
        observer.observe(target);
        return () => observer.disconnect();
    }, [hasNextPage, onLoadMore]);

    return (
        <>
            <section className="posts-list">
                {posts.map((post) => (
                    <ErrorBoundary
                        key={post.postId}
                        fallback={<PostItemFallback />}
                    >
                        <PostListItem post={post} />
                    </ErrorBoundary>
                ))}
            </section>
            {hasNextPage && <div ref={observerTargetRef} className="scroll-observer-target" />}
        </>
    );
}

export default PostList;
