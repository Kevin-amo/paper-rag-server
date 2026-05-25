package com.lqr.paperragserver.auth.security;

import com.lqr.paperragserver.auth.service.TokenRevocationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * JWT 认证过滤器，从请求头或资源访问参数中解析访问令牌并写入安全上下文。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final DatabaseUserDetailsService userDetailsService;
    private final TokenRevocationService tokenRevocationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 尝试从请求头或资源访问参数中解析访问令牌
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // 判断 token 是否在黑名单（退出登录）
                if (tokenRevocationService.isRevoked(token)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                Jwt jwt = jwtDecoder.decode(token);
                String username = jwt.getSubject();
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!isTokenBoundToUser(jwt, userDetails)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isTokenBoundToUser(Jwt jwt, UserDetails userDetails) {
        if (!(userDetails instanceof SecurityUserPrincipal principal)) {
            return false;
        }
        try {
            String userId = jwt.getClaimAsString("userId");
            return userId != null && principal.getId().equals(UUID.fromString(userId));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        String queryToken = request.getParameter("access_token");
        return queryToken == null || queryToken.isBlank() ? null : queryToken.trim();
    }
}