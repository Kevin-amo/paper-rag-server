package com.lqr.paperragserver.rag.service;

import com.lqr.paperragserver.common.model.RagAnswer;

/**
 * 问答编排服务接口。
 *
 * <p>实现类负责串联检索、提示词构造、模型调用和引用整理，最终输出完整答案。</p>
 */
public interface RagAnswerService {

    /**
     * 根据问题生成最终问答结果。
     *
     * @param question 用户问题
     * @param topK 召回片段数量，传空时使用默认值
     * @return 包含答案和引用的结果对象
     */
    RagAnswer answer(String question, Integer topK);

    /**
     * 使用默认召回数量生成问答结果。
     *
     * @param question 用户问题
     * @return 包含答案和引用的结果对象
     */
    default RagAnswer answer(String question) {
        return answer(question, null);
    }
}