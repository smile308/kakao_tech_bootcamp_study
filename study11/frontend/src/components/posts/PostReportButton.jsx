import { formatCount } from "../../utils/formatter.js";

function PostReportButton({ post, fullness, onClick }) {
    const disabled = post.isMine || post.isReported;

    return (
        <button
            type="button"
            className={`detail-stat-card detail-stat-card--reports ${post.isReported ? "is-reported" : ""} ${post.isMine ? "is-disabled" : ""} ${fullness >= 1 ? "is-full" : ""}`.trim()}
            style={{ "--fullness": 1 + fullness * 1.5 }}
            disabled={disabled}
            onClick={onClick}
        >
            <strong className="detail-stat-number">{formatCount(post.reportCount)}</strong>
            <span className="detail-stat-label">신고수</span>
        </button>
    );
}

export default PostReportButton;
