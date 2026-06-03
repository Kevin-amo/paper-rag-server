package com.lqr.paperragserver.document.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 上传原始文档持久化服务。
 */
public interface DocumentUploadStorageService {

    /**
     * 将上传文件持久化到存储后端。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param jobId 入库任务 ID
     * @param file 上传的文件
     * @param fallbackFileName 备用文件名
     * @return 存储结果，包含文件名和存储路径
     * @throws IOException 文件写入失败时抛出
     */
    StoredUpload store(UUID ownerUserId, String sourceId, UUID jobId, MultipartFile file, String fallbackFileName) throws IOException;

    /**
     * 读取指定路径的文件内容。
     *
     * @param filePath 文件存储路径
     * @return 文件二进制内容
     * @throws IOException 文件读取失败时抛出
     */
    byte[] read(String filePath) throws IOException;

    /**
     * 删除指定路径的文件。
     *
     * @param filePath 文件存储路径
     * @throws IOException 文件删除失败时抛出
     */
    void delete(String filePath) throws IOException;

    record StoredUpload(String fileName, String filePath) {
    }
}