package com.lqr.paperragserver.document;

import com.lqr.paperragserver.document.config.DocumentIngestionProperties;
import com.lqr.paperragserver.document.service.impl.DocumentUploadCleanupScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentUploadCleanupSchedulerTest {

    @TempDir
    Path tempDir;

    @Test
    void cleanupExpiredUploadsShouldDeleteOnlyExpiredFilesAndEmptyDirectories() throws Exception {
        Path expiredDirectory = Files.createDirectories(tempDir.resolve("user-1").resolve("source-1"));
        Path freshDirectory = Files.createDirectories(tempDir.resolve("user-2").resolve("source-2"));
        Path expiredFile = Files.writeString(expiredDirectory.resolve("old.pdf"), "old");
        Path freshFile = Files.writeString(freshDirectory.resolve("fresh.pdf"), "fresh");
        Files.setLastModifiedTime(expiredFile, FileTime.from(Instant.now().minus(Duration.ofHours(2))));
        Files.setLastModifiedTime(freshFile, FileTime.from(Instant.now()));
        DocumentUploadCleanupScheduler scheduler = new DocumentUploadCleanupScheduler(new DocumentIngestionProperties(
                tempDir.toString(), false, 3, new DocumentIngestionProperties.Listener(2, 4),
                new DocumentIngestionProperties.Cleanup(true, Duration.ofHours(1), "0 0 * * * *")
        ));

        scheduler.cleanupExpiredUploads();

        assertThat(expiredFile).doesNotExist();
        assertThat(expiredDirectory).doesNotExist();
        assertThat(freshFile).exists();
        assertThat(tempDir).exists();
    }

    @Test
    void cleanupExpiredUploadsShouldRespectDisabledCleanup() throws Exception {
        Path expiredFile = Files.writeString(tempDir.resolve("old.pdf"), "old");
        Files.setLastModifiedTime(expiredFile, FileTime.from(Instant.now().minus(Duration.ofHours(2))));
        DocumentUploadCleanupScheduler scheduler = new DocumentUploadCleanupScheduler(new DocumentIngestionProperties(
                tempDir.toString(), false, 3, new DocumentIngestionProperties.Listener(2, 4),
                new DocumentIngestionProperties.Cleanup(false, Duration.ofHours(1), "0 0 * * * *")
        ));

        scheduler.cleanupExpiredUploads();

        assertThat(expiredFile).exists();
    }
}