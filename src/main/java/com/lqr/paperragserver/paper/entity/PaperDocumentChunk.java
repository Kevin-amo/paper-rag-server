package com.lqr.paperragserver.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
import com.lqr.paperragserver.common.typehandler.UuidTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "public.paper_document_chunk", autoResultMap = true)
public class PaperDocumentChunk {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID ownerUserId;
    private String chunkId;
    private String sourceId;
    private Integer chunkIndex;
    private String content;
    private String contentHash;
    private Integer chunkStart;
    private Integer chunkEnd;
    private Integer pageNumber;
    private String sectionTitle;

    @TableField(value = "metadata", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> metadata;

    @TableField(value = "vector_store_id", typeHandler = UuidTypeHandler.class)
    private UUID vectorStoreId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}