package com.ritham.erp.module.backup;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.backup.dto.BackupDirectoryDto;
import com.ritham.erp.module.backup.dto.BackupScheduleConfigRequest;
import com.ritham.erp.module.backup.dto.BackupStatusResponse;
import com.ritham.erp.module.backup.dto.BackupTriggerRequest;
import com.ritham.erp.module.backup.dto.BackupTriggerResponse;
import com.ritham.erp.module.backup.dto.BackupUploadResponse;
import com.ritham.erp.module.backup.dto.InspectBackupResponse;
import com.ritham.erp.module.backup.dto.RestoreBackupRequest;
import com.ritham.erp.module.backup.dto.RestoreBackupResponse;
import com.ritham.erp.module.backup.service.BackupService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

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
        ReflectionTestUtils.setField(backupService, "backupScriptPath", tempDir.resolve("nonexistent-backup.ps1").toString());
        ReflectionTestUtils.setField(backupService, "backupConfigFile", tempDir.resolve("backup-config.json").toString());
        ReflectionTestUtils.setField(backupService, "pgBinDir", tempDir.resolve("no-pg-bin").toString());
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

    @Test
    @DisplayName("getBackupStatus reports connected & writable for uncreated subfolder on mounted drive")
    void statusReportsWritableForUncreatedSubfolder() {
        Path futureFolder = tempDir.resolve("custom_new").resolve("nested");
        BackupStatusResponse status = backupService.getBackupStatus(futureFolder.toString());

        assertNotNull(status.getDrive());
        assertTrue(status.getDrive().isConnected());
        assertFalse(status.getDrive().isFolderExists());
        assertTrue(status.getDrive().isWritable());
        assertEquals(backupRoot.toString().replace("/", "\\"), status.getActiveBackupRoot());
    }

    @Test
    @DisplayName("updateActiveBackupLocation persists path cleanly and reloads via initPersistedBackupRoot")
    void updateActiveBackupLocationPersistsAndReloads() throws IOException {
        Path customFolder = tempDir.resolve("SelectedFolder").resolve("DailySnapshots");
        Path psBackup = tempDir.resolve("erp-backup.ps1");
        Files.writeString(psBackup, "$CONFIG = @{\n    BackupRoot = \"E:\\ERP-Backups\"\n}\n", StandardCharsets.UTF_8);
        Path cfgJson = tempDir.resolve("cfg").resolve("backup-config.json");

        ReflectionTestUtils.setField(backupService, "backupScriptPath", psBackup.toString());
        ReflectionTestUtils.setField(backupService, "backupConfigFile", cfgJson.toString());

        BackupStatusResponse updated = backupService.updateActiveBackupLocation(customFolder.toString(), true);
        assertEquals(customFolder.toAbsolutePath().normalize().toString(), updated.getActiveBackupRoot());
        assertTrue(Files.exists(customFolder.resolve("Database").resolve("Daily")));
        assertTrue(Files.exists(customFolder.resolve("Database").resolve("Uploads")));

        String scriptText = Files.readString(psBackup, StandardCharsets.UTF_8);
        assertTrue(scriptText.contains(customFolder.toAbsolutePath().normalize().toString()));
        assertFalse(scriptText.contains("\\\\\\\\"));

        BackupService reloaded = new BackupService();
        ReflectionTestUtils.setField(reloaded, "backupConfigFile", cfgJson.toString());
        ReflectionTestUtils.setField(reloaded, "backupScriptPath", psBackup.toString());
        ReflectionTestUtils.invokeMethod(reloaded, "initPersistedBackupRoot");

        assertEquals(
                customFolder.toAbsolutePath().normalize().toString(),
                ReflectionTestUtils.getField(reloaded, "backupRoot")
        );
    }

    @Test
    @DisplayName("uploadBackupFile stores uploaded .dump inside selected folder Database/Uploads directory")
    void uploadBackupFileStoresInsideSelectedFolderUploadsDirectory() throws IOException {
        Path selectedFolder = tempDir.resolve("ClientSelectedFolder");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ritham_test_2026.dump",
                "application/octet-stream",
                "PGDMP_DUMMY_CONTENT".getBytes(StandardCharsets.UTF_8)
        );

        BackupUploadResponse resp = backupService.uploadBackupFile(file, selectedFolder.toString());
        Path expectedFile = selectedFolder.toAbsolutePath().normalize()
                .resolve("Database").resolve("Uploads").resolve("ritham_test_2026.dump");

        assertEquals("Uploads", resp.getCategory());
        assertEquals(expectedFile.toString(), resp.getSavedPath());
        assertTrue(Files.exists(expectedFile));
    }

    @Test
    @DisplayName("listDirectories and createDirectory browse safely and block path traversal")
    void directoryBrowserListsAndCreatesSubfoldersWithTraversalGuard() {
        BackupDirectoryDto.DirectoryEntry created = backupService.createDirectory(tempDir.toString(), "MyErpBackups");
        assertTrue(Files.isDirectory(Path.of(created.getPath())));

        BackupDirectoryDto listed = backupService.listDirectories(tempDir.toString());
        assertTrue(listed.getEntries().stream().anyMatch(e -> "MyErpBackups".equals(e.getName())));

        AppException ex = assertThrows(AppException.class, () ->
                backupService.createDirectory(tempDir.toString(), "../Escape"));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("H2 backup, TEST drill, and PRODUCTION restore recover deleted DB rows and migration uploads")
    void h2BackupAndRestoreRoundtripPreservesDataAndSuspendsPool() throws Exception {
        String dbName = "h2roundtrip_" + System.nanoTime();
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        HikariConfig config = new HikariConfig();
        config.setPoolName("H2RoundtripTestPool");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("sa");
        config.setPassword("");
        config.setAllowPoolSuspension(true);
        config.setMaximumPoolSize(4);

        Path selectedFolder = tempDir.resolve("H2SelectedFolder");
        Path srcUploads = tempDir.resolve("srcUploads");
        Files.createDirectories(srcUploads);
        Path uploadedFile = srcUploads.resolve("sample-import.csv");
        Files.writeString(uploadedFile, "name,phone\nAarav,9876543210\n", StandardCharsets.UTF_8);

        try (HikariDataSource ds = new HikariDataSource(config)) {
            try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE branches (id BIGINT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("CREATE TABLE customers (id BIGINT PRIMARY KEY, name VARCHAR(100), branch_id BIGINT)");
                stmt.execute("CREATE TABLE customer_measurements (id BIGINT PRIMARY KEY, customer_id BIGINT)");
                stmt.execute("CREATE TABLE customer_orders (id BIGINT PRIMARY KEY, order_number VARCHAR(50), customer_id BIGINT)");
                stmt.execute("CREATE TABLE order_payments (id BIGINT PRIMARY KEY, order_id BIGINT, amount NUMERIC(12,2))");
                stmt.execute("CREATE TABLE employees (id BIGINT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("CREATE TABLE production_stages (id BIGINT PRIMARY KEY, stage_name VARCHAR(50))");

                stmt.execute("INSERT INTO branches VALUES (1, 'Main Branch')");
                stmt.execute("INSERT INTO customers VALUES (1, 'Customer One', 1), (2, 'Customer Two', 1)");
                stmt.execute("INSERT INTO customer_measurements VALUES (1, 1)");
                stmt.execute("INSERT INTO customer_orders VALUES (10, 'ORD-0010', 1), (11, 'ORD-0011', 2)");
                stmt.execute("INSERT INTO order_payments VALUES (100, 10, 1500.00)");
                stmt.execute("INSERT INTO employees VALUES (1, 'Master Tailor')");
                stmt.execute("INSERT INTO production_stages VALUES (1, 'CUTTING')");
            }

            BackupService h2Service = new BackupService();
            ReflectionTestUtils.setField(h2Service, "backupRoot", selectedFolder.toString());
            ReflectionTestUtils.setField(h2Service, "backupConfigFile", tempDir.resolve("h2-backup-config.json").toString());
            ReflectionTestUtils.setField(h2Service, "migrationUploadDir", srcUploads.toString());
            ReflectionTestUtils.setField(h2Service, "datasourceUrl", jdbcUrl);
            ReflectionTestUtils.setField(h2Service, "dbUsername", "sa");
            ReflectionTestUtils.setField(h2Service, "dbPassword", "");
            ReflectionTestUtils.setField(h2Service, "databaseName", dbName);
            ReflectionTestUtils.setField(h2Service, "dataSource", ds);

            BackupTriggerResponse trigger = h2Service.runBackupNow(BackupTriggerRequest.builder()
                    .targetLocation(selectedFolder.toString())
                    .completeBackup(true)
                    .build());

            assertTrue(trigger.isSuccess(), "Expected H2 backup to succeed: " + trigger.getMessage());
            assertNotNull(trigger.getBackupFileName());
            assertTrue(trigger.getBackupFileName().endsWith(".sql"));
            Path snapshotFile = Path.of(trigger.getBackupFilePath());
            assertTrue(Files.exists(snapshotFile));

            long migrationCopies;
            try (var stream = Files.list(selectedFolder.resolve("Files").resolve("Daily"))) {
                migrationCopies = stream.filter(p -> p.getFileName().toString().startsWith("migration_")).count();
            }
            assertTrue(migrationCopies >= 1, "Expected migration upload directory copy under Files/Daily");

            InspectBackupResponse inspected = h2Service.inspectBackup(
                    selectedFolder.toString(), "Daily", trigger.getBackupFileName(), null);
            assertTrue(inspected.isValid());
            assertTrue(inspected.getTocEntriesCount() > 0);

            // Simulate accidental deletion of live orders, customers, and local upload file
            try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM order_payments");
                stmt.execute("DELETE FROM customer_orders");
                stmt.execute("DELETE FROM customer_measurements");
                stmt.execute("DELETE FROM customers");
            }
            Files.deleteIfExists(uploadedFile);
            assertEquals(0L, countRows(ds, "customers"));
            assertEquals(0L, countRows(ds, "customer_orders"));

            // Sandbox TEST drill must report backed-up row counts without modifying live DB
            RestoreBackupResponse testRestore = h2Service.restoreBackup(RestoreBackupRequest.builder()
                    .backupFileName(trigger.getBackupFileName())
                    .category("Daily")
                    .location(selectedFolder.toString())
                    .restoreMode("TEST")
                    .build());

            assertTrue(testRestore.isSuccess(), "Expected H2 TEST restore to succeed: " + testRestore.getMessage());
            assertEquals("H2", testRestore.getDatabaseEngine());
            assertFalse(testRestore.isRequiresReload());
            assertEquals(2L, testRestore.getTableRowCounts().get("customers"));
            assertEquals(2L, testRestore.getTableRowCounts().get("customer_orders"));
            assertEquals(0L, countRows(ds, "customers"), "TEST drill must not alter live DB");
            Path backupLogPath = selectedFolder.resolve("Logs").resolve("backup.log");
            assertTrue(Files.exists(backupLogPath));
            assertTrue(Files.readString(backupLogPath, StandardCharsets.UTF_8).contains("test drill verified"));

            // PRODUCTION restore must recover live DB rows + migration upload files with pool suspension
            RestoreBackupResponse prodRestore = h2Service.restoreBackup(RestoreBackupRequest.builder()
                    .backupFileName(trigger.getBackupFileName())
                    .category("Daily")
                    .location(selectedFolder.toString())
                    .restoreMode("PRODUCTION")
                    .confirmText("CONFIRM RESTORE")
                    .build());

            assertTrue(prodRestore.isSuccess(), "Expected H2 PRODUCTION restore to succeed: " + prodRestore.getMessage());
            assertEquals("H2", prodRestore.getDatabaseEngine());
            assertTrue(prodRestore.isRequiresReload());
            assertTrue(prodRestore.getFilesRestoredCount() >= 1);
            assertEquals(2L, prodRestore.getTableRowCounts().get("customers"));
            assertEquals(2L, prodRestore.getTableRowCounts().get("customer_orders"));
            assertEquals(2L, countRows(ds, "customers"));
            assertEquals(2L, countRows(ds, "customer_orders"));
            assertEquals(1L, countRows(ds, "order_payments"));
            assertTrue(Files.exists(uploadedFile), "Expected migration upload file to be recovered");
        }
    }

    @Test
    @DisplayName("restoreBackup rejects mismatched snapshot format for active DB engine")
    void restoreBackupRejectsEngineExtensionMismatch() throws IOException {
        Path sqlSnapshot = backupRoot.resolve("Database").resolve("Daily").resolve("h2_snap.sql");
        Files.writeString(sqlSnapshot, "CREATE TABLE t(id INT);", StandardCharsets.UTF_8);

        // Default backupService has empty datasourceUrl (PostgreSQL mode) -> .sql must be rejected
        AppException pgMismatch = assertThrows(AppException.class, () ->
                backupService.restoreBackup(RestoreBackupRequest.builder()
                        .backupFileName("h2_snap.sql")
                        .category("Daily")
                        .location(backupRoot.toString())
                        .restoreMode("TEST")
                        .build()));
        assertEquals(ErrorCode.VALIDATION_FAILED, pgMismatch.getErrorCode());

        // H2-configured service -> .dump must be rejected
        Path pgDump = backupRoot.resolve("Database").resolve("Daily").resolve("pg_snap.dump");
        Files.writeString(pgDump, "PGDMP", StandardCharsets.UTF_8);
        BackupService h2MismatchService = new BackupService();
        ReflectionTestUtils.setField(h2MismatchService, "backupRoot", backupRoot.toString());
        ReflectionTestUtils.setField(h2MismatchService, "backupConfigFile", tempDir.resolve("h2-mismatch.json").toString());
        ReflectionTestUtils.setField(h2MismatchService, "datasourceUrl", "jdbc:h2:mem:mismatch;MODE=PostgreSQL");

        AppException h2Mismatch = assertThrows(AppException.class, () ->
                h2MismatchService.restoreBackup(RestoreBackupRequest.builder()
                        .backupFileName("pg_snap.dump")
                        .category("Daily")
                        .location(backupRoot.toString())
                        .restoreMode("TEST")
                        .build()));
        assertEquals(ErrorCode.VALIDATION_FAILED, h2Mismatch.getErrorCode());
    }

    @Test
    @DisplayName("restoreBackup treats non-zero script exit code (exit 1) strictly as failure")
    void restoreBackupTreatsExitCodeOneAsFailure() throws IOException {
        Path dummyDump = backupRoot.resolve("Database").resolve("Daily").resolve("fail_case.dump");
        Files.writeString(dummyDump, "PGDMP", StandardCharsets.UTF_8);

        Path failingScript = tempDir.resolve("fail-restore.ps1");
        Files.writeString(failingScript, "Write-Output 'Simulated restore abort'\nexit 1\n", StandardCharsets.UTF_8);
        ReflectionTestUtils.setField(backupService, "restoreScriptPath", failingScript.toString());

        RestoreBackupResponse response = backupService.restoreBackup(RestoreBackupRequest.builder()
                .backupFileName("fail_case.dump")
                .category("Daily")
                .location(backupRoot.toString())
                .restoreMode("TEST")
                .build());

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("exit code 1"), "Unexpected message: " + response.getMessage());
    }

    @Test
    @DisplayName("H2 PRODUCTION restore aborts cleanly before dropping live DB if SQL replay fails")
    void h2ProductionRestoreAbortsBeforeWipingLiveDbIfScriptIsCorrupt() throws Exception {
        String dbName = "h2corrupt_" + System.nanoTime();
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        HikariConfig config = new HikariConfig();
        config.setPoolName("H2CorruptGuardTestPool");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("sa");
        config.setPassword("");
        config.setAllowPoolSuspension(true);

        Path selectedFolder = tempDir.resolve("H2CorruptTestFolder");
        Files.createDirectories(selectedFolder.resolve("Database").resolve("Daily"));
        Path brokenSql = selectedFolder.resolve("Database").resolve("Daily").resolve("broken_snapshot.sql");
        Files.writeString(brokenSql, "CREATE TABLE ok_tbl (id INT);\nTHIS IS INVALID SQL THAT MUST FAIL;\n",
                StandardCharsets.UTF_8);

        try (HikariDataSource ds = new HikariDataSource(config)) {
            try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE branches (id BIGINT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("CREATE TABLE customers (id BIGINT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("CREATE TABLE customer_orders (id BIGINT PRIMARY KEY, order_number VARCHAR(50))");
                stmt.execute("INSERT INTO branches VALUES (1, 'Main Branch')");
                stmt.execute("INSERT INTO customers VALUES (1, 'Live Customer')");
                stmt.execute("INSERT INTO customer_orders VALUES (1, 'ORD-LIVE')");
            }

            BackupService h2Service = new BackupService();
            ReflectionTestUtils.setField(h2Service, "backupRoot", selectedFolder.toString());
            ReflectionTestUtils.setField(h2Service, "backupConfigFile", tempDir.resolve("h2-corrupt.json").toString());
            ReflectionTestUtils.setField(h2Service, "datasourceUrl", jdbcUrl);
            ReflectionTestUtils.setField(h2Service, "dbUsername", "sa");
            ReflectionTestUtils.setField(h2Service, "dbPassword", "");
            ReflectionTestUtils.setField(h2Service, "databaseName", dbName);
            ReflectionTestUtils.setField(h2Service, "dataSource", ds);

            RestoreBackupResponse prodRestore = h2Service.restoreBackup(RestoreBackupRequest.builder()
                    .backupFileName("broken_snapshot.sql")
                    .category("Daily")
                    .location(selectedFolder.toString())
                    .restoreMode("PRODUCTION")
                    .confirmText("CONFIRM RESTORE")
                    .build());

            assertFalse(prodRestore.isSuccess(), "Expected corrupt SQL restore to fail safely");
            assertEquals(1L, countRows(ds, "customers"), "Live customers must NOT be dropped on invalid SQL");
            assertEquals(1L, countRows(ds, "customer_orders"), "Live orders must NOT be dropped on invalid SQL");
        }
    }

    @Test
    @DisplayName("Daily 7:00 PM schedule persists in config, targets selected folder, and enforces GFS Daily limit")
    void daily7PmSchedulePersistsAndTriggersIntoSelectedFolder() throws Exception {
        String dbName = "h2sched_" + System.nanoTime();
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        HikariConfig config = new HikariConfig();
        config.setPoolName("H2ScheduleTestPool");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("sa");
        config.setPassword("");

        Path selectedFolder = tempDir.resolve("ScheduledSelectedFolder");
        Path configJson = tempDir.resolve("sched-backup-config.json");

        try (HikariDataSource ds = new HikariDataSource(config)) {
            try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE branches (id BIGINT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("INSERT INTO branches VALUES (1, 'Ritham Main Studio')");
            }

            BackupService schedService = new BackupService();
            ReflectionTestUtils.setField(schedService, "backupRoot", selectedFolder.toString());
            ReflectionTestUtils.setField(schedService, "backupConfigFile", configJson.toString());
            ReflectionTestUtils.setField(schedService, "scheduleScriptPath", tempDir.resolve("no-task.ps1").toString());
            ReflectionTestUtils.setField(schedService, "datasourceUrl", jdbcUrl);
            ReflectionTestUtils.setField(schedService, "dbUsername", "sa");
            ReflectionTestUtils.setField(schedService, "dbPassword", "");
            ReflectionTestUtils.setField(schedService, "dataSource", ds);

            schedService.updateActiveBackupLocation(selectedFolder.toString(), true);
            BackupStatusResponse status = schedService.updateBackupSchedule(BackupScheduleConfigRequest.builder()
                    .enabled(true)
                    .hour(19)
                    .minute(0)
                    .backupOnDeviceConnect(true)
                    .syncWindowsTask(false)
                    .build());

            assertTrue(status.isScheduleEnabled());
            assertEquals(19, status.getScheduleHour());
            assertEquals(0, status.getScheduleMinute());
            assertEquals("Daily 7:00 PM", status.getScheduleTimeText());
            assertTrue(status.getTaskScheduleText().contains("7:00 PM"));
            assertTrue(status.getTaskScheduleText().contains(selectedFolder.toString()));
            assertTrue(status.getNextScheduledRun().contains("at 7:00 PM"));
            assertTrue(Files.readString(configJson, StandardCharsets.UTF_8).contains("\"scheduleHour\" : 19"));

            // Ensure before 7:00 PM does not trigger automatic daily run
            ReflectionTestUtils.setField(schedService, "lastAutoBackupDate", null);
            schedService.evaluateAutoBackupTriggers(java.time.LocalDateTime.of(2026, 11, 25, 18, 59));
            Path dailyDir = selectedFolder.resolve("Database").resolve("Daily");
            long countBefore7pm;
            try (var s = Files.list(dailyDir)) {
                countBefore7pm = s.filter(p -> p.getFileName().toString().endsWith(".sql")).count();
            }
            assertEquals(0L, countBefore7pm, "Must not trigger daily backup before 7:00 PM");

            // Trigger at 7:00 PM (19:00) into selectedFolder
            schedService.evaluateAutoBackupTriggers(java.time.LocalDateTime.of(2026, 11, 25, 19, 0));
            long countAt7pm;
            try (var s = Files.list(dailyDir)) {
                countAt7pm = s.filter(p -> p.getFileName().toString().endsWith(".sql")).count();
            }
            assertEquals(1L, countAt7pm, "Expected 7:00 PM scheduled backup inside selected folder");
            String logText = Files.readString(selectedFolder.resolve("Logs").resolve("backup.log"), StandardCharsets.UTF_8);
            assertTrue(logText.contains("[AUTO-SCHEDULED]"), "Expected [AUTO-SCHEDULED] audit line");

            // Create 8 older dummy snapshots to verify Daily retention limit = 7
            for (int day = 1; day <= 8; day++) {
                String ts = String.format("2026-10-%02d_19-00-00", day);
                Path oldFile = dailyDir.resolve("ritham_erp_h2_" + ts + ".sql");
                Files.writeString(oldFile, "-- old snapshot", StandardCharsets.UTF_8);
                oldFile.toFile().setLastModified(1000L * day);
            }
            schedService.runBackupNow(BackupTriggerRequest.builder()
                    .targetLocation(selectedFolder.toString())
                    .completeBackup(false)
                    .build());

            long retainedDaily;
            try (var s = Files.list(dailyDir)) {
                retainedDaily = s.filter(p -> p.getFileName().toString().endsWith(".sql")).count();
            }
            assertEquals(7L, retainedDaily, "Daily retention must keep newest 7 H2 snapshots");
        }
    }

    @Test
    @DisplayName("Selected device coming live (offline -> online transition) triggers immediate automatic backup")
    void deviceReconnectTriggersImmediateBackupOnOfflineToOnlineTransition() throws Exception {
        String dbName = "h2reconnect_" + System.nanoTime();
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        HikariConfig config = new HikariConfig();
        config.setPoolName("H2ReconnectTestPool");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("sa");
        config.setPassword("");

        Path selectedFolder = tempDir.resolve("ReconnectDriveFolder");
        Files.createDirectories(selectedFolder.resolve("Database").resolve("Daily"));

        try (HikariDataSource ds = new HikariDataSource(config)) {
            try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE branches (id BIGINT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("INSERT INTO branches VALUES (1, 'Ritham Main Studio')");
            }

            BackupService reconnectService = new BackupService();
            ReflectionTestUtils.setField(reconnectService, "backupRoot", selectedFolder.toString());
            ReflectionTestUtils.setField(reconnectService, "backupConfigFile", tempDir.resolve("reconnect-cfg.json").toString());
            ReflectionTestUtils.setField(reconnectService, "datasourceUrl", jdbcUrl);
            ReflectionTestUtils.setField(reconnectService, "dbUsername", "sa");
            ReflectionTestUtils.setField(reconnectService, "dbPassword", "");
            ReflectionTestUtils.setField(reconnectService, "dataSource", ds);
            ReflectionTestUtils.setField(reconnectService, "scheduleEnabled", true);
            ReflectionTestUtils.setField(reconnectService, "scheduleHour", 19);
            ReflectionTestUtils.setField(reconnectService, "scheduleMinute", 0);
            ReflectionTestUtils.setField(reconnectService, "backupOnDeviceConnect", true);
            // Simulate previous poll observed the selected drive detached/offline
            ReflectionTestUtils.setField(reconnectService, "lastKnownWatchedRoot", selectedFolder.toString());
            ReflectionTestUtils.setField(reconnectService, "lastKnownDeviceConnected", Boolean.FALSE);

            // Evaluate at 2:30 PM (before 7 PM) when device comes live -> must trigger immediately
            reconnectService.evaluateAutoBackupTriggers(java.time.LocalDateTime.of(2026, 11, 25, 14, 30));

            Path dailyDir = selectedFolder.resolve("Database").resolve("Daily");
            long createdCount;
            try (var s = Files.list(dailyDir)) {
                createdCount = s.filter(p -> p.getFileName().toString().endsWith(".sql")).count();
            }
            assertEquals(1L, createdCount, "Expected immediate backup when device transitioned offline -> online");
            String logText = Files.readString(selectedFolder.resolve("Logs").resolve("backup.log"), StandardCharsets.UTF_8);
            assertTrue(logText.contains("[AUTO-DEVICE-CONNECT]"), "Expected [AUTO-DEVICE-CONNECT] log entry");

            // Immediate subsequent tick must NOT trigger duplicate backup
            reconnectService.evaluateAutoBackupTriggers(java.time.LocalDateTime.of(2026, 11, 25, 14, 31));
            long afterSecondTick;
            try (var s = Files.list(dailyDir)) {
                afterSecondTick = s.filter(p -> p.getFileName().toString().endsWith(".sql")).count();
            }
            assertEquals(1L, afterSecondTick, "Steady online ticks must not trigger duplicate reconnect backups");
        }
    }

    private long countRows(HikariDataSource ds, String tableName) throws Exception {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return rs.next() ? rs.getLong(1) : -1L;
        }
    }
}
