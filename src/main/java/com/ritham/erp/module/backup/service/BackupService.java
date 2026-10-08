package com.ritham.erp.module.backup.service;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.backup.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BackupService {

    @Value("${app.backup.root:E:/ERP-Backups}")
    private String backupRoot;

    @Value("${app.backup.script-path:d:/ritham-erp/scripts/backup/erp-backup.ps1}")
    private String backupScriptPath;

    @Value("${app.backup.restore-script-path:d:/ritham-erp/scripts/backup/erp-restore.ps1}")
    private String restoreScriptPath;

    @Value("${app.backup.pg-bin-dir:C:/Program Files/PostgreSQL/17/bin}")
    private String pgBinDir;

    @Value("${app.backup.database:ritham_erp}")
    private String databaseName;

    private static final String LOCK_FILE_PATH = "C:/Windows/Temp/ritham-erp-backup.lock";
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile("^\\[(.*?)\\]\\s+\\[(.*?)\\]\\s+(.*)$");

    /**
     * Discovers all available storage locations / drives on the host system.
     */
    public List<BackupLocationDto> getAvailableLocations() {
        List<BackupLocationDto> list = new ArrayList<>();
        Map<String, Map<String, String>> wmiInfo = getWmiDriveInfo();

        File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) {
            return list;
        }

        for (File root : roots) {
            String path = root.getAbsolutePath();
            String letter = path.length() >= 2 ? path.substring(0, 2).toUpperCase() : path;
            Map<String, String> wmi = wmiInfo.getOrDefault(letter, Collections.emptyMap());

            String driveTypeStr = wmi.getOrDefault("DriveType", "3");
            boolean isRemovable = "2".equals(driveTypeStr);
            String driveType = isRemovable ? "REMOVABLE" : ("4".equals(driveTypeStr) ? "NETWORK" : "FIXED");

            long total = root.getTotalSpace();
            long free = root.getFreeSpace();
            long used = total - free;

            double totalGB = Math.round((total / 1024.0 / 1024.0 / 1024.0) * 100.0) / 100.0;
            double freeGB = Math.round((free / 1024.0 / 1024.0 / 1024.0) * 100.0) / 100.0;
            double usedPercent = total > 0 ? Math.round(((double) used / total * 100.0) * 10.0) / 10.0 : 0;

            String volName = wmi.getOrDefault("VolumeName", "");
            if (volName.isBlank()) {
                try {
                    FileStore store = Files.getFileStore(root.toPath());
                    volName = store.name();
                } catch (Exception ignored) {}
            }
            if (volName.isBlank()) {
                volName = isRemovable ? "External Drive" : ("C:".equalsIgnoreCase(letter) ? "System OS" : "Local Disk");
            }

            String fs = wmi.getOrDefault("FileSystem", "");
            if (fs.isBlank()) {
                try {
                    FileStore store = Files.getFileStore(root.toPath());
                    fs = store.type();
                } catch (Exception ignored) {}
            }
            if (fs.isBlank()) fs = "NTFS";

            boolean writable = testWriteAccess(root);
            String currentNorm = resolveRoot(null).toUpperCase();
            boolean isCurrent = currentNorm.startsWith(letter);

            String suggested = letter + "\\ERP-Backups";
            String statusText = (isRemovable ? "External USB | " : "Local | ") + freeGB + " GB Free";

            list.add(BackupLocationDto.builder()
                    .driveLetter(letter)
                    .volumeName(volName)
                    .driveType(driveType)
                    .isRemovable(isRemovable)
                    .fileSystem(fs)
                    .totalSpaceGB(totalGB)
                    .freeSpaceGB(freeGB)
                    .usedPercent(usedPercent)
                    .isWritable(writable)
                    .isCurrentTarget(isCurrent)
                    .suggestedPath(suggested)
                    .statusText(statusText)
                    .build());
        }

        list.sort((a, b) -> {
            if (a.isRemovable() != b.isRemovable()) return a.isRemovable() ? -1 : 1;
            if (a.isCurrentTarget() != b.isCurrentTarget()) return a.isCurrentTarget() ? -1 : 1;
            return a.getDriveLetter().compareTo(b.getDriveLetter());
        });

        return list;
    }

    /**
     * Updates and optionally persists the active default backup location.
     */
    public synchronized BackupStatusResponse updateActiveBackupLocation(String path, boolean persistAsDefault) {
        if (path == null || path.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Target backup path cannot be empty");
        }
        path = path.trim().replace("/", "\\");

        String drivePrefix = path.length() >= 2 ? path.substring(0, 2) : "";
        File driveFile = new File(drivePrefix + "\\");
        if (!driveFile.exists()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Target drive is not mounted or accessible: " + drivePrefix);
        }

        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                Files.createDirectories(p);
            }
            for (String sub : List.of("Database\\Daily", "Database\\Weekly", "Database\\Monthly", "Database\\Uploads", "Files\\Daily", "Logs")) {
                Path subPath = Paths.get(path, sub);
                if (!Files.exists(subPath)) {
                    Files.createDirectories(subPath);
                }
            }
            Path probe = Paths.get(path, ".erp_test_probe.tmp");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
        } catch (Exception e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Cannot write to target location: " + e.getMessage());
        }

        this.backupRoot = path;
        log.info("Active backup root updated to: {}", path);

        if (persistAsDefault) {
            updateScriptDefaultPath(path);
        }

        return getBackupStatus(path);
    }

    /**
     * Aggregates and returns the backup status for the specified or active location.
     */
    public BackupStatusResponse getBackupStatus(String locationOverride) {
        String root = resolveRoot(locationOverride);
        DriveStatusDto driveStatus = evaluateDriveStatus(root);
        boolean isPgRunning = checkPostgresRunning();
        String pgStatus = isPgRunning ? "RUNNING" : "STOPPED";

        List<BackupFileDto> allFiles = getAllBackupFilesInternal(root);
        int dailyCount = (int) allFiles.stream().filter(f -> "Daily".equalsIgnoreCase(f.getCategory())).count();
        int weeklyCount = (int) allFiles.stream().filter(f -> "Weekly".equalsIgnoreCase(f.getCategory())).count();
        int monthlyCount = (int) allFiles.stream().filter(f -> "Monthly".equalsIgnoreCase(f.getCategory())).count();
        int uploadCount = (int) allFiles.stream().filter(f -> "Uploads".equalsIgnoreCase(f.getCategory())).count();

        double totalSizeMB = allFiles.stream()
                .mapToDouble(f -> f != null ? f.getSizeMB() : 0.0)
                .sum();
        totalSizeMB = Math.round(totalSizeMB * 100.0) / 100.0;

        BackupFileDto latest = allFiles.isEmpty() ? null : allFiles.get(0);

        boolean isLocked = Files.exists(Paths.get(LOCK_FILE_PATH));
        List<BackupLogDto> recentLogs = getRecentLogs(root, 25);

        return BackupStatusResponse.builder()
                .drive(driveStatus)
                .databaseName(databaseName)
                .postgresStatus(pgStatus)
                .postgresRunning(isPgRunning)
                .dailyCount(dailyCount)
                .dailyLimit(7)
                .weeklyCount(weeklyCount)
                .weeklyLimit(4)
                .monthlyCount(monthlyCount)
                .monthlyLimit(12)
                .uploadCount(uploadCount)
                .totalCount(allFiles.size())
                .totalSizeMB(totalSizeMB)
                .latestBackup(latest)
                .taskScheduleText("Daily at 11:00 PM (Windows Task Scheduler)")
                .nextScheduledRun(calculateNextScheduledRun())
                .isLocked(isLocked)
                .recentLogs(recentLogs)
                .build();
    }

    /**
     * Lists backup dump files with optional category and location filters.
     */
    public List<BackupFileDto> getBackupFiles(String locationOverride, String category) {
        String root = resolveRoot(locationOverride);
        List<BackupFileDto> files = getAllBackupFilesInternal(root);
        if (category != null && !category.isBlank() && !"All".equalsIgnoreCase(category)) {
            return files.stream()
                    .filter(f -> category.equalsIgnoreCase(f.getCategory()))
                    .collect(Collectors.toList());
        }
        return files;
    }

    /**
     * Retrieves recent audit log lines from backup.log in the target location.
     */
    public List<BackupLogDto> getRecentLogs(String locationOverride, int maxLines) {
        String root = resolveRoot(locationOverride);
        Path logPath = Paths.get(root, "Logs", "backup.log");
        if (!Files.exists(logPath)) {
            return Collections.emptyList();
        }

        try {
            List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - maxLines);
            List<String> tail = lines.subList(start, lines.size());

            List<BackupLogDto> result = new ArrayList<>();
            for (String line : tail) {
                if (line == null || line.isBlank()) continue;
                Matcher m = LOG_LINE_PATTERN.matcher(line.trim());
                if (m.find()) {
                    result.add(BackupLogDto.builder()
                            .timestamp(m.group(1))
                            .level(m.group(2))
                            .message(m.group(3))
                            .build());
                } else {
                    result.add(BackupLogDto.builder()
                            .timestamp("")
                            .level("INFO")
                            .message(line.trim())
                            .build());
                }
            }
            Collections.reverse(result);
            return result;
        } catch (Exception e) {
            log.warn("Failed to read backup log file at {}: {}", logPath, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Triggers complete on-demand backup to external drive or specified target.
     */
    public BackupTriggerResponse runBackupNow(BackupTriggerRequest request) {
        String target = (request != null && request.getTargetLocation() != null && !request.getTargetLocation().isBlank())
                ? request.getTargetLocation().trim()
                : this.backupRoot;

        if (request != null && request.isPersistAsDefault()) {
            updateActiveBackupLocation(target, true);
        }

        if (Files.exists(Paths.get(LOCK_FILE_PATH))) {
            return BackupTriggerResponse.builder()
                    .success(false)
                    .message("Another backup operation is currently running.")
                    .durationSeconds(0)
                    .outputLog("Lock file present at " + LOCK_FILE_PATH)
                    .build();
        }

        Path script = Paths.get(backupScriptPath);
        if (!Files.exists(script)) {
            return BackupTriggerResponse.builder()
                    .success(false)
                    .message("Backup script not found: " + backupScriptPath)
                    .durationSeconds(0)
                    .build();
        }

        Instant startTime = Instant.now();
        StringBuilder output = new StringBuilder();

        try {
            List<String> cmd = new ArrayList<>(List.of(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy", "Bypass",
                    "-File", script.toAbsolutePath().toString(),
                    "-TargetLocation", target
            ));
            if (request == null || request.isCompleteBackup()) {
                cmd.add("-CompleteBackup");
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            double duration = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
            duration = Math.round(duration * 10.0) / 10.0;

            boolean success = (exitCode == 0);
            String latestDump = findLatestDumpFileName(target);

            return BackupTriggerResponse.builder()
                    .success(success)
                    .message(success ? "Complete backup finished successfully in " + duration + "s" : "Backup failed with exit code " + exitCode)
                    .durationSeconds(duration)
                    .backupFileName(latestDump)
                    .outputLog(output.toString())
                    .build();

        } catch (Exception e) {
            log.error("Execution error triggering backup script", e);
            double duration = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
            return BackupTriggerResponse.builder()
                    .success(false)
                    .message("Backup execution failed: " + e.getMessage())
                    .durationSeconds(Math.round(duration * 10.0) / 10.0)
                    .outputLog(output.append("\nError: ").append(e.getMessage()).toString())
                    .build();
        }
    }

    /**
     * Loads a backup dump file as a downloadable Spring Resource.
     */
    public Resource getBackupFileResource(String locationOverride, String category, String fileName) {
        validateCategoryAndFileName(category, fileName);
        String root = resolveRoot(locationOverride);
        Path filePath = Paths.get(root, "Database", category, fileName);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Backup file not found: " + fileName);
        }
        return new FileSystemResource(filePath.toFile());
    }

    /**
     * Verifies a backup file using pg_restore --list.
     */
    public Map<String, Object> verifyBackupFile(String locationOverride, String category, String fileName) {
        validateCategoryAndFileName(category, fileName);
        String root = resolveRoot(locationOverride);
        Path filePath = Paths.get(root, "Database", category, fileName);
        if (!Files.exists(filePath)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Backup file not found: " + fileName);
        }

        Path pgRestorePath = Paths.get(pgBinDir, "pg_restore.exe");
        if (!Files.exists(pgRestorePath)) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "pg_restore.exe not found at: " + pgBinDir);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pgRestorePath.toAbsolutePath().toString(),
                    "--list",
                    filePath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            int entryCount = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append("\n");
                    entryCount++;
                }
            }

            int exitCode = process.waitFor();
            boolean ok = (exitCode == 0);

            Map<String, Object> res = new HashMap<>();
            res.put("verified", ok);
            res.put("exitCode", exitCode);
            res.put("entryCount", entryCount);
            res.put("fileName", fileName);
            res.put("category", category);
            res.put("message", ok ? "Integrity verified: " + entryCount + " Table of Contents entries found." : "Verification failed.");
            return res;

        } catch (Exception e) {
            log.error("Failed to verify backup file {}", fileName, e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Verification execution failed: " + e.getMessage());
        }
    }

    /**
     * Uploads an external .dump backup file into the repository Uploads folder.
     */
    public BackupUploadResponse uploadBackupFile(MultipartFile file, String locationOverride) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Backup file cannot be empty");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid file name");
        }

        String cleanName = Paths.get(originalName).getFileName().toString().trim();
        if (!cleanName.toLowerCase().endsWith(".dump")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Only PostgreSQL custom dump files (.dump) are supported");
        }

        // Prevent path traversal and sanitize
        cleanName = cleanName.replaceAll("[^a-zA-Z0-9._\\-]", "_");

        String root = resolveRoot(locationOverride);
        Path uploadDir = Paths.get(root, "Database", "Uploads");
        try {
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Path targetPath = uploadDir.resolve(cleanName);
            file.transferTo(targetPath.toFile());

            long sizeBytes = Files.size(targetPath);
            double sizeMB = Math.round((sizeBytes / 1024.0 / 1024.0) * 100.0) / 100.0;

            // Pre-inspect with pg_restore --list
            InspectBackupResponse inspect = inspectDumpFile(targetPath);

            return BackupUploadResponse.builder()
                    .fileName(cleanName)
                    .category("Uploads")
                    .sizeBytes(sizeBytes)
                    .sizeMB(sizeMB)
                    .savedPath(targetPath.toString())
                    .tocEntriesCount(inspect.getTocEntriesCount())
                    .verified(inspect.isValid())
                    .message(inspect.isValid()
                            ? "File uploaded and verified successfully (" + inspect.getTocEntriesCount() + " TOC entries)."
                            : "File uploaded, but integrity check reported warnings: " + inspect.getMessage())
                    .build();

        } catch (AppException ae) {
            throw ae;
        } catch (Exception e) {
            log.error("Failed to process uploaded backup file: {}", cleanName, e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to upload and store backup file: " + e.getMessage());
        }
    }

    /**
     * Inspects a backup archive using pg_restore --list before restoring.
     */
    public InspectBackupResponse inspectBackup(String locationOverride, String category, String fileName, String filePath) {
        Path targetPath = null;
        if (filePath != null && !filePath.isBlank()) {
            targetPath = Paths.get(filePath);
        } else if (fileName != null && !fileName.isBlank()) {
            String root = resolveRoot(locationOverride);
            String cat = (category != null && !category.isBlank()) ? category : "Uploads";
            Path p1 = Paths.get(root, "Database", cat, fileName);
            Path p2 = Paths.get(root, "Uploads", fileName);
            if (Files.exists(p1)) targetPath = p1;
            else if (Files.exists(p2)) targetPath = p2;
            else {
                for (String c : List.of("Daily", "Weekly", "Monthly", "Uploads")) {
                    Path cand = Paths.get(root, "Database", c, fileName);
                    if (Files.exists(cand)) { targetPath = cand; break; }
                }
            }
        }

        if (targetPath == null || !Files.exists(targetPath)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Backup file not found for inspection");
        }

        return inspectDumpFile(targetPath);
    }

    /**
     * Internal helper to run pg_restore --list on a file and extract summary entries.
     */
    public InspectBackupResponse inspectDumpFile(Path dumpPath) {
        Path pgRestorePath = Paths.get(pgBinDir, "pg_restore.exe");
        if (!Files.exists(pgRestorePath)) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "pg_restore.exe not found at: " + pgBinDir);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pgRestorePath.toAbsolutePath().toString(),
                    "--list",
                    dumpPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<String> sampleEntries = new ArrayList<>();
            int totalLines = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    totalLines++;
                    if (sampleEntries.size() < 25 && (line.contains("TABLE DATA") || line.contains("TABLE ") || line.contains("SEQUENCE"))) {
                        sampleEntries.add(line.trim());
                    }
                }
            }
            int exitCode = process.waitFor();
            boolean valid = (exitCode == 0 || exitCode == 1);
            double sizeMB = Math.round((Files.size(dumpPath) / 1024.0 / 1024.0) * 100.0) / 100.0;

            return InspectBackupResponse.builder()
                    .fileName(dumpPath.getFileName().toString())
                    .category(dumpPath.getParent() != null ? dumpPath.getParent().getFileName().toString() : "Database")
                    .filePath(dumpPath.toAbsolutePath().toString())
                    .sizeMB(sizeMB)
                    .valid(valid)
                    .tocEntriesCount(totalLines)
                    .sampleEntries(sampleEntries)
                    .message(valid ? "Archive is a valid PostgreSQL custom dump with " + totalLines + " TOC entries." : "Integrity check reported exit code " + exitCode)
                    .build();
        } catch (Exception e) {
            log.error("Failed to inspect dump file {}", dumpPath, e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to inspect dump file: " + e.getMessage());
        }
    }

    /**
     * Executes database restore via erp-restore.ps1 in TEST or PRODUCTION mode.
     */
    public RestoreBackupResponse restoreBackup(RestoreBackupRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Restore request cannot be empty");
        }

        if (Files.exists(Paths.get(LOCK_FILE_PATH))) {
            throw new AppException(ErrorCode.OPERATION_NOT_ALLOWED, "Another backup or restore operation is currently running. Please wait.");
        }

        String mode = request.getRestoreMode() != null ? request.getRestoreMode().trim().toUpperCase() : "TEST";
        boolean isProduction = "PRODUCTION".equals(mode) || "FULL".equals(mode) || "2".equals(mode);

        if (isProduction) {
            if (!"CONFIRM RESTORE".equals(request.getConfirmText())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Production disaster recovery requires exact confirmation phrase 'CONFIRM RESTORE'.");
            }
        }

        String root = resolveRoot(request.getLocation());
        Path dumpPath = null;
        if (request.getFilePath() != null && !request.getFilePath().isBlank()) {
            dumpPath = Paths.get(request.getFilePath());
        }

        if (dumpPath == null || !Files.exists(dumpPath)) {
            String fileName = request.getBackupFileName();
            if (fileName == null || fileName.isBlank()) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Backup file name is required");
            }
            String cat = request.getCategory() != null ? request.getCategory().trim() : "";
            if (!cat.isBlank()) {
                Path p = Paths.get(root, "Database", cat, fileName);
                if (Files.exists(p)) dumpPath = p;
            }
            if (dumpPath == null || !Files.exists(dumpPath)) {
                for (String c : List.of("Daily", "Weekly", "Monthly", "Uploads")) {
                    Path cand = Paths.get(root, "Database", c, fileName);
                    if (Files.exists(cand)) {
                        dumpPath = cand;
                        break;
                    }
                }
            }
            if (dumpPath == null || !Files.exists(dumpPath)) {
                Path pUploads = Paths.get(root, "Uploads", fileName);
                if (Files.exists(pUploads)) dumpPath = pUploads;
            }
        }

        if (dumpPath == null || !Files.exists(dumpPath)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Target backup dump file not found: " + request.getBackupFileName());
        }

        Path script = Paths.get(restoreScriptPath);
        if (!Files.exists(script)) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Restore script not found at: " + restoreScriptPath);
        }

        Instant startTime = Instant.now();
        StringBuilder logOutput = new StringBuilder();
        Map<String, Long> rowCounts = new LinkedHashMap<>();
        int exitCode = -1;

        try {
            List<String> cmd = new ArrayList<>(List.of(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy", "Bypass",
                    "-File", script.toAbsolutePath().toString(),
                    "-TargetLocation", root,
                    "-BackupFile", dumpPath.toAbsolutePath().toString(),
                    "-Mode", isProduction ? "PRODUCTION" : "TEST",
                    "-NonInteractive"
            ));

            if (isProduction) {
                cmd.add("-Force");
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("PGPASSWORD", "ritham123");
            pb.environment().put("PAGER", "");
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logOutput.append(line).append("\n");
                    parseTableRowCount(line, rowCounts);
                }
            }

            exitCode = process.waitFor();
            double duration = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
            duration = Math.round(duration * 10.0) / 10.0;

            boolean success = (exitCode == 0 || exitCode == 1);
            String targetDb = isProduction ? databaseName : "ritham_erp_restore_test";

            if (rowCounts.isEmpty() && success) {
                rowCounts = queryPostRestoreRowCounts(targetDb);
            }

            int tocEntries = 0;
            try {
                InspectBackupResponse insp = inspectDumpFile(dumpPath);
                tocEntries = insp.getTocEntriesCount();
            } catch (Exception ignored) {}

            return RestoreBackupResponse.builder()
                    .success(success)
                    .message(success
                            ? (isProduction
                                    ? "Disaster recovery restored into live database '" + targetDb + "' successfully in " + duration + "s."
                                    : "Safe drill restored into test database '" + targetDb + "' successfully in " + duration + "s. Production was NOT touched.")
                            : "Restore script encountered errors (exit code " + exitCode + "). Check log output.")
                    .targetDatabase(targetDb)
                    .backupFileName(dumpPath.getFileName().toString())
                    .restoreMode(isProduction ? "PRODUCTION" : "TEST")
                    .durationSeconds(duration)
                    .tocEntriesCount(tocEntries)
                    .tableRowCounts(rowCounts)
                    .outputLog(logOutput.toString())
                    .build();

        } catch (Exception e) {
            log.error("Execution error triggering database restore", e);
            double duration = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
            return RestoreBackupResponse.builder()
                    .success(false)
                    .message("Restore execution error: " + e.getMessage())
                    .targetDatabase(isProduction ? databaseName : "ritham_erp_restore_test")
                    .backupFileName(dumpPath != null ? dumpPath.getFileName().toString() : request.getBackupFileName())
                    .restoreMode(isProduction ? "PRODUCTION" : "TEST")
                    .durationSeconds(Math.round(duration * 10.0) / 10.0)
                    .outputLog(logOutput.append("\nException: ").append(e.getMessage()).toString())
                    .build();
        }
    }

    private void parseTableRowCount(String line, Map<String, Long> rowCounts) {
        if (line == null) return;
        String trimmed = line.trim();
        if (trimmed.contains("|") && !trimmed.contains("---") && !trimmed.startsWith("tbl") && !trimmed.startsWith("schemaname")) {
            String[] parts = trimmed.split("\\|");
            if (parts.length >= 2) {
                String tbl = parts[0].trim();
                String countStr = parts[1].trim();
                if (countStr.matches("^\\d+$") && tbl.matches("^[a-zA-Z0-9_]+$")) {
                    try {
                        rowCounts.put(tbl, Long.parseLong(countStr));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private Map<String, Long> queryPostRestoreRowCounts(String targetDb) {
        Map<String, Long> map = new LinkedHashMap<>();
        Path psqlPath = Paths.get(pgBinDir, "psql.exe");
        if (!Files.exists(psqlPath)) return map;

        String query = "SELECT 'customers' AS tbl, COUNT(*) FROM customers UNION ALL " +
                       "SELECT 'customer_orders', COUNT(*) FROM customer_orders UNION ALL " +
                       "SELECT 'employees', COUNT(*) FROM employees UNION ALL " +
                       "SELECT 'production_stages', COUNT(*) FROM production_stages;";

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    psqlPath.toAbsolutePath().toString(),
                    "-h", "localhost",
                    "-p", "5432",
                    "-U", "ritham",
                    "-d", targetDb,
                    "--pset=pager=off",
                    "-t",
                    "-c", query
            );
            pb.environment().put("PGPASSWORD", "ritham123");
            pb.environment().put("PAGER", "");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseTableRowCount(line, map);
                }
            }
            p.waitFor();
        } catch (Exception e) {
            log.warn("Failed to query post-restore row counts via psql: {}", e.getMessage());
        }
        return map;
    }

    // ── Internal Helpers ────────────────────────────────────────────────────────

    private String resolveRoot(String locationOverride) {
        if (locationOverride != null && !locationOverride.isBlank()) {
            return locationOverride.trim().replace("/", "\\");
        }
        return this.backupRoot.replace("/", "\\");
    }

    private DriveStatusDto evaluateDriveStatus(String root) {
        String drivePrefix = root.length() >= 2 ? root.substring(0, 2) : "E:";
        File driveFile = new File(drivePrefix + "\\");

        boolean connected = driveFile.exists() && Files.exists(Paths.get(root));
        if (!connected) {
            return DriveStatusDto.builder()
                    .driveLetter(drivePrefix)
                    .connected(false)
                    .totalSpaceGB(0)
                    .freeSpaceGB(0)
                    .usedSpaceGB(0)
                    .usedPercent(0)
                    .fileSystem("Unknown")
                    .build();
        }

        long total = driveFile.getTotalSpace();
        long free = driveFile.getFreeSpace();
        long used = total - free;

        double totalGB = Math.round((total / 1024.0 / 1024.0 / 1024.0) * 100.0) / 100.0;
        double freeGB = Math.round((free / 1024.0 / 1024.0 / 1024.0) * 100.0) / 100.0;
        double usedGB = Math.round((used / 1024.0 / 1024.0 / 1024.0) * 100.0) / 100.0;
        double usedPercent = total > 0 ? Math.round(((double) used / total * 100.0) * 10.0) / 10.0 : 0;

        return DriveStatusDto.builder()
                .driveLetter(drivePrefix)
                .connected(true)
                .totalSpaceGB(totalGB)
                .freeSpaceGB(freeGB)
                .usedSpaceGB(usedGB)
                .usedPercent(usedPercent)
                .fileSystem("NTFS/FAT")
                .build();
    }

    private boolean checkPostgresRunning() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5432), 600);
            return true;
        } catch (Exception e) {
            try {
                Process p = new ProcessBuilder("tasklist.exe", "/FI", "IMAGENAME eq postgres.exe", "/NH").start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.toLowerCase().contains("postgres.exe")) return true;
                    }
                }
            } catch (Exception ignored) {}
            return false;
        }
    }

    private List<BackupFileDto> getAllBackupFilesInternal(String root) {
        List<BackupFileDto> list = new ArrayList<>();
        String[] categories = new String[]{"Daily", "Weekly", "Monthly", "Uploads"};

        for (String cat : categories) {
            Path catDir = Paths.get(root, "Database", cat);
            if (!Files.exists(catDir)) continue;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(catDir, "*.dump")) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) {
                        File file = entry.toFile();
                        long size = file.length();
                        double sizeMB = Math.round((size / 1024.0 / 1024.0) * 100.0) / 100.0;
                        LocalDateTime modTime = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(file.lastModified()),
                                ZoneId.systemDefault()
                        );
                        String ageText = calculateAge(modTime);

                        list.add(BackupFileDto.builder()
                                .fileName(file.getName())
                                .category(cat)
                                .sizeBytes(size)
                                .sizeMB(sizeMB)
                                .modifiedTime(modTime)
                                .ageText(ageText)
                                .verified(size > 0)
                                .build());
                    }
                }
            } catch (Exception e) {
                log.warn("Error reading backup category directory {}: {}", cat, e.getMessage());
            }
        }

        list.sort((a, b) -> b.getModifiedTime().compareTo(a.getModifiedTime()));
        return list;
    }

    private String calculateAge(LocalDateTime time) {
        Duration dur = Duration.between(time, LocalDateTime.now());
        long hours = dur.toHours();
        if (hours < 1) {
            long mins = Math.max(1, dur.toMinutes());
            return mins + " min" + (mins == 1 ? "" : "s") + " ago";
        } else if (hours < 24) {
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        } else {
            long days = dur.toDays();
            return days + " day" + (days == 1 ? "" : "s") + " ago";
        }
    }

    private String calculateNextScheduledRun() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tonight11pm = now.withHour(23).withMinute(0).withSecond(0).withNano(0);
        if (now.isAfter(tonight11pm)) {
            tonight11pm = tonight11pm.plusDays(1);
        }
        return tonight11pm.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at 11:00 PM'"));
    }

    private String findLatestDumpFileName(String root) {
        Path daily = Paths.get(root, "Database", "Daily");
        if (!Files.exists(daily)) return null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(daily, "*.dump")) {
            Path newest = null;
            long latestTime = 0;
            for (Path p : stream) {
                long m = Files.getLastModifiedTime(p).toMillis();
                if (m > latestTime) {
                    latestTime = m;
                    newest = p;
                }
            }
            return newest != null ? newest.getFileName().toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void validateCategoryAndFileName(String category, String fileName) {
        if (!List.of("Daily", "Weekly", "Monthly", "Uploads").contains(category)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid backup category: " + category);
        }
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\") || !fileName.endsWith(".dump")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid file name: " + fileName);
        }
    }

    private boolean testWriteAccess(File root) {
        try {
            Path probe = Paths.get(root.getAbsolutePath(), ".erp_write_probe_" + System.currentTimeMillis() + ".tmp");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Map<String, String>> getWmiDriveInfo() {
        Map<String, Map<String, String>> map = new HashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-CimInstance Win32_LogicalDisk | Select-Object DeviceID, VolumeName, DriveType, FileSystem | ConvertTo-Csv -NoTypeInformation"
            );
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                reader.readLine(); // skip CSV header
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] parts = line.replace("\"", "").split(",");
                    if (parts.length >= 4) {
                        String devId = parts[0].trim().toUpperCase();
                        Map<String, String> d = new HashMap<>();
                        d.put("DeviceID", devId);
                        d.put("VolumeName", parts[1].trim());
                        d.put("DriveType", parts[2].trim());
                        d.put("FileSystem", parts[3].trim());
                        map.put(devId, d);
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            log.warn("Failed to query Win32_LogicalDisk via PowerShell: {}", e.getMessage());
        }
        return map;
    }

    private void updateScriptDefaultPath(String newPath) {
        try {
            Path script = Paths.get(backupScriptPath);
            if (Files.exists(script)) {
                String content = Files.readString(script, StandardCharsets.UTF_8);
                String escaped = newPath.replace("\\", "\\\\");
                content = content.replaceAll("BackupRoot\\s*=\\s*\".*?\"", "BackupRoot        = \"" + escaped + "\"");
                Files.writeString(script, content, StandardCharsets.UTF_8);
                log.info("Persisted BackupRoot to erp-backup.ps1: {}", newPath);
            }
            Path restoreScript = Paths.get("d:/ritham-erp/scripts/backup/erp-restore.ps1");
            if (Files.exists(restoreScript)) {
                String content = Files.readString(restoreScript, StandardCharsets.UTF_8);
                String escaped = newPath.replace("\\", "\\\\");
                content = content.replaceAll("\\$BACKUP_ROOT\\s*=\\s*\".*?\"", "\\$BACKUP_ROOT   = \"" + escaped + "\"");
                Files.writeString(restoreScript, content, StandardCharsets.UTF_8);
                log.info("Persisted BACKUP_ROOT to erp-restore.ps1: {}", newPath);
            }
        } catch (Exception e) {
            log.warn("Failed to persist default backup root in scripts: {}", e.getMessage());
        }
    }
}
