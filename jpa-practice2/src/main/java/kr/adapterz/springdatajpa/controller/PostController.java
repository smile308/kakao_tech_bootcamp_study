package kr.adapterz.springdatajpa.controller;

import kr.adapterz.springdatajpa.dto.PostSummaryDto;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.service.PostService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

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

    @GetMapping("/search/title/keyword")
    public List<PostResponse> searchByTitle(@RequestParam String keyword) {
        return postService.findByTitle(keyword).stream().map(PostResponse::of).toList();
    }


    @GetMapping("/search/author/nickname")
    public List<PostResponse> byAuthor(@RequestParam String nickname) {
        return postService.findByAuthorNickname(nickname).stream().map(PostResponse::of).toList();
    }

    @GetMapping("/title/author/{authorId}")
    public List<String> getTitlesByAuthor(@PathVariable Long authorId) {
        return postService.findTitlesByAuthorId(authorId);
    }

    @GetMapping("/summaries/keyword")
    public List<PostSummaryDto> summaryByTitle(@RequestParam String keyword) {
        return postService.findPostSummaries(keyword);
    }

    @GetMapping("/all/n-plus-one")
    public List<PostResponse> allWithNPlusOne() {
        return postService.findALlPostsWithNPlusOne().stream().map(PostResponse::of).toList();
    }

    @GetMapping("/all/entity-graph")
    public List<PostResponse> allWithEntityGraph() {
        return postService.findAllPostsByEntityGraph().stream().map(PostResponse::of).toList();
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
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String createdBy;
        private String updatedBy;

        public static PostResponse of(Post post) {
            return new PostResponse(post.getId(), post.getTitle(), post.getContent(),
                    post.getAuthor().getId(), post.getAuthor().getNickname(),post.getCreatedAt(),
                    post.getUpdatedAt(),
                    post.getCreatedBy(),
                    post.getUpdatedBy());
        }

        public PostResponse(Long id, String title, String content, Long authorId, String authorNickname,LocalDateTime createdAt,
                            LocalDateTime updatedAt,
                            String createdBy,
                            String updatedBy) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.authorId = authorId;
            this.authorNickname = authorNickname;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
        }
    }
}