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
        const result = await request(`/posts/${postId}`);
        return normalizeDetailPost(result);
    },
};

