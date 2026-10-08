package com.ritham.erp.module.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveStatusDto {
    private String driveLetter;
    private boolean connected;
    private double totalSpaceGB;
    private double freeSpaceGB;
    private double usedSpaceGB;
    private double usedPercent;
    private String fileSystem;
}
