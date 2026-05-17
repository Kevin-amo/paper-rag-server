package com.lqr.paperragserver.storage.service;

import java.io.InputStream;

/**
 * 对象存储服务边界，屏蔽具体云厂商 SDK。
 */
public interface ObjectStorageService {

    void putObject(String objectKey, InputStream inputStream, long contentLength, String contentType);

    void deleteObject(String objectKey);

    String publicUrl(String objectKey);
}