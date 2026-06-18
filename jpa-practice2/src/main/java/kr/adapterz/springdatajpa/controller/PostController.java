package kr.adapterz.springdatajpa.controller;

import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.service.PostService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public PostResponse create(@RequestBody CreatePostRequest request) {
        Post post = postService.create(request.authorId, request.title, request.content);
        return PostResponse.of(post);
    }

    @GetMapping("/{id}")
    public PostResponse get(@PathVariable Long id) {
        return PostResponse.of(postService.findById(id));
    }

    @PatchMapping("/{id}")
    public PostResponse update(@PathVariable Long id, @RequestBody UpdatePostRequest request) {
        Post updatedPost = postService.update(id, request.title, request.content);
        return PostResponse.of(updatedPost);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        postService.delete(id);
    }

    @Data
    public static class UpdatePostRequest {
        private String title;
        private String content;
    }

    @Data
    public static class CreatePostRequest {
        private Long authorId;
        private String title;
        private String content;
    }

    @Data
    public static class PostResponse {
        private Long id;
        private String title;
        private String content;
        private Long authorId;
        private String authorNickname;

        public static PostResponse of(Post post) {
            return new PostResponse(post.getId(), post.getTitle(), post.getContent(),
                    post.getAuthor().getId(), post.getAuthor().getNickname());
        }

        public PostResponse(Long id, String title, String content, Long authorId, String authorNickname) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.authorId = authorId;
            this.authorNickname = authorNickname;
        }
    }
}