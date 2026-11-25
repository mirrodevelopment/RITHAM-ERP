package com.ritham.erp.module.backup.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBackupDirectoryRequest {

    @NotBlank(message = "Parent folder path is required")
    private String parentPath;

    @NotBlank(message = "New folder name is required")
    private String folderName;
}
