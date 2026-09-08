-- ============================================================================
-- V17 — Add OCR Processed Image Path to migration_documents
-- ============================================================================
-- Adds a separate column to store the path of the contrast-enhanced,
-- orientation-corrected image that was fed to Tesseract.
--
-- The original uploaded file path is preserved in file_path (never overwritten).
-- The processed/enhanced image path is stored in ocr_processed_image_path.
--
-- This allows the Review UI to offer:
--   "View Original"          → served from file_path
--   "View OCR Processed"     → served from ocr_processed_image_path
--
-- Compatible with PostgreSQL and H2 (IF NOT EXISTS guards make it idempotent).
-- ============================================================================

ALTER TABLE migration_documents
    ADD COLUMN IF NOT EXISTS ocr_processed_image_path VARCHAR(1000);

COMMENT ON COLUMN migration_documents.ocr_processed_image_path
    IS 'Path to the preprocessed (grayscale, contrast-enhanced, oriented) image used for OCR. '
       'The original upload is always preserved in file_path.';
