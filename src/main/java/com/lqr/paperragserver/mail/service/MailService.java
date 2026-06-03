package com.lqr.paperragserver.mail.service;

import java.time.Duration;

/**
 * 邮件发送服务。
 */
public interface MailService {

    /**
     * 发送注册验证码邮件。
     *
     * @param to   收件人邮箱地址
     * @param code 验证码
     * @param ttl  验证码有效时长
     */
    void sendRegisterEmailCode(String to, String code, Duration ttl);

    /**
     * 发送换绑邮箱验证码邮件。
     *
     * @param to   收件人邮箱地址
     * @param code 验证码
     * @param ttl  验证码有效时长
     */
    void sendChangeEmailCode(String to, String code, Duration ttl);
}