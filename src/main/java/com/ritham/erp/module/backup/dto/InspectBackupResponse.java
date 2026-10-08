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
public class InspectBackupResponse {
    private String fileName;
    private String category;
    private String filePath;
    private double sizeMB;
    private boolean valid;
    private int tocEntriesCount;
    private List<String> sampleEntries;
    private String message;
}
