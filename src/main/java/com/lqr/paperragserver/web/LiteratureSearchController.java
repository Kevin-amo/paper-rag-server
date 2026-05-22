package com.lqr.paperragserver.web;

import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.literature.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.LiteratureConversationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private final LiteratureConversationService literatureConversationService;

    public LiteratureSearchController(LiteratureConversationService literatureConversationService) {
        this.literatureConversationService = literatureConversationService;
    }

    @PostMapping("/search")
    public LiteratureSearchResponse search(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                           @Valid @RequestBody LiteratureSearchRequest request) {
        return literatureConversationService.search(principal.getId(), request);
    }
}