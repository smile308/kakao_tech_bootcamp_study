package kr.adapterz.springboot.service;

import kr.adapterz.springboot.dto.PostRequestDto;
import kr.adapterz.springboot.dto.PostResponseDto;
import kr.adapterz.springboot.entity.Post;
import kr.adapterz.springboot.entity.User;
import kr.adapterz.springboot.repository.PostRepository;
import kr.adapterz.springboot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import kr.adapterz.springboot.exception.NotFoundException;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public PostResponseDto createPost(Long userId, PostRequestDto request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new  NotFoundException("USER_NOT_FOUND"));

        Post post = new Post(
                request.getTitle(),
                request.getContent(),
                author
        );

        Post savedPost = postRepository.save(post);
        return new PostResponseDto(savedPost);
    }

    @Transactional(readOnly = true)
    public PostResponseDto getPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND"));
        return new PostResponseDto(post);
    }

    @Transactional
    public PostResponseDto updatePost(Long postId, PostRequestDto request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND"));

        post.changeTitle(request.getTitle());
        post.changeContent(request.getContent());

        return new PostResponseDto(post);
    }

    @Transactional
    public void deletePost(Long postId) {
        postRepository.deleteById(postId);
    }
}