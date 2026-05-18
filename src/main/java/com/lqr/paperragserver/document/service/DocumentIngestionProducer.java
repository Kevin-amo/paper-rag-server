package com.lqr.paperragserver.document.service;

import com.lqr.paperragserver.document.model.DocumentIngestionMessage;

/**
 * 文档入库消息生产者。
 */
public interface DocumentIngestionProducer {

    void publish(DocumentIngestionMessage message);
}