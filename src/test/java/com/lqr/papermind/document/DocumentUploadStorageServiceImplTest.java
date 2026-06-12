package com.lqr.papermind.document;

import com.lqr.papermind.document.config.DocumentIngestionProperties;
import com.lqr.papermind.document.service.impl.DocumentUploadStorageServiceImpl;
import com.lqr.papermind.document.service.DocumentUploadStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地文档上传存储服务的文件名净化和落盘路径测试。
 */
class DocumentUploadStorageServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void storeShouldSanitizeFileNameAndPersistUnderOwnerAndSource() throws Exception {
        DocumentIngestionProperties properties = new DocumentIngestionProperties(
                tempDir.toString(), true, 3, new DocumentIngestionProperties.Listener(2, 4), null
        );
        DocumentUploadStorageServiceImpl service = new DocumentUploadStorageServiceImpl(properties);
        UUID ownerUserId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "..\\evil.pdf", "application/pdf", "content".getBytes());

        DocumentUploadStorageService.StoredUpload upload = service.store(ownerUserId, "source/../1", jobId, file, "fallback.pdf");

        Path storedPath = Path.of(upload.filePath());
        assertThat(upload.fileName()).doesNotContain("..").doesNotContain("\\").doesNotContain("/");
        assertThat(storedPath).startsWith(tempDir.toAbsolutePath().normalize());
        assertThat(storedPath.toString()).contains(ownerUserId.toString());
        assertThat(Files.readString(storedPath)).isEqualTo("content");
    }
}