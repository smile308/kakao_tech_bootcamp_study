package kr.adapterz.springdatajpa.dto.post;

import kr.adapterz.springdatajpa.entity.Post;

import java.util.List;

public final class PostResponseFactory {

    private PostResponseFactory() {
    }

    public static PostListResponseDto createListResponse(Post post) {
        return new PostListResponseDto(post, post.getUser());
    }

    public static PostPageResponseDto createPageResponse(
            List<PostListResponseDto> posts,
            boolean hasNextPage
    ) {
        return new PostPageResponseDto(posts, hasNextPage);
    }
}