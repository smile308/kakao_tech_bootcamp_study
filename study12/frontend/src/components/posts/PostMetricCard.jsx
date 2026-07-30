import { formatCount } from "../../utils/formatter.js";

function PostMetricCard({ count, label, fullness, className = "" }) {
    return (
        <div
            className={`detail-stat-card ${className} ${fullness >= 1 ? "is-full" : ""}`.trim()}
            style={{ "--fullness": 1 + fullness * 1.5 }}
        >
            <strong className="detail-stat-number">{formatCount(count)}</strong>
            <span className="detail-stat-label">{label}</span>
        </div>
    );
}

export default PostMetricCard;
