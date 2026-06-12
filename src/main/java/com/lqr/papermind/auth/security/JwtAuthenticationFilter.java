package com.lqr.papermind.auth.security;

import com.lqr.papermind.auth.service.TokenRevocationService;
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
 * JWT 认证过滤器，从请求头或查询参数中提取令牌，校验有效性后设置安全上下文。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final DatabaseUserDetailsService userDetailsService;
    private final TokenRevocationService tokenRevocationService;

    /**
     * 从请求中提取 JWT 令牌，校验有效性后设置安全上下文。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                if (tokenRevocationService.isRevoked(token)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                Jwt jwt = jwtDecoder.decode(token);
                if (isTokenRevokedByPasswordChange(jwt)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
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

    /**
     * 判断令牌是否因密码变更而失效，即令牌签发时间早于密码变更时间。
     *
     * @param jwt 解码后的 JWT 对象
     * @return 令牌因密码变更而失效返回 true，否则返回 false
     */
    private boolean isTokenRevokedByPasswordChange(Jwt jwt) {
        try {
            String userId = jwt.getClaimAsString("userId");
            if (userId == null) {
                return false;
            }
            return tokenRevocationService.isTokenRevokedByPasswordChange(
                    UUID.fromString(userId), jwt.getIssuedAt());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * 判断令牌是否绑定到当前用户，即令牌中的用户ID与加载的用户主体一致。
     *
     * @param jwt 解码后的 JWT 对象
     * @param userDetails 加载的用户详情
     * @return 绑定一致返回 true，否则返回 false
     */
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

    /**
     * 从请求头或查询参数中提取 Bearer 令牌。
     *
     * @param request HTTP 请求
     * @return 令牌字符串，不存在时返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        String queryToken = request.getParameter("access_token");
        return queryToken == null || queryToken.isBlank() ? null : queryToken.trim();
    }
}
