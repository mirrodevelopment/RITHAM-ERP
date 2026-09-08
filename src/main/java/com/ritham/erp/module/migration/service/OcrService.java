package com.ritham.erp.module.migration.service;

import java.nio.file.Path;

/**
 * Contract for an OCR engine.
 *
 * <p>The abstraction allows swapping the implementation from local Tesseract to
 * Azure Computer Vision or Google Vision without touching any other service.
 *
 * <p>Implementations must be thread-safe.
 */
public interface OcrService {

    /**
     * Process a single document (image or PDF first-page) and return the OCR result.
     *
     * @param documentPath absolute path to the file on disk
     * @param language     Tesseract language code, e.g. "eng" or "eng+tam"
     * @return the OCR result (never null; rawText may be empty string on failure)
     */
    OcrResult processDocument(Path documentPath, String language);

    /**
     * Optional helper to rotate an image file on disk by specified degrees.
     *
     * @param documentPath absolute path to the file on disk
     * @param angle        degrees to rotate (e.g. 90, 180, 270)
     */
    default void rotateImageOnDisk(Path documentPath, int angle) {
        // Default no-op
    }

    /** Value object for a recognized word bounding box normalized between 0.0 and 1.0. */
    record WordBox(
            String text,
            double x,
            double y,
            double width,
            double height,
            float confidence
    ) {}

    /** Value object returned by the OCR engine. */
    record OcrResult(
            /** Raw text extracted from the document. */
            String rawText,
            /** Average confidence across all recognized words (0.0 – 100.0). */
            double confidence,
            /** Number of pages processed (1 for images). */
            int pageCount,
            /** Wall-clock time taken in milliseconds. */
            long processingMs,
            /** True if OCR completed without a fatal error. */
            boolean success,
            /** Error description if success = false. */
            String errorMessage,
            /** Detected orientation: PORTRAIT or LANDSCAPE */
            String orientation,
            /** Image width in pixels */
            int imageWidth,
            /** Image height in pixels */
            int imageHeight,
            /** Word-level bounding boxes (coordinates normalized 0.0 to 1.0) */
            java.util.List<WordBox> wordBoxes,
            /** Path to the preprocessed (grayscale + contrast-enhanced) image used for OCR.
             *  Null for PDF OCR paths where the image is temporary. */
            String processedImagePath
    ) {
        public OcrResult(String rawText, double confidence, int pageCount, long processingMs, boolean success, String errorMessage) {
            this(rawText, confidence, pageCount, processingMs, success, errorMessage, "PORTRAIT", 0, 0, java.util.Collections.emptyList(), null);
        }

        public static OcrResult failure(String error) {
            return new OcrResult("", 0.0, 0, 0L, false, error, "PORTRAIT", 0, 0, java.util.Collections.emptyList(), null);
        }
    }
}
