package com.lqr.paperragserver.review.audit;

import com.lqr.paperragserver.review.entity.ReviewAuditLogEntity;
import com.lqr.paperragserver.review.mapper.ReviewAuditLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReviewAuditServiceTest {
    private final ReviewAuditLogMapper mapper = mock(ReviewAuditLogMapper.class);
    private final ReviewAuditService service = new ReviewAuditService(mapper);

    @Test
    void appendShouldStoreBeforeAfterAndDiff() {
        service.append(UUID.randomUUID(), UUID.randomUUID(), "ADJUST_REPORT", "????", Map.of("score", 70), Map.of("score", 80), Map.of("ip", "local"));
        ArgumentCaptor<ReviewAuditLogEntity> captor = ArgumentCaptor.forClass(ReviewAuditLogEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getBeforeSnapshot()).containsEntry("score", 70);
        assertThat(captor.getValue().getAfterSnapshot()).containsEntry("score", 80);
        assertThat(captor.getValue().getDiff()).containsKey("score");
    }
}
