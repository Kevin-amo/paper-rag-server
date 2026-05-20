package com.lqr.paperragserver.auth.security;

import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.service.TokenRevocationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JWT 认证过滤器测试，验证访问令牌解析、撤销拦截和安全上下文写入。
 */
class JwtAuthenticationFilterTest {

    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    private final DatabaseUserDetailsService userDetailsService = mock(DatabaseUserDetailsService.class);
    private final TokenRevocationService tokenRevocationService = mock(TokenRevocationService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtDecoder,
            userDetailsService,
            tokenRevocationService
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateRequestWithValidBearerToken() throws Exception {
        when(jwtDecoder.decode("valid-token")).thenReturn(Jwt.withTokenValue("valid-token")
                .header("alg", "HS256")
                .subject("alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(principal("alice", List.of(RoleCodes.USER)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("alice");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void shouldRejectRevokedBearerToken() throws Exception {
        when(tokenRevocationService.isRevoked("revoked-token")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer revoked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtDecoder, never()).decode("revoked-token");
        verify(userDetailsService, never()).loadUserByUsername("alice");
    }

    @Test
    void shouldIgnoreMissingToken() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private SecurityUserPrincipal principal(String username, List<String> roles) {
        SysUser user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash("{noop}password");
        user.setStatus("ACTIVE");
        return new SecurityUserPrincipal(user, roles);
    }
}