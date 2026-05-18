package com.lqr.paperragserver.document.impl;

import com.lqr.paperragserver.config.DocumentIngestionProperties;
import com.lqr.paperragserver.document.service.DocumentUploadStorageService;
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
public class LocalDocumentUploadStorageService implements DocumentUploadStorageService {

    private final DocumentIngestionProperties properties;

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

    @Override
    public byte[] read(String filePath) throws IOException {
        Path path = Paths.get(filePath).normalize();
        return Files.readAllBytes(path);
    }

    @Override
    public void delete(String filePath) throws IOException {
        if (filePath != null && !filePath.isBlank()) {
            Files.deleteIfExists(Paths.get(filePath).normalize());
        }
    }

    private Path rootDirectory() {
        return Paths.get(properties.storageDir()).toAbsolutePath().normalize();
    }

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

    private String safePathSegment(String value) {
        String segment = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "-");
        return segment.isBlank() ? "unknown" : segment;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "upload.bin";
    }
}