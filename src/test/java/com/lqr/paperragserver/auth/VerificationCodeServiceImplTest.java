package com.lqr.paperragserver.auth;

import com.lqr.paperragserver.auth.config.SecurityProperties;
import com.lqr.paperragserver.auth.service.impl.VerificationCodeServiceImpl;
import com.lqr.paperragserver.mail.service.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class VerificationCodeServiceImplTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final MailService mailService = mock(MailService.class);
    private VerificationCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new VerificationCodeServiceImpl(
                redisTemplate,
                new SecurityProperties(null, null, new SecurityProperties.RegisterEmailCode(
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(60),
                        2,
                        2,
                        3
                ), null),
                mailService
        );
    }

    @Test
    void createRegisterEmailCodeShouldWriteCodeAndCooldownWithNormalizedEmail() {
        when(redisTemplate.hasKey("auth:code:register:email:cooldown:user@example.com")).thenReturn(false);
        when(valueOperations.increment("auth:code:register:email:daily:user@example.com")).thenReturn(1L);
        when(valueOperations.increment("auth:code:register:ip:minute:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("auth:code:register:ip:daily:127.0.0.1")).thenReturn(1L);

        service.createRegisterEmailCode(" User@Example.COM ", "127.0.0.1");

        verify(valueOperations).set(eq("auth:code:register:email:user@example.com"), any(String.class), eq(Duration.ofMinutes(5)));
        verify(valueOperations).set("auth:code:register:email:cooldown:user@example.com", "1", Duration.ofSeconds(60));
        verify(mailService).sendRegisterEmailCode(eq("user@example.com"), matches("\\d{6}"), eq(Duration.ofMinutes(5)));
        verify(redisTemplate).expire("auth:code:register:email:daily:user@example.com", Duration.ofDays(1));
        verify(redisTemplate).expire("auth:code:register:ip:minute:127.0.0.1", Duration.ofSeconds(60));
        verify(redisTemplate).expire("auth:code:register:ip:daily:127.0.0.1", Duration.ofDays(1));
    }

    @Test
    void createRegisterEmailCodeShouldRejectWhenCooldownExists() {
        when(redisTemplate.hasKey("auth:code:register:email:cooldown:user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createRegisterEmailCode("user@example.com", "127.0.0.1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getReason()).isEqualTo("验证码发送过于频繁，请稍后再试");
                });

        verify(valueOperations, never()).increment(any(String.class));
        verify(valueOperations, never()).set(any(String.class), any(String.class), any(Duration.class));
        verify(mailService, never()).sendRegisterEmailCode(any(String.class), any(String.class), any(Duration.class));
    }

    @Test
    void createRegisterEmailCodeShouldRejectWhenEmailDailyLimitExceeded() {
        when(redisTemplate.hasKey("auth:code:register:email:cooldown:user@example.com")).thenReturn(false);
        when(valueOperations.increment("auth:code:register:email:daily:user@example.com")).thenReturn(3L);

        assertThatThrownBy(() -> service.createRegisterEmailCode("user@example.com", "127.0.0.1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getReason()).isEqualTo("该邮箱今日验证码发送次数已达上限");
                });

        verify(valueOperations, never()).set(eq("auth:code:register:email:user@example.com"), any(String.class), any(Duration.class));
        verify(mailService, never()).sendRegisterEmailCode(any(String.class), any(String.class), any(Duration.class));
    }

    @Test
    void createRegisterEmailCodeShouldRejectWhenIpMinuteLimitExceeded() {
        when(redisTemplate.hasKey("auth:code:register:email:cooldown:user@example.com")).thenReturn(false);
        when(valueOperations.increment("auth:code:register:email:daily:user@example.com")).thenReturn(1L);
        when(valueOperations.increment("auth:code:register:ip:minute:127.0.0.1")).thenReturn(3L);

        assertThatThrownBy(() -> service.createRegisterEmailCode("user@example.com", "127.0.0.1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getReason()).isEqualTo("当前 IP 验证码请求过于频繁，请稍后再试");
                });

        verify(valueOperations, never()).set(eq("auth:code:register:email:user@example.com"), any(String.class), any(Duration.class));
        verify(mailService, never()).sendRegisterEmailCode(any(String.class), any(String.class), any(Duration.class));
    }

    @Test
    void createRegisterEmailCodeShouldRejectWhenIpDailyLimitExceeded() {
        when(redisTemplate.hasKey("auth:code:register:email:cooldown:user@example.com")).thenReturn(false);
        when(valueOperations.increment("auth:code:register:email:daily:user@example.com")).thenReturn(1L);
        when(valueOperations.increment("auth:code:register:ip:minute:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("auth:code:register:ip:daily:127.0.0.1")).thenReturn(4L);

        assertThatThrownBy(() -> service.createRegisterEmailCode("user@example.com", "127.0.0.1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getReason()).isEqualTo("当前 IP 今日验证码发送次数已达上限");
                });

        verify(valueOperations, never()).set(eq("auth:code:register:email:user@example.com"), any(String.class), any(Duration.class));
        verify(mailService, never()).sendRegisterEmailCode(any(String.class), any(String.class), any(Duration.class));
    }

    @Test
    void createRegisterEmailCodeShouldDeleteCodeAndCooldownWhenMailFails() {
        when(redisTemplate.hasKey("auth:code:register:email:cooldown:user@example.com")).thenReturn(false);
        when(valueOperations.increment("auth:code:register:email:daily:user@example.com")).thenReturn(1L);
        when(valueOperations.increment("auth:code:register:ip:minute:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("auth:code:register:ip:daily:127.0.0.1")).thenReturn(1L);
        doThrow(new IllegalStateException("send failed"))
                .when(mailService)
                .sendRegisterEmailCode(eq("user@example.com"), any(String.class), eq(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> service.createRegisterEmailCode("user@example.com", "127.0.0.1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getReason()).isEqualTo("验证码邮件发送失败，请稍后再试");
                });

        verify(valueOperations).set(eq("auth:code:register:email:user@example.com"), any(String.class), eq(Duration.ofMinutes(5)));
        verify(valueOperations).set("auth:code:register:email:cooldown:user@example.com", "1", Duration.ofSeconds(60));
        verify(redisTemplate).delete(List.of(
                "auth:code:register:email:user@example.com",
                "auth:code:register:email:cooldown:user@example.com"
        ));
    }
}