package com.ritham.erp.module.backup.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupScheduleConfigRequest {

    @Builder.Default
    private boolean enabled = true;

    @Min(value = 0, message = "Hour must be between 0 and 23")
    @Max(value = 23, message = "Hour must be between 0 and 23")
    @Builder.Default
    private int hour = 19;

    @Min(value = 0, message = "Minute must be between 0 and 59")
    @Max(value = 59, message = "Minute must be between 0 and 59")
    @Builder.Default
    private int minute = 0;

    @Builder.Default
    private boolean backupOnDeviceConnect = true;

    @Builder.Default
    private boolean syncWindowsTask = true;
}
