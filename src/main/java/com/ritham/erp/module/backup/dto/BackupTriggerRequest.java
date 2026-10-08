package com.ritham.erp.module.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupTriggerRequest {
    private String targetLocation;       // Optional custom path or drive root, e.g. "E:\ERP-Backups"
    @Builder.Default
    private boolean completeBackup = true; // Complete backup (DB + Globals + Files + Manifest)
    private boolean persistAsDefault;    // If true, sets this location as persistent default
}
