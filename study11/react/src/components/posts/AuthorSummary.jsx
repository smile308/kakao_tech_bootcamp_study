import { formatDateTime } from "../../utils/formatter.js";

function AuthorSummary({
    profileImage,
    nickname,
    createdAt,
    variant = "detail",
}) {
    const classPrefix = variant === "comment" ? "comment-author" : "detail-author";
    const createdAtClassName = variant === "comment" ? "comment-created-at" : "detail-created-at";
    const imageAlt = variant === "comment" ? "댓글 작성자 프로필 이미지" : "작성자 프로필 이미지";

    const content = (
        <>
            <div
                className={`${classPrefix}-image-box ${profileImage ? "" : "is-empty"}`.trim()}
            >
                {profileImage && (
                    <img
                        src={profileImage}
                        className={`${classPrefix}-image`}
                        alt={imageAlt}
                    />
                )}
            </div>
            <p className={`${classPrefix}-name`}>{nickname}</p>
            <p className={createdAtClassName}>{formatDateTime(createdAt)}</p>
        </>
    );

    if (variant === "comment") {
        return content;
    }

    return (
        <section className="detail-author-area">
            {content}
        </section>
    );
}

export default AuthorSummary;
