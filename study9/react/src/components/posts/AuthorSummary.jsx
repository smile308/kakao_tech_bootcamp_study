import { formatDateTime } from "../../utils/formatter.js";

function AuthorSummary({ post }) {
    return (
        <section className="detail-author-area">
            <div
                className={`detail-author-image-box ${post.authorProfileImage ? "" : "is-empty"}`.trim()}
            >
                {post.authorProfileImage && (
                    <img
                        src={post.authorProfileImage}
                        className="detail-author-image"
                        alt="작성자 프로필 이미지"
                    />
                )}
            </div>
            <p className="detail-author-name">{post.authorNickname}</p>
            <p className="detail-created-at">{formatDateTime(post.createdAt)}</p>
        </section>
    );
}

export default AuthorSummary;
