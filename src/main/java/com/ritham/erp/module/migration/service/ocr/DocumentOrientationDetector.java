package com.ritham.erp.module.migration.service.ocr;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Automatically detects the correct upright orientation of the measurement sheet.
 * Evaluates all four cardinal angles (0°, 90°, 180°, 270°) against expected printed form vocabulary
 * to reliably recover from rotated phone photographs and landscape scanner inputs.
 */
@Component
@Slf4j
public class DocumentOrientationDetector {

    public record OrientationResult(
            int angle,
            BufferedImage orientedImage,
            int score
    ) {}

    private static final List<ScoredPattern> KEYWORDS = List.of(
            // Top center garment heading (highest priority indicator)
            new ScoredPattern(Pattern.compile("(?i)\\b(?:BLOUSE|BLOSE|BLOUS)\\b"), 100),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:CHUDI|CHURIDAR|CHUDHY|CHUDHI|CHUD)\\b"), 100),

            // Standard printed header labels
            new ScoredPattern(Pattern.compile("(?i)\\bERODE\\b"), 50),
            new ScoredPattern(Pattern.compile("(?i)\\bNAME\\b"), 40),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:DUE\\s*DATE|DUE)\\b"), 40),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:OR\\.?\\s*DATE|DATE)\\b"), 40),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:PH(?:ONE)?|MOB(?:ILE)?)\\b"), 40),

            // Form measurement printed column labels
            new ScoredPattern(Pattern.compile("(?i)\\bLTH\\b"), 25),
            new ScoredPattern(Pattern.compile("(?i)\\bSHO\\b"), 25),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:H\\.?S\\.?|HS)\\b"), 20),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:H\\.?L\\.?|HL)\\b"), 20),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:F\\.?N\\.?|FN)\\b"), 20),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:B\\.?N\\.?|BN)\\b"), 20),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:H\\.?B\\.?|HB)\\b"), 20),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:DP-?1|DP1)\\b"), 20),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:T\\.?S\\.?|TS)\\b"), 20),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:P\\.?L\\.?|PL)\\b"), 20),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:S\\.?CUT|SCUT)\\b"), 20),
            new ScoredPattern(Pattern.compile("(?i)\\bLNG\\b"), 20),
            new ScoredPattern(Pattern.compile("(?i)\\bSHALL\\b"), 20),
            new ScoredPattern(Pattern.compile("(?i)\\b(?:B\\.?D\\.?|BD)\\b"), 20)
    );

    private record ScoredPattern(Pattern pattern, int score) {}

    /**
     * Detects optimal rotation angle and returns the oriented image in landscape orientation.
     * The physical measurement sheets are horizontal/landscape forms.
     * If the uploaded image is already landscape (width >= height), candidates are strictly {0, 180}
     * so it is never accidentally rotated into portrait.
     * If the uploaded image is portrait (height > width), candidates are {270, 90} to rotate it into landscape.
     */
    public OrientationResult detectAndOrient(BufferedImage image, OcrEngine ocrEngine) {
        if (image == null) {
            return new OrientationResult(0, null, 0);
        }

        int w = image.getWidth();
        int h = image.getHeight();

        // Always scan in landscape
        int[] candidates = (w >= h) ? new int[]{0, 180} : new int[]{270, 90};

        int bestAngle = candidates[0];
        int maxScore = -1;

        for (int angle : candidates) {
            BufferedImage testImg = (angle == 0) ? image : TesseractOcrEngine.rotate(image, angle);

            // Fast score check on scaled-down image (max 1000px)
            int score = scoreOrientation(testImg);
            log.info("Landscape orientation check: angle={}, score={} [input w={}, h={}]", angle, score, w, h);

            if (score > maxScore) {
                maxScore = score;
                bestAngle = angle;
            }

            // Early exit if high confidence hit found (garment + header = score >= 150)
            if (score >= 150) {
                bestAngle = angle;
                break;
            }
        }

        log.info("Landscape orientation chosen: angle={} (score={})", bestAngle, maxScore);
        BufferedImage finalImage = (bestAngle == 0) ? image : TesseractOcrEngine.rotate(image, bestAngle);
        return new OrientationResult(bestAngle, finalImage, maxScore);
    }

    private int scoreOrientation(BufferedImage img) {
        try {
            // Scale down for fast orientation check
            int targetW = Math.min(img.getWidth(), 1000);
            int targetH = (int) ((double) img.getHeight() * targetW / img.getWidth());
            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_BYTE_GRAY);
            java.awt.Graphics2D g = scaled.createGraphics();
            g.drawImage(img, 0, 0, targetW, targetH, null);
            g.dispose();

            Tesseract tess = new Tesseract();
            String path = resolveTessDataPath();
            if (path != null) tess.setDatapath(path);
            tess.setLanguage("eng");
            tess.setPageSegMode(net.sourceforge.tess4j.ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT); // PSM 11
            String text = tess.doOCR(scaled);
            if (text == null || text.isBlank()) return 0;

            int totalScore = 0;
            for (ScoredPattern sp : KEYWORDS) {
                if (sp.pattern.matcher(text).find()) {
                    totalScore += sp.score;
                }
            }
            return totalScore;
        } catch (Throwable t) {
            log.debug("Orientation score check error: {}", t.getMessage());
            return 0;
        }
    }

    private String resolveTessDataPath() {
        if (new File("tessdata").exists()) return new File("tessdata").getAbsolutePath();
        if (new File("C:/Program Files/Tesseract-OCR/tessdata").exists()) return "C:/Program Files/Tesseract-OCR/tessdata";
        if (new File("C:/Users/admin/AppData/Local/Programs/Tesseract-OCR/tessdata").exists()) return "C:/Users/admin/AppData/Local/Programs/Tesseract-OCR/tessdata";
        return null;
    }
}
