package com.lqr.paperragserver.document.service.impl;

import com.lqr.paperragserver.document.config.DocumentIngestionProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;

/**
 * 清理文档入库过程中遗留的本地上传临时文件。
 */
@Component
@RequiredArgsConstructor
public class DocumentUploadCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadCleanupScheduler.class);

    private final DocumentIngestionProperties properties;

    /**
     * 定时任务，读取配置文件cron信息，每小时执行一次clean
     */
    @Scheduled(cron = "${app.document-ingestion.cleanup.cron:0 0 * * * *}")
    public void cleanupExpiredUploads() {
        DocumentIngestionProperties.Cleanup cleanup = properties.cleanup();
        if (cleanup == null || !cleanup.enabled()) {
            return;
        }
        Path rootDirectory = Paths.get(properties.storageDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(rootDirectory)) {
            return;
        }
        Instant expireBefore = Instant.now().minus(cleanup.retention());
        try {
            deleteExpiredFiles(rootDirectory, expireBefore);
            deleteEmptyDirectories(rootDirectory);
        } catch (IOException ex) {
            log.warn("清理文档上传临时文件失败: {}", rootDirectory, ex);
        }
    }

    private void deleteExpiredFiles(Path rootDirectory, Instant expireBefore) throws IOException {
        // 使用 Java NIO 的 walkFileTree 方法递归遍历目录下的所有文件和子目录
        Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile() && attrs.lastModifiedTime().toInstant().isBefore(expireBefore)) {
                    Files.deleteIfExists(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.warn("跳过无法访问的文档上传临时文件: {}", file, exc);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteEmptyDirectories(Path rootDirectory) throws IOException {
        Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    log.warn("跳过无法访问的文档上传临时目录: {}", dir, exc);
                    return FileVisitResult.CONTINUE;
                }
                if (!dir.equals(rootDirectory) && isEmptyDirectory(dir)) {
                    Files.deleteIfExists(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isEmptyDirectory(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        }
    }
}