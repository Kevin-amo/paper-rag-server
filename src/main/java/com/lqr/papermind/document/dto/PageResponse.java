package com.lqr.papermind.document.dto;

import java.util.List;

/**
 * 通用分页响应结构。
 *
 * @param <T> 分页条目类型
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}