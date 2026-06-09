package com.lqr.papermind.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 文档异步入库任务实体，记录上传文件从排队到索引完成的处理状态。
 */
@Data
@TableName("public.document_ingestion_job")
public class DocumentIngestionJob {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID ownerUserId;
    private String sourceId;
    private String fileName;
    private String filePath;
    private String title;
    private String status;
    private Integer progress;
    private String errorMessage;
    private Integer retryCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}