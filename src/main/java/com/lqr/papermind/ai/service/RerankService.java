package com.lqr.papermind.ai.service;

import com.lqr.papermind.common.model.RetrievedChunk;

import java.util.List;

/**
 * 检索候选精排序服务。
 */
public interface RerankService {

    /**
     * 根据用户问题对候选片段重新排序。
     *
     * @param question 用户问题
     * @param candidates 已完成权限过滤和候选融合的片段
     * @param topN 期望返回数量
     * @return 精排序后的片段列表；服务不可用时返回原候选顺序
     */
    List<RetrievedChunk> rerank(String question, List<RetrievedChunk> candidates, int topN);
}