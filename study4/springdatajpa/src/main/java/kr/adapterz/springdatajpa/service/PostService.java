package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.post.PostListResponseDto;
import kr.adapterz.springdatajpa.dto.post.PostResponseDto;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public List<PostListResponseDto> getPostList() {
        List<Post> posts = postRepository.findAll();

        return posts.stream()
                .map(post -> {
                    User user = userRepository.findById(post.getUser_id())
                            .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

                    return new PostListResponseDto(post, user);
                })
                .toList();
    }

}
