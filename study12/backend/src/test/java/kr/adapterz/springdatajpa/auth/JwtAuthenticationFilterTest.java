package kr.adapterz.springdatajpa.auth;

import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.AuthException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter = new JwtAuthenticationFilter(
                jwtProvider,
                customUserDetailsService,
                new kr.adapterz.springdatajpa.config.ErrorResponseWriter()
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 유효한_JWT면_사용자_인증_정보를_SecurityContext에_저장한다() throws Exception {
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

        when(jwtProvider.getAccessTokenClaims(accessToken))
                .thenReturn(new AccessTokenClaims(42L, 0L));
        when(customUserDetailsService.loadUserByUserId(42L)).thenReturn(userDetails);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(userDetails);
        verify(jwtProvider).getAccessTokenClaims(accessToken);
        verify(customUserDetailsService).loadUserByUserId(42L);
    }

    @Test
    void JWT_인증_버전이_현재_사용자와_다르면_401_Invalid_Token을_반환한다() throws Exception {
        String accessToken = "stale-access-token";
        User user = new User(
                "test@test.com",
                "encoded-password",
                "tester",
                "profile.png",
                0
        );
        ReflectionTestUtils.setField(user, "userId", 42L);
        user.changePassword("new-encoded-password");
        CustomUserDetails userDetails = new CustomUserDetails(user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts");
        request.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(jwtProvider.getAccessTokenClaims(accessToken))
                .thenReturn(new AccessTokenClaims(42L, 0L));
        when(customUserDetailsService.loadUserByUserId(42L)).thenReturn(userDetails);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_TOKEN");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 위조되거나_만료된_JWT면_401_Invalid_Token을_반환한다() throws Exception {
        String accessToken = "invalid-access-token";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts");
        request.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(jwtProvider.getAccessTokenClaims(accessToken))
                .thenThrow(new AuthException("Invalid_Token"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("INVALID_TOKEN");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(customUserDetailsService);
    }
}
