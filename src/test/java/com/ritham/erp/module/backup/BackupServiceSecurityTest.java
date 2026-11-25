package com.ritham.erp.module.backup;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.backup.dto.RestoreBackupRequest;
import com.ritham.erp.module.backup.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceSecurityTest {

    @TempDir
    Path tempDir;

    private BackupService backupService;
    private Path backupRoot;
    private Path outsideFile;

    @BeforeEach
    void setUp() throws IOException {
        backupRoot = tempDir.resolve("ERP-Backups");
        Files.createDirectories(backupRoot.resolve("Database").resolve("Daily"));
        outsideFile = tempDir.resolve("secret.dump");
        Files.writeString(outsideFile, "outside");

        backupService = new BackupService();
        ReflectionTestUtils.setField(backupService, "backupRoot", backupRoot.toString());
        ReflectionTestUtils.setField(backupService, "restoreScriptPath", tempDir.resolve("nonexistent.ps1").toString());
    }

    @Test
    @DisplayName("inspectBackup rejects filePath outside configured backup directory")
    void inspectBackupRejectsOutsideFilePath() {
        AppException ex = assertThrows(AppException.class, () ->
                backupService.inspectBackup(backupRoot.toString(), null, null, outsideFile.toString()));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("inspectBackup rejects path traversal in fileName")
    void inspectBackupRejectsTraversalFileName() {
        AppException ex = assertThrows(AppException.class, () ->
                backupService.inspectBackup(backupRoot.toString(), "Daily", "../secret.dump", null));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("inspectBackup rejects invalid category outside allowlist")
    void inspectBackupRejectsInvalidCategory() {
        AppException ex = assertThrows(AppException.class, () ->
                backupService.inspectBackup(backupRoot.toString(), "../../Windows", "valid.dump", null));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("restoreBackup rejects filePath escaping configured backup root")
    void restoreBackupRejectsOutsideFilePath() {
        RestoreBackupRequest req = RestoreBackupRequest.builder()
                .backupFileName("secret.dump")
                .location(backupRoot.toString())
                .filePath(outsideFile.toString())
                .restoreMode("TEST")
                .build();

        AppException ex = assertThrows(AppException.class, () -> backupService.restoreBackup(req));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }
}
