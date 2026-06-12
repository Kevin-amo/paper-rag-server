package com.lqr.papermind.ai.impl;

import com.lqr.papermind.ai.service.impl.RerankServiceImpl;
import com.lqr.papermind.common.model.DocumentChunk;
import com.lqr.papermind.common.model.RetrievedChunk;
import com.lqr.papermind.rag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class RerankServiceImplTest {

    @Test
    void rerankShouldMapRequestAndResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RerankServiceImpl service = new RerankServiceImpl(
                builder,
                new RagProperties(800, 120, 5, 0),
                "test-key",
                false
        );
        server.expect(requestTo("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gte-rerank-v2"))
                .andExpect(jsonPath("$.input.query").value("query text"))
                .andExpect(jsonPath("$.input.documents[0]").value("first content"))
                .andExpect(jsonPath("$.input.documents[1]").value("second content"))
                .andExpect(jsonPath("$.parameters.top_n").value(2))
                .andRespond(withSuccess("""
                        {
                          "output": {
                            "results": [
                              {"index": 1, "relevance_score": 0.91},
                              {"index": 0, "relevance_score": 0.43}
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RetrievedChunk> results = service.rerank("query text", candidates(), 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunk().chunkId()).isEqualTo("chunk-b");
        assertThat(results.get(0).rankScore()).isEqualTo(0.91);
        assertThat(results.get(1).chunk().chunkId()).isEqualTo("chunk-a");
        assertThat(results.get(1).rankScore()).isEqualTo(0.43);
        server.verify();
    }

    @Test
    void rerankShouldFallbackWhenApiKeyMissing() {
        RerankServiceImpl service = new RerankServiceImpl(
                RestClient.builder(),
                new RagProperties(800, 120, 5, 0),
                ""
        );
        List<RetrievedChunk> candidates = candidates();

        List<RetrievedChunk> results = service.rerank("query text", candidates, 2);

        assertThat(results).isSameAs(candidates);
    }

    @Test
    void rerankShouldFallbackWhenResponseIsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RerankServiceImpl service = new RerankServiceImpl(
                builder,
                new RagProperties(800, 120, 5, 0),
                "test-key",
                false
        );
        server.expect(requestTo("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"))
                .andRespond(withSuccess("{\"output\":{\"results\":[]}}", MediaType.APPLICATION_JSON));
        List<RetrievedChunk> candidates = candidates();

        List<RetrievedChunk> results = service.rerank("query text", candidates, 2);

        assertThat(results).isSameAs(candidates);
        server.verify();
    }

    private List<RetrievedChunk> candidates() {
        return List.of(
                new RetrievedChunk(new DocumentChunk("chunk-a", "source-1", 0, "first content", Map.of()), 0.8),
                new RetrievedChunk(new DocumentChunk("chunk-b", "source-1", 1, "second content", Map.of()), 0.6)
        );
    }
}