package com.lqr.paperragserver.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName(value = "public.review_consensus", autoResultMap = true)
public class ReviewConsensusEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID taskId;
    private UUID leadReviewerUserId;
    private UUID confirmedByUserId;

    @TableField(value = "score_summary", typeHandler = JsonbTypeHandler.class)
    private Object scoreSummary;

    @TableField(value = "comment_summary", typeHandler = JsonbTypeHandler.class)
    private Object commentSummary;

    @TableField(value = "disagreement_items", typeHandler = JsonbTypeHandler.class)
    private Object disagreementItems;

    private Integer finalScore;
    private String finalRecommendation;
    private String status;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
