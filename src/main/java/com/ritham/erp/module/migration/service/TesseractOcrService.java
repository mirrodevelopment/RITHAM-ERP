package com.ritham.erp.module.migration.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tesseract-based OCR implementation.
 *
 * <p>Tesseract must be installed on the host OS and {@code app.migration.tesseract-data-path}
 * must point to the directory containing {@code eng.traineddata} (and any other language
 * packs needed).
 *
 * <p>On Windows the default installation path is usually:
 * {@code C:\Program Files\Tesseract-OCR\tessdata}
 *
 * <p>For PDF files, only the first page is OCR'd (sufficient for single-order paper records).
 */
@Service
@Slf4j
public class TesseractOcrService implements OcrService {

    /** DPI used when rendering PDF pages to image for OCR. Higher = better accuracy. */
    private static final float PDF_RENDER_DPI = 300f;

    @Value("${app.migration.tesseract-data-path:C:/Program Files/Tesseract-OCR/tessdata}")
    private String tessDataPath;

    @Override
    public OcrResult processDocument(Path documentPath, String language) {
        long start = System.currentTimeMillis();

        if (!Files.exists(documentPath)) {
            return OcrResult.failure("File not found: " + documentPath);
        }

        String fileName = documentPath.getFileName().toString().toLowerCase(Locale.ROOT);

        try {
            if (fileName.endsWith(".pdf")) {
                return processPdf(documentPath, language, start);
            } else {
                return processImage(documentPath, language, start);
            }
        } catch (Exception e) {
            log.error("OCR failed for {}: {}", documentPath, e.getMessage(), e);
            return OcrResult.failure("OCR engine error: " + e.getMessage());
        }
    }

