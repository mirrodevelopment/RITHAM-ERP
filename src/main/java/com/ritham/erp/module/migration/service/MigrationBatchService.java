package com.ritham.erp.module.migration.service;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.branch.entity.Branch;
import com.ritham.erp.module.branch.repository.BranchRepository;
import com.ritham.erp.module.migration.dto.*;
import com.ritham.erp.module.migration.entity.*;
import com.ritham.erp.module.migration.enums.DocumentType;
import com.ritham.erp.module.migration.enums.MigrationDocumentStatus;
import com.ritham.erp.module.migration.repository.*;
import com.ritham.erp.security.BranchContext;
import com.ritham.erp.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Orchestrates batch and document management in the migration pipeline.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create / list migration batches (branch-scoped)</li>
 *   <li>Upload and store document files</li>
 *   <li>Trigger OCR and extraction</li>
 *   <li>Save reviewer corrections</li>
 *   <li>Expose statistics for the dashboard</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationBatchService {

    private final MigrationBatchRepository    batchRepository;
    private final MigrationDocumentRepository documentRepository;
    private final MigrationReviewRepository   reviewRepository;
    private final MigrationImportRepository   importRepository;
    private final BranchRepository            branchRepository;

    private final OcrService             ocrService;
    private final ExtractionService      extractionService;
    private final CustomerMatchingService matchingService;
    private final PdfPageService         pdfPageService;

    @Value("${app.migration.upload-dir:D:/ritham-erp-uploads/migration}")
    private String uploadDir;

    @Value("${app.migration.ocr-language:eng}")
    private String ocrLanguage;

    // ── Batch management ──────────────────────────────────────────────────────

    @Transactional
    public MigrationBatch createBatch(CreateBatchRequest req) {
        Branch branch = null;
        if (req.branchId() != null) {
            branch = branchRepository.findById(req.branchId()).orElse(null);
        }
        if (branch == null) {
            Long callerBranch = BranchContext.getBranchId();
            if (callerBranch != null) {
                branch = branchRepository.findById(callerBranch).orElse(null);
            }
        }
        if (branch == null) {
            var emp = currentEmployee();
            if (emp != null && emp.getBranch() != null) {
                branch = emp.getBranch();
            }
        }
        if (branch == null) {
            branch = branchRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                            "No company branch available for migration"));
        }

        // Enforce branch isolation: non-admin cannot create batches for other branches
        Long callerBranch = BranchContext.getBranchId();
        if (callerBranch != null && req.branchId() != null && !callerBranch.equals(req.branchId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED,
                    "You cannot create a migration batch for a different branch");
        }

        Short year = req.sourceYear() != null
                ? req.sourceYear()
                : (short) java.time.Year.now().getValue();

        String desc = (req.description() != null && !req.description().isBlank())
                ? req.description().trim()
                : "Historical Migration";

        String batchCode = generateBatchCode(branch, year);
        MigrationBatch batch = MigrationBatch.builder()
                .batchCode(batchCode)
                .branch(branch)
                .description(desc)
                .sourceYear(year)
                .createdBy(currentEmployee())
                .build();

        return batchRepository.save(batch);
    }

    @Transactional(readOnly = true)
    public Page<MigrationBatch> listBatches(Pageable pageable) {
        Long branchId = BranchContext.getBranchId();
        if (branchId != null) {
            return batchRepository.findByBranchIdWithBranch(branchId, pageable);
        }
        return batchRepository.findAllWithBranch(pageable);
    }

    @Transactional(readOnly = true)
    public MigrationBatch getBatch(Long id) {
        MigrationBatch batch = batchRepository.findByIdWithDetails(id)
                .orElseGet(() -> batchRepository.findById(id)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                                "Migration batch not found: " + id)));
        verifyBranchAccess(batch);
        return batch;
    }

    // ── Document upload ───────────────────────────────────────────────────────

    @Transactional
    public List<MigrationDocument> uploadDocuments(Long batchId, MultipartFile file) throws IOException {
        MigrationBatch batch = getBatch(batchId);

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String ext      = getExtension(fileName);
        validateFileType(ext);

        List<MigrationDocument> createdDocs = new ArrayList<>();

        if ("pdf".equalsIgnoreCase(ext)) {
            // 1. Store original uploaded PDF in batch source directory for audit/provenance
            Path sourceDir = Paths.get(uploadDir, batch.getBatchCode(), "source");
            Files.createDirectories(sourceDir);
            String storedPdfFileName = System.currentTimeMillis() + "_" + fileName;
            Path sourcePdfPath = sourceDir.resolve(storedPdfFileName);
            Files.write(sourcePdfPath, file.getBytes());

            // 2. Count pages
            int totalPages = pdfPageService.getPageCount(sourcePdfPath);
            if (totalPages <= 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "PDF contains no pages: " + fileName);
            }

            // 3. Sequential doc code generation
            String brCode = batch.getBranch() != null && batch.getBranch().getBranchCode() != null
                    ? batch.getBranch().getBranchCode().replaceAll("[^A-Z0-9]", "") : "XX";
            String prefix = "HIST-" + batch.getSourceYear() + "-" + brCode;
            int baseSeq = documentRepository.findMaxSequenceForPrefix(prefix, prefix.length());

            // 4. Render and register each page as an independent MigrationDocument
            for (int p = 1; p <= totalPages; p++) {
                String docCode = prefix + "-" + String.format("%06d", baseSeq + p);
                Path pageDir = Paths.get(uploadDir, batch.getBatchCode(), docCode);
                Files.createDirectories(pageDir);
                Path renderedImagePath = pageDir.resolve(String.format("page_%03d.png", p));

                pdfPageService.renderSinglePage(sourcePdfPath, p, renderedImagePath);

                String pageDocName = String.format("%s (Page %d/%d)", fileName, p, totalPages);

                MigrationDocument doc = MigrationDocument.builder()
                        .batch(batch)
                        .documentCode(docCode)
                        .fileName(pageDocName)
                        .filePath(renderedImagePath.toAbsolutePath().toString())
                        .fileType(DocumentType.PDF)
                        .sourceYear(batch.getSourceYear())
                        .pageNumber(p)
                        .totalPages(totalPages)
                        .sourceFileName(fileName)
                        .sourceFilePath(sourcePdfPath.toAbsolutePath().toString())
                        .ocrStatus(MigrationDocumentStatus.UPLOADED.name())
                        .extractionStatus(MigrationDocumentStatus.UPLOADED.name())
                        .reviewStatus(MigrationDocumentStatus.UPLOADED.name())
                        .importStatus(MigrationDocumentStatus.UPLOADED.name())
                        .createdBy(currentEmployee())
                        .build();

                doc = documentRepository.save(doc);
                batch.incrementUploaded();
                createdDocs.add(doc);
                log.info("Registered PDF page {}/{} as document {} -> {}", p, totalPages, docCode, renderedImagePath);
            }

            batchRepository.save(batch);
        } else {
            // Single image
            String docCode  = generateDocumentCode(batch);
            Path dir = Paths.get(uploadDir, batch.getBatchCode(), docCode);
            Files.createDirectories(dir);
            Path storedPath = dir.resolve(fileName);
            Files.write(storedPath, file.getBytes());

            // Measurement slips are landscape forms — if image was uploaded in portrait (h > w), rotate 270 deg to landscape
            try {
                java.awt.image.BufferedImage uploadedImg = javax.imageio.ImageIO.read(storedPath.toFile());
                if (uploadedImg != null && uploadedImg.getHeight() > uploadedImg.getWidth()) {
                    ocrService.rotateImageOnDisk(storedPath, 270);
                    log.info("Auto-rotated uploaded portrait image {} to landscape", fileName);
                }
            } catch (Exception ex) {
                log.warn("Could not check/orient uploaded image {}: {}", fileName, ex.getMessage());
            }

            MigrationDocument doc = MigrationDocument.builder()
                    .batch(batch)
                    .documentCode(docCode)
                    .fileName(fileName)
                    .filePath(storedPath.toAbsolutePath().toString())
                    .fileType(DocumentType.IMAGE)
                    .sourceYear(batch.getSourceYear())
                    .pageNumber(1)
                    .totalPages(1)
                    .sourceFileName(fileName)
                    .sourceFilePath(storedPath.toAbsolutePath().toString())
                    .ocrStatus(MigrationDocumentStatus.UPLOADED.name())
                    .extractionStatus(MigrationDocumentStatus.UPLOADED.name())
                    .reviewStatus(MigrationDocumentStatus.UPLOADED.name())
                    .importStatus(MigrationDocumentStatus.UPLOADED.name())
                    .createdBy(currentEmployee())
                    .build();

            doc = documentRepository.save(doc);
            batch.incrementUploaded();
            batchRepository.save(batch);
            createdDocs.add(doc);
            log.info("Uploaded image document {} -> {}", docCode, storedPath);
        }

        return createdDocs;
    }

    @Transactional
    public MigrationDocument uploadDocument(Long batchId, MultipartFile file) throws IOException {
        List<MigrationDocument> docs = uploadDocuments(batchId, file);
        return docs.isEmpty() ? null : docs.get(0);
    }

    public List<MigrationDocument> getSiblingPages(Long documentId) {
        MigrationDocument doc = getDocument(documentId);
        if (doc.getTotalPages() == null || doc.getTotalPages() <= 1 || doc.getSourceFilePath() == null) {
            return List.of(doc);
        }
        return documentRepository.findByBatchIdAndSourceFilePathOrderByPageNumberAsc(
                doc.getBatch().getId(), doc.getSourceFilePath());
    }

    @Transactional
    public List<MigrationDocument> approveAllPages(Long documentId) {
        List<MigrationDocument> siblings = getSiblingPages(documentId);
        List<MigrationDocument> approved = new ArrayList<>();
        for (MigrationDocument s : siblings) {
            if (!MigrationDocumentStatus.VERIFIED.name().equals(s.getReviewStatus()) &&
                !MigrationDocumentStatus.IMPORTED.name().equals(s.getImportStatus())) {
                s.setReviewStatus(MigrationDocumentStatus.VERIFIED.name());
                if (s.getBatch() != null) {
                    s.getBatch().incrementApproved();
                    s.getBatch().incrementReviewed();
                }
                approved.add(documentRepository.save(s));
            }
        }
        return approved;
    }

    // ── OCR trigger ───────────────────────────────────────────────────────────

    @Transactional
    public MigrationDocument runOcr(Long documentId) {
        MigrationDocument doc = getDocument(documentId);

        boolean wasCompleted = MigrationDocumentStatus.OCR_COMPLETED.name().equals(doc.getOcrStatus());
        boolean wasFailed = MigrationDocumentStatus.OCR_FAILED.name().equals(doc.getOcrStatus());

        doc.setOcrStatus(MigrationDocumentStatus.OCR_PROCESSING.name());
        documentRepository.save(doc);

        OcrService.OcrResult result = ocrService.processDocument(
                Paths.get(doc.getFilePath()), ocrLanguage);

        if (result.success()) {
            doc.setRawOcrText(result.rawText());
            doc.setOcrConfidence(java.math.BigDecimal.valueOf(result.confidence()));
            doc.setOcrStatus(MigrationDocumentStatus.OCR_COMPLETED.name());
            doc.setErrorMessage(null);
            if (result.processedImagePath() != null && !result.processedImagePath().isBlank()) {
                doc.setOcrProcessedImagePath(result.processedImagePath());
            }
            if (!wasCompleted) {
                doc.getBatch().incrementProcessed();
            }
            log.info("OCR completed for {} ({} chars, conf={}%)",
                    doc.getDocumentCode(), result.rawText().length(), result.confidence());
            doc = documentRepository.save(doc);

            // Automatically run extraction with dynamic text-anchored wordBoxes and orientation
            doc = runExtraction(documentId, result.wordBoxes(), result.orientation());
            return doc;
        } else {
            doc.setOcrStatus(MigrationDocumentStatus.OCR_FAILED.name());
            doc.setErrorMessage(result.errorMessage());
            if (!wasFailed) {
                doc.getBatch().incrementFailed();
            }
            log.warn("OCR failed for {}: {}", doc.getDocumentCode(), result.errorMessage());
            return documentRepository.save(doc);
        }
    }

    /**
     * Re-scan a document: optionally rotate file on disk, run OCR, and re-run extraction.
     */
    @Transactional
    public MigrationDocument rescanDocument(Long documentId, int rotateAngle) {
        MigrationDocument doc = getDocument(documentId);

        int normAngle = ((rotateAngle % 360) + 360) % 360;
        if (normAngle != 0) {
            ocrService.rotateImageOnDisk(Paths.get(doc.getFilePath()), normAngle);
        } else {
            // Ensure document on disk is in landscape orientation (h > w -> rotate 270)
            try {
                Path fp = Paths.get(doc.getFilePath());
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(fp.toFile());
                if (img != null && img.getHeight() > img.getWidth()) {
                    ocrService.rotateImageOnDisk(fp, 270);
                    log.info("Rescan auto-rotated portrait image {} to landscape", doc.getDocumentCode());
                }
            } catch (Exception ex) {
                log.warn("Could not check/orient image on rescan for {}: {}", doc.getDocumentCode(), ex.getMessage());
            }
        }

        // runOcr will execute OCR and automatically runExtraction with wordBoxes
        return runOcr(documentId);
    }

    // ── Extraction trigger ────────────────────────────────────────────────────

    @Transactional
    public MigrationDocument runExtraction(Long documentId) {
        return runExtraction(documentId, Collections.emptyList(), null);
    }

    @Transactional
    public MigrationDocument runExtraction(Long documentId, List<OcrService.WordBox> wordBoxes, String orientationParam) {
        MigrationDocument doc = getDocument(documentId);

        if (doc.getRawOcrText() == null || doc.getRawOcrText().isBlank()) {
            throw new AppException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "Cannot extract: OCR has not been completed for this document");
        }

        String orientation = orientationParam;
        if (orientation == null || orientation.isBlank()) {
            orientation = "PORTRAIT";
            try {
                if (doc.getFilePath() != null) {
                    java.awt.image.BufferedImage bimg = javax.imageio.ImageIO.read(java.nio.file.Paths.get(doc.getFilePath()).toFile());
                    if (bimg != null && bimg.getWidth() > bimg.getHeight()) {
                        orientation = "LANDSCAPE";
                    }
                }
            } catch (Exception ignored) {}
        }

        ExtractedData data = extractionService.extract(doc.getRawOcrText(), wordBoxes != null ? wordBoxes : Collections.emptyList(), orientation);
        String json = extractionService.toJson(data);

        Short extractedYear = extractionService.extractYear(doc.getRawOcrText(),
                data.order() != null ? data.order().orderDate() : null);
        if (extractedYear != null) {
            doc.setSourceYear(extractedYear);
        }

        boolean hasMinimumData = data.customer().customerMobile() != null;
        doc.setExtractedDataJson(json);
        // Set percentage based on number of fields filled
        if (data.overallConfidence() != null && data.overallConfidence() > 0.0) {
            double pct = Math.round(data.overallConfidence() * 1000.0) / 10.0;
            doc.setOcrConfidence(java.math.BigDecimal.valueOf(pct));
        }
        doc.setExtractionStatus(MigrationDocumentStatus.EXTRACTION_COMPLETED.name());
        doc.setReviewStatus(MigrationDocumentStatus.REVIEW_REQUIRED.name());
        if (!hasMinimumData) {
            doc.setErrorMessage("Customer mobile number not detected — please verify during review");
        } else {
            doc.setErrorMessage(null);
        }

        return documentRepository.save(doc);
    }

    // ── Review ────────────────────────────────────────────────────────────────

    @Transactional
    public MigrationDocument saveReview(Long documentId, MigrationReviewRequest req) {
        MigrationDocument doc = getDocument(documentId);

        String status = switch (req.action().toUpperCase(Locale.ROOT)) {
            case "APPROVED", "VERIFIED" -> {
                // Enforce minimum 50% field completion before allowing approval
                String dataJson = req.correctedDataJson() != null && !req.correctedDataJson().isBlank()
                        ? req.correctedDataJson() : doc.effectiveDataJson();
                if (dataJson != null && !dataJson.isBlank()) {
                    ExtractedData data = extractionService.fromJson(dataJson);
                    double ratio = extractionService.calculateCompletionRatio(data);
                    if (ratio < 0.50) {
                        int pct = (int) Math.round(ratio * 100);
                        throw new AppException(ErrorCode.OPERATION_NOT_ALLOWED,
                                "Cannot process document: only " + pct + "% of fields are filled. At least 50% field completion is required before approval.");
                    }
                }
                doc.setCorrectedDataJson(req.correctedDataJson());
                doc.setReviewStatus(MigrationDocumentStatus.VERIFIED.name());
                doc.getBatch().incrementApproved();
                doc.getBatch().incrementReviewed();
                yield MigrationDocumentStatus.VERIFIED.name();
            }
            case "REJECTED" -> {
                doc.setReviewStatus(MigrationDocumentStatus.REJECTED.name());
                doc.getBatch().incrementFailed();
                doc.getBatch().incrementReviewed();
                yield MigrationDocumentStatus.REJECTED.name();
            }
            case "NEEDS_REWORK" -> {
                doc.setCorrectedDataJson(req.correctedDataJson());
                doc.setReviewStatus(MigrationDocumentStatus.REVIEW_REQUIRED.name());
                yield MigrationDocumentStatus.REVIEW_REQUIRED.name();
            }
            default -> throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Invalid review action: " + req.action());
        };

        MigrationReview review = MigrationReview.builder()
                .document(doc)
                .reviewer(currentEmployee())
                .reviewStatus(status)
                .correctedDataJson(req.correctedDataJson())
                .remarks(req.remarks())
                .build();
        reviewRepository.save(review);

        return documentRepository.save(doc);
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    public MigrationStatisticsResponse getStatistics() {
        List<MigrationBatch> batches = listBatches(Pageable.unpaged()).getContent();

        long totalDocs = batches.stream().mapToLong(b -> b.getTotalDocuments() != null ? b.getTotalDocuments() : 0).sum();
        long ocrDone   = batches.stream().mapToLong(b ->
                documentRepository.countByBatchIdAndOcrStatus(b.getId(),
                        MigrationDocumentStatus.OCR_COMPLETED.name())).sum();
        long reviewReq = batches.stream().mapToLong(b ->
                documentRepository.countByBatchIdNeedsReview(b.getId())).sum();
        long verified  = batches.stream().mapToLong(b ->
                documentRepository.countByBatchIdVerified(b.getId())).sum();
        long imported  = batches.stream().mapToLong(b ->
                documentRepository.countByBatchIdImported(b.getId())).sum();
        long failed    = batches.stream().mapToLong(b -> b.getFailedCount() != null ? b.getFailedCount() : 0L).sum();

        List<MigrationStatisticsResponse.BatchStat> stats = batches.stream()
                .map(b -> {
                    int bReview   = (int) documentRepository.countByBatchIdNeedsReview(b.getId());
                    int bVerified = (int) documentRepository.countByBatchIdVerified(b.getId());
                    int bImported = (int) documentRepository.countByBatchIdImported(b.getId());
                    int bTotal    = b.getTotalDocuments() != null && b.getTotalDocuments() > 0
                            ? b.getTotalDocuments()
                            : (bReview + bVerified + bImported);
                    return new MigrationStatisticsResponse.BatchStat(
                            b.getId(), b.getBatchCode(), b.getSourceYear(),
                            b.getBranch() != null ? b.getBranch().getName() : "Unknown",
                            bTotal, bReview, bVerified, bImported, b.getProgress(), b.getStatus());
                })
                .toList();

        return new MigrationStatisticsResponse(
                totalDocs, ocrDone, reviewReq, verified, imported, 0L, failed, 0L, stats);
    }

    // ── Lookups ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MigrationDocument getDocument(Long id) {
        MigrationDocument doc = documentRepository.findByIdWithDetails(id)
                .orElseGet(() -> documentRepository.findById(id)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                                "Migration document not found: " + id)));
        verifyBranchAccess(doc.getBatch());
        return doc;
    }

    @Transactional(readOnly = true)
    public Page<MigrationDocument> listDocuments(Long batchId, String reviewStatus, Pageable pageable) {
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            String s = reviewStatus.trim().toUpperCase(Locale.ROOT);
            if ("REVIEW_REQUIRED".equals(s) || "NEEDS_REVIEW".equals(s)) {
                return documentRepository.findByBatchIdNeedsReviewWithDetails(batchId, pageable);
            }
            if ("VERIFIED".equals(s)) {
                return documentRepository.findByBatchIdVerifiedWithDetails(batchId, pageable);
            }
            if ("IMPORTED".equals(s)) {
                return documentRepository.findByBatchIdImportedWithDetails(batchId, pageable);
            }
            return documentRepository.findByBatchIdAndReviewStatusWithDetails(batchId, reviewStatus, pageable);
        }
        return documentRepository.findByBatchIdWithDetails(batchId, pageable);
    }

    public List<MigrationReview> getReviewHistory(Long documentId) {
        getDocument(documentId); // access check
        return reviewRepository.findByDocumentIdOrderByReviewedAtDesc(documentId);
    }

    public CustomerMatchingService.MatchResult getMatches(Long documentId) {
        MigrationDocument doc = getDocument(documentId);
        String dataJson = doc.effectiveDataJson();
        ExtractedData data = (dataJson != null && !dataJson.isBlank())
                ? extractionService.fromJson(dataJson)
                : null;
        if (data == null) {
            return new CustomerMatchingService.MatchResult(
                    CustomerMatchingService.MatchType.NO_MATCH, List.of());
        }
        return matchingService.findMatches(
                data.customer().customerMobile(),
                data.customer().customerName(),
                data.order() != null ? data.order().garmentType() : null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void verifyBranchAccess(MigrationBatch batch) {
        Long callerBranch = BranchContext.getBranchId();
        if (callerBranch != null && batch.getBranch() != null
                && !callerBranch.equals(batch.getBranch().getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED,
                    "You do not have access to this migration batch");
        }
    }

    private String generateBatchCode(Branch branch, Short year) {
        String brCode = branch.getBranchCode() != null
                ? branch.getBranchCode().replaceAll("[^A-Z0-9]", "") : "XX";
        String prefix = "BATCH-" + year + "-" + brCode;
        int seq = batchRepository.findMaxSequenceForPrefix(prefix, prefix.length()) + 1;
        return prefix + "-" + String.format("%03d", seq);
    }

    private String generateDocumentCode(MigrationBatch batch) {
        String brCode = batch.getBranch() != null && batch.getBranch().getBranchCode() != null
                ? batch.getBranch().getBranchCode().replaceAll("[^A-Z0-9]", "") : "XX";
        String prefix = "HIST-" + batch.getSourceYear() + "-" + brCode;
        int seq = documentRepository.findMaxSequenceForPrefix(prefix, prefix.length()) + 1;
        return prefix + "-" + String.format("%06d", seq);
    }

    private void validateFileType(String ext) {
        if (!List.of("jpg", "jpeg", "png", "tiff", "tif", "bmp", "pdf").contains(ext)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Unsupported file type: " + ext + ". Allowed: jpg, jpeg, png, tiff, pdf");
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String sanitizeFileName(String raw) {
        if (raw == null) return "upload.jpg";
        return raw.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private com.ritham.erp.module.employee.entity.Employee currentEmployee() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getEmployee();
        }
        return null;
    }

    // ── Deletion & Cleanup ─────────────────────────────────────────────────────

    @Transactional
    public void deleteDocument(Long id) {
        MigrationDocument doc = documentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Document not found: " + id));
        verifyBranchAccess(doc.getBatch());

        // 1. Delete associated import record if present
        importRepository.findByDocumentId(id).ifPresent(importRepository::delete);

        // 2. Delete associated reviews
        reviewRepository.deleteAll(reviewRepository.findByDocumentIdOrderByReviewedAtDesc(id));

        // 3. Delete physical file on disk
        if (doc.getFilePath() != null) {
            try {
                Path p = Paths.get(doc.getFilePath());
                Files.deleteIfExists(p);
                if (p.getParent() != null) {
                    Files.deleteIfExists(p.getParent());
                }
            } catch (Exception e) {
                log.warn("Could not delete file {}: {}", doc.getFilePath(), e.getMessage());
            }
        }

        // 4. Update batch statistics
        MigrationBatch batch = doc.getBatch();
        if (batch != null) {
            batch.setTotalDocuments(Math.max(0, (batch.getTotalDocuments() != null ? batch.getTotalDocuments() : 1) - 1));
            batch.setUploadedCount(Math.max(0, (batch.getUploadedCount() != null ? batch.getUploadedCount() : 1) - 1));
            if (!MigrationDocumentStatus.UPLOADED.name().equals(doc.getOcrStatus())) {
                batch.setProcessedCount(Math.max(0, (batch.getProcessedCount() != null ? batch.getProcessedCount() : 1) - 1));
            }
            if (MigrationDocumentStatus.REVIEW_REQUIRED.name().equals(doc.getReviewStatus())) {
                batch.setReviewedCount(Math.max(0, (batch.getReviewedCount() != null ? batch.getReviewedCount() : 1) - 1));
            }
            if (MigrationDocumentStatus.VERIFIED.name().equals(doc.getReviewStatus())) {
                batch.setApprovedCount(Math.max(0, (batch.getApprovedCount() != null ? batch.getApprovedCount() : 1) - 1));
            }
            if (MigrationDocumentStatus.IMPORTED.name().equals(doc.getImportStatus())) {
                batch.setImportedCount(Math.max(0, (batch.getImportedCount() != null ? batch.getImportedCount() : 1) - 1));
            }
            batchRepository.save(batch);
        }

        // 5. Delete document
        documentRepository.delete(doc);
        log.info("Deleted migration document {}", doc.getDocumentCode());
    }

    @Transactional
    public void deleteBatch(Long batchId) {
        MigrationBatch batch = getBatch(batchId);
        verifyBranchAccess(batch);

        List<MigrationDocument> docs = documentRepository.findByBatchId(batchId, Pageable.unpaged()).getContent();
        for (MigrationDocument doc : docs) {
            importRepository.findByDocumentId(doc.getId()).ifPresent(importRepository::delete);
            reviewRepository.deleteAll(reviewRepository.findByDocumentIdOrderByReviewedAtDesc(doc.getId()));
            if (doc.getFilePath() != null) {
                try {
                    Path p = Paths.get(doc.getFilePath());
                    Files.deleteIfExists(p);
                    if (p.getParent() != null) {
                        Files.deleteIfExists(p.getParent());
                    }
                } catch (Exception e) {
                    log.warn("Could not delete file {}: {}", doc.getFilePath(), e.getMessage());
                }
            }
        }

        // Remove batch directory on disk
        try {
            Path batchDir = Paths.get(uploadDir, batch.getBatchCode());
            if (Files.exists(batchDir)) {
                try (var stream = Files.walk(batchDir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                            });
                }
            }
        } catch (Exception e) {
            log.warn("Could not delete batch dir {}: {}", batch.getBatchCode(), e.getMessage());
        }

        documentRepository.deleteAll(docs);
        batchRepository.delete(batch);
        log.info("Deleted migration batch {}", batch.getBatchCode());
    }

    @Transactional
    public void clearAllMigrationData() {
        log.warn("Admin triggered clearAllMigrationData - removing all migration records and files");

        // 1. Delete all import logs
        importRepository.deleteAll();

        // 2. Delete all reviews
        reviewRepository.deleteAll();

        // 3. Delete all documents
        documentRepository.deleteAll();

        // 4. Delete all batches
        batchRepository.deleteAll();

        // 5. Delete physical upload directory contents
        try {
            Path baseDir = Paths.get(uploadDir);
            if (Files.exists(baseDir)) {
                try (var stream = Files.walk(baseDir)) {
                    stream.filter(p -> !p.equals(baseDir))
                            .sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try { Files.deleteIfExists(path); } catch (Exception ignored) {}
                            });
                }
            }
        } catch (Exception e) {
            log.warn("Could not clear upload directory {}: {}", uploadDir, e.getMessage());
        }

        log.info("All migration and OCR data successfully cleared");
    }
}
