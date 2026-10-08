package com.ritham.erp.module.backup.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestoreBackupRequest {

    /**
     * The file name of the backup (e.g. ritham_erp_daily_2026-10-08_020000.dump).
     */
    @NotBlank(message = "Backup file name is required")
    private String backupFileName;

    /**
     * Category folder under Database (Daily, Weekly, Monthly, Uploads) or null.
     */
    private String category;

    /**
     * Base location override (optional, defaults to active backup root).
     */
    private String location;

    /**
     * Direct file path (optional, if known).
     */
    private String filePath;

    /**
     * Mode: "TEST" (restores into ritham_erp_restore_test) or "PRODUCTION" (restores into ritham_erp).
     * Defaults to "TEST".
     */
    @Builder.Default
    private String restoreMode = "TEST";

    /**
     * Confirmation phrase required for PRODUCTION restore.
     * Must strictly match "CONFIRM RESTORE".
     */
    private String confirmText;
}
