package com.lqr.paperragserver.review.risk;

import com.lqr.paperragserver.review.entity.ReviewRiskItemEntity;
import com.lqr.paperragserver.review.mapper.ReviewRiskItemMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewRiskServiceTest {

    @Test
    void replaceReportRisksShouldDeleteExistingAndInsertNormalizedRiskItems() {
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        ReviewRiskItemMapper mapper = mock(ReviewRiskItemMapper.class);
        ReviewRiskService service = new ReviewRiskService(mapper);

        service.replaceReportRisks(reportId, taskId, List.of(Map.of(
                "type", "REFERENCE_FORMAT",
                "level", "HIGH",
                "evidence", "[1] 缺少年份",
                "suggestion", "补全年份",
                "confidence", 0.9
        )));

        verify(mapper).deleteByReportId(reportId);
        ArgumentCaptor<ReviewRiskItemEntity> captor = ArgumentCaptor.forClass(ReviewRiskItemEntity.class);
        verify(mapper).insert(captor.capture());
        ReviewRiskItemEntity inserted = captor.getValue();
        assertThat(inserted.getReportId()).isEqualTo(reportId);
        assertThat(inserted.getTaskId()).isEqualTo(taskId);
        assertThat(inserted.getRiskType()).isEqualTo("REFERENCE_FORMAT");
        assertThat(inserted.getRiskLevel()).isEqualTo("HIGH");
        assertThat(inserted.getEvidence()).isEqualTo("[1] 缺少年份");
        assertThat(inserted.getSuggestion()).isEqualTo("补全年份");
        assertThat(inserted.getDetector()).isEqualTo("MODEL");
        assertThat(inserted.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.9));
        assertThat(inserted.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void updateStatusShouldLoadExistingAndUpdateStatusAndReviewerNote() {
        UUID riskId = UUID.randomUUID();
        ReviewRiskItemMapper mapper = mock(ReviewRiskItemMapper.class);
        ReviewRiskItemEntity existing = new ReviewRiskItemEntity();
        existing.setId(riskId);
        existing.setStatus("OPEN");
        when(mapper.selectById(riskId)).thenReturn(existing);
        ReviewRiskService service = new ReviewRiskService(mapper);

        service.updateStatus(riskId, "CONFIRMED", "证据明确");

        ArgumentCaptor<ReviewRiskItemEntity> captor = ArgumentCaptor.forClass(ReviewRiskItemEntity.class);
        verify(mapper).updateById(captor.capture());
        ReviewRiskItemEntity updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(riskId);
        assertThat(updated.getStatus()).isEqualTo("CONFIRMED");
        assertThat(updated.getReviewerNote()).isEqualTo("证据明确");
        assertThat(updated.getUpdatedAt()).isNotNull();
    }
}
