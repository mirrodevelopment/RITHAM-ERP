package com.ritham.erp.module.migration.dto;

import java.time.OffsetDateTime;

/** Response DTO for a migration batch. */
public record MigrationBatchResponse(
        Long id,
        String batchCode,
        Long branchId,
        String branchName,
        String description,
        Short sourceYear,
        Integer totalDocuments,
        Integer uploadedCount,
        Integer processedCount,
        Integer reviewedCount,
        Integer approvedCount,
        Integer importedCount,
        Integer failedCount,
        Integer progressPercent,
        String status,
        String createdByName,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {}
