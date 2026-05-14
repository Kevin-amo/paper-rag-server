package com.lqr.paperragserver.common.model;

/**
 * 回答中引用的来源片段。
 *
 * @param sourceId 引用所属文档来源标识
 * @param chunkId 引用所属片段标识
 * @param chunkIndex 片段在原文中的顺序号
 * @param title 文档标题
 * @param excerpt 展示给用户的引用摘要或原文摘录
 * @param rankScore 该引用片段的融合排序分，用于前端展示与排序
 */
public record AnswerCitation(
        String sourceId,
        String chunkId,
        int chunkIndex,
        String title,
        String excerpt,
        double rankScore
) {
}