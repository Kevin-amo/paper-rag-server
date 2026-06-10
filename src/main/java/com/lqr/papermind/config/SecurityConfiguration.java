package com.lqr.papermind.config;

import com.lqr.papermind.auth.config.SecurityProperties;
import com.lqr.papermind.auth.security.JwtAuthenticationFilter;
import com.lqr.papermind.auth.security.RestAccessDeniedHandler;
import com.lqr.papermind.auth.security.RestAuthenticationEntryPoint;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 安全配置。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * 配置 HTTP 安全过滤链，包括 CSRF、会话管理、安全头、异常处理和请求授权规则。
     *
     * @param http                     Spring Security HTTP 安全构建器
     * @param jwtAuthenticationFilter  JWT 认证过滤器
     * @param authenticationEntryPoint REST 认证入口点
     * @param accessDeniedHandler      REST 访问拒绝处理器
     * @return 配置完成的安全过滤链
     * @throws Exception 配置过程中的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(cto -> cto.disable())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31536000))
                        .xssProtection(xss -> xss.disable())
                        .addHeaderWriter((request, response) -> {
                            response.setHeader("X-Content-Type-Options", "nosniff");
                            response.setHeader("X-XSS-Protection", "0");
                            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                            response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
                        }))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register", "/auth/register/email-code").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/reviews/**", "/review-leader/**").hasAnyRole("REVIEWER", "ADMIN")
                        .requestMatchers("/documents/**", "/conversations/**", "/agent/**").hasRole("USER")
                        .requestMatchers("/auth/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * 注册 BCrypt 密码编码器，用于用户注册和登录验证。
     *
     * @return BCrypt 密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 创建 JWT 编码器，使用配置中的密钥对 JWT 令牌进行签名。
     *
     * @param securityProperties 安全配置属性，包含 JWT 密钥
     * @return Nimbus JWT 编码器实例
     */
    @Bean
    public JwtEncoder jwtEncoder(SecurityProperties securityProperties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey(securityProperties)));
    }

    /**
     * 创建 JWT 解码器，使用配置中的密钥和 HS256 算法验证 JWT 令牌。
     *
     * @param securityProperties 安全配置属性，包含 JWT 密钥
     * @return Nimbus JWT 解码器实例
     */
    @Bean
    public JwtDecoder jwtDecoder(SecurityProperties securityProperties) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey(securityProperties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * 从安全配置属性中提取 JWT 签名密钥，转换为 HmacSHA256 规格的 SecretKey。
     *
     * @param securityProperties 安全配置属性，包含 JWT 密钥字符串
     * @return HmacSHA256 规格的 SecretKey 实例
     */
    private SecretKey jwtSecretKey(SecurityProperties securityProperties) {
        byte[] keyBytes = securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}