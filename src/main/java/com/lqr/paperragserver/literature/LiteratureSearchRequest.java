package com.lqr.paperragserver.literature;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 文献搜索请求。
 */
public record LiteratureSearchRequest(
        @NotBlank(message = "搜索关键词不能为空") String query,
        @Positive(message = "limit 必须为正数") Integer limit,
        List<String> categories,
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "dateFrom 格式必须为 YYYY-MM-DD") String dateFrom,
        String sortBy
) {
}