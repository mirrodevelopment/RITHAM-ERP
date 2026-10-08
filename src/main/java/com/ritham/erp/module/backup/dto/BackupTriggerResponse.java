package com.ritham.erp.module.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupTriggerResponse {
    private boolean success;
    private String message;
    private double durationSeconds;
    private String backupFileName;
    private String outputLog;
}
