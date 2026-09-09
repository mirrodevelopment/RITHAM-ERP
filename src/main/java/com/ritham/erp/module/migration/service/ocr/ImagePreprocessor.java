package com.ritham.erp.module.migration.service.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Image preprocessing pipeline:
 * - Detects paper boundaries on wooden desk / background and crops document slip
 * - Grayscale conversion
 * - Contrast stretching for clear handwriting and printed labels
 * - Writes enhanced debug image to disk without modifying original
 */
@Component
@Slf4j
public class ImagePreprocessor {

    public record PreprocessedResult(
            BufferedImage processedImage,
            Path processedImagePath
    ) {}

    /**
     * Preprocesses an image for OCR and persists the preprocessed result alongside original.
     */
    public PreprocessedResult preprocess(BufferedImage original, Path originalPath) throws IOException {
        if (original == null) {
            throw new IllegalArgumentException("Original image cannot be null");
        }

        // 1. Crop to paper slip boundaries if dark border exists
        BufferedImage cropped = autoCropPaperBoundary(original);

        // 2. Grayscale & contrast enhancement
        BufferedImage enhanced = enhanceContrastAndGrayscale(cropped);

        // 3. Save preprocessed image for review desk and debugging
        Path processedPath = null;
        if (originalPath != null && originalPath.getParent() != null) {
            String fn = originalPath.getFileName().toString();
            String ext = fn.toLowerCase(Locale.ROOT).endsWith(".jpg") ||
                    fn.toLowerCase(Locale.ROOT).endsWith(".jpeg") ? "jpg" : "png";
            processedPath = originalPath.getParent().resolve("ocr_processed_" + fn.replaceAll("\\.(png|jpg|jpeg)$", "." + ext));
            ImageIO.write(enhanced, ext, processedPath.toFile());
            log.info("Saved OCR-preprocessed image to {}", processedPath.getFileName());
        }

        return new PreprocessedResult(enhanced, processedPath);
    }

    /**
     * Detects bright rectangular paper slip boundary against darker background (e.g. wooden table).
     */
    public BufferedImage autoCropPaperBoundary(BufferedImage image) {
        if (image == null) return null;
        int imgW = image.getWidth();
        int imgH = image.getHeight();

        int minX = imgW, maxX = 0, minY = imgH, maxY = 0;
        int step = Math.max(2, Math.min(imgW, imgH) / 400);

        for (int y = 0; y < imgH; y += step) {
            for (int x = 0; x < imgW; x += step) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                // Threshold: paper is brighter than dark wood (handles slightly aged/yellowed paper)
                if (r > 88 && g > 88 && b > 88) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        int pw = maxX - minX;
        int ph = maxY - minY;

        // If a clear sub-rectangle of at least 25% area was found with margin on border, crop it
        if (pw > 200 && ph > 200 &&
                (minX > 15 || minY > 15 || maxX < imgW - 15 || maxY < imgH - 15) &&
                ((double) (pw * ph) / (imgW * imgH) >= 0.25)) {

            int pad = 8;
            int cx = Math.max(0, minX - pad);
            int cy = Math.max(0, minY - pad);
            int cw = Math.min(imgW - cx, pw + pad * 2);
            int ch = Math.min(imgH - cy, ph + pad * 2);

            log.info("Cropped document paper slip from [{}, {}] to [{}, {}]", minX, minY, maxX, maxY);
            return image.getSubimage(cx, cy, cw, ch);
        }

        return image;
    }

    /**
     * Converts to grayscale and applies linear min-max contrast stretching.
     */
    public BufferedImage enhanceContrastAndGrayscale(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);

        int minLum = 255;
        int maxLum = 0;

        for (int y = 0; y < h; y += 4) {
            for (int x = 0; x < w; x += 4) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int lum = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                if (lum < minLum) minLum = lum;
                if (lum > maxLum) maxLum = lum;
            }
        }

        if (maxLum <= minLum) {
            maxLum = 255;
            minLum = 0;
        }

        double scale = 255.0 / (maxLum - minLum);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int lum = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                int stretched = Math.min(255, Math.max(0, (int) ((lum - minLum) * scale)));
                int grayRgb = (stretched << 16) | (stretched << 8) | stretched;
                gray.setRGB(x, y, grayRgb);
            }
        }

        return gray;
    }
}
