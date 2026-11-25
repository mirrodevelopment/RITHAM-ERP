package com.ritham.erp.module.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestoreBackupResponse {
    private boolean success;
    private String message;
    private String targetDatabase;
    private String databaseEngine;
    private String backupFileName;
    private String restoreMode;
    private double durationSeconds;
    private int tocEntriesCount;
    private int filesRestoredCount;
    private boolean requiresReload;
    private Map<String, Long> tableRowCounts;
    private String outputLog;
}
