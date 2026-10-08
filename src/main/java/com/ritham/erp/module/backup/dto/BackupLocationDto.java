package com.ritham.erp.module.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupLocationDto {
    private String driveLetter;        // e.g. "E:"
    private String volumeName;         // e.g. "USB Drive" or "New Volume"
    private String driveType;          // "REMOVABLE", "FIXED", "NETWORK"
    private boolean isRemovable;       // true if external USB / removable flash
    private String fileSystem;         // "FAT32", "NTFS", "exFAT"
    private double totalSpaceGB;       // Total capacity in GB
    private double freeSpaceGB;        // Free space in GB
    private double usedPercent;        // Used percent
    private boolean isWritable;        // Write permission verified
    private boolean isCurrentTarget;   // Matches currently active backupRoot
    private String suggestedPath;      // e.g. "E:\ERP-Backups"
    private String statusText;         // e.g. "Ready • 12.6 GB Free"
}
