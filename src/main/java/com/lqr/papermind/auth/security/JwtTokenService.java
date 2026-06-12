package com.lqr.papermind.auth.security;

import com.lqr.papermind.auth.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JWT 令牌服务，负责签发、校验访问令牌并暴露过期时间配置。
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final SecurityProperties securityProperties;

    /**
     * 为当前用户签发访问令牌，包含用户ID、角色和过期时间等声明。
     *
     * @param principal 当前用户主体
     * @return 签发的 JWT 令牌字符串
     */
    public String createAccessToken(SecurityUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(securityProperties.jwt().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(securityProperties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(principal.getUsername())
                .claim("userId", principal.getId().toString())
                .claim("roles", principal.getRoles())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * 校验令牌是否有效。
     *
     * @param token JWT 令牌字符串
     * @return 有效返回 true，否则返回 false
     */
    public boolean isValid(String token) {
        try {
            jwtDecoder.decode(token);
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }

    /**
     * 解码并校验 JWT 令牌，返回包含所有声明的 Jwt 对象。
     *
     * @param token JWT 令牌字符串
     * @return 解码后的 Jwt 对象
     * @throws JwtException 令牌无效或已过期时抛出
     */
    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    /**
     * 获取访问令牌的过期时间（秒）。
     *
     * @return 过期时间秒数
     */
    public long accessTokenExpiresInSeconds() {
        return securityProperties.jwt().accessTokenTtl().toSeconds();
    }

    /**
     * 令牌中携带的用户摘要信息。
     */
    public record TokenUser(UUID userId, String username, List<String> roles) {
    }
}
