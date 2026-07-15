import { request } from "./api.js";
import { normalizeDetailPost, normalizeListPost } from "./postNormalizer.js";

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
