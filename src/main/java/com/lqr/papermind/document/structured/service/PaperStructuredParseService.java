package com.lqr.papermind.document.structured.service;

import com.lqr.papermind.document.structured.entity.PaperStructuredParseEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * 论文结构化解析编排服务。
 */
public interface PaperStructuredParseService {

    /**
     * 根据用户ID和来源ID查询已有的结构化解析结果。
     *
     * @param ownerUserId 文档所有者用户ID
     * @param sourceId    文档来源标识
     * @return 解析结果（可能为空）
     */
    Optional<PaperStructuredParseEntity> find(UUID ownerUserId, String sourceId);

    /**
     * 生成论文结构化解析结果（如果已存在则直接返回）。
     *
     * @param ownerUserId 文档所有者用户ID
     * @param sourceId    文档来源标识
     * @return 解析结果
     */
    PaperStructuredParseEntity generate(UUID ownerUserId, String sourceId);

    /**
     * 重新生成论文结构化解析结果。
     *
     * @param ownerUserId 文档所有者用户ID
     * @param sourceId    文档来源标识
     * @return 重新生成的解析结果
     */
    PaperStructuredParseEntity regenerate(UUID ownerUserId, String sourceId);
}