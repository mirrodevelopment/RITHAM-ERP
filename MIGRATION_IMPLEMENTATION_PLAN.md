# Historical Data Migration Module — Implementation Plan

## Goal

Add a dedicated module/migration to RITHAM ERP that safely digitizes 4 years of paper
records through a pipeline: Upload -> OCR -> Extract -> Human Review -> Import into
existing ERP tables. OCR never writes directly to production tables.

---

## Phase 1 — Existing ERP Safety [DONE]

### Already completed:
- ObjectOptimisticLockingFailureException -> HTTP 409 with message:
  "Order was modified by another user. Please refresh and try again."
- ErrorCode.CONCURRENT_MODIFICATION added
- No existing tests broken

---

## Open Questions (please answer before Phase 2 begins)

Q1 - File Storage Path
Where should uploaded scanned images/PDFs be stored?
  Default assumption: configurable via app.migration.upload-dir in application.yaml
  e.g. D:\ritham-erp-uploads\migration\

Q2 - Tesseract Language
Are paper records in English only, or Tamil + English?
  Default assumption: eng only (measurements and amounts are numeric/English)

Q3 - Confidence Threshold for Auto-Review Flag
Below what percentage should a field be automatically flagged for human review?
  Recommendation: 85% for numeric fields, 75% for name fields

Q4 - Measurement Profile Overwrite
When importing a 2022 historical order, should the customer current measurement
profile (customer_measurements) be updated to those old values?
  Recommendation: NO - only the immutable order snapshot is created unless
  reviewer explicitly checks "Update current profile"

Q5 - Historical Payment Date
If paper says "Paid 5000" but no date is recorded, use source_year as approximate
year and set paymentDateKnown=false. Revenue reports can exclude/segregate these.
  Confirm this approach.

---

## Phase 2 — Database Schema

### V12__historical_data_migration.sql (new file)

Four new tables, no changes to existing tables:

migration_batches:
  id, batch_code (BATCH-YYYY-NNN), branch_id (FK),
  description, source_year,
  total_documents, uploaded_count, processed_count,
  reviewed_count, approved_count, imported_count, failed_count,
  created_by (FK employees), created_at, completed_at, status

migration_documents:
  id, batch_id (FK), document_code (HIST-YYYY-BR-NNNNNN),
  file_name, file_path, file_type (IMAGE/PDF), source_year,
  ocr_status, extraction_status, review_status, import_status,
  raw_ocr_text (TEXT), extracted_data_json (TEXT),
  ocr_confidence (DECIMAL 5,2), error_message,
  created_by (FK employees), created_at, updated_at

migration_reviews:
  id, document_id (FK), reviewer_id (FK employees),
  review_status, corrected_data_json (TEXT), remarks, reviewed_at

migration_imports:
  id, document_id (FK), customer_mobile (FK customers),
  order_id (FK customer_orders), measurement_id (FK order_measurements),
  import_status, error_message, imported_by (FK employees), imported_at

Document status lifecycle:
  UPLOADED -> OCR_PROCESSING -> OCR_COMPLETED -> EXTRACTION_COMPLETED
  -> REVIEW_REQUIRED -> VERIFIED -> IMPORTING -> IMPORTED
  Failure states: OCR_FAILED, EXTRACTION_FAILED, REJECTED, IMPORT_FAILED, DUPLICATE

---

## Phase 3 — Backend Entities, Repos, DTOs

Files to create under src/main/java/com/ritham/erp/module/migration/:

  enums/
    MigrationDocumentStatus.java
    DocumentType.java

  entity/
    MigrationBatch.java
    MigrationDocument.java
    MigrationReview.java
    MigrationImport.java

  repository/
    MigrationBatchRepository.java
    MigrationDocumentRepository.java
    MigrationReviewRepository.java
    MigrationImportRepository.java

  dto/
    CreateBatchRequest.java
    MigrationBatchResponse.java
    MigrationDocumentResponse.java
    ExtractedData.java (contains ExtractedCustomerData + ExtractedOrderData)
    MigrationReviewRequest.java
    MigrationStatisticsResponse.java

