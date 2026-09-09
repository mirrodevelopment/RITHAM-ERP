package com.ritham.erp.module.migration.service.ocr;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * Extracts targeted physical sub-regions from the oriented measurement sheet.
 * Using normalized [0.0 - 1.0] coordinates ensures stability across different camera resolutions
 * (500x700, 1000x1400, 2000x2800, phone cameras, scanner PDFs).
 */
@Component
public class RegionExtractor {

    public record NormalizedRect(double x, double y, double width, double height) {}

    public record RegionCrop(
            BufferedImage croppedImage,
            NormalizedRect bounds
    ) {}

    // Physical form layout boundaries calibrated to Ritham Garments slips
    public static final NormalizedRect TOP_LEFT_REGION    = new NormalizedRect(0.00, 0.00, 0.45, 0.25);
    public static final NormalizedRect TOP_CENTER_REGION  = new NormalizedRect(0.28, 0.00, 0.42, 0.22);
    public static final NormalizedRect TOP_RIGHT_REGION   = new NormalizedRect(0.55, 0.00, 0.45, 0.25);
    public static final NormalizedRect MEASUREMENT_REGION = new NormalizedRect(0.00, 0.18, 0.50, 0.82);

    /**
     * Crops a sub-image corresponding to the given normalized rectangle.
     */
    public RegionCrop crop(BufferedImage fullImage, NormalizedRect rect) {
        if (fullImage == null || rect == null) return null;
        int imgW = fullImage.getWidth();
        int imgH = fullImage.getHeight();

        int x = (int) Math.max(0, Math.min(imgW - 1, Math.round(rect.x() * imgW)));
        int y = (int) Math.max(0, Math.min(imgH - 1, Math.round(rect.y() * imgH)));
        int w = (int) Math.max(1, Math.min(imgW - x, Math.round(rect.width() * imgW)));
        int h = (int) Math.max(1, Math.min(imgH - y, Math.round(rect.height() * imgH)));

        BufferedImage sub = fullImage.getSubimage(x, y, w, h);
        return new RegionCrop(sub, rect);
    }

    public RegionCrop cropTopLeft(BufferedImage image) {
        return crop(image, TOP_LEFT_REGION);
    }

    public RegionCrop cropTopCenter(BufferedImage image) {
        return crop(image, TOP_CENTER_REGION);
    }

    public RegionCrop cropTopRight(BufferedImage image) {
        return crop(image, TOP_RIGHT_REGION);
    }

    public RegionCrop cropMeasurements(BufferedImage image) {
        return crop(image, MEASUREMENT_REGION);
    }
}
