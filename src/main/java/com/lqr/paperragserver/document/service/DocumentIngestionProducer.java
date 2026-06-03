package com.lqr.paperragserver.document.service;

import com.lqr.paperragserver.document.model.DocumentIngestionMessage;

/**
 * 文档入库消息生产者。
 */
public interface DocumentIngestionProducer {

    /**
     * 发布文档入库消息到消息队列。
     *
     * @param message 文档入库消息
     */
    void publish(DocumentIngestionMessage message);
}