---

## Phase 4 — File Upload and Storage

Files to create/modify:

  [NEW] module/migration/service/MigrationFileService.java
    - Accept MultipartFile (JPEG, PNG, TIFF, PDF)
    - Validate type and size (max configurable, default 20 MB)
    - Store to {upload-dir}/{batchCode}/{documentCode}/{filename}
    - Generate unique document_code (HIST-{YYYY}-{BR_CODE}-{NNNNNN})

  [NEW] module/migration/service/MigrationBatchService.java
    - Batch CRUD

  [MODIFY] application.yaml
    Add:
      app.migration.upload-dir
      app.migration.max-file-size-mb
      app.migration.ocr-confidence-threshold

  [MODIFY] AppProperties.java
    Add migration nested config block

---

## Phase 5 — Local OCR (Tesseract via Tess4J)

  [MODIFY] pom.xml
    Add tess4j 5.11.0 + pdfbox 3.0.2

  [NEW] module/migration/service/OcrService.java (interface)
    OcrResult processDocument(Path documentPath, String languageHint)
    OcrResult: rawText, confidence, pageCount, processingMs

  [NEW] module/migration/service/TesseractOcrService.java
    Implements OcrService. PSM mode AUTO, language configurable.
    PDF -> image via PDFBox before OCR.

  [NEW] module/migration/service/MigrationOcrService.java
    Orchestrator: loads file, calls OcrService, saves raw_ocr_text,
    updates ocr_status, triggers ExtractionService on success.

---

## Phase 6 — Data Extraction

  [NEW] module/migration/service/ExtractionService.java

  Extraction field rules:
    customerName   - line after "Customer:" or "Name:"
    customerMobile - 10-digit number starting 6-9
    orderDate      - dd/MM/yyyy or dd-MM-yyyy
    deliveryDate   - line after "Delivery:"
    garmentType    - keyword: BLOUSE, CHUDI, SAREE, SALWAR, LEHENGA
    measurements   - KEY VALUE pairs (e.g. LTH 14, SHO 14.5)
    totalAmount    - line after "Total:" + number
    advanceAmount  - line after "Advance:" + number

  Each field carries confidence 0.0-1.0.
  Fields below threshold flagged as needsReview=true.
  Saves extracted_data_json, sets extraction_status.
  Sets review_status = REVIEW_REQUIRED.

---

## Phase 7 — Review Desk (Frontend)

  [NEW] frontend/desks/historical-data/historical-data.html
  [NEW] frontend/desks/historical-data/historical-data.js
  [NEW] frontend/desks/historical-data/historical-data.css

  Role guard: Auth.guard([ROLES.ADMIN, ROLES.OPERATIONS_MANAGER])
  Same script load pattern as all other desks.

  Three tabs:
    1. Dashboard - batch progress overview table
    2. Batch - document list with status filter
    3. Review - side-by-side:
       Left: original image rendered
       Right: editable fields with confidence indicators
         green >= 85%, amber 70-84%, red < 70%
       Customer match panel: [Use Existing] / [Create New]
       Buttons: [Save Draft] [Approve] [Reject]

  [MODIFY] frontend/js/constants.js
    Add migration API routes and ROUTES.HISTORICAL_DATA

  [MODIFY] navigation component
    Add "Historical Data" menu item (ADMIN, OPS_MANAGER only)

---

