package kr.adapterz.springdatajpa.auth;

import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void 이메일에_맞는_유저가_없으면_UsernameNotFoundException이_발생한다() {
        when(userRepository.findByEmailAndDeletedFalse("test@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("test@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("No_User");

        verify(userRepository).findByEmailAndDeletedFalse("test@test.com");
    }

    @Test
    void 이메일에_맞는_유저가_있으면_CustomUserDetails를_반환한다() {
        User user = new User("test@test.com", "encoded-password", "tester", "profile.png",0);
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findByEmailAndDeletedFalse("test@test.com"))
                .thenReturn(Optional.of(user));

        CustomUserDetails userDetails =
                customUserDetailsService.loadUserByUsername("test@test.com");

        assertThat(userDetails.getUserId()).isEqualTo(1L);
        assertThat(userDetails.getUsername()).isEqualTo("test@test.com");
        assertThat(userDetails.getPassword()).isEqualTo("encoded-password");

        verify(userRepository).findByEmailAndDeletedFalse("test@test.com");
    }
}