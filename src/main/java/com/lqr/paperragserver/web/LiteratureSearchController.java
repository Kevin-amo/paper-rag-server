package com.lqr.paperragserver.web;

import com.lqr.paperragserver.ai.tool.LiteratureSearchTool;
import com.lqr.paperragserver.literature.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.LiteratureSearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文献搜索接口。
 */
@RestController
@RequestMapping("/literature")
public class LiteratureSearchController {

    private final LiteratureSearchTool literatureSearchTool;

    public LiteratureSearchController(LiteratureSearchTool literatureSearchTool) {
        this.literatureSearchTool = literatureSearchTool;
    }

    @PostMapping("/search")
    public LiteratureSearchResponse search(@Valid @RequestBody LiteratureSearchRequest request) {
        return literatureSearchTool.searchLiterature(
                request.query(),
                request.limit(),
                request.sortBy(),
                request.dateFrom(),
                request.categories()
        );
    }
}