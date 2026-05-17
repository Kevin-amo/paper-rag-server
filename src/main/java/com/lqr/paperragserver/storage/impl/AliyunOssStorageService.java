package com.lqr.paperragserver.storage.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.lqr.paperragserver.config.OssProperties;
import com.lqr.paperragserver.storage.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;

/**
 * 阿里云 OSS 对象存储实现。
 */
@Service
@RequiredArgsConstructor
public class AliyunOssStorageService implements ObjectStorageService {

    private final OssProperties properties;

    @Override
    public void putObject(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        ensureConfigured();
        OSS ossClient = createClient();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentLength);
            metadata.setContentType(contentType);
            ossClient.putObject(properties.bucket(), objectKey, inputStream, metadata);
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        ensureConfigured();
        OSS ossClient = createClient();
        try {
            ossClient.deleteObject(properties.bucket(), objectKey);
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        String baseUrl = properties.normalizedPublicBaseUrl();
        if (baseUrl.isBlank()) {
            return objectKey;
        }
        return baseUrl + "/" + objectKey.replaceAll("^/+", "");
    }

    private OSS createClient() {
        return new OSSClientBuilder().build(
                properties.endpoint(),
                properties.accessKeyId(),
                properties.accessKeySecret()
        );
    }

    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "OSS 配置不完整");
        }
    }
}