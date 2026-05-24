package com.lqr.paperragserver.web;

import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.service.LiteratureConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiteratureSearchControllerTest {

    private final LiteratureConversationService literatureConversationService = mock(LiteratureConversationService.class);
    private LiteratureSearchController controller;
    private UUID ownerUserId;
    private SecurityUserPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new LiteratureSearchController(literatureConversationService);
        ownerUserId = UUID.randomUUID();
        principal = principal(ownerUserId);
    }

    @Test
    void searchShouldExposePostEndpoint() throws NoSuchMethodException {
        Method method = LiteratureSearchController.class.getMethod(
                "search",
                SecurityUserPrincipal.class,
                LiteratureSearchRequest.class
        );
        PostMapping mapping = method.getAnnotation(PostMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/search");
    }

    @Test
    void searchShouldDelegateToConversationService() {
        UUID conversationId = UUID.randomUUID();
        LiteratureSearchRequest request = new LiteratureSearchRequest("Graph RAG", 10, List.of("cs.AI"), "2024-01-01", "relevance");
        LiteratureSearchResult result = new LiteratureSearchResult(
                "Graph RAG", List.of("Alice"), "Abstract", 2024, "2024-01-01", null,
                List.of("cs.AI"), "cs.AI", null, "https://example.org/paper",
                "https://example.org/paper.pdf", "openalex", "https://openalex.org/W123"
        );
        LiteratureSearchResponse expected = new LiteratureSearchResponse(conversationId, "找到 1 篇论文", List.of(result));
        when(literatureConversationService.search(ownerUserId, request)).thenReturn(expected);

        LiteratureSearchResponse response = controller.search(principal, request);

        assertThat(response).isEqualTo(expected);
        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.summary()).isEqualTo("找到 1 篇论文");
        verify(literatureConversationService).search(ownerUserId, request);
    }

    private SecurityUserPrincipal principal(UUID userId) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("user");
        user.setPasswordHash("{noop}password");
        user.setDisplayName("User");
        user.setStatus("ACTIVE");
        return new SecurityUserPrincipal(user, List.of("USER"));
    }
}