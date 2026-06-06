package com.lqr.paperragserver.document.structured.service.impl;

import com.lqr.paperragserver.document.event.DocumentIndexedEvent;
import com.lqr.paperragserver.document.structured.service.PaperStructuredParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文档索引完成后的结构化解析入口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredParseDocumentIndexedListener {

    private final PaperStructuredParseService paperStructuredParseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentIndexed(DocumentIndexedEvent event) {
        try {
            paperStructuredParseService.generate(event.ownerUserId(), event.sourceId());
        } catch (RuntimeException ex) {
            log.warn("paper.structured.parse.listener.failed ownerUserId={} sourceId={}", event.ownerUserId(), event.sourceId(), ex);
        }
    }
}