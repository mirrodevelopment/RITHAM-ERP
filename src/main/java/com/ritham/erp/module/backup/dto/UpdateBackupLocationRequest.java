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
public class UpdateBackupLocationRequest {
    @NotBlank(message = "Backup location path cannot be empty")
    private String locationPath;         // e.g. "E:\ERP-Backups" or "D:\ERP-Backups"
    @Builder.Default
    private boolean persistAsDefault = true;
}
