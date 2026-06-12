package com.lqr.papermind.storage.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.lqr.papermind.storage.config.OssProperties;
import com.lqr.papermind.storage.service.ObjectStorageService;
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

    /**
     * 上传对象到阿里云 OSS。
     *
     * @param objectKey    对象键
     * @param inputStream  文件输入流
     * @param contentLength 文件内容长度（字节）
     * @param contentType  文件内容类型
     */
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

    /**
     * 删除阿里云 OSS 上的指定对象。
     *
     * @param objectKey 对象键
     */
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

    /**
     * 拼接对象的公共访问地址。
     *
     * @param objectKey 对象键
     * @return 公共访问 URL，未配置基础地址时返回对象键本身
     */
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

    /**
     * 创建阿里云 OSS 客户端实例。
     *
     * @return 已初始化的 OSS 客户端
     */
    private OSS createClient() {
        return new OSSClientBuilder().build(
                properties.endpoint(),
                properties.accessKeyId(),
                properties.accessKeySecret()
        );
    }

    /**
     * 校验 OSS 配置是否完整，配置不完整时抛出内部服务器错误异常。
     *
     * @throws ResponseStatusException OSS 配置不完整时抛出
     */
    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "OSS 配置不完整");
        }
    }
}