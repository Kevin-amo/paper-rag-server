package com.lqr.paperragserver.document.impl;

import com.lqr.paperragserver.document.config.DocumentIngestionRabbitConfiguration;
import com.lqr.paperragserver.document.model.DocumentIngestionMessage;
import com.lqr.paperragserver.document.service.DocumentIngestionProducer;
import com.lqr.paperragserver.paper.service.DocumentIngestionJobService;
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

    @Override
    public void publish(DocumentIngestionMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    DocumentIngestionRabbitConfiguration.EXCHANGE,
                    DocumentIngestionRabbitConfiguration.ROUTING_KEY,
                    message
            );
            documentIngestionJobService.markQueued(message.ownerUserId(), message.jobId());
        } catch (RuntimeException ex) {
            documentIngestionJobService.markFailed(message.ownerUserId(), message.jobId(), message.sourceId(), ex.getMessage());
            throw ex;
        }
    }
}