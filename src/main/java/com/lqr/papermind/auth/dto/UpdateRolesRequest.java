package com.lqr.papermind.auth.dto;

import java.util.List;

/**
 * 更新用户角色请求体。
 *
 * @param roles 新的角色编码列表
 */
public record UpdateRolesRequest(List<String> roles) {
}
