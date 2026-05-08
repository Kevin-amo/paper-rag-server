package com.lqr.paperragserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP 安全配置。
 *
 * <p>当前阶段先放行业务接口，便于本地开发和接口联调；后续接入用户体系时再收紧鉴权策略。</p>
 */
@Configuration
public class SecurityConfiguration {

    /**
     * 配置接口访问规则。
     *
     * @param http Spring Security HTTP 配置对象
     * @return 安全过滤链
     * @throws Exception 配置失败时抛出
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}