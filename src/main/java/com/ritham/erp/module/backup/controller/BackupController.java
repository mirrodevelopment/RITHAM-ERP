package com.ritham.erp.module.backup.controller;

import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.module.backup.dto.*;
import com.ritham.erp.module.backup.service.BackupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/backup")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BackupController {

    private final BackupService backupService;

    @GetMapping("/locations")
    public ResponseEntity<ApiResponse<List<BackupLocationDto>>> getLocations() {
        List<BackupLocationDto> locations = backupService.getAvailableLocations();
        return ResponseEntity.ok(ApiResponse.success(locations));
    }

    @PostMapping("/location")
    public ResponseEntity<ApiResponse<BackupStatusResponse>> setLocation(
            @Valid @RequestBody UpdateBackupLocationRequest request
    ) {
        BackupStatusResponse response = backupService.updateActiveBackupLocation(
                request.getLocationPath(),
                request.isPersistAsDefault()
        );
        return ResponseEntity.ok(ApiResponse.success("Backup location updated to " + request.getLocationPath(), response));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<BackupStatusResponse>> getStatus(
            @RequestParam(required = false) String location
    ) {
        BackupStatusResponse status = backupService.getBackupStatus(location);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/files")
    public ResponseEntity<ApiResponse<List<BackupFileDto>>> getFiles(
            @RequestParam(required = false) String location,
            @RequestParam(required = false, defaultValue = "All") String category
    ) {
        List<BackupFileDto> files = backupService.getBackupFiles(location, category);
        return ResponseEntity.ok(ApiResponse.success(files));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<BackupLogDto>>> getLogs(
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<BackupLogDto> logs = backupService.getRecentLogs(location, limit);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<BackupTriggerResponse>> runBackupNow(
            @RequestBody(required = false) BackupTriggerRequest request
    ) {
        BackupTriggerResponse result = backupService.runBackupNow(request);
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.success(result.getMessage(), result));
        }
    }

    @GetMapping("/download/{category}/{fileName}")
    public ResponseEntity<Resource> downloadBackup(
            @PathVariable String category,
            @PathVariable String fileName,
            @RequestParam(required = false) String location
    ) {
        Resource resource = backupService.getBackupFileResource(location, category, fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @PostMapping("/verify/{category}/{fileName}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyBackup(
            @PathVariable String category,
            @PathVariable String fileName,
            @RequestParam(required = false) String location
    ) {
        Map<String, Object> result = backupService.verifyBackupFile(location, category, fileName);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BackupUploadResponse>> uploadBackup(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String location
    ) {
        BackupUploadResponse response = backupService.uploadBackupFile(file, location);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @PostMapping("/inspect")
    public ResponseEntity<ApiResponse<InspectBackupResponse>> inspectBackup(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String filePath
    ) {
        InspectBackupResponse response = backupService.inspectBackup(location, category, fileName, filePath);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @PostMapping("/restore")
    public ResponseEntity<ApiResponse<RestoreBackupResponse>> restoreBackup(
            @Valid @RequestBody RestoreBackupRequest request
    ) {
        RestoreBackupResponse response = backupService.restoreBackup(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.success(response.getMessage(), response));
        }
    }
}
