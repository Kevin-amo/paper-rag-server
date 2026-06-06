package com.lqr.paperragserver.document.structured.service;

import com.lqr.paperragserver.document.structured.entity.PaperStructuredParseEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * 论文结构化解析编排服务。
 */
public interface PaperStructuredParseService {

    Optional<PaperStructuredParseEntity> find(UUID ownerUserId, String sourceId);

    PaperStructuredParseEntity generate(UUID ownerUserId, String sourceId);

    PaperStructuredParseEntity regenerate(UUID ownerUserId, String sourceId);
}