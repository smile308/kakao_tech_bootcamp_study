package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.post.PostListResponseDto;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    //게시물 목록 조회
    public List<PostListResponseDto> getPostList() {
        List<Post> posts = postRepository.findAll();
        List<PostListResponseDto> result = new ArrayList<>();

        //각 게시물의 user_id로 작성자 정보 붙이기
        for (Post post : posts) {
            User user = userRepository.findById(post.getUser_id())
                    .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

            PostListResponseDto dto = new PostListResponseDto(post, user);

            result.add(dto);
        }

        return result;
    }

}
