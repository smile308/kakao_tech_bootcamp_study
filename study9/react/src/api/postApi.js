import { request } from "./api.js";

function normalizeImageUrls(post) {
    if (Array.isArray(post?.imageFiles)) {
        return post.imageFiles.filter(Boolean);
    }

    return post?.imageFile ? [post.imageFile] : [];
}

function normalizeComment(comment) {
    return {
        commentId: comment?.commentId ?? null,
        content: comment?.content ?? "",
        authorNickname: comment?.userName ?? "삭제된 사용자",
        authorProfileImage: comment?.userProfileImage ?? null,
        createdAt: comment?.createdAt ?? "",
        isMine: comment?.isMine === true,
    };
}

function normalizeListPost(post) {
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

function normalizeDetailPost(post) {
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

export const postApi = {
    async getPosts({ page = 0, size = 10 } = {}) {
        const params = new URLSearchParams({
            page: String(page),
            size: String(size),
        });
        const result = await request(`/posts?${params.toString()}`);

        return {
            posts: Array.isArray(result?.posts)
                ? result.posts.map(normalizeListPost)
                : [],
            hasNextPage: result?.hasNextPage === true,
        };
    },

    async getPost(postId) {
        return normalizeDetailPost(await request(`/posts/${postId}`));
    },

    createPost(payload) {
        return request("/posts", {
            method: "POST",
            body: JSON.stringify(payload),
        });
    },

    updatePost(postId, payload) {
        return request(`/posts/${postId}`, {
            method: "PATCH",
            body: JSON.stringify(payload),
        });
    },

    deletePost(postId) {
        return request(`/posts/${postId}`, { method: "DELETE" });
    },

    reportPost(postId) {
        return request(`/posts/${postId}/reports`, { method: "POST" });
    },

    likePost(postId) {
        return request(`/posts/${postId}/likes`, { method: "POST" });
    },

    unlikePost(postId) {
        return request(`/posts/${postId}/likes`, { method: "DELETE" });
    },

    createComment(postId, commentContent) {
        return request(`/posts/${postId}/comments`, {
            method: "POST",
            body: JSON.stringify({ commentContent }),
        });
    },

    updateComment(postId, commentId, commentContent) {
        return request(`/posts/${postId}/comments`, {
            method: "PATCH",
            body: JSON.stringify({ commentId, commentContent }),
        });
    },

    deleteComment(postId, commentId) {
        return request(`/posts/${postId}/comments`, {
            method: "DELETE",
            body: JSON.stringify({ commentId }),
        });
    },
};
