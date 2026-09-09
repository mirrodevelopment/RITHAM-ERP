-- ============================================================================
-- V18 — Add OCR Version and Preprocessing Metadata to migration_documents
-- ============================================================================
-- Adds metadata columns to track the OCR engine version, form template version,
-- and preprocessing pipeline version applied to each migration document.
-- Compatible with PostgreSQL and H2.
-- ============================================================================

ALTER TABLE migration_documents ADD COLUMN IF NOT EXISTS ocr_version VARCHAR(20) DEFAULT '2.0';
ALTER TABLE migration_documents ADD COLUMN IF NOT EXISTS template_version VARCHAR(20) DEFAULT '2.0';
ALTER TABLE migration_documents ADD COLUMN IF NOT EXISTS preprocessing_version VARCHAR(20) DEFAULT '2.0';

