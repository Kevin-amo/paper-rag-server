package com.lqr.paperragserver.document.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 上传原始文档持久化服务。
 */
public interface DocumentUploadStorageService {

    StoredUpload store(UUID ownerUserId, String sourceId, UUID jobId, MultipartFile file, String fallbackFileName) throws IOException;

    byte[] read(String filePath) throws IOException;

    void delete(String filePath) throws IOException;

    record StoredUpload(String fileName, String filePath) {
    }
}