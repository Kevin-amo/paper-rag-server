package com.lqr.paperragserver.review.service.impl;

import com.lqr.paperragserver.document.event.DocumentIndexedEvent;
import com.lqr.paperragserver.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 评审文档入库完成后的任务创建入口。
 */
@Component
@RequiredArgsConstructor
public class ReviewDocumentIndexedListener {

    private final ReviewService reviewService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentIndexed(DocumentIndexedEvent event) {
        reviewService.createTaskForIndexedReviewDocument(event.ownerUserId(), event.sourceId());
    }
}