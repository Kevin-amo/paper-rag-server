package com.lqr.papermind.document.service.impl;

import com.lqr.papermind.document.config.DocumentIngestionProperties;
import com.lqr.papermind.document.service.DocumentUploadStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 基于本地文件系统的上传原始文件存储实现。
 */
@Service
@RequiredArgsConstructor
public class DocumentUploadStorageServiceImpl implements DocumentUploadStorageService {

    private final DocumentIngestionProperties properties;

    /**
     * 将上传文件持久化到本地文件系统。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param jobId 入库任务 ID
     * @param file 上传的文件
     * @param fallbackFileName 备用文件名
     * @return 存储结果，包含文件名和存储路径
     * @throws IOException 文件写入失败时抛出
     * @throws IllegalArgumentException 上传文件为空或路径非法时抛出
     */
    @Override
    public StoredUpload store(UUID ownerUserId, String sourceId, UUID jobId, MultipartFile file, String fallbackFileName) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String safeFileName = safeFileName(firstNonBlank(file.getOriginalFilename(), fallbackFileName, "upload.bin"));
        Path targetDirectory = rootDirectory()
                .resolve(ownerUserId.toString())
                .resolve(safePathSegment(sourceId))
                .normalize();
        Files.createDirectories(targetDirectory);
        Path targetPath = targetDirectory.resolve(jobId + "-" + safeFileName).normalize();
        if (!targetPath.startsWith(targetDirectory)) {
            throw new IllegalArgumentException("上传文件路径非法");
        }
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return new StoredUpload(safeFileName, targetPath.toString());
    }

    /**
     * 读取指定路径的文件内容。
     *
     * @param filePath 文件存储路径
     * @return 文件二进制内容
     * @throws IOException 文件读取失败时抛出
     */
    @Override
    public byte[] read(String filePath) throws IOException {
        Path path = Paths.get(filePath).normalize();
        return Files.readAllBytes(path);
    }

    /**
     * 删除指定路径的文件。
     *
     * @param filePath 文件存储路径
     * @throws IOException 文件删除失败时抛出
     */
    @Override
    public void delete(String filePath) throws IOException {
        if (filePath != null && !filePath.isBlank()) {
            Files.deleteIfExists(Paths.get(filePath).normalize());
        }
    }

    /**
     * 获取配置的存储根目录绝对路径。
     *
     * @return 存储根目录路径
     */
    private Path rootDirectory() {
        return Paths.get(properties.storageDir()).toAbsolutePath().normalize();
    }

    /**
     * 清理文件名中的非法字符和路径遍历片段，返回安全的文件名。
     *
     * @param fileName 原始文件名
     * @return 清理后的安全文件名
     */
    private String safeFileName(String fileName) {
        String cleaned = StringUtils.cleanPath(fileName).replace('\\', '/');
        int slashIndex = cleaned.lastIndexOf('/');
        String baseName = slashIndex >= 0 ? cleaned.substring(slashIndex + 1) : cleaned;
        baseName = baseName.replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[\\/]+", "-")
                .replace("..", "")
                .trim();
        return baseName.isBlank() ? "upload.bin" : baseName;
    }

    /**
     * 将路径片段中的非法字符替换为连字符，返回安全的路径段。
     *
     * @param value 原始路径片段
     * @return 清理后的安全路径段
     */
    private String safePathSegment(String value) {
        String segment = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "-");
        return segment.isBlank() ? "unknown" : segment;
    }

    /**
     * 从多个候选值中返回第一个非空白字符串。
     *
     * @param values 候选值列表
     * @return 第一个非空白值，均空白时返回默认文件名
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "upload.bin";
    }
}