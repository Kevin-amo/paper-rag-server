package com.lqr.paperragserver.auth.security;

import com.lqr.paperragserver.auth.config.SecurityProperties;
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

    public boolean isValid(String token) {
        try {
            jwtDecoder.decode(token);
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    public long accessTokenExpiresInSeconds() {
        return securityProperties.jwt().accessTokenTtl().toSeconds();
    }

    /**
     * 令牌中携带的用户摘要信息。
     */
    public record TokenUser(UUID userId, String username, List<String> roles) {
    }
}