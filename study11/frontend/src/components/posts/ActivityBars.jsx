import { useMemo } from "react";

const BAMBOO_MAX_VALUE = { like: 100, view: 100, report: 5 };
const BAMBOO_SEGMENT_MAX_PERCENT = { like: 50, view: 35, report: 15 };
const BAMBOO_LEAF_MAX = 24;

function createLeaves(commentCount, postId) {
    const leafCount = Math.min(Math.max(Number(commentCount) || 0, 0), BAMBOO_LEAF_MAX);

    return Array.from({ length: leafCount }, (_, index) => {
        const colorClass = Math.random() < 0.5 ? "a" : "b";
        const isDownward = Math.random() < 0.15;
        const angle = isDownward
            ? 150 + Math.random() * 60
            : -55 + Math.random() * 110;

        return {
            key: `${postId ?? "post"}-${index}`,
            colorClass,
            left: Math.random() * 92,
            angle,
        };
    });
}

function ActivityBars({ postId, likeCount, commentCount, viewCount, reportCount }) {
    const counts = {
        like: Math.max(Number(likeCount) || 0, 0),
        view: Math.max(Number(viewCount) || 0, 0),
        report: Math.max(Number(reportCount) || 0, 0),
        comment: Math.max(Number(commentCount) || 0, 0),
    };
    const leaves = useMemo(
        () => createLeaves(counts.comment, postId),
        [counts.comment, postId],
    );

    const segments = ["like", "view", "report"]
        .map((type) => {
            const ratio = Math.min(counts[type], BAMBOO_MAX_VALUE[type])
                / BAMBOO_MAX_VALUE[type];

            return {
                type,
                percent: ratio * BAMBOO_SEGMENT_MAX_PERCENT[type],
                isFull: ratio >= 1,
            };
        })
        .filter((segment) => segment.percent > 0);
    return (
        <div className="post-card__ground" aria-hidden="true">
            {segments.length > 0 && (
                <div className="post-card__bamboo-bar">
                    {segments.map((segment) => (
                        <span
                            key={segment.type}
                            className={`post-card__bamboo-segment post-card__bamboo-segment--${segment.type} ${segment.isFull ? "is-full" : ""}`.trim()}
                            style={{ width: `${segment.percent}%` }}
                        />
                    ))}
                </div>
            )}
            {leaves.map((leaf) => (
                <span
                    key={leaf.key}
                    className={`post-card__bamboo-leaf post-card__bamboo-leaf--${leaf.colorClass}`}
                    style={{
                        left: `${leaf.left}%`,
                        bottom: "8px",
                        transform: `rotate(${leaf.angle}deg)`,
                    }}
                />
            ))}
        </div>
    );
}

export default ActivityBars;
