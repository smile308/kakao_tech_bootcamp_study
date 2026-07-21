import { Link } from "react-router-dom";

import anonymousBambooAvatar from "../../assets/images/anonymous-bamboo-avatar.svg";
import AuthorSummary from "./AuthorSummary.jsx";
import PostImageGallery from "./PostImageGallery.jsx";
import PostLikeButton from "./PostLikeButton.jsx";
import PostMetricCard from "./PostMetricCard.jsx";
import PostReportButton from "./PostReportButton.jsx";

const REPORT_BLOCK_THRESHOLD = 5;

function getFullness(value, cap) {
    return Math.min(Math.max((Number(value) || 0) / cap, 0), 1);
}

function PostDetailCard({ post, commentCount, onDelete, onReport, onLike }) {
    const canModify = post.isMine && post.reportCount < REPORT_BLOCK_THRESHOLD;

    return (
        <>
            <h2 className="detail-post-title">{post.title}</h2>
            <AuthorSummary
                profileImage={anonymousBambooAvatar}
                nickname="익명 사용자"
                createdAt={post.createdAt}
            />
            {canModify && (
                <section className="detail-post-actions">
                    <Link to={`/posts/${post.postId}/edit`} className="detail-small-button">
                        수정
                    </Link>
                    <button type="button" className="detail-small-button" onClick={onDelete}>
                        삭제
                    </button>
                </section>
            )}
            <PostImageGallery imageUrls={post.imageUrls} />
            <p className="detail-post-content">{post.content}</p>
            <section className="detail-stats">
                <PostReportButton
                    post={post}
                    fullness={getFullness(post.reportCount, REPORT_BLOCK_THRESHOLD)}
                    onClick={onReport}
                />
                <PostLikeButton
                    isLiked={post.isLiked}
                    likeCount={post.likeCount}
                    fullness={getFullness(post.likeCount, 1000)}
                    onClick={onLike}
                />
                <PostMetricCard
                    count={post.viewCount}
                    label="조회수"
                    fullness={getFullness(post.viewCount, 10000)}
                    className="detail-stat-card--views"
                />
                <PostMetricCard
                    count={commentCount}
                    label="댓글"
                    fullness={getFullness(commentCount, 100)}
                    className="detail-stat-card--comments"
                />
            </section>
        </>
    );
}

export default PostDetailCard;
