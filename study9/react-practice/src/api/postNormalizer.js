function normalizeImageUrls(post) {
    if (Array.isArray(post?.imageFiles)) {
        return post.imageFiles.filter(Boolean);
    }

    return post?.imageFile ? [post.imageFile] : [];
}

export function normalizeComment(comment) {
    return {
        commentId: comment?.commentId ?? null,
        content: comment?.content ?? "",
        authorNickname: comment?.userName ?? "삭제된 사용자",
        authorProfileImage: comment?.userProfileImage ?? null,
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
        commentCount: post?.replyCount ?? 0,
        viewCount: post?.viewCount ?? 0,
        createdAt: post?.createdAt ?? "",
        authorNickname: post?.userName ?? "삭제된 사용자",
        authorProfileImage: post?.userProfileImage ?? null,
    };
}

export function normalizeDetailPost(post) {
    return {
        postId: post?.postId ?? null,
        title: post?.postTitle ?? "",
        content: post?.postContent ?? "",
        imageUrls: normalizeImageUrls(post),
        likeCount: post?.likeCount ?? 0,
        reportCount: post?.reportCount ?? 0,
        commentCount: post?.replyCount ?? 0,
        viewCount: post?.viewCount ?? 0,
        createdAt: post?.createdAt ?? "",
        authorNickname: post?.userName ?? "삭제된 사용자",
        authorProfileImage: post?.userProfileImage ?? null,
        isMine: post?.isMine === true,
        isLiked: post?.isLiked === true,
        isReported: post?.isReported === true,
        comments: Array.isArray(post?.comments)
            ? post.comments.map(normalizeComment)
            : [],
    };
}

