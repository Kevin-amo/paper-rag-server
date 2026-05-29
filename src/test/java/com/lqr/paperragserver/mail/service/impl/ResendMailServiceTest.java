package com.lqr.paperragserver.mail.service.impl;

import com.lqr.paperragserver.mail.config.ResendProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ResendMailServiceTest {

    @Test
    void registerEmailCodeRequestShouldContainRequiredFields() {
        ResendMailService service = new ResendMailService(
                mock(RestClient.Builder.class),
                properties("test-api-key")
        );

        ResendMailService.ResendEmailRequest request = service.registerEmailCodeRequest(
                "user@example.com",
                "123456",
                Duration.ofMinutes(5)
        );

        assertThat(request.from()).isEqualTo("PaperRAG <noreply@example.com>");
        assertThat(request.to()).containsExactly("user@example.com");
        assertThat(request.subject()).isEqualTo("PaperRAG 注册验证码");
        assertThat(request.html()).contains("123456", "5 分钟", "如果这不是您本人操作");
        assertThat(request.text()).contains("123456", "5 分钟", "如果这不是您本人操作");
    }

    @Test
    void sendRegisterEmailCodeShouldRejectMissingApiKeyBeforeRequest() {
        RestClient.Builder restClientBuilder = mock(RestClient.Builder.class);
        ResendMailService service = new ResendMailService(
                restClientBuilder,
                properties(" ")
        );

        assertThatThrownBy(() -> service.sendRegisterEmailCode("user@example.com", "123456", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Resend API Key 未配置");

        verifyNoInteractions(restClientBuilder);
    }

    private ResendProperties properties(String apiKey) {
        return new ResendProperties(
                true,
                apiKey,
                "PaperRAG <noreply@example.com>",
                "https://api.resend.com/emails",
                Duration.ofSeconds(10)
        );
    }
}