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
public class BackupDirectoryDto {
    private String currentPath;
    private String parentPath;
    private List<DirectoryEntry> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectoryEntry {
        private String name;
        private String path;
        private boolean writable;
    }
}
