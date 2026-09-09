package com.ritham.erp.module.migration.service.ocr;

import com.ritham.erp.module.migration.service.OcrService.WordBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.regex.Pattern;

/**
 * Detects the garment template type (BLOUSE or CHUDI) strictly from the TOP-CENTER of the form.
 * Does NOT guess or determine garment type from measurement rows.
 * If confidence is low, flags needsReview = true.
 */
@Component
@Slf4j
public class FormTemplateDetector {

    public record GarmentTypeResult(
            String garmentType,
            double confidence,
            WordBox boundingBox
    ) {}

    private static final Pattern BLOUSE_PATTERN =
            Pattern.compile("(?i)\\b(?:BLOUSE|BLOSE|BLOUS|8LOUSE)\\b");

    private static final Pattern CHUDI_PATTERN =
            Pattern.compile("(?i)\\b(?:CHU[RR]?[IL1l][DdD][AI1l][AR]?|CHU[DdD][I1l]|CHUDHY|CHUDHI|CHUOI|CHUD)\\b");

    /**
     * Evaluates top-center cropped region to detect garment heading.
     */
    public GarmentTypeResult detect(BufferedImage topCenterCrop, RegionExtractor.NormalizedRect cropBounds, OcrEngine ocrEngine) {
        if (topCenterCrop == null) {
            return new GarmentTypeResult("NEEDS_REVIEW", 0.0, null);
        }

        // Run OCR on top-center crop (PSM 6 or 11)
        OcrEngine.OcrEngineResult result = ocrEngine.ocrRegion(
                topCenterCrop, 0.0, 0.0, 1.0, 1.0, "eng", net.sourceforge.tess4j.ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT);

        String text = result.text();
        log.info("Top-center OCR text: '{}'", text.replace("\n", " "));

        // Check word boxes in top center
        WordBox matchedBox = null;
        for (WordBox wb : result.wordBoxes()) {
            if (BLOUSE_PATTERN.matcher(wb.text()).find()) {
                double gx = cropBounds.x() + wb.x() * cropBounds.width();
                double gy = cropBounds.y() + wb.y() * cropBounds.height();
                double gw = wb.width() * cropBounds.width();
                double gh = wb.height() * cropBounds.height();
                matchedBox = new WordBox("BLOUSE", gx, gy, gw, gh, wb.confidence());
                return new GarmentTypeResult("BLOUSE", 0.99, matchedBox);
            }
            if (CHUDI_PATTERN.matcher(wb.text()).find()) {
                double gx = cropBounds.x() + wb.x() * cropBounds.width();
                double gy = cropBounds.y() + wb.y() * cropBounds.height();
                double gw = wb.width() * cropBounds.width();
                double gh = wb.height() * cropBounds.height();
                matchedBox = new WordBox("CHUDI", gx, gy, gw, gh, wb.confidence());
                return new GarmentTypeResult("CHUDI", 0.99, matchedBox);
            }
        }

        // Fallback: check full text of crop
        if (text != null) {
            if (BLOUSE_PATTERN.matcher(text).find()) {
                matchedBox = new WordBox("BLOUSE", cropBounds.x(), cropBounds.y(), cropBounds.width(), cropBounds.height(), 90f);
                return new GarmentTypeResult("BLOUSE", 0.95, matchedBox);
            }
            if (CHUDI_PATTERN.matcher(text).find()) {
                matchedBox = new WordBox("CHUDI", cropBounds.x(), cropBounds.y(), cropBounds.width(), cropBounds.height(), 90f);
                return new GarmentTypeResult("CHUDI", 0.95, matchedBox);
            }
        }

        // Cannot confidently identify garment from top center
        log.warn("Garment type could not be confidently identified in top-center");
        return new GarmentTypeResult("NEEDS_REVIEW", 0.0, null);
    }
}
