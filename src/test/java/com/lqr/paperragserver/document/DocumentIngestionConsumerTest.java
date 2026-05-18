package com.lqr.paperragserver.document;

import com.lqr.paperragserver.config.DocumentIngestionProperties;
import com.lqr.paperragserver.document.impl.DocumentIngestionConsumer;
import com.lqr.paperragserver.document.model.DocumentIngestionMessage;
import com.lqr.paperragserver.document.service.DocumentIngestionProducer;
import com.lqr.paperragserver.document.service.DocumentIngestionService;
import com.lqr.paperragserver.paper.entity.DocumentIngestionJob;
import com.lqr.paperragserver.paper.service.DocumentIngestionJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIngestionConsumerTest {

    private final DocumentIngestionJobService jobService = mock(DocumentIngestionJobService.class);
    private final DocumentIngestionService ingestionService = mock(DocumentIngestionService.class);
    private final DocumentIngestionProducer producer = mock(DocumentIngestionProducer.class);
    private DocumentIngestionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DocumentIngestionConsumer(
                jobService,
                ingestionService,
                producer,
                new DocumentIngestionProperties("storage", true, 2, new DocumentIngestionProperties.Listener(2, 4))
        );
    }

    @Test
    void consumeShouldSkipIndexedJob() {
        DocumentIngestionMessage message = message();
        DocumentIngestionJob job = job(message, DocumentIngestionJobService.STATUS_INDEXED);
        when(jobService.findJob(message.ownerUserId(), message.jobId())).thenReturn(Optional.of(job));

        consumer.consume(message);

        verify(ingestionService, never()).processJob(job);
    }

    @Test
    void consumeShouldProcessClaimedJob() {
        DocumentIngestionMessage message = message();
        DocumentIngestionJob job = job(message, DocumentIngestionJobService.STATUS_QUEUED);
        when(jobService.findJob(message.ownerUserId(), message.jobId())).thenReturn(Optional.of(job));
        when(jobService.claimForProcessing(message.ownerUserId(), message.jobId())).thenReturn(true);

        consumer.consume(message);

        verify(ingestionService).processJob(job);
    }

    @Test
    void consumeShouldMarkFailedAfterMaxRetry() {
        DocumentIngestionMessage message = message();
        DocumentIngestionJob job = job(message, DocumentIngestionJobService.STATUS_QUEUED);
        when(jobService.findJob(message.ownerUserId(), message.jobId())).thenReturn(Optional.of(job));
        when(jobService.claimForProcessing(message.ownerUserId(), message.jobId())).thenReturn(true);
        when(jobService.incrementRetry(message.ownerUserId(), message.jobId())).thenReturn(2);
        doThrow(new IllegalStateException("解析失败")).when(ingestionService).processJob(job);

        try {
            consumer.consume(message);
        } catch (IllegalStateException ignored) {
        }

        verify(jobService).markFailed(message.ownerUserId(), message.jobId(), message.sourceId(), "解析失败");
    }

    private DocumentIngestionMessage message() {
        return new DocumentIngestionMessage(UUID.randomUUID(), UUID.randomUUID(), "source-1");
    }

    private DocumentIngestionJob job(DocumentIngestionMessage message, String status) {
        DocumentIngestionJob job = new DocumentIngestionJob();
        job.setId(message.jobId());
        job.setOwnerUserId(message.ownerUserId());
        job.setSourceId(message.sourceId());
        job.setStatus(status);
        return job;
    }
}