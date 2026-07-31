package kr.adapterz.springdatajpa.dto.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.PostCounter;
import kr.adapterz.springdatajpa.entity.PostViewCount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostResponseFactoryTest {

    @Mock
    private Post post;

    @Mock
    private PostCounter postCounter;

    @Mock
    private PostViewCount postViewCount;

    @Test
    void 목록_조회수는_분리된_조회수_카운터에서_읽는다() {
        when(post.getPostCounter()).thenReturn(postCounter);
        when(post.getPostViewCount()).thenReturn(postViewCount);
        when(postViewCount.getViewCount()).thenReturn(150L);

        PostListResponseDto response =
                PostResponseFactory.createListResponse(post);

        assertThat(response.getViewCount()).isEqualTo(150L);
        verify(postCounter, never()).getViewCount();
    }
}
