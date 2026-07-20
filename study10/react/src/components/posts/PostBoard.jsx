import { Link } from "react-router-dom";

import ErrorView from "../common/ErrorView.jsx";
import { ErrorBoundary } from "react-error-boundary";
import PostList from "./PostList.jsx";

function PostBoard({ posts, hasNextPage, onLoadMore }) {
    return (
        <section className="evaluation-board" aria-label="대나무숲 글 목록">
            <ErrorBoundary fallback={<ErrorView title="목록 안내를 표시하지 못했습니다." />}>
                <div className="board-toolbar">
                    <div>
                        <p className="posts-welcome">조용히 남겨진 이야기들이 모이는 공간</p>
                        <h2>대나무숲 글 목록</h2>
                    </div>
                    <div className="board-toolbar__actions">
                        <Link to="/posts/new" className="write-post-button">
                            글 작성
                        </Link>
                    </div>
                </div>
            </ErrorBoundary>
            <div className="evaluation-list-header" aria-hidden="true">
                <span>작성자</span>
                <span>글 제목</span>
                <span>작성일</span>
                <span>좋아요</span>
                <span>댓글</span>
                <span>조회수</span>
            </div>
            <ErrorBoundary fallback={<ErrorView title="게시글 목록을 표시하지 못했습니다." />}>
                <PostList
                    posts={posts}
                    hasNextPage={hasNextPage}
                    onLoadMore={onLoadMore}
                />
            </ErrorBoundary>
        </section>
    );
}

export default PostBoard;
