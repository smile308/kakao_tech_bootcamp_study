function normalizeImageUrls(imageUrls) {
    return Array.isArray(imageUrls) ? imageUrls.filter(Boolean) : [];
}

function normalizeComment(comment) {
    return {
        commentId: comment?.commentId ?? null,
        content: comment?.content ?? "",
        authorNickname: comment?.authorNickname ?? "삭제된 사용자",
        authorProfileImage: comment?.authorProfileImage ?? null,
        createdAt: comment?.createdAt ?? "",
        isMine: comment?.isMine === true,
    };
}

export function normalizeListPost(post) {
    return {
        postId: post?.postId ?? null,
        title: post?.title ?? "",
        likeCount: post?.likeCount ?? 0,
        reportCount: post?.reportCount ?? 0,
        commentCount: post?.commentCount ?? 0,
        viewCount: post?.viewCount ?? 0,
        createdAt: post?.createdAt ?? "",
        authorNickname: post?.authorNickname ?? "삭제된 사용자",
        authorProfileImage: post?.authorProfileImage ?? null,
    };
}

export function normalizeDetailPost(post) {
    return {
        postId: post?.postId ?? null,
        title: post?.title ?? "",
        content: post?.content ?? "",
        imageUrls: normalizeImageUrls(post?.imageUrls),
        likeCount: post?.likeCount ?? 0,
        reportCount: post?.reportCount ?? 0,
        commentCount: post?.commentCount ?? 0,
        viewCount: post?.viewCount ?? 0,
        createdAt: post?.createdAt ?? "",
        authorNickname: post?.authorNickname ?? "삭제된 사용자",
        authorProfileImage: post?.authorProfileImage ?? null,
        isMine: post?.isMine === true,
        isLiked: post?.isLiked === true,
        isReported: post?.isReported === true,
        comments: Array.isArray(post?.comments)
            ? post.comments.map(normalizeComment)
            : [],
    };
}
