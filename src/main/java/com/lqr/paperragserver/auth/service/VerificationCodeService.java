package com.lqr.paperragserver.auth.service;

/**
 * 验证码服务接口，按场景和通道隔离 Redis key。
 */
public interface VerificationCodeService {

    /**
     * 生成邮箱注册验证码
     *
     * @param email 邮箱地址
     */
    void createRegisterEmailCode(String email, String clientIp);

    /**
     * 生成换绑邮箱验证码
     *
     * @param email 邮箱地址
     * @param clientIp 客户端IP地址
     */
    void createChangeEmailCode(String email, String clientIp);

    /**
     * 校验邮箱注册验证码
     *
     * @param email 邮箱地址
     * @param code 验证码
     * @throws org.springframework.web.server.ResponseStatusException 验证码错误或已过期
     */
    void requireRegisterEmailCodeMatches(String email, String code);

    /**
     * 校验换绑邮箱验证码
     *
     * @param email 邮箱地址
     * @param code 验证码
     * @throws org.springframework.web.server.ResponseStatusException 验证码错误或已过期
     */
    void requireChangeEmailCodeMatches(String email, String code);

    /**
     * 删除邮箱注册验证码
     *
     * @param email 邮箱地址
     */
    void deleteRegisterEmailCode(String email);

    /**
     * 删除换绑邮箱验证码
     *
     * @param email 邮箱地址
     */
    void deleteChangeEmailCode(String email);
}
