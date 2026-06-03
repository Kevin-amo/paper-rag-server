package com.lqr.paperragserver.storage.service;

import java.io.InputStream;

/**
 * 对象存储服务边界，屏蔽具体云厂商 SDK。
 */
public interface ObjectStorageService {

    /**
     * 上传对象到对象存储。
     *
     * @param objectKey    对象键
     * @param inputStream  对象内容输入流
     * @param contentLength 内容长度
     * @param contentType  内容类型
     */
    void putObject(String objectKey, InputStream inputStream, long contentLength, String contentType);

    /**
     * 删除指定对象。
     *
     * @param objectKey 对象键
     */
    void deleteObject(String objectKey);

    /**
     * 获取对象的公共访问地址。
     *
     * @param objectKey 对象键
     * @return 公共访问 URL
     */
    String publicUrl(String objectKey);
}