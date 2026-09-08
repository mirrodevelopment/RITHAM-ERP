package com.ritham.erp.module.migration.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Response DTO for a single migration document. */
public record MigrationDocumentResponse(
        Long id,
        Long batchId,
        String batchCode,
        String documentCode,
        String fileName,
        String fileType,
        Short sourceYear,

        // multi-page PDF fields
        Integer pageNumber,
        Integer totalPages,
        String sourceFileName,

        // pipeline statuses
        String ocrStatus,
        String extractionStatus,
        String reviewStatus,
        String importStatus,

        // OCR output
        BigDecimal ocrConfidence,
        String rawOcrText,
        String ocrProcessedImagePath,

        // data (JSON strings - parsed client-side)
        String extractedDataJson,
        String correctedDataJson,

        String errorMessage,
        String createdByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Boolean measurementExists
) {
    public MigrationDocumentResponse(
            Long id, Long batchId, String batchCode, String documentCode,
            String fileName, String fileType, Short sourceYear,
            Integer pageNumber, Integer totalPages, String sourceFileName,
            String ocrStatus, String extractionStatus, String reviewStatus, String importStatus,
            BigDecimal ocrConfidence, String rawOcrText,
            String extractedDataJson, String correctedDataJson,
            String errorMessage, String createdByName,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(id, batchId, batchCode, documentCode, fileName, fileType, sourceYear,
             pageNumber, totalPages, sourceFileName,
             ocrStatus, extractionStatus, reviewStatus, importStatus,
             ocrConfidence, rawOcrText, null, extractedDataJson, correctedDataJson,
             errorMessage, createdByName, createdAt, updatedAt, false);
    }
}
