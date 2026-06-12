package com.lqr.papermind.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改昵称请求体。
 *
 * @param displayName 新昵称
 */
public record ChangeDisplayNameRequest(
        @NotBlank(message = "昵称不能为空") String displayName
) {
}
