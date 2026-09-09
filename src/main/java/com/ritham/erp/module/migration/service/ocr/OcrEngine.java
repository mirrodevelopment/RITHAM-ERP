package com.ritham.erp.module.migration.service.ocr;

import com.ritham.erp.module.migration.service.OcrService.WordBox;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * Abstraction for low-level OCR engine operations.
 * Isolates underlying optical character recognition libraries (e.g. Tesseract)
 * so that alternative engines can be plugged in seamlessly without modifying extraction logic.
 */
public interface OcrEngine {

    /**
     * Run OCR on the full buffered image with default page segmentation.
     */
    OcrEngineResult ocrImage(BufferedImage image, String language);

    /**
     * Run OCR on a cropped sub-rectangle using normalized [0.0 - 1.0] coordinates.
     */
    OcrEngineResult ocrRegion(BufferedImage image, double nx, double ny, double nw, double nh, String language, int psm);

    /**
     * Run OCR on a single text line or word with specified PSM (e.g. PSM 7 or PSM 8).
     */
    String ocrText(BufferedImage image, int psm);

    /**
     * Run OCR on a numeric region (e.g. measurement cell) restricting recognition to digits.
     */
    String ocrDigits(BufferedImage image);

    /**
     * Extract recognized word boxes with bounding coordinates normalized to [0.0 - 1.0].
     */
    List<WordBox> extractWordBoxes(BufferedImage image);

    /**
     * Optional helper to rotate an image on disk by specified angle.
     */
    void rotateImageOnDisk(Path imagePath, int angle);

    /**
     * Value record for engine execution output.
     */
    record OcrEngineResult(
            String text,
            double confidence,
            List<WordBox> wordBoxes,
            int width,
            int height
    ) {}
}
