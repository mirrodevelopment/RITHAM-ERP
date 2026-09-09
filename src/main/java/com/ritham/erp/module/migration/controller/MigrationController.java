package com.ritham.erp.module.migration.controller;

import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.common.response.PageResponse;
import com.ritham.erp.module.migration.dto.*;
import com.ritham.erp.module.migration.entity.*;
import com.ritham.erp.module.migration.service.CustomerMatchingService;
import com.ritham.erp.module.migration.service.MigrationBatchService;
import com.ritham.erp.module.migration.service.MigrationImportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import com.ritham.erp.module.migration.enums.MigrationDocumentStatus;
import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * REST API for the Historical Data Migration module.
 *
 * <pre>
 * Base path: /api/migration
 *
 * Batch endpoints:
 *   POST   /api/migration/batches                       Create a new batch
 *   GET    /api/migration/batches                       List batches (branch-scoped)
 *   GET    /api/migration/batches/{id}                  Get batch by ID
 *   GET    /api/migration/statistics                    Dashboard statistics
 *
 * Document endpoints:
 *   POST   /api/migration/batches/{id}/documents        Upload document
 *   GET    /api/migration/batches/{id}/documents        List documents in batch
 *   GET    /api/migration/documents/{id}                Get document by ID
 *   POST   /api/migration/documents/{id}/ocr            Trigger OCR
 *   POST   /api/migration/documents/{id}/extract        Trigger extraction
 *   POST   /api/migration/documents/{id}/review         Save review decision
 *   GET    /api/migration/documents/{id}/reviews        Review history
 *   GET    /api/migration/documents/{id}/matches        Customer match candidates
 *   POST   /api/migration/documents/{id}/import         Import to ERP
 * </pre>
 */
import com.ritham.erp.module.customer.repository.CustomerMeasurementRepository;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/migration")
public class MigrationController {

    private final MigrationBatchService  batchService;
    private final MigrationImportService importService;
    private final CustomerMeasurementRepository measurementRepository;

    @Autowired
    public MigrationController(MigrationBatchService batchService,
                               MigrationImportService importService,
                               CustomerMeasurementRepository measurementRepository) {
        this.batchService = batchService;
        this.importService = importService;
        this.measurementRepository = measurementRepository;
    }

