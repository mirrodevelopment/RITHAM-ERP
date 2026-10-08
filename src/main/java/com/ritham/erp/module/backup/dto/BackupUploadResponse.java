package com.ritham.erp.module.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupUploadResponse {
    private String fileName;
    private String category;
    private long sizeBytes;
    private double sizeMB;
    private String savedPath;
    private int tocEntriesCount;
    private boolean verified;
    private String message;
}
