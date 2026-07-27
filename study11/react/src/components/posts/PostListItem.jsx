import { useNavigate } from "react-router-dom";

import anonymousBambooAvatar from "../../assets/images/anonymous-bamboo-avatar.svg";
import { formatCount, formatDateTime } from "../../utils/formatter.js";
import ActivityBars from "./ActivityBars.jsx";

function PostListItem({ post }) {
    const navigate = useNavigate();

    return (
        <article
            className="post-card"
            tabIndex={0}
            onClick={() => navigate(`/posts/${post.postId}`)}
            onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") {
                    navigate(`/posts/${post.postId}`);
                }
            }}
        >
            <div className="post-card__info">
                <div className="post-card__author">
                    <div className="post-card__author-image-box">
                        <img
                            src={anonymousBambooAvatar}
                            alt="익명 사용자 대나무 프로필 이미지"
                            className="post-card__author-image"
                        />
                    </div>
                    <div className="post-card__author-text">
                        <p className="post-card__author-name">익명 사용자</p>
                    </div>
                </div>
                <div className="post-card__title-area">
                    <h3 className="post-card__title">{post.title || "대나무숲 글"}</h3>
                </div>
                <span className="post-card__date">{formatDateTime(post.createdAt)}</span>
                <span className="post-card__metric">{formatCount(post.likeCount)}</span>
                <span className="post-card__metric">{formatCount(post.commentCount)}</span>
                <span className="post-card__metric">{formatCount(post.viewCount)}</span>
            </div>
            <ActivityBars
                postId={post.postId}
                likeCount={post.likeCount}
                commentCount={post.commentCount}
                viewCount={post.viewCount}
                reportCount={post.reportCount}
            />
        </article>
    );
}

export default PostListItem;
