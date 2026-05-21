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
@TableName(value = "public.paper_document", autoResultMap = true)
public class PaperDocument {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID ownerUserId;
    private String sourceId;
    private String title;
    private String origin;
    private String fileName;
    private String fileType;
    private Long fileSize;

    @TableField(value = "authors", typeHandler = JsonbTypeHandler.class)
    private Object authors;

    @TableField("abstract")
    private String abstractText;

    private String doi;
    private String journal;
    private Integer publishYear;

    @TableField(value = "keywords", typeHandler = JsonbTypeHandler.class)
    private Object keywords;

    private String contentText;

    @TableField(value = "metadata", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> metadata;

    private String status;
    private Integer chunkCount;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}