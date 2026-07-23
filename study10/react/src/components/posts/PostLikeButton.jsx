import { formatCount } from "../../utils/formatter.js";

function PostLikeButton({
    isLiked,
    likeCount,
    fullness,
    isSubmitting,
    onClick,
}) {
    return (
        <button
            type="button"
            className={`detail-stat-card detail-stat-card--likes ${isLiked ? "is-liked" : ""} ${fullness >= 1 ? "is-full" : ""}`.trim()}
            style={{ "--fullness": 1 + fullness * 1.5 }}
            disabled={isSubmitting}
            aria-busy={isSubmitting}
            onClick={onClick}
        >
            <strong className="detail-stat-number">{formatCount(likeCount)}</strong>
            <span className="detail-stat-label">좋아요수</span>
        </button>
    );
}

export default PostLikeButton;