## Phase 8 — Import via Existing ERP Services

  [NEW] module/migration/service/CustomerMatchingService.java
    Matching priority:
      1. Exact mobile match -> return existing customer
      2. Name + mobile fuzzy -> show as candidate
      3. No match -> flag for reviewer to decide
    Never auto-creates customers without reviewer confirmation.

  [NEW] module/migration/service/MigrationImportService.java
    After reviewer approves:
      1. Customer: createOrGet via CustomerService
      2. Order: call OrderService.createHistoricalOrder()
         - source = HISTORICAL_MIGRATION
         - status set directly to DELIVERED/COMPLETED (no fake stage transitions)
         - branch isolation still enforced
      3. Measurements: save immutable OrderMeasurement snapshot
         If reviewer checked "Update profile" -> also update CustomerMeasurement
      4. Payments: create OrderPayment rows
         If date unknown -> paymentDateKnown = false
      5. Save MigrationImport audit row
      6. Update document import_status = IMPORTED

  [MODIFY] module/order/service/OrderService.java
    Add createHistoricalOrder(HistoricalOrderRequest req):
      - Skips normal validation and state machine
      - Direct status assignment
      - Branch isolation enforced via BranchContext

  [MODIFY] ErrorCode.java
    Add: MIGRATION_DOCUMENT_NOT_FOUND, MIGRATION_INVALID_TRANSITION,
         MIGRATION_ALREADY_IMPORTED, MIGRATION_CUSTOMER_CONFLICT,
         MIGRATION_IMPORT_ROLLBACK

---

## Phase 9 — Migration Controller

  [NEW] module/migration/controller/MigrationController.java

  All endpoints: @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS_MANAGER')")

  POST   /api/migration/batches
  GET    /api/migration/batches
  GET    /api/migration/batches/{id}
  POST   /api/migration/documents/upload
  GET    /api/migration/documents
  GET    /api/migration/documents/{id}
  POST   /api/migration/documents/{id}/ocr
  POST   /api/migration/documents/{id}/extract
  GET    /api/migration/documents/{id}/review
  PUT    /api/migration/documents/{id}/review
  POST   /api/migration/documents/{id}/approve
  POST   /api/migration/documents/{id}/reject
  GET    /api/migration/documents/{id}/matches
  POST   /api/migration/documents/{id}/import
  GET    /api/migration/imports
  GET    /api/migration/statistics

---

## Phase 10 — Test Suites

  [NEW] MigrationBatchServiceTest.java
  [NEW] MigrationOcrServiceTest.java
    - valid image, invalid image, empty image, PDF, unsupported format, low confidence
  [NEW] ExtractionServiceTest.java
    - BLOUSE, CHUDI, missing mobile, missing measurement, invalid date, invalid amount
  [NEW] CustomerMatchingServiceTest.java
    - exact mobile, same name, different mobile, no match, ambiguous match
  [NEW] MigrationImportServiceTest.java
    - verified to import, rejected cannot import, duplicate blocked,
      failed import rollback, branch isolation, no stage transitions
  [NEW] MigrationSecurityTest.java
    - ADMIN allowed, OPS_MANAGER allowed, RECEPTION denied (403),
      PRODUCTION denied (403), cross-branch import denied (403)

---

## Phase 11 — Pilot Run

  1. Upload 20-50 documents (clear + poor handwriting, both branches, multiple garment types)
  2. Trigger OCR on each
  3. Trigger extraction, check confidence and flagged fields
  4. Use Review Desk to manually correct at least 10 documents
  5. Approve and import 5 verified documents
  6. Verify in Order Desk: orders appear with correct customer, measurements, status
  7. Verify payment ledger
  8. Verify migration_imports audit rows link to ERP records
  9. Attempt cross-branch import as branch user -> confirm 403
  10. Attempt to re-import already-imported document -> confirm error

---

## Implementation Status

| Phase | Work                         | Status |
|-------|------------------------------|--------|
| 1     | Optimistic locking fix       | DONE   |
| 2     | V12 database schema          | NEXT   |
| 3     | Java entities, repos, DTOs   | PENDING |
| 4     | File upload and storage      | PENDING |
| 5     | Tesseract OCR (Tess4J)       | PENDING |
| 6     | Extraction and confidence    | PENDING |
| 7     | Review Desk frontend         | PENDING |
| 8     | Import via existing services | PENDING |
| 9     | Migration Controller         | PENDING |
| 10    | Test suites                  | PENDING |
| 11    | Pilot: 20-50 documents       | PENDING |
