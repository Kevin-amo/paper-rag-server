package com.lqr.paperragserver.document.service.impl;

import com.lqr.paperragserver.document.config.DocumentIngestionRabbitConfiguration;
import com.lqr.paperragserver.document.model.DocumentIngestionMessage;
import com.lqr.paperragserver.document.service.DocumentIngestionProducer;
import com.lqr.paperragserver.document.service.DocumentIngestionJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ 文档入库消息生产者。
 */
@Service
@RequiredArgsConstructor
public class RabbitDocumentIngestionProducer implements DocumentIngestionProducer {

    private final RabbitTemplate rabbitTemplate;
    private final DocumentIngestionJobService documentIngestionJobService;

    /**
     * 将文档入库消息投递到 RabbitMQ，投递成功后标记任务为已入队状态。
     *
     * @param message 文档入库消息
     */
    @Override
    public void publish(DocumentIngestionMessage message) {
        // 投递RabbitMQ消息（只传递任务索引，后续消费者根据jobId去数据库查任务）
        try {
            rabbitTemplate.convertAndSend(
                    DocumentIngestionRabbitConfiguration.EXCHANGE,
                    DocumentIngestionRabbitConfiguration.ROUTING_KEY,
                    message
            );
            // 标记任务已投递
            documentIngestionJobService.markQueued(message.ownerUserId(), message.jobId());
        } catch (RuntimeException ex) {
            documentIngestionJobService.markFailed(message.ownerUserId(), message.jobId(), message.sourceId(), ex.getMessage());
            throw ex;
        }
    }
}