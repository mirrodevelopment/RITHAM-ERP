package com.ritham.erp.module.migration.enums;

/**
 * Complete lifecycle of a single migration document.
 *
 * <pre>
 * Normal path:
 *   UPLOADED -> OCR_PROCESSING -> OCR_COMPLETED
 *           -> EXTRACTION_COMPLETED -> REVIEW_REQUIRED
 *           -> VERIFIED -> IMPORTING -> IMPORTED
 *
 * Failure / terminal states:
 *   OCR_FAILED, EXTRACTION_FAILED, REJECTED, IMPORT_FAILED, DUPLICATE
 * </pre>
 */
public enum MigrationDocumentStatus {

    // ── Upload ────────────────────────────────────────────────────────────────
    /** File received and stored; no OCR yet. */
    UPLOADED,

    // ── OCR ───────────────────────────────────────────────────────────────────
    /** Tesseract is actively processing the image. */
    OCR_PROCESSING,
    /** OCR raw text successfully produced. */
    OCR_COMPLETED,
    /** OCR engine could not read the document. */
    OCR_FAILED,

    // ── Extraction ────────────────────────────────────────────────────────────
    /** Structured fields extracted from raw OCR text. */
    EXTRACTION_COMPLETED,
    /** Could not extract minimum required fields (mobile / name). */
    EXTRACTION_FAILED,

    // ── Review ────────────────────────────────────────────────────────────────
    /** Awaiting a human reviewer. */
    REVIEW_REQUIRED,
    /** Reviewer corrected data; all required fields confirmed. */
    VERIFIED,
    /** Reviewer explicitly rejected this document (illegible / wrong paper). */
    REJECTED,

    // ── Import ────────────────────────────────────────────────────────────────
    /** Import transaction started. */
    IMPORTING,
    /** Data successfully written to production ERP tables. */
    IMPORTED,
    /** Production write failed; document remains in staging. */
    IMPORT_FAILED,
    /** An identical customer + order combination already exists in ERP. */
    DUPLICATE;

    public boolean isTerminal() {
        return this == IMPORTED || this == REJECTED || this == DUPLICATE;
    }

    public boolean isFailure() {
        return this == OCR_FAILED || this == EXTRACTION_FAILED
                || this == IMPORT_FAILED || this == REJECTED || this == DUPLICATE;
    }
}
