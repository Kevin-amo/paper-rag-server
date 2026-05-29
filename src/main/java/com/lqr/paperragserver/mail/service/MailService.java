package com.lqr.paperragserver.mail.service;

import java.time.Duration;

/**
 * 邮件发送服务。
 */
public interface MailService {

    void sendRegisterEmailCode(String to, String code, Duration ttl);
}