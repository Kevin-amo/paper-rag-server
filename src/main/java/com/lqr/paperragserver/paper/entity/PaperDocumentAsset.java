package com.lqr.paperragserver.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "public.paper_document_asset", autoResultMap = true)
public class PaperDocumentAsset {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID ownerUserId;
    private String assetId;
    private String sourceId;
    private Integer assetIndex;
    private String assetType;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String contentHash;
    private byte[] content;
    private String extractedText;
    private Integer textStart;
    private Integer textEnd;

    @TableField(value = "metadata", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> metadata;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}