    public MigrationController(MigrationBatchService batchService, MigrationImportService importService) {
        this(batchService, importService, null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BATCH ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * POST /api/migration/batches
     * Create a new migration batch.
     * Roles: ADMIN, BRANCH_MANAGER
     */
    @PostMapping("/batches")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<ApiResponse<MigrationBatchResponse>> createBatch(
            @RequestBody @Valid CreateBatchRequest req) {

        MigrationBatch batch = batchService.createBatch(req);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(toBatchResponse(batch)));
    }

    /**
     * GET /api/migration/batches
     * List batches (branch-scoped for branch staff, all branches for admin).
     */
    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<MigrationBatchResponse>>> listBatches(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        // Use unsorted pageable — sort is embedded in the JPQL FETCH JOIN query (ORDER BY b.createdAt DESC)
        Pageable pageable = PageRequest.of(page, size);
        Page<MigrationBatch> batches = batchService.listBatches(pageable);
        Page<MigrationBatchResponse> response = batches.map(this::toBatchResponse);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(response)));
    }

    /**
     * GET /api/migration/batches/{id}
     * Get a single batch by ID.
     */
    @GetMapping("/batches/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<MigrationBatchResponse>> getBatch(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(toBatchResponse(batchService.getBatch(id))));
    }

    /**
     * GET /api/migration/statistics
     * Dashboard statistics (total docs, per-batch progress, etc.).
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<MigrationStatisticsResponse>> getStatistics() {
        return ResponseEntity.ok(ApiResponse.success(batchService.getStatistics()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DOCUMENT ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * POST /api/migration/batches/{id}/documents
     * Upload a scanned image or PDF to a batch.
     * If the file is a multi-page PDF, every page is rendered and registered as an independent document.
     */
    @PostMapping("/batches/{id}/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<Object>> uploadDocument(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {

        List<MigrationDocument> docs = batchService.uploadDocuments(id, file);
        List<MigrationDocumentResponse> responses = docs.stream().map(this::toDocumentResponse).toList();
        if (responses.size() == 1) {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(responses.get(0)));
        }
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(responses));
    }

    /**
     * GET /api/migration/batches/{id}/documents
     * List documents in a batch, optionally filtered by reviewStatus.
     */
    @GetMapping("/batches/{id}/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<MigrationDocumentResponse>>> listDocuments(
            @PathVariable Long id,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<MigrationDocument> docs = batchService.listDocuments(id, reviewStatus, pageable);
        Page<MigrationDocumentResponse> response = docs.map(this::toDocumentResponse);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(response)));
    }

    /**
     * GET /api/migration/documents/{id}
     * Get a single document (includes raw OCR text for the review desk).
     */
    @GetMapping("/documents/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<MigrationDocumentResponse>> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(toDocumentResponse(batchService.getDocument(id))));
    }

    /**
     * GET /api/migration/documents/{id}/pages
     * Get all sibling pages of a multi-page document in order.
     */
    @GetMapping("/documents/{id}/pages")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<MigrationDocumentResponse>>> getDocumentPages(@PathVariable Long id) {
        List<MigrationDocument> siblings = batchService.getSiblingPages(id);
        return ResponseEntity.ok(ApiResponse.success(siblings.stream().map(this::toDocumentResponse).toList()));
    }

    /**
     * POST /api/migration/documents/{id}/approve-all-pages
     * Approve all pages of a multi-page PDF batch document in one step.
     */
    @PostMapping("/documents/{id}/approve-all-pages")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<MigrationDocumentResponse>>> approveAllPages(@PathVariable Long id) {
        List<MigrationDocument> approved = batchService.approveAllPages(id);
        return ResponseEntity.ok(ApiResponse.success(approved.stream().map(this::toDocumentResponse).toList()));
    }

    /**
     * POST /api/migration/documents/{id}/import-all-pages
     * Import all VERIFIED pages of a multi-page PDF batch document.
     */
    @PostMapping("/documents/{id}/import-all-pages")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<ApiResponse<List<MigrationImportSummary>>> importAllPages(@PathVariable Long id) {
        List<MigrationDocument> siblings = batchService.getSiblingPages(id);
        List<MigrationImportSummary> importedList = new ArrayList<>();
        for (MigrationDocument s : siblings) {
            if (MigrationDocumentStatus.VERIFIED.name().equals(s.getReviewStatus()) && !s.isAlreadyImported()) {
                MigrationImport imp = importService.importDocument(s.getId(), null);
                importedList.add(new MigrationImportSummary(
                        imp.getId(),
                        imp.getImportStatus(),
                        imp.getCustomerMobile(),
                        imp.getOrderId(),
                        imp.getMeasurementId(),
                        imp.getErrorMessage(),
                        imp.getImportedAt()));
            }
        }
        return ResponseEntity.ok(ApiResponse.success(importedList));
    }

    /**
     * GET /api/migration/documents/{id}/file
     * Stream original scanned file or rendered page image inline for preview.
     */
    @GetMapping("/documents/{id}/file")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<Resource> getDocumentFile(@PathVariable Long id) throws IOException {
        MigrationDocument doc = batchService.getDocument(id);
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Document file not found on disk");
        }
        String contentType = Files.probeContentType(path);
        if (contentType == null || MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)) {
            if (doc.getFilePath() != null && doc.getFilePath().toLowerCase().endsWith(".pdf")) {
                contentType = MediaType.APPLICATION_PDF_VALUE;
            } else {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
        }
        Resource resource = new UrlResource(path.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getFileName() + "\"")
                .body(resource);
    }

    /**
     * GET /api/migration/documents/{id}/source-file
     * Download or stream the original uploaded PDF source file.
     */
    @GetMapping("/documents/{id}/source-file")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<Resource> getDocumentSourceFile(@PathVariable Long id) throws IOException {
        MigrationDocument doc = batchService.getDocument(id);
        String srcPathStr = doc.getSourceFilePath() != null ? doc.getSourceFilePath() : doc.getFilePath();
        Path path = Paths.get(srcPathStr);
        if (!Files.exists(path)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Source file not found on disk");
        }
        String contentType = Files.probeContentType(path);
        if (contentType == null || MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)) {
            if (srcPathStr.toLowerCase().endsWith(".pdf")) {
                contentType = MediaType.APPLICATION_PDF_VALUE;
            } else {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
        }
        Resource resource = new UrlResource(path.toUri());
        String downloadName = doc.getSourceFileName() != null ? doc.getSourceFileName() : doc.getFileName();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + downloadName + "\"")
                .body(resource);
    }

    /**
     * GET /api/migration/documents/{id}/original-image
     * Stream original scanned file or rendered page image inline for preview.
     */
    @GetMapping("/documents/{id}/original-image")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<Resource> getDocumentOriginalImage(@PathVariable Long id) throws IOException {
        return getDocumentFile(id);
    }

    /**
     * GET /api/migration/documents/{id}/processed-image
     * Stream OCR preprocessed image (contrast-enhanced, rotated) if available, falling back to document file.
     */
    @GetMapping("/documents/{id}/processed-image")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<Resource> getDocumentProcessedImage(@PathVariable Long id) throws IOException {
        MigrationDocument doc = batchService.getDocument(id);
        String targetPathStr = doc.getOcrProcessedImagePath();
        if (targetPathStr == null || !Files.exists(Paths.get(targetPathStr))) {
            targetPathStr = doc.getFilePath();
        }
        Path path = Paths.get(targetPathStr);
        if (!Files.exists(path)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Document image not found on disk");
        }
        String contentType = Files.probeContentType(path);
        if (contentType == null || MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(contentType)) {
            contentType = "image/png";
        }
        Resource resource = new UrlResource(path.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"processed_" + doc.getFileName() + "\"")
                .body(resource);
    }

    /**
     * POST /api/migration/documents/{id}/ocr
     * Trigger Tesseract OCR on an UPLOADED document.
     */
    @PostMapping("/documents/{id}/ocr")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<MigrationDocumentResponse>> runOcr(@PathVariable Long id) {
        MigrationDocument doc = batchService.runOcr(id);
        return ResponseEntity.ok(ApiResponse.success(toDocumentResponse(doc)));
    }

    /**
     * POST /api/migration/documents/{id}/extract
     * Trigger field extraction on an OCR_COMPLETED document.
     */
    @PostMapping("/documents/{id}/extract")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<MigrationDocumentResponse>> runExtraction(@PathVariable Long id) {
        MigrationDocument doc = batchService.runExtraction(id);
        return ResponseEntity.ok(ApiResponse.success(toDocumentResponse(doc)));
    }

    /**
     * POST /api/migration/documents/{id}/rescan
     * Re-run OCR and extraction on a document.
     * Optionally takes rotate (e.g. 90, 180, 270) to permanently rotate image on disk before OCR.
     */
    @PostMapping("/documents/{id}/rescan")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<MigrationDocumentResponse>> rescan(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") int rotate) {
        MigrationDocument doc = batchService.rescanDocument(id, rotate);
        return ResponseEntity.ok(ApiResponse.success(toDocumentResponse(doc)));
    }

    /**
     * POST /api/migration/documents/{id}/review
     * Save a reviewer's decision (APPROVED / REJECTED / NEEDS_REWORK).
     */
    @PostMapping("/documents/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<MigrationDocumentResponse>> saveReview(
            @PathVariable Long id,
            @RequestBody MigrationReviewRequest req) {

        MigrationDocument doc = batchService.saveReview(id, req);
        return ResponseEntity.ok(ApiResponse.success(toDocumentResponse(doc)));
    }

    /**
     * GET /api/migration/documents/{id}/reviews
     * Full review history for a document (most recent first).
     */
    @GetMapping("/documents/{id}/reviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<List<MigrationReviewSummary>>> getReviewHistory(
            @PathVariable Long id) {

        List<MigrationReview> reviews = batchService.getReviewHistory(id);
        List<MigrationReviewSummary> response = reviews.stream()
                .map(r -> new MigrationReviewSummary(
                        r.getId(),
                        r.getReviewStatus(),
                        r.getRemarks(),
                        r.getCorrectedDataJson(),
                        r.getReviewer() != null ? r.getReviewer().getId() : null,
                        r.getReviewedAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/migration/documents/{id}/matches
     * Return potential ERP customer matches for a document's extracted mobile.
     */
    @GetMapping("/documents/{id}/matches")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<CustomerMatchingService.MatchResult>> getMatches(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(batchService.getMatches(id)));
    }

    /**
     * POST /api/migration/documents/{id}/import
     * Import a VERIFIED document into live ERP production tables.
     * Roles: ADMIN, BRANCH_MANAGER only (not STAFF — elevated privilege required).
     */
    @PostMapping("/documents/{id}/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<ApiResponse<MigrationImportSummary>> importDocument(
            @PathVariable Long id,
            @RequestBody(required = false) MigrationReviewRequest req) {

        MigrationImport importRecord = importService.importDocument(id, req);
        MigrationImportSummary summary = new MigrationImportSummary(
                importRecord.getId(),
                importRecord.getImportStatus(),
                importRecord.getCustomerMobile(),
                importRecord.getOrderId(),
                importRecord.getMeasurementId(),
                importRecord.getErrorMessage(),
                importRecord.getImportedAt());
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PAGE CONVENIENCE ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * POST /api/migration/pages/{id}/review
     * Review/update page extraction.
     */
    @PostMapping("/pages/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<MigrationDocumentResponse>> savePageReview(
            @PathVariable Long id,
            @RequestBody MigrationReviewRequest req) {
        return saveReview(id, req);
    }

    /**
     * POST /api/migration/pages/{id}/verify
     * Verify/Approve a page.
     */
    @PostMapping("/pages/{id}/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<MigrationDocumentResponse>> verifyPage(
            @PathVariable Long id,
            @RequestBody(required = false) MigrationReviewRequest req) {
        if (req == null) {
            req = new MigrationReviewRequest(MigrationDocumentStatus.VERIFIED.name(), null, "Page verified by reviewer", null, false);
        } else if (req.action() == null || req.action().isBlank()) {
            req = new MigrationReviewRequest(MigrationDocumentStatus.VERIFIED.name(), req.correctedDataJson(), req.remarks(), req.matchedCustomerMobile(), req.updateCustomerProfile());
        }
        return saveReview(id, req);
    }

    /**
     * POST /api/migration/pages/{id}/reject
     * Reject a page.
     */
    @PostMapping("/pages/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<MigrationDocumentResponse>> rejectPage(
            @PathVariable Long id,
            @RequestBody(required = false) MigrationReviewRequest req) {
        if (req == null) {
            req = new MigrationReviewRequest(MigrationDocumentStatus.REJECTED.name(), null, "Page rejected by reviewer", null, false);
        } else if (req.action() == null || req.action().isBlank()) {
            req = new MigrationReviewRequest(MigrationDocumentStatus.REJECTED.name(), req.correctedDataJson(), req.remarks(), req.matchedCustomerMobile(), req.updateCustomerProfile());
        }
        return saveReview(id, req);
    }

    /**
     * GET /api/migration/pages/{id}/matches
     * Matches for a page.
     */
    @GetMapping("/pages/{id}/matches")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'STAFF')")
    public ResponseEntity<ApiResponse<CustomerMatchingService.MatchResult>> getPageMatches(
            @PathVariable Long id) {
        return getMatches(id);
    }

    /**
     * POST /api/migration/pages/{id}/import
     * Import a verified page.
     */
    @PostMapping("/pages/{id}/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<ApiResponse<MigrationImportSummary>> importPage(
            @PathVariable Long id,
            @RequestBody(required = false) MigrationReviewRequest req) {
        return importDocument(id, req);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INLINE SUMMARY RECORDS  (avoid extra DTO files)
    // ═══════════════════════════════════════════════════════════════════════

    public record MigrationReviewSummary(
            Long id,
            String reviewStatus,
            String remarks,
            String correctedDataJson,
            Long reviewerId,
            java.time.OffsetDateTime reviewedAt) {}

    public record MigrationImportSummary(
            Long id,
            String importStatus,
            String customerMobile,
            Long orderId,
            Long measurementId,
            String errorMessage,
            java.time.OffsetDateTime importedAt) {}

    // ═══════════════════════════════════════════════════════════════════════
    // DELETION & CLEANUP ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * DELETE /api/migration/documents/{id}
     * Delete a single migration document and its physical file.
     */
    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable Long id) {
        batchService.deleteDocument(id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
    }

    /**
     * DELETE /api/migration/batches/{id}
     * Delete a migration batch, all its documents, and physical files.
     */
    @DeleteMapping("/batches/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBatch(@PathVariable Long id) {
        batchService.deleteBatch(id);
        return ResponseEntity.ok(ApiResponse.success("Batch deleted successfully", null));
    }

    /**
     * POST /api/migration/clear-all
     * Remove all old OCR data: documents, reviews, imports, batches, and files.
     */
    @PostMapping("/clear-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> clearAll() {
        batchService.clearAllMigrationData();
        return ResponseEntity.ok(ApiResponse.success("All migration and OCR data cleared successfully", null));
    }

    /**
     * DELETE /api/migration/clear-all
     */
    @DeleteMapping("/clear-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> clearAllDelete() {
        batchService.clearAllMigrationData();
        return ResponseEntity.ok(ApiResponse.success("All migration and OCR data cleared successfully", null));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAPPERS
    // ═══════════════════════════════════════════════════════════════════════

    private MigrationBatchResponse toBatchResponse(MigrationBatch b) {
        Long branchId = null;
        String branchName = null;
        if (b.getBranch() != null) {
            branchId = b.getBranch().getId();
            try {
                branchName = b.getBranch().getName();
            } catch (Exception ignored) {}
        }

        String createdByName = null;
        if (b.getCreatedBy() != null) {
            try {
                createdByName = b.getCreatedBy().getFullName();
            } catch (Exception ignored) {}
        }

        return new MigrationBatchResponse(
                b.getId(),
                b.getBatchCode(),
                branchId,
                branchName,
                b.getDescription(),
                b.getSourceYear(),
                b.getTotalDocuments(),
                b.getUploadedCount(),
                b.getProcessedCount(),
                b.getReviewedCount(),
                b.getApprovedCount(),
                b.getImportedCount(),
                b.getFailedCount(),
                b.getProgress(),
                b.getStatus(),
                createdByName,
                b.getCreatedAt(),
                b.getCompletedAt()
        );
    }

    private MigrationDocumentResponse toDocumentResponse(MigrationDocument d) {
        Long batchId = null;
        String batchCode = null;
        if (d.getBatch() != null) {
            batchId = d.getBatch().getId();
            try {
                batchCode = d.getBatch().getBatchCode();
            } catch (Exception ignored) {}
        }

        String createdByName = null;
        if (d.getCreatedBy() != null) {
            try {
                createdByName = d.getCreatedBy().getFullName();
            } catch (Exception ignored) {}
        }

        java.math.BigDecimal conf = d.getOcrConfidence();
        if (conf != null && (conf.doubleValue() == 85.0 || conf.doubleValue() == 85.00) && d.getExtractedDataJson() != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"overallConfidence\"\\s*:\\s*([0-9.]+)").matcher(d.getExtractedDataJson());
            if (m.find()) {
                try {
                    double val = Double.parseDouble(m.group(1));
                    if (val > 0.0) {
                        conf = java.math.BigDecimal.valueOf(Math.round(val * 1000.0) / 10.0);
                    }
                } catch (Exception ignored) {}
            }
        }

        Boolean measurementExists = false;
        String json = d.effectiveDataJson();
        if (json != null && !json.isBlank()) {
            try {
                java.util.regex.Matcher mMobile = java.util.regex.Pattern.compile("\"customerMobile\"\\s*:\\s*\"([6-9]\\d{9})\"").matcher(json);
                if (mMobile.find()) {
                    String mobile = mMobile.group(1);
                    String garment = null;
                    java.util.regex.Matcher mGarment = java.util.regex.Pattern.compile("\"garmentType\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                    if (mGarment.find()) {
                        garment = mGarment.group(1).trim().toUpperCase();
                    }
                    if (measurementRepository != null) {
                        if (garment != null && !garment.isBlank() && !"OTHER".equals(garment)) {
                            measurementExists = measurementRepository.existsByCustomerMobileAndGarmentType(mobile, garment);
                        }
                        if (!measurementExists) {
                            measurementExists = measurementRepository.existsByCustomerMobile(mobile);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return new MigrationDocumentResponse(
                d.getId(),
                batchId,
                batchCode,
                d.getDocumentCode(),
                d.getFileName(),
                d.getFileType() != null ? d.getFileType().name() : null,
                d.getSourceYear(),
                d.getPageNumber() != null ? d.getPageNumber() : 1,
                d.getTotalPages() != null ? d.getTotalPages() : 1,
                d.getSourceFileName() != null ? d.getSourceFileName() : d.getFileName(),
                d.getOcrStatus(),
                d.getExtractionStatus(),
                d.getReviewStatus(),
                d.getImportStatus(),
                conf,
                d.getRawOcrText(),
                d.getOcrProcessedImagePath(),
                d.getExtractedDataJson(),
                d.getCorrectedDataJson(),
                d.getErrorMessage(),
                createdByName,
                d.getCreatedAt(),
                d.getUpdatedAt(),
                measurementExists
        );
    }
}