    @Override
    public void rotateImageOnDisk(Path imagePath, int angle) {
        int normAngle = ((angle % 360) + 360) % 360;
        if (normAngle == 0) return;
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) return;
            BufferedImage rotated = rotateImage(image, normAngle);
            String ext = "png";
            String fn = imagePath.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fn.endsWith(".jpg") || fn.endsWith(".jpeg")) ext = "jpg";
            ImageIO.write(rotated, ext, imagePath.toFile());
            log.info("Manually rotated image on disk {} by {} degrees", imagePath.getFileName(), normAngle);
        } catch (Exception e) {
            log.warn("Could not rotate image on disk {}: {}", imagePath.getFileName(), e.getMessage());
        }
    }

    // ── Image processing ──────────────────────────────────────────────────────

    private OcrResult processImage(Path imagePath, String language, long start)
            throws IOException, TesseractException {

        BufferedImage original = ImageIO.read(imagePath.toFile());
        if (original == null) {
            return OcrResult.failure("Unsupported image format: " + imagePath.getFileName());
        }

        // ── Step 1: Determine correct orientation ────────────────────────────
        int angle = detectOrientation(original);
        BufferedImage oriented = (angle != 0) ? rotateImage(original, angle) : original;
        if (angle != 0) {
            log.info("Auto-rotating image {} by {} degrees for OCR", imagePath.getFileName(), angle);
        }

        // ── Step 2: Preprocess (grayscale + contrast stretch) ────────────────
        //   Original file is NEVER overwritten.
        //   The processed image is saved as ocr_processed_{filename} in the same dir.
        BufferedImage processed = enhanceForOcr(oriented);

        String fn  = imagePath.getFileName().toString();
        String ext = fn.toLowerCase(Locale.ROOT).endsWith(".jpg") ||
                     fn.toLowerCase(Locale.ROOT).endsWith(".jpeg") ? "jpg" : "png";
        Path processedPath = imagePath.getParent().resolve("ocr_processed_" + fn.replaceAll("\\.(png|jpg|jpeg)$", "." + ext));
        ImageIO.write(processed, ext, processedPath.toFile());
        log.info("Wrote OCR-processed image to {}", processedPath.getFileName());

        return runTesseract(processed, language, 1, start, processedPath.toAbsolutePath().toString());
    }

    // ── PDF processing ────────────────────────────────────────────────────────

    private OcrResult processPdf(Path pdfPath, String language, long start)
            throws IOException, TesseractException {

        try (PDDocument pdf = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            // OCR the first page only for direct PDF uploads (multi-page PDFs are
            // pre-split into page images by PdfPageService before this method is called).
            BufferedImage raw = renderer.renderImageWithDPI(0, PDF_RENDER_DPI, ImageType.GRAY);
            int angle = detectOrientation(raw);
            BufferedImage oriented = (angle != 0) ? rotateImage(raw, angle) : raw;
            BufferedImage processed = enhanceForOcr(oriented);
            return runTesseract(processed, language, pdf.getNumberOfPages(), start, null);
        }
    }

    // ── Tesseract invocation ──────────────────────────────────────────────────

    private OcrResult runTesseract(BufferedImage image, String language,
                                    int pageCount, long start, String processedImagePath)
            throws TesseractException {

        // Auto-detect and crop to bright paper boundaries if dark borders (e.g. wooden desk) exist
        BufferedImage processImg = image;
        try {
            int minX = image.getWidth(), maxX = 0, minY = image.getHeight(), maxY = 0;
            for (int y = 0; y < image.getHeight(); y += 4) {
                for (int x = 0; x < image.getWidth(); x += 4) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
            // Lower threshold: handles yellowed paper and dim photograph lighting
            if (r > 90 && g > 90 && b > 90) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }
            int pw = maxX - minX;
            int ph = maxY - minY;
            if (pw > 200 && ph > 200 && (minX > 15 || minY > 15 || maxX < image.getWidth() - 15 || maxY < image.getHeight() - 15)) {
                processImg = image.getSubimage(minX, minY, pw, ph);
                log.info("Cropped document paper slip from bounds [{}, {}] to [{}, {}]", minX, minY, maxX, maxY);
            }
        } catch (Exception e) {
            log.debug("Auto paper crop skipped: {}", e.getMessage());
        }

        Tesseract tess = buildTesseract(language);
        // PSM 6 = Assume a single uniform block of text (optimal for tabular slips)
        tess.setPageSegMode(6);
        String bodyText = tess.doOCR(processImg);

        // Header region pass (top 28% contains customer name, mobile, dates, erode)
        String headerText = "";
        try {
            int h = (int) (processImg.getHeight() * 0.28);
            if (h > 50) {
                BufferedImage header = processImg.getSubimage(0, 0, processImg.getWidth(), h);
                tess.setPageSegMode(11);
                String h11 = tess.doOCR(header);
                tess.setPageSegMode(6);
                String h6 = tess.doOCR(header);
                headerText = (h6 != null ? h6 : "") + "\n" + (h11 != null ? h11 : "");
            }
        } catch (Exception e) {
            log.warn("Header OCR pass skipped: {}", e.getMessage());
        }

        // Table region pass (left 50% contains vertical measurement labels and values)
        String tableText = "";
        try {
            int tableY = (int) (processImg.getHeight() * 0.18);
            int tableH = (int) (processImg.getHeight() * 0.80);
            int tableW = (int) (processImg.getWidth() * 0.50);
            if (tableH > 100 && tableW > 100) {
                BufferedImage tableCrop = processImg.getSubimage(0, tableY, tableW, tableH);
                tess.setPageSegMode(4); // Single column of variable text sizes
                String t4 = tess.doOCR(tableCrop);
                tess.setPageSegMode(6);
                String t6 = tess.doOCR(tableCrop);
                tableText = (t4 != null ? t4 : "") + "\n" + (t6 != null ? t6 : "");
            }
        } catch (Exception e) {
            log.warn("Table OCR pass skipped: {}", e.getMessage());
        }

        String rawText = (headerText + "\n" + tableText + "\n" + (bodyText != null ? bodyText : "")).strip();

        int imgW = processImg.getWidth();
        int imgH = processImg.getHeight();
        String orientation = imgW > imgH ? "LANDSCAPE" : "PORTRAIT";

        List<OcrService.WordBox> wordBoxes = new ArrayList<>();
        double confidence = calculateTesseractConfidence(tess, processImg, rawText, wordBoxes);

        long elapsed = System.currentTimeMillis() - start;
        log.info("OCR completed in {}ms, {} chars extracted, conf={}%, orientation={}, {} words",
                elapsed, rawText.length(), confidence, orientation, wordBoxes.size());

        return new OcrResult(
                rawText,
                confidence,
                pageCount,
                elapsed,
                !rawText.isBlank(),
                rawText.isBlank() ? "No text detected" : null,
                orientation,
                imgW,
                imgH,
                wordBoxes,
                processedImagePath
        );
    }

    private double calculateTesseractConfidence(Tesseract tess, BufferedImage img, String rawText, List<OcrService.WordBox> wordBoxes) {
        if (rawText == null || rawText.isBlank()) {
            return 0.0;
        }
        int wImg = img.getWidth();
        int hImg = img.getHeight();
        try {
            java.util.List<net.sourceforge.tess4j.Word> words = tess.getWords(img, net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel.RIL_WORD);
            if (words != null && !words.isEmpty()) {
                double sum = 0;
                int count = 0;
                for (net.sourceforge.tess4j.Word w : words) {
                    if (w.getText() != null && !w.getText().isBlank()) {
                        float c = w.getConfidence();
                        if (c > 0) {
                            sum += c;
                            count++;
                        }
                        if (wordBoxes != null && w.getBoundingBox() != null && wImg > 0 && hImg > 0) {
                            java.awt.Rectangle r = w.getBoundingBox();
                            double nx = Math.max(0.0, Math.min(1.0, (double) r.x / wImg));
                            double ny = Math.max(0.0, Math.min(1.0, (double) r.y / hImg));
                            double nw = Math.max(0.0, Math.min(1.0 - nx, (double) r.width / wImg));
                            double nh = Math.max(0.0, Math.min(1.0 - ny, (double) r.height / hImg));
                            wordBoxes.add(new OcrService.WordBox(w.getText().trim(), nx, ny, nw, nh, c));
                        }
                    }
                }
                if (count > 0) {
                    double avg = sum / count;
                    return Math.round(avg * 10.0) / 10.0;
                }
            }
        } catch (Throwable t) {
            log.debug("Tesseract getWords confidence calculation: {}", t.getMessage());
        }

        // Heuristic based on character density, vocabulary length and alphanumeric ratio
        long alnum = rawText.chars().filter(Character::isLetterOrDigit).count();
        double ratio = (double) alnum / Math.max(1, rawText.length());
        int words = rawText.split("\\s+").length;
        double base = 58.0 + (ratio * 26.0) + Math.min(14.0, words * 0.35);
        return Math.min(97.0, Math.max(25.0, Math.round(base * 10.0) / 10.0));
    }

    private Tesseract buildTesseract(String language) {
        Tesseract tess = new Tesseract();
        tess.setDatapath(tessDataPath);
        tess.setLanguage(language != null && !language.isBlank() ? language : "eng");
        tess.setPageSegMode(6);
        return tess;
    }

    // ── Image enhancement (grayscale + contrast stretch) ─────────────────────

    /**
     * Converts the image to grayscale and applies contrast stretching
     * (auto-levels: 5th–95th percentile mapped to 0–255).
     *
     * <p>This significantly improves Tesseract's accuracy on:
     * - Photographs taken under uneven lighting (e.g. book held open)
     * - Shadowed or slightly overexposed areas
     * - Yellowish/aged paper
     *
     * <p>Handwriting is preserved because contrast stretching is a linear transform;
     * it does not threshold or binarize the image.
     */
    private BufferedImage enhanceForOcr(BufferedImage src) {
        // Convert to grayscale
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2 = gray.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();

        // Build histogram and find 5th and 95th percentile brightness
        int[] histogram = new int[256];
        int totalPixels = gray.getWidth() * gray.getHeight();
        for (int y = 0; y < gray.getHeight(); y++) {
            for (int x = 0; x < gray.getWidth(); x++) {
                int brightness = gray.getRGB(x, y) & 0xff;
                histogram[brightness]++;
            }
        }
        int low = 0, high = 255;
        int cumulative = 0;
        int lowTarget  = (int) (totalPixels * 0.05);
        int highTarget = (int) (totalPixels * 0.95);
        for (int i = 0; i < 256; i++) {
            cumulative += histogram[i];
            if (cumulative < lowTarget)  low  = i;
            if (cumulative < highTarget) high = i;
        }
        if (high <= low) return gray; // flat image — return as-is

        // Apply linear stretch
        float scale = 255.0f / (high - low);
        BufferedImage stretched = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < gray.getHeight(); y++) {
            for (int x = 0; x < gray.getWidth(); x++) {
                int v = gray.getRGB(x, y) & 0xff;
                int stretched_v = Math.min(255, Math.max(0, Math.round((v - low) * scale)));
                int rgb = (stretched_v << 16) | (stretched_v << 8) | stretched_v;
                stretched.setRGB(x, y, rgb);
            }
        }
        return stretched;
    }

    private int detectOrientation(BufferedImage img) {
        boolean isLandscape = img.getWidth() > img.getHeight();
        // If image is in landscape, rotate to portrait (270° or 90°); otherwise check upright (0° or 180°)
        int[] candidateAngles = isLandscape ? new int[]{270, 90} : new int[]{0, 180};
        int bestAngle = isLandscape ? 270 : 0;
        int maxScore = -1;

        // Downscale to max 800px for lightning-fast orientation detection
        double scale = Math.min(800.0 / img.getWidth(), 800.0 / img.getHeight());
        int sw = Math.max(1, (int) (img.getWidth() * scale));
        int sh = Math.max(1, (int) (img.getHeight() * scale));
        BufferedImage small = new BufferedImage(sw, sh, BufferedImage.TYPE_BYTE_GRAY);
        java.awt.Graphics2D sg = small.createGraphics();
        sg.drawImage(img, 0, 0, sw, sh, null);
        sg.dispose();

        Tesseract tess = buildTesseract("eng");
        tess.setPageSegMode(6);

        for (int angle : candidateAngles) {
            try {
                BufferedImage rotated = (angle == 0) ? small : rotateImage(small, angle);
                String text = tess.doOCR(rotated).toUpperCase(Locale.ROOT);

                int score = calculateOrientationScore(text);
                log.info("Orientation check: angle={}, score={}, isLandscape={}", angle, score, isLandscape);

                if (score > maxScore) {
                    maxScore = score;
                    bestAngle = angle;
                }
            } catch (Exception e) {
                log.debug("Orientation check failed for angle {}: {}", angle, e.getMessage());
            }
        }
        log.info("Orientation detection: isLandscape={}, chosen angle={} (score={})", isLandscape, bestAngle, maxScore);
        return bestAngle;
    }

    private int calculateOrientationScore(String text) {
        if (text == null || text.isBlank()) return 0;
        int score = 0;

        // Tailoring garment keywords (strong signal)
        if (text.contains("CHUDI") || text.contains("CHURIDAR")) score += 120;
        if (text.contains("BLOUSE")) score += 120;
        if (text.contains("FROCK") || text.contains("SHIRT") || text.contains("PANT") || text.contains("SUIT")) score += 80;

        // Header field labels
        if (text.contains("NAME")) score += 60;
        if (text.contains("CUSTOMER")) score += 60;
        if (text.contains("DATE")) score += 50;
        if (text.contains("DUE")) score += 50;
        if (text.contains("ERODE")) score += 60;
        if (text.contains("PH") || text.contains("MOBILE") || text.contains("PHONE")) score += 50;

        // Measurement table headers & labels
        if (text.contains("MEASURE")) score += 60;
        if (text.contains("FIELD") || text.contains("VALUE")) score += 40;
        if (text.contains("F.N") || text.contains("FN")) score += 30;
        if (text.contains("B.N") || text.contains("BN")) score += 30;
        if (text.contains("H.B") || text.contains("HB")) score += 30;
        if (text.contains("T.S") || text.contains("TS")) score += 30;
        if (text.contains("P.L") || text.contains("PL")) score += 30;
        if (text.contains("SHALL") || text.contains("B.D") || text.contains("BD")) score += 30;

        // Date pattern regex (e.g. 05/04/2024 or 05-04-2024 or 05|04|2024)
        if (text.matches("(?s).*\\d{1,2}[/|.-]\\d{1,2}[/|.-]\\d{2,4}.*")) score += 50;

        // Mobile number pattern (10 digits)
        if (text.matches("(?s).*[6-9]\\d{9}.*")) score += 50;

        // Recognized alphabetic words (length >= 3)
        long wordCount = java.util.Arrays.stream(text.split("\\s+"))
                .filter(w -> w.length() >= 3 && w.matches("[A-Z]+"))
                .count();
        score += (int) Math.min(wordCount * 5, 80);

        return score;
    }

    private BufferedImage rotateImage(BufferedImage img, int angle) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage rotated;
        if (angle == 90 || angle == 270) {
            rotated = new BufferedImage(h, w, img.getType() == 0 ? BufferedImage.TYPE_INT_RGB : img.getType());
        } else {
            rotated = new BufferedImage(w, h, img.getType() == 0 ? BufferedImage.TYPE_INT_RGB : img.getType());
        }
        java.awt.Graphics2D g = rotated.createGraphics();
        g.translate(rotated.getWidth() / 2.0, rotated.getHeight() / 2.0);
        g.rotate(Math.toRadians(angle));
        g.translate(-w / 2.0, -h / 2.0);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rotated;
    }
}
