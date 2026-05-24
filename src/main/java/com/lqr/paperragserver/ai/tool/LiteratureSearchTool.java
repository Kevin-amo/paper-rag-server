package com.lqr.paperragserver.ai.tool;

import com.lqr.paperragserver.literature.exception.LiteratureSearchException;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.service.LiteratureSearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LiteratureSearchTool {

    private final LiteratureSearchService literatureSearchService;

    public LiteratureSearchTool(LiteratureSearchService literatureSearchService) {
        this.literatureSearchService = literatureSearchService;
    }

    @Tool(description = "Search academic papers by title, keyword, author or research topic. Use this tool when the user asks to search, find, recommend, or retrieve papers/literature/articles, including Chinese requests like 搜文献、找论文、推荐文章.")
    public LiteratureSearchResponse searchLiterature(
            @ToolParam(description = "Search query extracted from the user request, such as RAG, machine learning, paper title, keyword, author or research topic.") String query,
            @ToolParam(description = "Maximum number of results. Default is 10, maximum is 50. If the user asks for one paper/article, use 1.", required = false) Integer limit,
            @ToolParam(description = "Sort mode. Allowed values: relevance or date.", required = false) String sortBy,
            @ToolParam(description = "Publication date lower bound. Format: YYYY-MM-DD.", required = false) String dateFrom,
            @ToolParam(description = "Optional research categories.", required = false) List<String> categories
    ) {
        if (query == null || query.isBlank()) {
            throw new LiteratureSearchException(HttpStatus.BAD_REQUEST, "LITERATURE_QUERY_REQUIRED", "搜索关键词不能为空");
        }
        if (limit != null && limit <= 0) {
            throw new LiteratureSearchException(HttpStatus.BAD_REQUEST, "LITERATURE_LIMIT_INVALID", "limit 必须为正数");
        }
        return literatureSearchService.search(new LiteratureSearchRequest(query, limit, categories, dateFrom, sortBy));
    }
}