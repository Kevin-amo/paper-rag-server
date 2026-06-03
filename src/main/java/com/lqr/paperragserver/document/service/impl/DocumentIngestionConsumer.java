package com.lqr.paperragserver.document.service.impl;

import com.lqr.paperragserver.document.config.DocumentIngestionProperties;
import com.lqr.paperragserver.document.config.DocumentIngestionRabbitConfiguration;
import com.lqr.paperragserver.document.model.DocumentIngestionMessage;
import com.lqr.paperragserver.document.service.DocumentIngestionProducer;
import com.lqr.paperragserver.document.service.DocumentIngestionService;
import com.lqr.paperragserver.document.entity.DocumentIngestionJob;
import com.lqr.paperragserver.document.service.DocumentIngestionJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 文档入库消息消费者。
 */
@Component
@RequiredArgsConstructor
public class DocumentIngestionConsumer {

    private final DocumentIngestionJobService documentIngestionJobService;
    private final DocumentIngestionService documentIngestionService;
    private final DocumentIngestionProducer documentIngestionProducer;
    private final DocumentIngestionProperties properties;

    /**
     * 消费文档入库消息，抢占任务并执行入库流程。
     * <p>处理失败时增加重试计数，超过最大重试次数后标记任务失败。</p>
     *
     * @param message 文档入库消息
     */
    @RabbitListener(queues = DocumentIngestionRabbitConfiguration.QUEUE,
            containerFactory = "documentIngestionListenerContainerFactory")
    public void consume(DocumentIngestionMessage message) {
        DocumentIngestionJob job = documentIngestionJobService.findJob(message.ownerUserId(), message.jobId())
                .orElse(null);
        if (job == null || DocumentIngestionJobService.STATUS_INDEXED.equals(job.getStatus())) {
            return;
        }
        if (!documentIngestionJobService.claimForProcessing(message.ownerUserId(), message.jobId())) {
            return;
        }
        try {
            documentIngestionService.processJob(job);
        } catch (RuntimeException ex) {
            int retryCount = documentIngestionJobService.incrementRetry(message.ownerUserId(), message.jobId());
            if (retryCount >= properties.maxRetryCount()) {
                documentIngestionJobService.markFailed(message.ownerUserId(), message.jobId(), message.sourceId(), ex.getMessage());
                throw ex;
            }
            documentIngestionProducer.publish(message);
        }
    }
}