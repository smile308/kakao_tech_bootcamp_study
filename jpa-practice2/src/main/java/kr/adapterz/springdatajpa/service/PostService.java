package kr.adapterz.springdatajpa.service;


import kr.adapterz.springdatajpa.dto.PostSummaryDto;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public Post create(Long authorId, String title, String content){
        User author = userRepository.findById(authorId).orElseThrow(()->new IllegalArgumentException("user not found"));
        Post post = new Post(title,content,author);
        return postRepository.save(post);
    }

    public Post findById(Long id){
        return postRepository.findById(id).orElseThrow(()->new IllegalArgumentException("post not found"));
    }

    @Transactional
    public Post update(Long id, String title, String content){
        Post post = findById(id);
        if(title !=null)
            post.changeTitle(title);
        if(content!=null)
            post.changeContent(content);
        return post;
    }

    @Transactional
    public void delete(Long id){
        postRepository.delete(findById(id));
    }

    public List<Post> findByTitle(String keyword){
        //return postRepository.findByTitleContainingIgnoreCase(keyword);
        return postRepository.searchByTitle(keyword);
    }

    public List<Post> findByAuthorNickname(String nickname){
        //return postRepository.findByAuthor_Nickname(nickname);
        return postRepository.findByAuthorNickname(nickname);
    }
    public List<String> findTitlesByAuthorId(Long authorId) {
        return postRepository.findTitlesByAuthorId(authorId);
    }

    public List<PostSummaryDto> findPostSummaries(String keyword) {
        return postRepository.findPostSummaries(keyword);
    }
}

