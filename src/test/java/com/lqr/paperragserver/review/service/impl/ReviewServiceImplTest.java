package com.lqr.paperragserver.review.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.review.assessment.ReviewOutputParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewServiceImplTest {

    @Test
    void constructorShouldAcceptReviewOutputParserDependency() {
        ObjectMapper objectMapper = new ObjectMapper();
        ReviewServiceImpl service = new ReviewServiceImpl(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ReviewOutputParser(objectMapper),
                objectMapper
        );

        assertThat(service).isNotNull();
    }
}
