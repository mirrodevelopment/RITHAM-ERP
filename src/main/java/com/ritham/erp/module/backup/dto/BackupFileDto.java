package com.ritham.erp.module.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupFileDto {
    private String fileName;
    private String category; // Daily, Weekly, Monthly
    private long sizeBytes;
    private double sizeMB;
    private LocalDateTime modifiedTime;
    private String ageText;
    private boolean verified;
}
