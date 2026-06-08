package com.lqr.paperragserver.review.audit;

import com.lqr.paperragserver.review.entity.ReviewAuditLogEntity;
import com.lqr.paperragserver.review.mapper.ReviewAuditLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
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
        Map<String, Object> before = Map.of("score", 70);
        Map<String, Object> after = Map.of("score", 80);
        Map<String, Object> clientInfo = Map.of("ip", "local");

        service.append(UUID.randomUUID(), UUID.randomUUID(), "ADJUST_REPORT", "manual adjustment", before, after, clientInfo);

        ArgumentCaptor<ReviewAuditLogEntity> captor = ArgumentCaptor.forClass(ReviewAuditLogEntity.class);
        verify(mapper).insert(captor.capture());
        ReviewAuditLogEntity log = captor.getValue();
        assertThat(log.getSnapshot()).isSameAs(after);
        assertThat(log.getBeforeSnapshot()).containsEntry("score", 70);
        assertThat(log.getAfterSnapshot()).containsEntry("score", 80);
        assertThat(log.getClientInfo()).containsEntry("ip", "local");
        assertThat(log.getDiff()).containsKey("score");
        assertThat(change(log.getDiff(), "score"))
                .containsEntry("before", 70)
                .containsEntry("after", 80);
    }

    @Test
    void appendShouldDiffAddedDeletedNullAndUnchangedKeys() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("same", "value");
        before.put("deleted", "old");
        before.put("nullChanged", null);
        before.put("staysNull", null);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("same", "value");
        after.put("added", "new");
        after.put("nullChanged", "now");
        after.put("staysNull", null);

        service.append(UUID.randomUUID(), UUID.randomUUID(), "ADJUST_REPORT", "manual adjustment", before, after, Map.of());

        ArgumentCaptor<ReviewAuditLogEntity> captor = ArgumentCaptor.forClass(ReviewAuditLogEntity.class);
        verify(mapper).insert(captor.capture());
        Map<String, Object> diff = captor.getValue().getDiff();
        assertThat(diff).containsOnlyKeys("deleted", "nullChanged", "added");
        assertThat(change(diff, "deleted"))
                .containsEntry("before", "old")
                .containsEntry("after", null);
        assertThat(change(diff, "added"))
                .containsEntry("before", null)
                .containsEntry("after", "new");
        assertThat(change(diff, "nullChanged"))
                .containsEntry("before", null)
                .containsEntry("after", "now");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> change(Map<String, Object> diff, String key) {
        return (Map<String, Object>) diff.get(key);
    }
}
