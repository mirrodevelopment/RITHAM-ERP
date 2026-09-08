-- ============================================================================
-- V14 - Add Multi-Page PDF columns to migration_documents
-- ============================================================================
-- Enables every page of a multi-page PDF to be processed, OCR'd, reviewed,
-- and imported as an independent historical measurement document.
-- Single images default to page_number=1, total_pages=1.
-- ============================================================================

ALTER TABLE migration_documents ADD COLUMN IF NOT EXISTS page_number INTEGER NOT NULL DEFAULT 1;
ALTER TABLE migration_documents ADD COLUMN IF NOT EXISTS total_pages INTEGER NOT NULL DEFAULT 1;
ALTER TABLE migration_documents ADD COLUMN IF NOT EXISTS source_file_name VARCHAR(255);
ALTER TABLE migration_documents ADD COLUMN IF NOT EXISTS source_file_path VARCHAR(1000);

CREATE INDEX IF NOT EXISTS idx_migration_documents_source_file ON migration_documents(source_file_name);
CREATE INDEX IF NOT EXISTS idx_migration_documents_page_num ON migration_documents(page_number);
CREATE INDEX IF NOT EXISTS idx_migration_documents_batch_page ON migration_documents(batch_id, page_number);
