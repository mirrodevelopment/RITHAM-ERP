package com.ritham.erp.module.migration.enums;

/** Supported upload file types for migration documents. */
public enum DocumentType {
    /** JPEG, PNG, TIFF, BMP — processed directly by Tesseract. */
    IMAGE,
    /** PDF — first page rendered to image via PDFBox, then OCR'd. */
    PDF
}
