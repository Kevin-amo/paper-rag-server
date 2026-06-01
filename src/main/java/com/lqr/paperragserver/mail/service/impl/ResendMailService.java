package com.lqr.paperragserver.mail.service.impl;

import com.lqr.paperragserver.mail.config.ResendProperties;
import com.lqr.paperragserver.mail.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Resend API 的邮件发送实现。
 */
@Slf4j
@Service
public class ResendMailService implements MailService {

    private static final String PROVIDER = "resend";
    private static final String REGISTER_EMAIL_CODE_SUBJECT = "PaperRAG 注册验证码";
    private static final String CHANGE_EMAIL_CODE_SUBJECT = "PaperRAG 换绑邮箱验证码";

    private final RestClient.Builder restClientBuilder;
    private final ResendProperties properties;

    public ResendMailService(RestClient.Builder restClientBuilder, ResendProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
    }

    @Override
    public void sendRegisterEmailCode(String to, String code, Duration ttl) {
        sendEmailCode(to, registerEmailCodeRequest(to, code, ttl));
    }

    @Override
    public void sendChangeEmailCode(String to, String code, Duration ttl) {
        sendEmailCode(to, changeEmailCodeRequest(to, code, ttl));
    }

    private void sendEmailCode(String to, ResendEmailRequest request) {
        requireConfigured();
        long startNanos = System.nanoTime();
        try {
            client(properties.timeout())
                    .post()
                    .uri(properties.endpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("mail.send provider={} result=success email={} costMs={}", PROVIDER, to, elapsedMs(startNanos));
        } catch (RestClientResponseException ex) {
            log.warn("mail.send provider={} result=fail email={} reason=HTTP_{} costMs={}",
                    PROVIDER, to, ex.getStatusCode().value(), elapsedMs(startNanos));
            throw new IllegalStateException("Resend 邮件服务响应失败", ex);
        } catch (ResourceAccessException ex) {
            log.warn("mail.send provider={} result=fail email={} reason=CONNECTION_FAILED costMs={}",
                    PROVIDER, to, elapsedMs(startNanos), ex);
            throw new IllegalStateException("Resend 邮件服务连接失败", ex);
        } catch (RestClientException ex) {
            log.warn("mail.send provider={} result=fail email={} reason=REQUEST_FAILED costMs={}",
                    PROVIDER, to, elapsedMs(startNanos), ex);
            throw new IllegalStateException("Resend 邮件服务调用失败", ex);
        }
    }

    ResendEmailRequest registerEmailCodeRequest(String to, String code, Duration ttl) {
        String ttlText = ttlText(ttl);
        String html = """
                <div style=\"font-family: Arial, sans-serif; line-height: 1.6; color: #1f2937;\">
                  <p>您好，</p>
                  <p>您正在注册 PaperRAG，验证码为：</p>
                  <p style=\"font-size: 28px; font-weight: 700; letter-spacing: 6px; color: #2563eb;\">%s</p>
                  <p>验证码有效期为 %s，请在有效期内完成注册。</p>
                  <p>如果这不是您本人操作，可以忽略这封邮件。</p>
                </div>
                """.formatted(code, ttlText);
        String text = """
                您好，

                您正在注册 PaperRAG，验证码为：%s
                验证码有效期为 %s，请在有效期内完成注册。
                如果这不是您本人操作，可以忽略这封邮件。
                """.formatted(code, ttlText);
        return new ResendEmailRequest(
                properties.from(),
                List.of(to),
                REGISTER_EMAIL_CODE_SUBJECT,
                html,
                text
        );
    }

    ResendEmailRequest changeEmailCodeRequest(String to, String code, Duration ttl) {
        String ttlText = ttlText(ttl);
        String html = """
                <div style=\"font-family: Arial, sans-serif; line-height: 1.6; color: #1f2937;\">
                  <p>您好，</p>
                  <p>您正在为 PaperRAG 账号换绑邮箱，验证码为：</p>
                  <p style=\"font-size: 28px; font-weight: 700; letter-spacing: 6px; color: #2563eb;\">%s</p>
                  <p>验证码有效期为 %s，请在有效期内完成邮箱换绑。</p>
                  <p>如果这不是您本人操作，请忽略这封邮件并尽快检查账号安全。</p>
                </div>
                """.formatted(code, ttlText);
        String text = """
                您好，

                您正在为 PaperRAG 账号换绑邮箱，验证码为：%s
                验证码有效期为 %s，请在有效期内完成邮箱换绑。
                如果这不是您本人操作，请忽略这封邮件并尽快检查账号安全。
                """.formatted(code, ttlText);
        return new ResendEmailRequest(
                properties.from(),
                List.of(to),
                CHANGE_EMAIL_CODE_SUBJECT,
                html,
                text
        );
    }

    private void requireConfigured() {
        if (Boolean.FALSE.equals(properties.enabled())) {
            throw new IllegalStateException("Resend 邮件服务未启用");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Resend API Key 未配置");
        }
        if (properties.from() == null || properties.from().isBlank()) {
            throw new IllegalStateException("Resend 发件人未配置");
        }
    }

    private RestClient client(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    private String ttlText(Duration ttl) {
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(5) : ttl;
        long seconds = safeTtl.toSeconds();
        if (seconds % 60 == 0) {
            return seconds / 60 + " 分钟";
        }
        return seconds + " 秒";
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    record ResendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String html,
            String text
    ) {
    }
}