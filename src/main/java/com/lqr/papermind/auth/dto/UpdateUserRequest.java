package com.lqr.papermind.auth.dto;

/**
 * 管理员更新用户基础资料请求体。
 *
 * @param displayName 新昵称
 * @param email 新邮箱地址
 */
public record UpdateUserRequest(String displayName, String email) {
}
