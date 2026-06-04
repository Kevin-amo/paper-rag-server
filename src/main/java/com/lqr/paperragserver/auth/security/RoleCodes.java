package com.lqr.paperragserver.auth.security;

/**
 * 系统角色编码。
 */
public final class RoleCodes {

    public static final String ADMIN = "ADMIN";
    public static final String REVIEWER = "REVIEWER";
    public static final String USER = "USER";

    private RoleCodes() {
    }

    /**
     * 将角色编码转换为 Spring Security 授权标识（添加 ROLE_ 前缀）。
     *
     * @param roleCode 角色编码
     * @return 带前缀的授权标识
     */
    public static String authority(String roleCode) {
        return "ROLE_" + roleCode;
    }
}