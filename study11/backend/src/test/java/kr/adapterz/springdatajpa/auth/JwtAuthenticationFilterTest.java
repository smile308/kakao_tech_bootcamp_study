package kr.adapterz.springdatajpa.auth;

import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.AuthException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 JWT면 사용자 인증 정보를 SecurityContext에 저장한다")
    void validTokenSetsAuthentication() throws Exception {
        String accessToken = "valid-access-token";
        User user = new User(
                "test@test.com",
                "encoded-password",
                "tester",
                "profile.png",
                0
        );
        ReflectionTestUtils.setField(user, "userId", 42L);
        CustomUserDetails userDetails = new CustomUserDetails(user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts");
        request.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(jwtProvider.getUserId(accessToken)).thenReturn(42L);
        when(customUserDetailsService.loadUserByUserId(42L)).thenReturn(userDetails);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(userDetails);
        verify(jwtProvider).getUserId(accessToken);
        verify(customUserDetailsService).loadUserByUserId(42L);
    }

    @Test
    @DisplayName("위조되거나 만료된 JWT면 401 Invalid_Token을 반환한다")
    void invalidTokenReturnsUnauthorized() throws Exception {
        String accessToken = "invalid-access-token";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts");
        request.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(jwtProvider.getUserId(accessToken))
                .thenThrow(new AuthException("Invalid_Token"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("Invalid_Token");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(customUserDetailsService);
    }
}
