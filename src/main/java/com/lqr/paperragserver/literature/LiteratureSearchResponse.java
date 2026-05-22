package com.lqr.paperragserver.literature;

import java.util.List;

/**
 * 统一文献搜索响应。
 */
public record LiteratureSearchResponse(
        List<LiteratureSearchResult> items
) {
    public LiteratureSearchResponse {
        if (items == null) {
            items = List.of();
        }
    }
}