package com.lqr.paperragserver.auth.security;

/**
 * 系统角色编码。
 */
public final class RoleCodes {

    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";

    private RoleCodes() {
    }

    public static String authority(String roleCode) {
        return "ROLE_" + roleCode;
    }
}