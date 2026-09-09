package com.ritham.erp.module.migration.service.ocr;

import com.ritham.erp.module.migration.service.OcrService.WordBox;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Concrete {@link OcrEngine} implementation wrapping the local Tesseract OCR library via Tess4J.
 */
@Component
@Slf4j
public class TesseractOcrEngine implements OcrEngine {

    @Value("${app.migration.tesseract-data-path:C:/Program Files/Tesseract-OCR/tessdata}")
    private String tessDataPath;

    public TesseractOcrEngine() {}

    public TesseractOcrEngine(String tessDataPath) {
        this.tessDataPath = tessDataPath;
    }

    public void setTessDataPath(String tessDataPath) {
        this.tessDataPath = tessDataPath;
    }

    private Tesseract buildTesseract(String language) {
        Tesseract tess = new Tesseract();
        String resolvedPath = resolveTessDataPath();
        if (resolvedPath != null) {
            tess.setDatapath(resolvedPath);
        }
        tess.setLanguage(language != null && !language.isBlank() ? language : "eng");
        tess.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);
        return tess;
    }

    private String resolveTessDataPath() {
        if (tessDataPath != null && new File(tessDataPath).exists()) {
            return tessDataPath;
        }
        if (new File("tessdata").exists()) {
            return new File("tessdata").getAbsolutePath();
        }
        if (new File("C:/Program Files/Tesseract-OCR/tessdata").exists()) {
            return "C:/Program Files/Tesseract-OCR/tessdata";
        }
        if (new File("C:/Users/admin/AppData/Local/Programs/Tesseract-OCR/tessdata").exists()) {
            return "C:/Users/admin/AppData/Local/Programs/Tesseract-OCR/tessdata";
        }
        return tessDataPath;
    }

    @Override
    public OcrEngineResult ocrImage(BufferedImage image, String language) {
        if (image == null) {
            return new OcrEngineResult("", 0.0, Collections.emptyList(), 0, 0);
        }
        Tesseract tess = buildTesseract(language);
        tess.setPageSegMode(ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK); // PSM 6
        try {
            String text = tess.doOCR(image);
            List<WordBox> boxes = extractWordBoxesFromTesseract(tess, image);
            double confidence = calculateConfidence(boxes, text);
            return new OcrEngineResult(text != null ? text.strip() : "", confidence, boxes, image.getWidth(), image.getHeight());
        } catch (TesseractException e) {
            log.error("Tesseract full image OCR error: {}", e.getMessage());
            return new OcrEngineResult("", 0.0, Collections.emptyList(), image.getWidth(), image.getHeight());
        }
    }

    @Override
    public OcrEngineResult ocrRegion(BufferedImage image, double nx, double ny, double nw, double nh, String language, int psm) {
        if (image == null) return new OcrEngineResult("", 0.0, Collections.emptyList(), 0, 0);
        int imgW = image.getWidth();
        int imgH = image.getHeight();

        int rx = (int) Math.max(0, Math.min(imgW - 1, Math.round(nx * imgW)));
        int ry = (int) Math.max(0, Math.min(imgH - 1, Math.round(ny * imgH)));
        int rw = (int) Math.max(1, Math.min(imgW - rx, Math.round(nw * imgW)));
        int rh = (int) Math.max(1, Math.min(imgH - ry, Math.round(nh * imgH)));

        BufferedImage sub = image.getSubimage(rx, ry, rw, rh);
        Tesseract tess = buildTesseract(language);
        tess.setPageSegMode(psm);
        try {
            String text = tess.doOCR(sub);
            List<WordBox> relativeBoxes = extractWordBoxesFromTesseract(tess, sub);
            // Translate relative boxes to whole-page normalized coordinates
            List<WordBox> globalBoxes = new ArrayList<>();
            for (WordBox b : relativeBoxes) {
                double gx = nx + (b.x() * nw);
                double gy = ny + (b.y() * nh);
                double gw = b.width() * nw;
                double gh = b.height() * nh;
                globalBoxes.add(new WordBox(b.text(), gx, gy, gw, gh, b.confidence()));
            }
            double conf = calculateConfidence(relativeBoxes, text);
            return new OcrEngineResult(text != null ? text.strip() : "", conf, globalBoxes, rw, rh);
        } catch (TesseractException e) {
            log.warn("Tesseract region OCR error for [{}, {}, {}, {}]: {}", nx, ny, nw, nh, e.getMessage());
            return new OcrEngineResult("", 0.0, Collections.emptyList(), rw, rh);
        }
    }

    @Override
    public String ocrText(BufferedImage image, int psm) {
        if (image == null) return "";
        Tesseract tess = buildTesseract("eng");
        tess.setPageSegMode(psm);
        try {
            String res = tess.doOCR(image);
            return res != null ? res.strip() : "";
        } catch (TesseractException e) {
            log.debug("Tesseract ocrText error: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public String ocrDigits(BufferedImage image) {
        if (image == null) return "";
        Tesseract tess = buildTesseract("eng");
        tess.setPageSegMode(ITessAPI.TessPageSegMode.PSM_SINGLE_LINE); // PSM 7 or 8
        tess.setVariable("tessedit_char_whitelist", "0123456789.");
        try {
            String res = tess.doOCR(image);
            return res != null ? res.replaceAll("[^0-9.]", "").strip() : "";
        } catch (TesseractException e) {
            log.debug("Tesseract ocrDigits error: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public List<WordBox> extractWordBoxes(BufferedImage image) {
        if (image == null) return Collections.emptyList();
        Tesseract tess = buildTesseract("eng");
        return extractWordBoxesFromTesseract(tess, image);
    }

    private List<WordBox> extractWordBoxesFromTesseract(Tesseract tess, BufferedImage img) {
        List<WordBox> wordBoxes = new ArrayList<>();
        int wImg = img.getWidth();
        int hImg = img.getHeight();
        if (wImg <= 0 || hImg <= 0) return wordBoxes;

        try {
            List<Word> words = tess.getWords(img, ITessAPI.TessPageIteratorLevel.RIL_WORD);
            if (words != null) {
                for (Word w : words) {
                    if (w.getText() != null && !w.getText().isBlank()) {
                        Rectangle r = w.getBoundingBox();
                        if (r != null) {
                            double nx = Math.max(0.0, Math.min(1.0, (double) r.x / wImg));
                            double ny = Math.max(0.0, Math.min(1.0, (double) r.y / hImg));
                            double nw = Math.max(0.0, Math.min(1.0 - nx, (double) r.width / wImg));
                            double nh = Math.max(0.0, Math.min(1.0 - ny, (double) r.height / hImg));
                            wordBoxes.add(new WordBox(w.getText().trim(), nx, ny, nw, nh, w.getConfidence()));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            log.debug("Failed extracting word boxes: {}", t.getMessage());
        }
        return wordBoxes;
    }

    private double calculateConfidence(List<WordBox> boxes, String rawText) {
        if (boxes != null && !boxes.isEmpty()) {
            double sum = 0;
            int count = 0;
            for (WordBox b : boxes) {
                if (b.confidence() > 0) {
                    sum += b.confidence();
                    count++;
                }
            }
            if (count > 0) {
                return Math.round((sum / count) * 10.0) / 10.0;
            }
        }
        if (rawText == null || rawText.isBlank()) return 0.0;
        long alnum = rawText.chars().filter(Character::isLetterOrDigit).count();
        double ratio = (double) alnum / Math.max(1, rawText.length());
        return Math.min(95.0, Math.max(30.0, Math.round((50.0 + ratio * 40.0) * 10.0) / 10.0));
    }

    @Override
    public void rotateImageOnDisk(Path imagePath, int angle) {
        int normAngle = ((angle % 360) + 360) % 360;
        if (normAngle == 0) return;
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) return;
            BufferedImage rotated = rotate(image, normAngle);
            String ext = "png";
            String fn = imagePath.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fn.endsWith(".jpg") || fn.endsWith(".jpeg")) ext = "jpg";
            ImageIO.write(rotated, ext, imagePath.toFile());
            log.info("Rotated image on disk {} by {} degrees", imagePath.getFileName(), normAngle);
        } catch (Exception e) {
            log.warn("Could not rotate image on disk {}: {}", imagePath.getFileName(), e.getMessage());
        }
    }

    public static BufferedImage rotate(BufferedImage img, int angle) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage rotated;
        if (angle == 90 || angle == 270) {
            rotated = new BufferedImage(h, w, img.getType() == 0 ? BufferedImage.TYPE_INT_RGB : img.getType());
        } else {
            rotated = new BufferedImage(w, h, img.getType() == 0 ? BufferedImage.TYPE_INT_RGB : img.getType());
        }
        Graphics2D g = rotated.createGraphics();
        g.translate(rotated.getWidth() / 2.0, rotated.getHeight() / 2.0);
        g.rotate(Math.toRadians(angle));
        g.translate(-w / 2.0, -h / 2.0);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rotated;
    }
}
