package com.lqr.paperragserver.auth.service;

/**
 * 登录失败尝试限制服务，按账号与客户端 IP 维度记录失败并执行临时锁定。
 */
public interface LoginAttemptService {

    /**
     * 如果当前账号或 IP 已被锁定，则直接拒绝本次登录。
     */
    void assertNotLocked(String username, String clientIp);

    /**
     * 记录一次登录失败，达到阈值时锁定账号与 IP。
     */
    void recordFailure(String username, String clientIp);

    /**
     * 登录成功后清理账号与 IP 的失败计数和锁定状态。
     */
    void clearOnSuccess(String username, String clientIp);
}