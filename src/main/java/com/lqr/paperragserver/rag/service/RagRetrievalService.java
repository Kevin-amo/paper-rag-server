package com.lqr.paperragserver.rag.service;

import com.lqr.paperragserver.common.model.RetrievedChunk;

import java.util.List;
import java.util.UUID;

/**
 * 问答检索服务接口。
 *
 * <p>实现类负责基于用户问题从向量库或其他检索源中召回相关上下文片段。</p>
 */
public interface RagRetrievalService {

    /**
     * 检索与问题相关的上下文片段。
     *
     * @param question 用户问题
     * @param topK 需要返回的候选片段数量
     * @return 召回的上下文片段列表
     */
    List<RetrievedChunk> retrieve(UUID ownerUserId, String question, int topK);
}