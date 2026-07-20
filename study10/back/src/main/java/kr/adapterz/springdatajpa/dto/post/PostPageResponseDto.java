package kr.adapterz.springdatajpa.dto.post;

import lombok.Getter;

import java.util.List;

@Getter
public class PostPageResponseDto {

    private List<PostListResponseDto> posts;
    private boolean hasNextPage;

    public PostPageResponseDto(List<PostListResponseDto> posts, boolean hasNextPage) {
        this.posts = posts;
        this.hasNextPage = hasNextPage;
    }
}