package com.ritham.erp.module.migration.entity;

import com.ritham.erp.module.employee.entity.Employee;
import com.ritham.erp.module.migration.enums.DocumentType;
import com.ritham.erp.module.migration.enums.MigrationDocumentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One row per scanned paper document (image or PDF).
 *
 * <p>Pipeline status columns (ocr_status, extraction_status, review_status,
 * import_status) are updated independently as the document moves through each
 * phase.  The authoritative "where is this document now" view is review_status
 * (or import_status once import has been attempted).
 */
@Entity
@Table(name = "migration_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private MigrationBatch batch;

    /** Unique document code, e.g. HIST-2023-CBE-000001. */
    @Column(name = "document_code", nullable = false, unique = true, length = 40)
    private String documentCode;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private DocumentType fileType;

    @Column(name = "source_year", nullable = false)
    private Short sourceYear;

    // ── Multi-Page PDF Metadata ───────────────────────────────────────────────

    @Column(name = "page_number", nullable = false)
    @Builder.Default
    private Integer pageNumber = 1;

    @Column(name = "total_pages", nullable = false)
    @Builder.Default
    private Integer totalPages = 1;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "source_file_path", length = 1000)
    private String sourceFilePath;

    /** Path to the preprocessed (grayscale, contrast-enhanced, oriented) image used for OCR.
     *  The original uploaded file path is always in {@link #filePath}. */
    @Column(name = "ocr_processed_image_path", length = 1000)
    private String ocrProcessedImagePath;

    @Column(name = "ocr_version", length = 20)
    @Builder.Default
    private String ocrVersion = "2.0";

    @Column(name = "template_version", length = 20)
    @Builder.Default
    private String templateVersion = "2.0";

    @Column(name = "preprocessing_version", length = 20)
    @Builder.Default
    private String preprocessingVersion = "2.0";

    // ── Pipeline status ───────────────────────────────────────────────────────

    @Column(name = "ocr_status", nullable = false, length = 30)
    @Builder.Default
    private String ocrStatus = MigrationDocumentStatus.UPLOADED.name();

    @Column(name = "extraction_status", nullable = false, length = 30)
    @Builder.Default
    private String extractionStatus = MigrationDocumentStatus.UPLOADED.name();

    @Column(name = "review_status", nullable = false, length = 30)
    @Builder.Default
    private String reviewStatus = MigrationDocumentStatus.UPLOADED.name();

    @Column(name = "import_status", nullable = false, length = 30)
    @Builder.Default
    private String importStatus = MigrationDocumentStatus.UPLOADED.name();

    // ── OCR output ────────────────────────────────────────────────────────────

    @Column(name = "raw_ocr_text", columnDefinition = "TEXT")
    private String rawOcrText;

    /** Average confidence across all OCR'd words (0.00 – 100.00). */
    @Column(name = "ocr_confidence", precision = 5, scale = 2)
    private BigDecimal ocrConfidence;

    // ── Extraction output ─────────────────────────────────────────────────────

    /** JSON serialisation of ExtractedData DTO. */
    @Column(name = "extracted_data_json", columnDefinition = "TEXT")
    private String extractedDataJson;

    /** Reviewer-corrected version of extracted data (JSON). */
    @Column(name = "corrected_data_json", columnDefinition = "TEXT")
    private String correctedDataJson;

    // ── Error tracking ────────────────────────────────────────────────────────

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // ── Audit ─────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the "best available" data: corrected by reviewer, or raw extraction. */
    public String effectiveDataJson() {
        return correctedDataJson != null ? correctedDataJson : extractedDataJson;
    }

    public boolean isReadyToImport() {
        return MigrationDocumentStatus.VERIFIED.name().equals(reviewStatus);
    }

    public boolean isAlreadyImported() {
        return MigrationDocumentStatus.IMPORTED.name().equals(importStatus);
    }
}
