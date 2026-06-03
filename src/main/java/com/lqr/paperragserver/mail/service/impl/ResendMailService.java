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

    /**
     * 创建 Resend 邮件服务实例。
     *
     * @param restClientBuilder Spring HTTP 客户端构建器
     * @param properties        Resend 邮件服务配置
     */
    public ResendMailService(RestClient.Builder restClientBuilder, ResendProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
    }

    /**
     * 发送注册验证码邮件。
     *
     * @param to   收件人邮箱地址
     * @param code 验证码
     * @param ttl  验证码有效时长
     */
    @Override
    public void sendRegisterEmailCode(String to, String code, Duration ttl) {
        sendEmailCode(to, registerEmailCodeRequest(to, code, ttl));
    }

    /**
     * 发送换绑邮箱验证码邮件。
     *
     * @param to   收件人邮箱地址
     * @param code 验证码
     * @param ttl  验证码有效时长
     */
    @Override
    public void sendChangeEmailCode(String to, String code, Duration ttl) {
        sendEmailCode(to, changeEmailCodeRequest(to, code, ttl));
    }

    /**
     * 通过 Resend API 发送验证码邮件，发送失败时抛出非法状态异常。
     *
     * @param to      收件人邮箱地址
     * @param request 已组装的邮件请求体
     * @throws IllegalStateException Resend 服务未启用、连接失败或响应异常时抛出
     */
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

    /**
     * 构建注册验证码邮件请求体。
     *
     * @param to   收件人邮箱地址
     * @param code 验证码
     * @param ttl  验证码有效时长
     * @return 包含 HTML 和纯文本内容的邮件请求
     */
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

    /**
     * 构建换绑邮箱验证码邮件请求体。
     *
     * @param to   收件人邮箱地址
     * @param code 验证码
     * @param ttl  验证码有效时长
     * @return 包含 HTML 和纯文本内容的邮件请求
     */
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

    /**
     * 校验 Resend 邮件服务是否已正确配置，未配置时抛出异常。
     *
     * @throws IllegalStateException 服务未启用、API Key 或发件人未配置时抛出
     */
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

    /**
     * 创建带请求超时配置的 RestClient。
     *
     * @param timeout 连接和读取超时时间
     * @return 配置好的 RestClient 实例
     */
    private RestClient client(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 将验证码有效时长转换为可读的中文文本。
     *
     * @param ttl 有效时长，为空或无效时默认 5 分钟
     * @return 格式化的时长文本，如"5 分钟"或"30 秒"
     */
    private String ttlText(Duration ttl) {
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(5) : ttl;
        long seconds = safeTtl.toSeconds();
        if (seconds % 60 == 0) {
            return seconds / 60 + " 分钟";
        }
        return seconds + " 秒";
    }

    /**
     * 计算从指定起点到当前时间的毫秒耗时。
     *
     * @param startNanos 起始纳秒时间戳
     * @return 耗时毫秒数
     */
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