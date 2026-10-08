package com.ritham.erp.module.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupStatusResponse {
    private DriveStatusDto drive;
    private String databaseName;
    private String postgresStatus; // RUNNING, STOPPED, UNKNOWN
    private boolean postgresRunning;
    private int dailyCount;
    private int dailyLimit;
    private int weeklyCount;
    private int weeklyLimit;
    private int monthlyCount;
    private int monthlyLimit;
    private int uploadCount;
    private int totalCount;
    private double totalSizeMB;
    private BackupFileDto latestBackup;
    private String taskScheduleText;
    private String nextScheduledRun;
    private boolean isLocked;
    private List<BackupLogDto> recentLogs;
}
