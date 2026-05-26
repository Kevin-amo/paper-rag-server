package com.lqr.paperragserver.document.dto;

public record BatchDocumentIngestionItemRequest(String fileName, String sourceId, String title) {
}