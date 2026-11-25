package com.ritham.erp.module.backup.service;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.backup.dto.*;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BackupService {

    @Value("${app.backup.root:E:/ERP-Backups}")
    private volatile String backupRoot = "E:/ERP-Backups";

    @Value("${app.backup.script-path:d:/ritham-erp/scripts/backup/erp-backup.ps1}")
    private String backupScriptPath = "d:/ritham-erp/scripts/backup/erp-backup.ps1";

    @Value("${app.backup.restore-script-path:d:/ritham-erp/scripts/backup/erp-restore.ps1}")
    private String restoreScriptPath = "d:/ritham-erp/scripts/backup/erp-restore.ps1";

    @Value("${app.backup.schedule-script-path:d:/ritham-erp/scripts/backup/setup-task-scheduler.ps1}")
    private String scheduleScriptPath = "d:/ritham-erp/scripts/backup/setup-task-scheduler.ps1";

    @Value("${app.backup.pg-bin-dir:C:/Program Files/PostgreSQL/17/bin}")
    private String pgBinDir = "C:/Program Files/PostgreSQL/17/bin";

    @Value("${app.backup.database:ritham_erp}")
    private String databaseName = "ritham_erp";

    @Value("${spring.datasource.username:ritham}")
    private String dbUsername = "ritham";

    @Value("${spring.datasource.password:}")
    private String dbPassword = "";

    @Value("${app.backup.config-file:d:/ritham-erp/scripts/backup/backup-config.json}")
    private String backupConfigFile = "d:/ritham-erp/scripts/backup/backup-config.json";

    @Value("${app.migration.upload-dir:D:/ritham-erp-uploads/migration}")
    private String migrationUploadDir = "D:/ritham-erp-uploads/migration";

    @Value("${spring.datasource.url:}")
    private String datasourceUrl = "";

    @Value("${app.backup.schedule.enabled:true}")
    private volatile boolean scheduleEnabled = true;

    @Value("${app.backup.schedule.hour:19}")
    private volatile int scheduleHour = 19;

    @Value("${app.backup.schedule.minute:0}")
    private volatile int scheduleMinute = 0;

    @Value("${app.backup.schedule.backup-on-device-connect:true}")
    private volatile boolean backupOnDeviceConnect = true;

    @Autowired(required = false)
    private DataSource dataSource;

    private static final String LOCK_FILE_PATH = "C:/Windows/Temp/ritham-erp-backup.lock";
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile("^\\[(.*?)\\]\\s+\\[(.*?)\\]\\s+(.*)$");
    private static final Pattern BACKUP_ROOT_SCRIPT_PATTERN = Pattern.compile("BackupRoot\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern H2_SNAPSHOT_PATTERN = Pattern.compile("^ritham_erp_h2_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.sql$");

    private volatile LocalDate lastAutoBackupDate;
    private volatile Instant lastDeviceReconnectBackupAt;
    private volatile Boolean lastKnownDeviceConnected;
    private volatile String lastKnownWatchedRoot;
    private volatile String lastAutoBackupMessage;
    private final AtomicBoolean autoBackupRunning = new AtomicBoolean(false);

    @PostConstruct
    void initPersistedBackupRoot() {
        try {
            boolean loadedRootFromConfig = false;
            if (backupConfigFile != null && !backupConfigFile.isBlank()) {
                Path cfgPath = Paths.get(backupConfigFile);
                if (Files.exists(cfgPath)) {
                    String json = Files.readString(cfgPath, StandardCharsets.UTF_8);
                    tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
                    Object parsed = mapper.readValue(json, Object.class);
                    if (parsed instanceof Map<?, ?> map) {
                        Object savedRoot = map.get("backupRoot");
                        if (savedRoot != null && !savedRoot.toString().isBlank()) {
                            this.backupRoot = savedRoot.toString().trim().replace("/", "\\");
                            loadedRootFromConfig = true;
                            log.info("Loaded persisted backupRoot from config: {}", this.backupRoot);
                        }
                        if (map.get("scheduleEnabled") instanceof Boolean b) {
                            this.scheduleEnabled = b;
                        }
                        if (map.get("scheduleHour") instanceof Number n) {
                            int h = n.intValue();
                            if (h >= 0 && h <= 23) this.scheduleHour = h;
                        }
                        if (map.get("scheduleMinute") instanceof Number n) {
                            int m = n.intValue();
                            if (m >= 0 && m <= 59) this.scheduleMinute = m;
                        }
                        if (map.get("backupOnDeviceConnect") instanceof Boolean b) {
                            this.backupOnDeviceConnect = b;
                        }
                        if (map.get("lastAutoBackupDate") != null && !map.get("lastAutoBackupDate").toString().isBlank()) {
                            try {
                                this.lastAutoBackupDate = LocalDate.parse(map.get("lastAutoBackupDate").toString().trim());
                            } catch (Exception ignored) {
                            }
                        }
                        if (map.get("lastAutoBackupMessage") != null && !map.get("lastAutoBackupMessage").toString().isBlank()) {
                            this.lastAutoBackupMessage = map.get("lastAutoBackupMessage").toString().trim();
                        }
                    }
                }
            }
            if (!loadedRootFromConfig && backupScriptPath != null && !backupScriptPath.isBlank()) {
                Path script = Paths.get(backupScriptPath);
                if (Files.exists(script)) {
                    String content = Files.readString(script, StandardCharsets.UTF_8);
                    Matcher m = BACKUP_ROOT_SCRIPT_PATTERN.matcher(content);
                    if (m.find()) {
                        String scriptRoot = m.group(1).trim().replace("\\\\", "\\").replace("/", "\\");
                        if (!scriptRoot.isBlank()) {
                            this.backupRoot = scriptRoot;
                            log.info("Loaded backupRoot from erp-backup.ps1: {}", this.backupRoot);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not load persisted backup root config: {}", e.getMessage());
        }
    }

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
                } catch (Exception ignored) {
                }
            }
            if (volName.isBlank()) {
                volName = isRemovable ? "External Drive" : ("C:".equalsIgnoreCase(letter) ? "System OS" : "Local Disk");
            }

            String fs = wmi.getOrDefault("FileSystem", "");
            if (fs.isBlank()) {
                try {
                    FileStore store = Files.getFileStore(root.toPath());
                    fs = store.type();
                } catch (Exception ignored) {
                }
            }
            if (fs.isBlank())
                fs = "NTFS";

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
            if (a.isRemovable() != b.isRemovable())
                return a.isRemovable() ? -1 : 1;
            if (a.isCurrentTarget() != b.isCurrentTarget())
                return a.isCurrentTarget() ? -1 : 1;
            return a.getDriveLetter().compareTo(b.getDriveLetter());
        });

        return list;
    }

    /**
     * Updates and optionally persists the active default backup location.
     */
    public synchronized BackupStatusResponse updateActiveBackupLocation(String path, boolean persistAsDefault) {
        String normalizedPath = validateAndPrepareTargetDirectory(path);
        this.backupRoot = normalizedPath;
        this.lastKnownWatchedRoot = normalizedPath;
        this.lastKnownDeviceConnected = true;
        log.info("Active backup root updated to: {}", normalizedPath);

        if (persistAsDefault) {
            updateScriptDefaultPath(normalizedPath);
        }

        return getBackupStatus(normalizedPath);
    }

    /**
     * Updates daily schedule and device-reconnect auto-backup preferences from UI controls.
     */
    public synchronized BackupStatusResponse updateBackupSchedule(BackupScheduleConfigRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Schedule configuration is required");
        }
        int h = request.getHour();
        int m = request.getMinute();
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid schedule time: " + h + ":" + m);
        }

        this.scheduleEnabled = request.isEnabled();
        this.scheduleHour = h;
        this.scheduleMinute = m;
        this.backupOnDeviceConnect = request.isBackupOnDeviceConnect();

        LocalTime chosen = LocalTime.of(h, m);
        LocalDate today = LocalDate.now();
        if (this.scheduleEnabled && !chosen.isAfter(LocalTime.now())) {
            // Chosen time already passed today — wait until tomorrow unless triggered manually
            this.lastAutoBackupDate = today;
        } else if (this.scheduleEnabled && today.equals(this.lastAutoBackupDate)) {
            // Re-scheduled to a future time today — allow running at the new time tonight
            this.lastAutoBackupDate = null;
        }

        persistBackupConfigFile();
        if (request.isSyncWindowsTask()) {
            syncWindowsScheduledTask(this.scheduleEnabled, this.scheduleHour, this.scheduleMinute);
        }

        String root = resolveRoot(null);
        appendBackupLogLine(root, "INFO", "Schedule updated: "
                + (this.scheduleEnabled ? ("Daily at " + formatScheduleTime12h(h, m)) : "Paused")
                + " | Backup on device connect=" + this.backupOnDeviceConnect
                + " | Target=" + root);
        return getBackupStatus(root);
    }

    /**
     * Periodic background check running daily schedule (default 7:00 PM) and immediate
     * backup when the selected drive/device transitions from offline to live.
     */
    @Scheduled(fixedDelayString = "${app.backup.schedule.poll-ms:15000}", initialDelayString = "${app.backup.schedule.initial-delay-ms:15000}")
    public void pollScheduledAndDeviceReconnect() {
        evaluateAutoBackupTriggers(LocalDateTime.now());
    }

    public void evaluateAutoBackupTriggers(LocalDateTime now) {
        String root = resolveRoot(null);
        DriveStatusDto drive = evaluateDriveStatus(root);
        boolean currentlyReady = drive.isConnected() && drive.isWritable();

        if (lastKnownWatchedRoot == null || !lastKnownWatchedRoot.equalsIgnoreCase(root)) {
            lastKnownWatchedRoot = root;
            lastKnownDeviceConnected = currentlyReady;
        } else {
            boolean cameOnline = backupOnDeviceConnect && Boolean.FALSE.equals(lastKnownDeviceConnected) && currentlyReady;
            lastKnownDeviceConnected = currentlyReady;
            if (cameOnline) {
                boolean cooldownElapsed = lastDeviceReconnectBackupAt == null
                        || Duration.between(lastDeviceReconnectBackupAt, Instant.now()).getSeconds() >= 60;
                if (cooldownElapsed) {
                    lastDeviceReconnectBackupAt = Instant.now();
                    executeAutoBackupWithGuard(root, "AUTO-DEVICE-CONNECT",
                            "Selected device came live (" + drive.getDriveLetter() + ")", now.toLocalDate());
                    return;
                }
            }
        }

        LocalDate today = now.toLocalDate();
        LocalTime targetTime = LocalTime.of(scheduleHour, scheduleMinute);
        if (scheduleEnabled && !today.equals(lastAutoBackupDate) && !now.toLocalTime().isBefore(targetTime)) {
            if (!currentlyReady) {
                lastAutoBackupMessage = "Waiting for offline device (" + drive.getDriveLetter()
                        + ") for " + formatScheduleTime12h(scheduleHour, scheduleMinute) + " backup";
                return;
            }
            executeAutoBackupWithGuard(root, "AUTO-SCHEDULED",
                    "Daily scheduled backup (" + formatScheduleTime12h(scheduleHour, scheduleMinute) + ")", today);
        }
    }

    private void executeAutoBackupWithGuard(String root, String tag, String reason, LocalDate runDate) {
        if (Files.exists(Paths.get(LOCK_FILE_PATH))) {
            log.info("[{}] Skipped because another backup lock exists", tag);
            return;
        }
        if ("AUTO-SCHEDULED".equals(tag) && hasVeryRecentDailyBackup(root, 60)) {
            this.lastAutoBackupDate = runDate;
            persistBackupConfigFile();
            return;
        }
        if (!autoBackupRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            BackupTriggerResponse res = runBackupNow(BackupTriggerRequest.builder()
                    .targetLocation(root)
                    .completeBackup(true)
                    .persistAsDefault(false)
                    .build());
            if (res != null && res.isSuccess()) {
                this.lastAutoBackupDate = runDate;
                this.lastAutoBackupMessage = "[" + tag + "] " + reason + " -> " + res.getBackupFileName();
                appendBackupLogLine(root, "OK", "[" + tag + "] " + reason + " completed: " + res.getBackupFileName());
                persistBackupConfigFile();
            } else {
                String errMsg = res != null ? res.getMessage() : "Unknown failure";
                this.lastAutoBackupMessage = "[" + tag + "] Failed: " + errMsg;
                appendBackupLogLine(root, "ERROR", "[" + tag + "] " + reason + " failed: " + errMsg);
            }
        } catch (Exception e) {
            log.error("[{}] Auto backup failed for target {}", tag, root, e);
            this.lastAutoBackupMessage = "[" + tag + "] Error: " + e.getMessage();
            appendBackupLogLine(root, "ERROR", "[" + tag + "] " + reason + " exception: " + e.getMessage());
        } finally {
            autoBackupRunning.set(false);
        }
    }

    private boolean hasVeryRecentDailyBackup(String root, long withinSeconds) {
        String latest = findLatestDumpFileName(root);
        if (latest == null) return false;
        try {
            Path p = Paths.get(root, "Database", "Daily", latest);
            if (!Files.exists(p)) return false;
            long ageSec = Duration.between(Files.getLastModifiedTime(p).toInstant(), Instant.now()).getSeconds();
            return ageSec >= 0 && ageSec < withinSeconds;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void syncWindowsScheduledTask(boolean enabled, int hour, int minute) {
        if (scheduleScriptPath == null || scheduleScriptPath.isBlank()) return;
        Path script = Paths.get(scheduleScriptPath);
        if (!Files.exists(script)) return;
        try {
            List<String> cmd = new ArrayList<>(List.of(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", script.toAbsolutePath().toString(),
                    "-Hour", String.valueOf(hour),
                    "-Minute", String.valueOf(minute)
            ));
            if (!enabled) {
                cmd.add("-Disable");
            }
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            int exit = p.waitFor();
            log.info("Synced Windows Task Scheduler (enabled={}, {}:{}) exit={}", enabled, hour, minute, exit);
        } catch (Exception e) {
            log.warn("Could not update Windows Task Scheduler (in-app scheduler remains active): {}", e.getMessage());
        }
    }

    private String computeDeviceWatcherStatus(DriveStatusDto driveStatus) {
        String letter = (driveStatus != null && driveStatus.getDriveLetter() != null)
                ? driveStatus.getDriveLetter()
                : "target";
        if (!backupOnDeviceConnect) {
            return "Device trigger OFF";
        }
        if (driveStatus != null && driveStatus.isConnected() && driveStatus.isWritable()) {
            return "Live & Watching " + letter;
        }
        return "Offline — waits for " + letter;
    }

    private String formatScheduleTime12h(int hour, int minute) {
        return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
    }

    /**
     * Lists host drives or child folders inside the specified directory (ADMIN-only
     * folder browser).
     */
    public BackupDirectoryDto listDirectories(String path) {
        if (path == null || path.isBlank()) {
            File[] roots = File.listRoots();
            List<BackupDirectoryDto.DirectoryEntry> driveEntries = new ArrayList<>();
            if (roots != null) {
                for (File root : roots) {
                    if (!root.exists())
                        continue;
                    String rootPathStr = root.getAbsolutePath();
                    driveEntries.add(BackupDirectoryDto.DirectoryEntry.builder()
                            .name(rootPathStr)
                            .path(rootPathStr)
                            .writable(testWriteAccess(root))
                            .build());
                }
            }
            return BackupDirectoryDto.builder()
                    .currentPath("")
                    .parentPath(null)
                    .entries(driveEntries)
                    .build();
        }

        Path dir;
        try {
            dir = Paths.get(path.trim().replace("/", "\\")).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid directory path: " + path);
        }

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Directory does not exist: " + dir);
        }

        List<BackupDirectoryDto.DirectoryEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                try {
                    if (!Files.isDirectory(child))
                        continue;
                    String name = child.getFileName() != null ? child.getFileName().toString() : "";
                    if (name.isBlank()
                            || name.equalsIgnoreCase("$RECYCLE.BIN")
                            || name.equalsIgnoreCase("System Volume Information")
                            || name.equalsIgnoreCase("Recovery")
                            || Files.isHidden(child)) {
                        continue;
                    }
                    entries.add(BackupDirectoryDto.DirectoryEntry.builder()
                            .name(name)
                            .path(child.toAbsolutePath().normalize().toString())
                            .writable(Files.isWritable(child))
                            .build());
                } catch (Exception ignored) {
                    // Skip system-protected folders
                }
            }
        } catch (IOException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Cannot read directory " + dir + ": " + e.getMessage());
        }

        entries.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.getName() != null ? a.getName() : "",
                b.getName() != null ? b.getName() : ""));
        Path parent = dir.getParent();
        return BackupDirectoryDto.builder()
                .currentPath(dir.toString())
                .parentPath(parent != null ? parent.toString() : "")
                .entries(entries)
                .build();
    }

    /**
     * Creates a new subfolder inside an existing mounted directory (ADMIN-only
     * folder browser).
     */
    public BackupDirectoryDto.DirectoryEntry createDirectory(String parentPath, String folderName) {
        if (parentPath == null || parentPath.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Parent directory is required");
        }
        if (folderName == null || folderName.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Folder name is required");
        }
        String cleanName = folderName.trim();
        if (cleanName.contains("..") || cleanName.contains("/") || cleanName.contains("\\")
                || cleanName.matches(".*[<>:\"|?*].*")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid folder name: " + cleanName);
        }

        Path parent;
        try {
            parent = Paths.get(parentPath.trim().replace("/", "\\")).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid parent folder path");
        }

        if (!Files.exists(parent) || !Files.isDirectory(parent)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Parent folder does not exist: " + parent);
        }

        Path target = parent.resolve(cleanName).normalize();
        if (!isPathWithin(target, parent)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid target subfolder path");
        }

        try {
            Files.createDirectories(target);
            if (!testWriteAccess(target.toFile())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Created folder is not writable: " + target);
            }
            return BackupDirectoryDto.DirectoryEntry.builder()
                    .name(cleanName)
                    .path(target.toString())
                    .writable(true)
                    .build();
        } catch (AppException ae) {
            throw ae;
        } catch (Exception e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Failed to create folder: " + e.getMessage());
        }
    }

    private String validateAndPrepareTargetDirectory(String path) {
        if (path == null || path.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Target backup path cannot be empty");
        }
        Path p;
        try {
            p = Paths.get(path.trim().replace("/", "\\")).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid backup folder path: " + path);
        }

        Path mountRoot = p.getRoot();
        if (mountRoot == null || !Files.exists(mountRoot)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Target drive is not mounted or accessible: " + (mountRoot != null ? mountRoot : path));
        }

        try {
            Files.createDirectories(p);
            for (String sub : List.of("Database\\Daily", "Database\\Weekly", "Database\\Monthly", "Database\\Uploads",
                    "Files\\Daily", "Logs")) {
                Files.createDirectories(p.resolve(sub.replace("\\", File.separator)));
            }
            Path probe = p.resolve(".erp_test_probe_" + System.currentTimeMillis() + ".tmp");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
        } catch (Exception e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Cannot write to target location: " + e.getMessage());
        }

        return p.toString();
    }

    /**
     * Aggregates and returns the backup status for the specified or active
     * location.
     */
    public BackupStatusResponse getBackupStatus(String locationOverride) {
        String root = resolveRoot(locationOverride);
        DriveStatusDto driveStatus = evaluateDriveStatus(root);
        boolean h2Mode = isH2Engine();
        boolean isDbRunning = h2Mode ? (dataSource != null) : checkPostgresRunning();
        String dbEngineName = h2Mode ? "H2" : "PostgreSQL";
        String pgStatus = isDbRunning ? "RUNNING" : "STOPPED";
        String effectiveDbName = h2Mode ? "ritham_erp (H2 dev)" : databaseName;

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

        boolean isLocked = Files.exists(Paths.get(LOCK_FILE_PATH)) || autoBackupRunning.get();
        List<BackupLogDto> recentLogs = getRecentLogs(root, 25);
        String activeRoot = resolveRoot(null);
        String formattedTime = formatScheduleTime12h(scheduleHour, scheduleMinute);
        String watcherText = computeDeviceWatcherStatus(driveStatus);

        return BackupStatusResponse.builder()
                .activeBackupRoot(activeRoot)
                .databaseEngine(dbEngineName)
                .snapshotFormat(h2Mode ? ".sql" : ".dump")
                .drive(driveStatus)
                .databaseName(effectiveDbName)
                .postgresStatus(pgStatus)
                .postgresRunning(isDbRunning)
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
                .scheduleEnabled(scheduleEnabled)
                .scheduleHour(scheduleHour)
                .scheduleMinute(scheduleMinute)
                .scheduleTimeText(scheduleEnabled ? ("Daily " + formattedTime) : "Auto Backup Paused")
                .backupOnDeviceConnect(backupOnDeviceConnect)
                .scheduledTargetRoot(activeRoot)
                .deviceWatcherStatus(watcherText)
                .lastAutoBackupMessage(lastAutoBackupMessage)
                .taskScheduleText(scheduleEnabled
                        ? ("Daily at " + formattedTime + " -> " + activeRoot)
                        : ("Automated schedule paused (Target: " + activeRoot + ")"))
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
                if (line == null || line.isBlank())
                    continue;
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
        String rawTarget = (request != null && request.getTargetLocation() != null
                && !request.getTargetLocation().isBlank())
                        ? request.getTargetLocation().trim()
                        : this.backupRoot;

        String target = validateAndPrepareTargetDirectory(rawTarget);
        this.backupRoot = target;
        if (request != null && request.isPersistAsDefault()) {
            updateScriptDefaultPath(target);
        }

        if (Files.exists(Paths.get(LOCK_FILE_PATH))) {
            return BackupTriggerResponse.builder()
                    .success(false)
                    .message("Another backup operation is currently running.")
                    .durationSeconds(0)
                    .targetLocation(target)
                    .outputLog("Lock file present at " + LOCK_FILE_PATH)
                    .build();
        }

        if (isH2Engine() && dataSource != null) {
            return runH2BackupNow(target, request == null || request.isCompleteBackup());
        }

        Path script = Paths.get(backupScriptPath);
        if (!Files.exists(script)) {
            return BackupTriggerResponse.builder()
                    .success(false)
                    .message("Backup script not found: " + backupScriptPath)
                    .durationSeconds(0)
                    .targetLocation(target)
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
                    "-TargetLocation", target));
            if (request == null || request.isCompleteBackup()) {
                cmd.add("-CompleteBackup");
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (dbPassword != null && !dbPassword.isBlank()) {
                pb.environment().put("PGPASSWORD", dbPassword);
            }
            if (databaseName != null && !databaseName.isBlank()) {
                pb.environment().put("ERP_DB_NAME", databaseName);
            }
            if (dbUsername != null && !dbUsername.isBlank()) {
                pb.environment().put("ERP_DB_USER", dbUsername);
            }
            if (pgBinDir != null && !pgBinDir.isBlank()) {
                pb.environment().put("ERP_PG_BIN_DIR", pgBinDir);
            }
            if (migrationUploadDir != null && !migrationUploadDir.isBlank()) {
                pb.environment().put("ERP_UPLOAD_DIR", migrationUploadDir);
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
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
            String latestDumpFullPath = latestDump != null
                    ? Paths.get(target, "Database", "Daily", latestDump).toAbsolutePath().normalize().toString()
                    : null;

            return BackupTriggerResponse.builder()
                    .success(success)
                    .message(success ? "Complete backup finished successfully in " + duration + "s"
                            : "Backup failed with exit code " + exitCode)
                    .durationSeconds(duration)
                    .backupFileName(latestDump)
                    .targetLocation(target)
                    .backupFilePath(latestDumpFullPath)
                    .outputLog(output.toString())
                    .build();

        } catch (Exception e) {
            log.error("Execution error triggering backup script", e);
            double duration = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
            return BackupTriggerResponse.builder()
                    .success(false)
                    .message("Backup execution failed: " + e.getMessage())
                    .durationSeconds(Math.round(duration * 10.0) / 10.0)
                    .targetLocation(target)
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
        Path catDir = Paths.get(root, "Database", category).toAbsolutePath().normalize();
        Path filePath = catDir.resolve(fileName.trim()).normalize();
        if (!isPathWithin(filePath, catDir)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid backup file path");
        }
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
        Path catDir = Paths.get(root, "Database", category).toAbsolutePath().normalize();
        Path filePath = catDir.resolve(fileName.trim()).normalize();
        if (!isPathWithin(filePath, catDir)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid backup file path");
        }
        if (!Files.exists(filePath)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Backup file not found: " + fileName);
        }

        if (fileName.trim().toLowerCase(Locale.ROOT).endsWith(".sql")) {
            InspectBackupResponse insp = inspectSqlFile(filePath);
            Map<String, Object> res = new HashMap<>();
            res.put("verified", insp.isValid());
            res.put("exitCode", insp.isValid() ? 0 : 1);
            res.put("entryCount", insp.getTocEntriesCount());
            res.put("fileName", fileName);
            res.put("category", category);
            res.put("message", insp.getMessage());
            return res;
        }

        Path pgRestorePath = Paths.get(pgBinDir, "pg_restore.exe");
        if (!Files.exists(pgRestorePath)) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "pg_restore.exe not found at: " + pgBinDir);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pgRestorePath.toAbsolutePath().toString(),
                    "--list",
                    filePath.toAbsolutePath().toString());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            int entryCount = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
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
            res.put("message", ok ? "Integrity verified: " + entryCount + " Table of Contents entries found."
                    : "Verification failed.");
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
        String lowerName = cleanName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".dump") && !lowerName.endsWith(".sql")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Only PostgreSQL (.dump) or H2 snapshot (.sql) backup files are supported");
        }

        // Prevent path traversal and sanitize
        cleanName = cleanName.replaceAll("[^a-zA-Z0-9._\\-]", "_");

        String root = validateAndPrepareTargetDirectory(resolveRoot(locationOverride));
        Path uploadDir = Paths.get(root, "Database", "Uploads").toAbsolutePath().normalize();
        try {
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Path targetPath = uploadDir.resolve(cleanName).normalize();
            if (!isPathWithin(targetPath, uploadDir)) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid uploaded backup file path");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            long sizeBytes = Files.size(targetPath);
            double sizeMB = Math.round((sizeBytes / 1024.0 / 1024.0) * 100.0) / 100.0;

            // Pre-inspect with pg_restore --list when available
            int tocCount = 0;
            boolean verified = false;
            String inspectNote = "";
            try {
                InspectBackupResponse inspect = inspectDumpFile(targetPath);
                tocCount = inspect.getTocEntriesCount();
                verified = inspect.isValid();
                inspectNote = verified
                        ? "File uploaded and verified successfully (" + tocCount + " TOC entries)."
                        : "File uploaded, but integrity check reported warnings: " + inspect.getMessage();
            } catch (Exception ex) {
                log.warn("Uploaded {} saved at {}, pre-inspection skipped/warned: {}", cleanName, targetPath,
                        ex.getMessage());
                inspectNote = "File uploaded to " + targetPath + " (TOC verification skipped: " + ex.getMessage() + ")";
            }

            return BackupUploadResponse.builder()
                    .fileName(cleanName)
                    .category("Uploads")
                    .sizeBytes(sizeBytes)
                    .sizeMB(sizeMB)
                    .savedPath(targetPath.toString())
                    .tocEntriesCount(tocCount)
                    .verified(verified)
                    .message(inspectNote)
                    .build();

        } catch (AppException ae) {
            throw ae;
        } catch (Exception e) {
            log.error("Failed to process uploaded backup file: {}", cleanName, e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to upload and store backup file: " + e.getMessage());
        }
    }

    /**
     * Inspects a backup archive using pg_restore --list before restoring.
     */
    public InspectBackupResponse inspectBackup(String locationOverride, String category, String fileName,
            String filePath) {
        Path targetPath = resolveAndValidateBackupFile(locationOverride, category, fileName, filePath, true);
        if (targetPath == null || !Files.exists(targetPath)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Backup file not found for inspection");
        }
        return inspectDumpFile(targetPath);
    }

    /**
     * Internal helper to inspect a .dump (pg_restore --list) or .sql (H2 SQL
     * script) backup file.
     */
    public InspectBackupResponse inspectDumpFile(Path dumpPath) {
        if (dumpPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql")) {
            return inspectSqlFile(dumpPath);
        }
        Path pgRestorePath = Paths.get(pgBinDir, "pg_restore.exe");
        if (!Files.exists(pgRestorePath)) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "pg_restore.exe not found at: " + pgBinDir);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pgRestorePath.toAbsolutePath().toString(),
                    "--list",
                    dumpPath.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<String> sampleEntries = new ArrayList<>();
            int totalLines = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    totalLines++;
                    if (sampleEntries.size() < 25
                            && (line.contains("TABLE DATA") || line.contains("TABLE ") || line.contains("SEQUENCE"))) {
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
                    .message(valid ? "Archive is a valid PostgreSQL custom dump with " + totalLines + " TOC entries."
                            : "Integrity check reported exit code " + exitCode)
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
            throw new AppException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "Another backup or restore operation is currently running. Please wait.");
        }

        String mode = request.getRestoreMode() != null ? request.getRestoreMode().trim().toUpperCase() : "TEST";
        boolean isProduction = "PRODUCTION".equals(mode) || "FULL".equals(mode) || "2".equals(mode);

        if (isProduction) {
            if (!"CONFIRM RESTORE".equals(request.getConfirmText())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Production disaster recovery requires exact confirmation phrase 'CONFIRM RESTORE'.");
            }
        }

        String root = resolveRoot(request.getLocation());
        Path dumpPath = resolveAndValidateBackupFile(
                request.getLocation(),
                request.getCategory(),
                request.getBackupFileName(),
                request.getFilePath(),
                false);

        if (dumpPath == null || !Files.exists(dumpPath)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Target backup dump file not found: " + request.getBackupFileName());
        }

        String dumpNameLower = dumpPath.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean h2Engine = isH2Engine();
        if (h2Engine) {
            if (!dumpNameLower.endsWith(".sql")) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Active database is H2 dev mode; restore requires an H2 .sql backup snapshot (or switch profile to prod for PostgreSQL .dump files).");
            }
            return restoreH2Backup(root, dumpPath, isProduction);
        }

        if (!dumpNameLower.endsWith(".dump")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Active database is PostgreSQL; restore requires a PostgreSQL .dump archive.");
        }

        Path script = Paths.get(restoreScriptPath);
        if (!Files.exists(script)) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Restore script not found at: " + restoreScriptPath);
        }

        Instant startTime = Instant.now();
        StringBuilder logOutput = new StringBuilder();
        Map<String, Long> rowCounts = new LinkedHashMap<>();
        int exitCode = -1;
        int filesRestoredCount = 0;

        HikariPoolMXBean suspendedPool = null;
        if (isProduction) {
            suspendedPool = suspendHikariPoolIfPresent();
        }

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
                    "-NonInteractive"));

            if (isProduction) {
                cmd.add("-Force");
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (dbPassword != null && !dbPassword.isBlank()) {
                pb.environment().put("PGPASSWORD", dbPassword);
            }
            if (databaseName != null && !databaseName.isBlank()) {
                pb.environment().put("ERP_DB_NAME", databaseName);
            }
            if (dbUsername != null && !dbUsername.isBlank()) {
                pb.environment().put("ERP_DB_USER", dbUsername);
            }
            if (pgBinDir != null && !pgBinDir.isBlank()) {
                pb.environment().put("ERP_PG_BIN_DIR", pgBinDir);
            }
            if (migrationUploadDir != null && !migrationUploadDir.isBlank()) {
                pb.environment().put("ERP_UPLOAD_DIR", migrationUploadDir);
            }
            pb.environment().put("PAGER", "");
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logOutput.append(line).append("\n");
                    parseTableRowCount(line, rowCounts);
                    if (line.trim().startsWith("RESTORED_FILES_COUNT=")) {
                        try {
                            filesRestoredCount = Integer
                                    .parseInt(line.trim().substring("RESTORED_FILES_COUNT=".length()));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            exitCode = process.waitFor();
            double duration = Duration.between(startTime, Instant.now()).toMillis() / 1000.0;
            duration = Math.round(duration * 10.0) / 10.0;

            boolean success = (exitCode == 0);
            String targetDb = isProduction ? databaseName : "ritham_erp_restore_test";

            if (!isProduction && success) {
                appendBackupLogLine(root, "INFO",
                        "PostgreSQL test drill verified (live DB NOT modified) from " + dumpPath.getFileName());
            }

            if (rowCounts.isEmpty() && success) {
                rowCounts = queryPostRestoreRowCounts(targetDb);
            }

            int tocEntries = 0;
            try {
                InspectBackupResponse insp = inspectDumpFile(dumpPath);
                tocEntries = insp.getTocEntriesCount();
            } catch (Exception ignored) {
            }

            return RestoreBackupResponse.builder()
                    .success(success)
                    .message(success
                            ? (isProduction
                                    ? "Disaster recovery restored into live database '" + targetDb
                                            + "' successfully in " + duration + "s."
                                    : "Safe drill restored into test database '" + targetDb + "' successfully in "
                                            + duration + "s. Production was NOT touched.")
                            : "Restore script encountered errors (exit code " + exitCode + "). Check log output.")
                    .targetDatabase(targetDb)
                    .databaseEngine("PostgreSQL")
                    .backupFileName(dumpPath.getFileName().toString())
                    .restoreMode(isProduction ? "PRODUCTION" : "TEST")
                    .durationSeconds(duration)
                    .tocEntriesCount(tocEntries)
                    .filesRestoredCount(filesRestoredCount)
                    .requiresReload(isProduction && success)
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
                    .databaseEngine("PostgreSQL")
                    .backupFileName(dumpPath != null ? dumpPath.getFileName().toString() : request.getBackupFileName())
                    .restoreMode(isProduction ? "PRODUCTION" : "TEST")
                    .durationSeconds(Math.round(duration * 10.0) / 10.0)
                    .outputLog(logOutput.append("\nException: ").append(e.getMessage()).toString())
                    .build();
        } finally {
            resumeAndEvictHikariPool(suspendedPool);
        }
    }

    private void parseTableRowCount(String line, Map<String, Long> rowCounts) {
        if (line == null)
            return;
        String trimmed = line.trim();
        if (trimmed.contains("|") && !trimmed.contains("---") && !trimmed.startsWith("tbl")
                && !trimmed.startsWith("schemaname")) {
            String[] parts = trimmed.split("\\|");
            if (parts.length >= 2) {
                String tbl = parts[0].trim();
                String countStr = parts[1].trim();
                if (countStr.matches("^\\d+$") && tbl.matches("^[a-zA-Z0-9_]+$")) {
                    try {
                        rowCounts.put(tbl, Long.parseLong(countStr));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private Map<String, Long> queryPostRestoreRowCounts(String targetDb) {
        Map<String, Long> map = new LinkedHashMap<>();
        Path psqlPath = Paths.get(pgBinDir, "psql.exe");
        if (!Files.exists(psqlPath))
            return map;

        String query = "SELECT 'branches' AS tbl, COUNT(*) FROM branches UNION ALL " +
                "SELECT 'customers', COUNT(*) FROM customers UNION ALL " +
                "SELECT 'customer_measurements', COUNT(*) FROM customer_measurements UNION ALL " +
                "SELECT 'customer_orders', COUNT(*) FROM customer_orders UNION ALL " +
                "SELECT 'order_payments', COUNT(*) FROM order_payments UNION ALL " +
                "SELECT 'employees', COUNT(*) FROM employees UNION ALL " +
                "SELECT 'production_stages', COUNT(*) FROM production_stages;";

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    psqlPath.toAbsolutePath().toString(),
                    "-h", "localhost",
                    "-p", "5432",
                    "-U", (dbUsername != null && !dbUsername.isBlank()) ? dbUsername : "ritham",
                    "-d", targetDb,
                    "--pset=pager=off",
                    "-t",
                    "-c", query);
            if (dbPassword != null && !dbPassword.isBlank()) {
                pb.environment().put("PGPASSWORD", dbPassword);
            }
            pb.environment().put("PAGER", "");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
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
        Path rootPath;
        try {
            rootPath = Paths.get(root).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return DriveStatusDto.builder()
                    .driveLetter("?")
                    .connected(false)
                    .folderExists(false)
                    .writable(false)
                    .totalSpaceGB(0)
                    .freeSpaceGB(0)
                    .usedSpaceGB(0)
                    .usedPercent(0)
                    .fileSystem("Unknown")
                    .build();
        }

        Path mountRoot = rootPath.getRoot();
        String drivePrefix = mountRoot != null
                ? mountRoot.toString().replace("\\", "").replace("/", "")
                : (root.length() >= 2 ? root.substring(0, 2) : "E:");
        if (drivePrefix.isEmpty())
            drivePrefix = "/";

        if (mountRoot == null || !Files.exists(mountRoot)) {
            return DriveStatusDto.builder()
                    .driveLetter(drivePrefix)
                    .connected(false)
                    .folderExists(false)
                    .writable(false)
                    .totalSpaceGB(0)
                    .freeSpaceGB(0)
                    .usedSpaceGB(0)
                    .usedPercent(0)
                    .fileSystem("Unknown")
                    .build();
        }

        boolean folderExists = Files.exists(rootPath);
        Path probeTarget = folderExists ? rootPath : findNearestExistingAncestor(rootPath);
        boolean writable = probeTarget != null && Files.isDirectory(probeTarget)
                && testWriteAccess(probeTarget.toFile());

        File driveFile = mountRoot.toFile();
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
                .folderExists(folderExists)
                .writable(writable)
                .totalSpaceGB(totalGB)
                .freeSpaceGB(freeGB)
                .usedSpaceGB(usedGB)
                .usedPercent(usedPercent)
                .fileSystem("NTFS/FAT")
                .build();
    }

    private Path findNearestExistingAncestor(Path candidate) {
        Path cur = candidate;
        while (cur != null) {
            if (Files.exists(cur)) {
                return cur;
            }
            cur = cur.getParent();
        }
        return null;
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
                        if (line.toLowerCase().contains("postgres.exe"))
                            return true;
                    }
                }
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private List<BackupFileDto> getAllBackupFilesInternal(String root) {
        List<BackupFileDto> list = new ArrayList<>();
        String[] categories = new String[] { "Daily", "Weekly", "Monthly", "Uploads" };

        for (String cat : categories) {
            Path catDir = Paths.get(root, "Database", cat);
            if (!Files.exists(catDir))
                continue;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(catDir)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry) && isBackupArchiveFileName(entry.getFileName().toString())) {
                        File file = entry.toFile();
                        long size = file.length();
                        double sizeMB = Math.round((size / 1024.0 / 1024.0) * 100.0) / 100.0;
                        LocalDateTime modTime = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(file.lastModified()),
                                ZoneId.systemDefault());
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
        if (!scheduleEnabled) {
            return "Auto Daily Paused (" + (backupOnDeviceConnect ? "Device Connect ON" : "Manual Only") + ")";
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime nextRun = now.withHour(scheduleHour).withMinute(scheduleMinute).withSecond(0).withNano(0);
        if (!now.isBefore(nextRun) || today.equals(lastAutoBackupDate)) {
            nextRun = nextRun.plusDays(1);
        }
        return nextRun.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a", Locale.ENGLISH));
    }

    private String findLatestDumpFileName(String root) {
        Path daily = Paths.get(root, "Database", "Daily");
        if (!Files.exists(daily))
            return null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(daily)) {
            Path newest = null;
            long latestTime = 0;
            for (Path p : stream) {
                if (!Files.isRegularFile(p) || !isBackupArchiveFileName(p.getFileName().toString())) {
                    continue;
                }
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
        validateSafeDumpFileName(fileName);
    }

    private void validateSafeDumpFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..")
                || fileName.contains("/") || fileName.contains("\\")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid file name: " + fileName);
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".dump") && !lower.endsWith(".sql")) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid file name: " + fileName);
        }
    }

    private boolean isBackupArchiveFileName(String name) {
        if (name == null || name.isBlank())
            return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("_globals_") && lower.endsWith(".sql")) {
            return false;
        }
        return lower.endsWith(".dump") || lower.endsWith(".sql");
    }

    private boolean isH2Engine() {
        if (datasourceUrl != null && !datasourceUrl.isBlank()) {
            return datasourceUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:h2:");
        }
        if (dataSource != null) {
            try (Connection c = dataSource.getConnection()) {
                return c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("h2");
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private HikariPoolMXBean suspendHikariPoolIfPresent() {
        if (dataSource instanceof HikariDataSource hikari) {
            try {
                HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
                if (pool != null) {
                    pool.suspendPool();
                    pool.softEvictConnections();
                    return pool;
                }
            } catch (Exception e) {
                log.debug("Could not suspend Hikari pool (continuing): {}", e.getMessage());
            }
        }
        return null;
    }

    private void resumeAndEvictHikariPool(HikariPoolMXBean pool) {
        if (pool != null) {
            try {
                pool.softEvictConnections();
                pool.resumePool();
            } catch (Exception e) {
                log.warn("Error resuming Hikari pool: {}", e.getMessage());
            }
        }
    }

    /**
     * Opens a direct JDBC connection bypassing the suspended Hikari pool during
     * live H2 restore.
     */
    private Connection openDirectH2Connection() throws java.sql.SQLException {
        String url = datasourceUrl;
        String user = dbUsername;
        String pass = dbPassword;
        if (dataSource instanceof HikariDataSource hikari) {
            if (url == null || url.isBlank()) {
                url = hikari.getJdbcUrl();
            }
            if (hikari.getUsername() != null && !hikari.getUsername().isBlank()) {
                user = hikari.getUsername();
            }
            if (hikari.getPassword() != null) {
                pass = hikari.getPassword();
            }
        }
        if (url != null && !url.isBlank()) {
            return DriverManager.getConnection(
                    url,
                    (user != null && !user.isBlank()) ? user : "sa",
                    pass != null ? pass : "");
        }
        return dataSource.getConnection();
    }

    private BackupTriggerResponse runH2BackupNow(String target, boolean completeBackup) {
        Instant startTime = Instant.now();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String fileName = "ritham_erp_h2_" + ts + ".sql";
        Path dumpPath = Paths.get(target, "Database", "Daily", fileName).toAbsolutePath().normalize();
        StringBuilder logOut = new StringBuilder();

        try {
            String escapedSqlPath = dumpPath.toString().replace("\\", "/").replace("'", "''");
            try (Connection conn = dataSource.getConnection();
                    Statement st = conn.createStatement()) {
                st.execute("SCRIPT TO '" + escapedSqlPath + "' CHARSET 'UTF-8'");
            }

            InspectBackupResponse insp = inspectSqlFile(dumpPath);
            int filesCopied = 0;
            if (completeBackup && migrationUploadDir != null && !migrationUploadDir.isBlank()) {
                Path srcUploads = Paths.get(migrationUploadDir);
                if (Files.exists(srcUploads) && Files.isDirectory(srcUploads)) {
                    Path destUploads = Paths.get(target, "Files", "Daily", "migration_" + ts);
                    filesCopied = copyDirectoryRecursively(srcUploads, destUploads);
                }
            }

            double duration = Math.round((Duration.between(startTime, Instant.now()).toMillis() / 1000.0) * 10.0)
                    / 10.0;
            writeH2ManifestAndLog(target, dumpPath, insp.getTocEntriesCount(), duration, completeBackup, filesCopied);
            applyH2RetentionPolicy(target, dumpPath, ts);

            logOut.append("Engine   : H2 Embedded Database\n")
                    .append("Saved to : ").append(dumpPath).append("\n")
                    .append("Statements: ").append(insp.getTocEntriesCount()).append("\n")
                    .append("Files    : ").append(filesCopied).append("\n");

            return BackupTriggerResponse.builder()
                    .success(true)
                    .message("Complete H2 snapshot saved successfully in " + duration + "s")
                    .durationSeconds(duration)
                    .backupFileName(fileName)
                    .targetLocation(target)
                    .backupFilePath(dumpPath.toString())
                    .outputLog(logOut.toString())
                    .build();
        } catch (Exception e) {
            log.error("H2 backup execution failed", e);
            double duration = Math.round((Duration.between(startTime, Instant.now()).toMillis() / 1000.0) * 10.0)
                    / 10.0;
            appendBackupLogLine(target, "ERROR", "H2 backup failed: " + e.getMessage());
            return BackupTriggerResponse.builder()
                    .success(false)
                    .message("H2 backup failed: " + e.getMessage())
                    .durationSeconds(duration)
                    .targetLocation(target)
                    .outputLog(logOut.append("Error: ").append(e.getMessage()).toString())
                    .build();
        }
    }

    private Map<String, Long> executeInMemoryH2DryRun(String escapedSqlPath) throws java.sql.SQLException {
        String testUrl = "jdbc:h2:mem:ritham_restore_test_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection testConn = DriverManager.getConnection(testUrl, "sa", "");
                Statement st = testConn.createStatement()) {
            st.execute("RUNSCRIPT FROM '" + escapedSqlPath + "' CHARSET 'UTF-8'");
            Map<String, Long> counts = queryJdbcTableCounts(testConn);
            st.execute("SHUTDOWN");
            return counts;
        }
    }

    private RestoreBackupResponse restoreH2Backup(String root, Path dumpPath, boolean isProduction) {
        if (dataSource == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "No active DataSource available for H2 restore");
        }
        Instant startTime = Instant.now();
        StringBuilder logOut = new StringBuilder();
        String escapedSqlPath = dumpPath.toAbsolutePath().normalize().toString().replace("\\", "/").replace("'", "''");
        Map<String, Long> rowCounts = new LinkedHashMap<>();
        int filesRestored = 0;

        try {
            InspectBackupResponse insp = inspectSqlFile(dumpPath);
            if (!insp.isValid()) {
                return RestoreBackupResponse.builder()
                        .success(false)
                        .message("Backup SQL file appears invalid or empty (" + insp.getTocEntriesCount()
                                + " statements)")
                        .targetDatabase(isProduction ? "ritham_erp (H2 dev)" : "h2_restore_test")
                        .databaseEngine("H2")
                        .backupFileName(dumpPath.getFileName().toString())
                        .restoreMode(isProduction ? "PRODUCTION" : "TEST")
                        .durationSeconds(0)
                        .outputLog("Invalid H2 backup SQL script: " + dumpPath)
                        .build();
            }

            if (!isProduction) {
                rowCounts = executeInMemoryH2DryRun(escapedSqlPath);
                appendBackupLogLine(root, "INFO",
                        "H2 test drill verified (live DB NOT modified) from " + dumpPath.getFileName()
                                + " (" + rowCounts + ")");
                double duration = Math.round((Duration.between(startTime, Instant.now()).toMillis() / 1000.0) * 10.0)
                        / 10.0;
                logOut.append("[TEST] Verified H2 snapshot in isolated in-memory database\n")
                        .append("[TEST] Source file: ").append(dumpPath).append("\n")
                        .append("[TEST] Note: Live H2 database was NOT modified.\n")
                        .append("[TEST] Verified table rows: ").append(rowCounts).append("\n");

                return RestoreBackupResponse.builder()
                        .success(true)
                        .message("Dry-run test passed in " + duration + "s — LIVE database was NOT modified.")
                        .targetDatabase("h2_restore_test")
                        .databaseEngine("H2")
                        .backupFileName(dumpPath.getFileName().toString())
                        .restoreMode("TEST")
                        .durationSeconds(duration)
                        .tocEntriesCount(insp.getTocEntriesCount())
                        .filesRestoredCount(0)
                        .requiresReload(false)
                        .tableRowCounts(rowCounts)
                        .outputLog(logOut.toString())
                        .build();
            }

            // Verify snapshot replays cleanly in memory BEFORE dropping live H2 objects
            executeInMemoryH2DryRun(escapedSqlPath);

            HikariPoolMXBean suspendedPool = suspendHikariPoolIfPresent();
            try (Connection liveConn = openDirectH2Connection();
                    Statement st = liveConn.createStatement()) {
                boolean origAutoCommit = liveConn.getAutoCommit();
                if (!origAutoCommit) {
                    liveConn.setAutoCommit(true);
                }
                st.execute("DROP ALL OBJECTS");
                st.execute("RUNSCRIPT FROM '" + escapedSqlPath + "' CHARSET 'UTF-8'");
                rowCounts = queryJdbcTableCounts(liveConn);
            } finally {
                resumeAndEvictHikariPool(suspendedPool);
            }

            // Confirm the resumed Hikari application pool itself sees the restored live data
            rowCounts = queryPooledRowCountsOrFallback(rowCounts);

            filesRestored = restoreLatestMigrationFilesIfPresent(root);
            double duration = Math.round((Duration.between(startTime, Instant.now()).toMillis() / 1000.0) * 10.0)
                    / 10.0;
            appendBackupLogLine(root, "OK",
                    "H2 live restore completed from " + dumpPath.getFileName() + " (" + rowCounts + ")");

            logOut.append("[PRODUCTION] Restored H2 live database from ").append(dumpPath).append("\n")
                    .append("[PRODUCTION] Files restored: ").append(filesRestored).append("\n")
                    .append("[PRODUCTION] Table counts: ").append(rowCounts).append("\n");

            return RestoreBackupResponse.builder()
                    .success(true)
                    .message("Live H2 database restored from " + dumpPath.getFileName() + " in " + duration + "s.")
                    .targetDatabase("ritham_erp (H2 dev)")
                    .databaseEngine("H2")
                    .backupFileName(dumpPath.getFileName().toString())
                    .restoreMode("PRODUCTION")
                    .durationSeconds(duration)
                    .tocEntriesCount(insp.getTocEntriesCount())
                    .filesRestoredCount(filesRestored)
                    .requiresReload(true)
                    .tableRowCounts(rowCounts)
                    .outputLog(logOut.toString())
                    .build();
        } catch (Exception e) {
            log.error("H2 restore failed from {}", dumpPath, e);
            double duration = Math.round((Duration.between(startTime, Instant.now()).toMillis() / 1000.0) * 10.0)
                    / 10.0;
            appendBackupLogLine(root, "ERROR", "H2 restore failed: " + e.getMessage());
            return RestoreBackupResponse.builder()
                    .success(false)
                    .message("H2 restore failed: " + e.getMessage())
                    .targetDatabase(isProduction ? "ritham_erp (H2 dev)" : "h2_restore_test")
                    .databaseEngine("H2")
                    .backupFileName(dumpPath.getFileName().toString())
                    .restoreMode(isProduction ? "PRODUCTION" : "TEST")
                    .durationSeconds(duration)
                    .outputLog(logOut.append("Error: ").append(e.getMessage()).toString())
                    .build();
        }
    }

    private Map<String, Long> queryPooledRowCountsOrFallback(Map<String, Long> fallback) {
        if (dataSource == null) {
            return fallback;
        }
        try (Connection pooledConn = dataSource.getConnection()) {
            Map<String, Long> pooledCounts = queryJdbcTableCounts(pooledConn);
            return pooledCounts.isEmpty() ? fallback : pooledCounts;
        } catch (Exception e) {
            log.warn("Post-restore pooled row count check skipped: {}", e.getMessage());
            return fallback;
        }
    }

    private InspectBackupResponse inspectSqlFile(Path sqlPath) {
        try {
            List<String> lines = Files.readAllLines(sqlPath, StandardCharsets.UTF_8);
            List<String> sampleEntries = new ArrayList<>();
            int statementCount = 0;
            for (String line : lines) {
                if (line == null)
                    continue;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--"))
                    continue;
                statementCount++;
                String upper = trimmed.toUpperCase(Locale.ROOT);
                if (sampleEntries.size() < 25 && (upper.startsWith("CREATE ") || upper.startsWith("INSERT INTO "))) {
                    sampleEntries.add(trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed);
                }
            }
            boolean valid = statementCount > 0;
            double sizeMB = Math.round((Files.size(sqlPath) / 1024.0 / 1024.0) * 100.0) / 100.0;
            return InspectBackupResponse.builder()
                    .fileName(sqlPath.getFileName().toString())
                    .category(sqlPath.getParent() != null ? sqlPath.getParent().getFileName().toString() : "Database")
                    .filePath(sqlPath.toAbsolutePath().toString())
                    .sizeMB(sizeMB)
                    .valid(valid)
                    .tocEntriesCount(statementCount)
                    .sampleEntries(sampleEntries)
                    .message(valid
                            ? "Archive is a valid SQL backup snapshot with " + statementCount + " SQL statements."
                            : "SQL snapshot contains no executable statements.")
                    .build();
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to inspect SQL snapshot: " + e.getMessage());
        }
    }

    private Map<String, Long> queryJdbcTableCounts(Connection conn) {
        Map<String, Long> map = new LinkedHashMap<>();
        List<String> tables = List.of(
                "branches", "customers", "customer_measurements",
                "customer_orders", "order_payments", "employees", "production_stages");
        try (Statement st = conn.createStatement()) {
            for (String tbl : tables) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tbl)) {
                    if (rs.next()) {
                        map.put(tbl, rs.getLong(1));
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private int copyDirectoryRecursively(Path srcDir, Path destDir) throws IOException {
        int count = 0;
        Files.createDirectories(destDir);
        try (var stream = Files.walk(srcDir)) {
            for (Path src : (Iterable<Path>) stream::iterator) {
                Path rel = srcDir.relativize(src);
                Path dst = destDir.resolve(rel);
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else if (Files.isRegularFile(src)) {
                    if (dst.getParent() != null)
                        Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
        }
        return count;
    }

    private int restoreLatestMigrationFilesIfPresent(String root) {
        if (migrationUploadDir == null || migrationUploadDir.isBlank())
            return 0;
        Path filesDaily = Paths.get(root, "Files", "Daily");
        if (!Files.exists(filesDaily) || !Files.isDirectory(filesDaily))
            return 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(filesDaily)) {
            Path newest = null;
            long newestTime = 0;
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && entry.getFileName().toString().startsWith("migration_")) {
                    long m = Files.getLastModifiedTime(entry).toMillis();
                    if (m > newestTime) {
                        newestTime = m;
                        newest = entry;
                    }
                }
            }
            if (newest != null) {
                return copyDirectoryRecursively(newest, Paths.get(migrationUploadDir));
            }
        } catch (Exception e) {
            log.warn("Could not restore migration upload files from {}: {}", filesDaily, e.getMessage());
        }
        return 0;
    }

    private void writeH2ManifestAndLog(
            String target,
            Path dumpPath,
            int statementCount,
            double durationSeconds,
            boolean completeBackup,
            int filesCopied) {
        try {
            double sizeMB = Math.round((Files.size(dumpPath) / 1024.0 / 1024.0) * 100.0) / 100.0;
            String fileName = dumpPath.getFileName().toString();
            String ts = fileName.replace("ritham_erp_h2_", "").replace(".sql", "");
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("snapshotName", fileName);
            manifest.put("databaseEngine", "H2");
            manifest.put("timestamp", ts);
            manifest.put("targetLocation", target);
            manifest.put("dumpFile", fileName);
            manifest.put("dumpSizeMB", sizeMB);
            manifest.put("tocEntriesCount", statementCount);
            manifest.put("filesCopied", filesCopied);
            manifest.put("durationSeconds", durationSeconds);
            manifest.put("completeBackup", completeBackup);
            manifest.put("status", "VERIFIED");

            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
            if (dumpPath.getParent() != null) {
                Files.writeString(dumpPath.getParent().resolve("manifest_" + ts + ".json"), json,
                        StandardCharsets.UTF_8);
            }
            Files.writeString(Paths.get(target, "manifest-latest.json"), json, StandardCharsets.UTF_8);
            appendBackupLogLine(target, "OK", "H2 snapshot created: " + fileName + " (" + sizeMB + " MB, "
                    + statementCount + " SQL statements, files=" + filesCopied + ")");
        } catch (Exception e) {
            log.warn("Could not write H2 manifest/log: {}", e.getMessage());
        }
    }

    private void appendBackupLogLine(String target, String level, String message) {
        try {
            Path logFile = Paths.get(target, "Logs", "backup.log");
            if (logFile.getParent() != null)
                Files.createDirectories(logFile.getParent());
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String line = "[" + ts + "] [" + level + "] " + message + System.lineSeparator();
            Files.writeString(logFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private Path resolveAndValidateBackupFile(
            String locationOverride,
            String category,
            String fileName,
            String filePath,
            boolean allowMissingFileName) {
        String root = resolveRoot(locationOverride);
        Path allowedDbDir = Paths.get(root, "Database").toAbsolutePath().normalize();
        Path allowedUploadsDir = Paths.get(root, "Uploads").toAbsolutePath().normalize();

        if (filePath != null && !filePath.isBlank()) {
            Path candidate;
            try {
                candidate = Paths.get(filePath.trim()).toAbsolutePath().normalize();
            } catch (InvalidPathException e) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid backup file path");
            }
            validateSafeDumpFileName(candidate.getFileName() != null ? candidate.getFileName().toString() : null);
            boolean insideAllowed = isPathWithin(candidate, allowedDbDir) || isPathWithin(candidate, allowedUploadsDir);
            if (!insideAllowed) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Backup file path must be inside the configured backup directory");
            }
            return candidate;
        }

        if (fileName == null || fileName.isBlank()) {
            if (allowMissingFileName) {
                return null;
            }
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Backup file name is required");
        }

        String cleanName = fileName.trim();
        validateSafeDumpFileName(cleanName);

        String cat = category != null ? category.trim() : "";
        if (!cat.isBlank()) {
            validateCategoryAndFileName(cat, cleanName);
            Path p1 = allowedDbDir.resolve(cat).resolve(cleanName).normalize();
            if (Files.exists(p1) && isPathWithin(p1, allowedDbDir)) {
                return p1;
            }
        }

        for (String c : List.of("Daily", "Weekly", "Monthly", "Uploads")) {
            Path cand = allowedDbDir.resolve(c).resolve(cleanName).normalize();
            if (Files.exists(cand) && isPathWithin(cand, allowedDbDir)) {
                return cand;
            }
        }

        Path pUploads = allowedUploadsDir.resolve(cleanName).normalize();
        if (Files.exists(pUploads) && isPathWithin(pUploads, allowedUploadsDir)) {
            return pUploads;
        }

        return null;
    }

    private boolean isPathWithin(Path candidate, Path allowedRoot) {
        try {
            Path normalizedCandidate = candidate.toAbsolutePath().normalize();
            Path normalizedRoot = allowedRoot.toAbsolutePath().normalize();
            if (!normalizedCandidate.startsWith(normalizedRoot)) {
                return false;
            }
            if (Files.exists(normalizedCandidate) && Files.exists(normalizedRoot)) {
                return normalizedCandidate.toRealPath().startsWith(normalizedRoot.toRealPath());
            }
            return true;
        } catch (IOException e) {
            return false;
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
                    "Get-CimInstance Win32_LogicalDisk | Select-Object DeviceID, VolumeName, DriveType, FileSystem | ConvertTo-Json -Compress");
            Process p = pb.start();
            String jsonOutput;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                jsonOutput = reader.lines().collect(Collectors.joining("\n")).trim();
            }
            p.waitFor();
            if (!jsonOutput.isEmpty()) {
                tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
                Object parsed = mapper.readValue(jsonOutput, Object.class);
                List<?> items = parsed instanceof List<?> list ? list : List.of(parsed);
                for (Object item : items) {
                    if (!(item instanceof Map<?, ?> node))
                        continue;
                    Object rawDevId = node.get("DeviceID");
                    if (rawDevId == null)
                        continue;
                    String devId = rawDevId.toString().trim().toUpperCase(Locale.ROOT);
                    if (devId.isEmpty())
                        continue;
                    Map<String, String> d = new HashMap<>();
                    d.put("DeviceID", devId);
                    d.put("VolumeName", node.get("VolumeName") != null ? node.get("VolumeName").toString().trim() : "");
                    d.put("DriveType", node.get("DriveType") != null ? node.get("DriveType").toString().trim() : "");
                    d.put("FileSystem", node.get("FileSystem") != null ? node.get("FileSystem").toString().trim() : "");
                    map.put(devId, d);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query Win32_LogicalDisk via PowerShell: {}", e.getMessage());
        }
        return map;
    }

    private synchronized void persistBackupConfigFile() {
        try {
            if (backupConfigFile == null || backupConfigFile.isBlank()) {
                return;
            }
            String winPath = resolveRoot(null);
            Path cfgPath = Paths.get(backupConfigFile);
            if (cfgPath.getParent() != null) {
                Files.createDirectories(cfgPath.getParent());
            }
            Map<String, Object> configMap = new LinkedHashMap<>();
            configMap.put("backupRoot", winPath);
            configMap.put("scheduleEnabled", this.scheduleEnabled);
            configMap.put("scheduleHour", this.scheduleHour);
            configMap.put("scheduleMinute", this.scheduleMinute);
            configMap.put("backupOnDeviceConnect", this.backupOnDeviceConnect);
            configMap.put("lastAutoBackupDate", this.lastAutoBackupDate != null ? this.lastAutoBackupDate.toString() : "");
            configMap.put("lastAutoBackupMessage", this.lastAutoBackupMessage != null ? this.lastAutoBackupMessage : "");

            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configMap);
            Files.writeString(cfgPath, json, StandardCharsets.UTF_8);
            log.info("Persisted backup config to backup-config.json (root={}, enabled={}, {}:{}, onConnect={})",
                    winPath, this.scheduleEnabled, this.scheduleHour, this.scheduleMinute, this.backupOnDeviceConnect);
        } catch (Exception e) {
            log.warn("Failed to persist backup-config.json: {}", e.getMessage());
        }
    }

    private void applyH2RetentionPolicy(String target, Path dumpPath, String ts) {
        try {
            LocalDate today = LocalDate.now();
            Path manifestFile = dumpPath.getParent() != null
                    ? dumpPath.getParent().resolve("manifest_" + ts + ".json")
                    : null;

            if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
                promoteH2Snapshot(dumpPath, manifestFile, Paths.get(target, "Database", "Weekly"));
            }
            if (today.getDayOfMonth() == 1) {
                promoteH2Snapshot(dumpPath, manifestFile, Paths.get(target, "Database", "Monthly"));
            }

            pruneOldH2Snapshots(Paths.get(target, "Database", "Daily"), 7);
            pruneOldH2Snapshots(Paths.get(target, "Database", "Weekly"), 4);
            pruneOldH2Snapshots(Paths.get(target, "Database", "Monthly"), 12);
            pruneOldMigrationDirectories(Paths.get(target, "Files", "Daily"), 7);
        } catch (Exception e) {
            log.warn("H2 retention cleanup encountered a warning: {}", e.getMessage());
        }
    }

    private void promoteH2Snapshot(Path dumpPath, Path manifestFile, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        Files.copy(dumpPath, destDir.resolve(dumpPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        if (manifestFile != null && Files.exists(manifestFile)) {
            Files.copy(manifestFile, destDir.resolve(manifestFile.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void pruneOldH2Snapshots(Path dir, int keep) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }
        List<Path> snapshots = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)
                        && H2_SNAPSHOT_PATTERN.matcher(entry.getFileName().toString()).matches()) {
                    snapshots.add(entry);
                }
            }
        }
        if (snapshots.size() <= keep) {
            return;
        }
        snapshots.sort((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()));
        for (int i = keep; i < snapshots.size(); i++) {
            Path oldSql = snapshots.get(i);
            Files.deleteIfExists(oldSql);
            Matcher m = H2_SNAPSHOT_PATTERN.matcher(oldSql.getFileName().toString());
            if (m.matches()) {
                Files.deleteIfExists(dir.resolve("manifest_" + m.group(1) + ".json"));
            }
        }
    }

    private void pruneOldMigrationDirectories(Path filesDailyDir, int keep) throws IOException {
        if (!Files.exists(filesDailyDir) || !Files.isDirectory(filesDailyDir)) {
            return;
        }
        List<Path> dirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(filesDailyDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && entry.getFileName().toString().startsWith("migration_")) {
                    dirs.add(entry);
                }
            }
        }
        if (dirs.size() <= keep) {
            return;
        }
        dirs.sort((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()));
        for (int i = keep; i < dirs.size(); i++) {
            try (var walk = Files.walk(dirs.get(i))) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    private void updateScriptDefaultPath(String newPath) {
        try {
            String winPath = newPath.replace("/", "\\");
            persistBackupConfigFile();

            String escaped = Matcher.quoteReplacement(winPath);
            if (backupScriptPath != null && !backupScriptPath.isBlank()) {
                Path script = Paths.get(backupScriptPath);
                if (Files.exists(script)) {
                    String content = Files.readString(script, StandardCharsets.UTF_8);
                    content = content.replaceAll("BackupRoot\\s*=\\s*\".*?\"",
                            "BackupRoot        = \"" + escaped + "\"");
                    Files.writeString(script, content, StandardCharsets.UTF_8);
                    log.info("Persisted BackupRoot to erp-backup.ps1: {}", winPath);
                }
            }
            if (restoreScriptPath != null && !restoreScriptPath.isBlank()) {
                Path restoreScript = Paths.get(restoreScriptPath);
                if (Files.exists(restoreScript)) {
                    String content = Files.readString(restoreScript, StandardCharsets.UTF_8);
                    content = content.replaceAll("\\$BACKUP_ROOT\\s*=\\s*\".*?\"",
                            "\\$BACKUP_ROOT   = \"" + escaped + "\"");
                    Files.writeString(restoreScript, content, StandardCharsets.UTF_8);
                    log.info("Persisted BACKUP_ROOT to erp-restore.ps1: {}", winPath);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to persist default backup root in scripts/config: {}", e.getMessage());
        }
    }
}
