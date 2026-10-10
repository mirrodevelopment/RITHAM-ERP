package com.ritham.erp.module.migration.service;

import com.ritham.erp.module.migration.service.ocr.DocumentOrientationDetector;
import com.ritham.erp.module.migration.service.ocr.ImagePreprocessor;
import com.ritham.erp.module.migration.service.ocr.OcrEngine;
import com.ritham.erp.module.migration.service.ocr.RegionExtractor;
import com.ritham.erp.module.migration.service.ocr.TesseractOcrEngine;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * High-level OCR service coordinating image preprocessing, orientation detection,
 * multi-region OCR execution, and result compilation.
 */
@Service
@Slf4j
public class TesseractOcrService implements OcrService {

    private static final float PDF_RENDER_DPI = 300f;

    private final OcrEngine ocrEngine;
    private final ImagePreprocessor imagePreprocessor;
    private final DocumentOrientationDetector orientationDetector;
    private final RegionExtractor regionExtractor;

    @Autowired
    public TesseractOcrService(
            OcrEngine ocrEngine,
            ImagePreprocessor imagePreprocessor,
            DocumentOrientationDetector orientationDetector,
            RegionExtractor regionExtractor) {
        this.ocrEngine = ocrEngine;
        this.imagePreprocessor = imagePreprocessor;
        this.orientationDetector = orientationDetector;
        this.regionExtractor = regionExtractor;
    }

    public TesseractOcrService() {
        this(new TesseractOcrEngine(), new ImagePreprocessor(), new DocumentOrientationDetector(), new RegionExtractor());
    }

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
        ocrEngine.rotateImageOnDisk(imagePath, angle);
    }

    // ── Image processing ──────────────────────────────────────────────────────

    private OcrResult processImage(Path imagePath, String language, long start) throws IOException {
        BufferedImage original = ImageIO.read(imagePath.toFile());
        if (original == null) {
            return OcrResult.failure("Unsupported image format: " + imagePath.getFileName());
        }

        // 1. Automatic orientation detection (0, 90, 180, 270)
        DocumentOrientationDetector.OrientationResult orientRes = orientationDetector.detectAndOrient(original, ocrEngine);
        BufferedImage oriented = orientRes.orientedImage();
        if (orientRes.angle() != 0) {
            log.info("Auto-rotating image {} by {} degrees (score={}) to landscape", imagePath.getFileName(), orientRes.angle(), orientRes.score());
            rotateImageOnDisk(imagePath, orientRes.angle());
        }

        // 2. Preprocessing: auto-crop paper boundary, grayscale, contrast enhancement
        ImagePreprocessor.PreprocessedResult prepRes = imagePreprocessor.preprocess(oriented, imagePath);
        BufferedImage processed = prepRes.processedImage();
        String processedImagePath = prepRes.processedImagePath() != null ? prepRes.processedImagePath().toAbsolutePath().toString() : null;

        // 3. Multi-region OCR execution
        return executeMultiRegionOcr(processed, language, 1, start, processedImagePath);
    }

    // ── PDF processing ────────────────────────────────────────────────────────

    private OcrResult processPdf(Path pdfPath, String language, long start) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            BufferedImage raw = renderer.renderImageWithDPI(0, PDF_RENDER_DPI, ImageType.RGB);

            DocumentOrientationDetector.OrientationResult orientRes = orientationDetector.detectAndOrient(raw, ocrEngine);
            BufferedImage oriented = orientRes.orientedImage();

            ImagePreprocessor.PreprocessedResult prepRes = imagePreprocessor.preprocess(oriented, null);
            BufferedImage processed = prepRes.processedImage();

            return executeMultiRegionOcr(processed, language, pdf.getNumberOfPages(), start, null);
        }
    }

    // ── Multi-Region OCR Execution ────────────────────────────────────────────

    private OcrResult executeMultiRegionOcr(
            BufferedImage image, String language, int pageCount, long start, String processedImagePath) {

        int imgW = image.getWidth();
        int imgH = image.getHeight();
        String orientation = "LANDSCAPE";

        // Pass A: Top-Center Crop (Garment Type)
        RegionExtractor.RegionCrop topCenterCrop = regionExtractor.cropTopCenter(image);
        OcrEngine.OcrEngineResult tcRes = ocrEngine.ocrRegion(
                topCenterCrop.croppedImage(), 0.0, 0.0, 1.0, 1.0, language, net.sourceforge.tess4j.ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT);

        // Pass B: Top-Left Crop (Customer Name & Order ID / Erode)
        RegionExtractor.RegionCrop topLeftCrop = regionExtractor.cropTopLeft(image);
        OcrEngine.OcrEngineResult tlRes = ocrEngine.ocrRegion(
                topLeftCrop.croppedImage(), 0.0, 0.0, 1.0, 1.0, language, net.sourceforge.tess4j.ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT);

        // Pass C: Top-Right Crop (Mobile, Order Date, Due Date)
        RegionExtractor.RegionCrop topRightCrop = regionExtractor.cropTopRight(image);
        OcrEngine.OcrEngineResult trRes = ocrEngine.ocrRegion(
                topRightCrop.croppedImage(), 0.0, 0.0, 1.0, 1.0, language, net.sourceforge.tess4j.ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT);

        // Pass D: Left Measurements Column Crop
        RegionExtractor.RegionCrop measCrop = regionExtractor.cropMeasurements(image);
        OcrEngine.OcrEngineResult measRes = ocrEngine.ocrRegion(
                measCrop.croppedImage(), 0.0, 0.0, 1.0, 1.0, language, net.sourceforge.tess4j.ITessAPI.TessPageSegMode.PSM_SINGLE_COLUMN);

        // Pass E: Full image block OCR for complete context and debug
        OcrEngine.OcrEngineResult fullRes = ocrEngine.ocrImage(image, language);

        // Combine raw text from regions with full text
        StringBuilder sb = new StringBuilder();
        if (!tcRes.text().isBlank()) sb.append(tcRes.text()).append("\n");
        if (!tlRes.text().isBlank()) sb.append(tlRes.text()).append("\n");
        if (!trRes.text().isBlank()) sb.append(trRes.text()).append("\n");
        if (!measRes.text().isBlank()) sb.append(measRes.text()).append("\n");
        if (!fullRes.text().isBlank()) sb.append(fullRes.text()).append("\n");

        String combinedRawText = sb.toString().strip();

        // Combine word boxes from all regions and full image
        List<WordBox> combinedBoxes = new ArrayList<>();

        // Add translated boxes from top-center
        for (WordBox b : tcRes.wordBoxes()) {
            double gx = RegionExtractor.TOP_CENTER_REGION.x() + b.x() * RegionExtractor.TOP_CENTER_REGION.width();
            double gy = RegionExtractor.TOP_CENTER_REGION.y() + b.y() * RegionExtractor.TOP_CENTER_REGION.height();
            double gw = b.width() * RegionExtractor.TOP_CENTER_REGION.width();
            double gh = b.height() * RegionExtractor.TOP_CENTER_REGION.height();
            combinedBoxes.add(new WordBox(b.text(), gx, gy, gw, gh, b.confidence()));
        }

        // Add translated boxes from top-left
        for (WordBox b : tlRes.wordBoxes()) {
            double gx = RegionExtractor.TOP_LEFT_REGION.x() + b.x() * RegionExtractor.TOP_LEFT_REGION.width();
            double gy = RegionExtractor.TOP_LEFT_REGION.y() + b.y() * RegionExtractor.TOP_LEFT_REGION.height();
            double gw = b.width() * RegionExtractor.TOP_LEFT_REGION.width();
            double gh = b.height() * RegionExtractor.TOP_LEFT_REGION.height();
            combinedBoxes.add(new WordBox(b.text(), gx, gy, gw, gh, b.confidence()));
        }

        // Add translated boxes from top-right
        for (WordBox b : trRes.wordBoxes()) {
            double gx = RegionExtractor.TOP_RIGHT_REGION.x() + b.x() * RegionExtractor.TOP_RIGHT_REGION.width();
            double gy = RegionExtractor.TOP_RIGHT_REGION.y() + b.y() * RegionExtractor.TOP_RIGHT_REGION.height();
            double gw = b.width() * RegionExtractor.TOP_RIGHT_REGION.width();
            double gh = b.height() * RegionExtractor.TOP_RIGHT_REGION.height();
            combinedBoxes.add(new WordBox(b.text(), gx, gy, gw, gh, b.confidence()));
        }

        // Add translated boxes from measurements
        for (WordBox b : measRes.wordBoxes()) {
            double gx = RegionExtractor.MEASUREMENT_REGION.x() + b.x() * RegionExtractor.MEASUREMENT_REGION.width();
            double gy = RegionExtractor.MEASUREMENT_REGION.y() + b.y() * RegionExtractor.MEASUREMENT_REGION.height();
            double gw = b.width() * RegionExtractor.MEASUREMENT_REGION.width();
            double gh = b.height() * RegionExtractor.MEASUREMENT_REGION.height();
            combinedBoxes.add(new WordBox(b.text(), gx, gy, gw, gh, b.confidence()));
        }

        // Add full image word boxes
        combinedBoxes.addAll(fullRes.wordBoxes());

        double avgConfidence = fullRes.confidence() > 0 ? fullRes.confidence() : 80.0;
        long elapsed = System.currentTimeMillis() - start;

        log.info("Region-aware OCR completed in {}ms: {} words extracted, conf={}%", elapsed, combinedBoxes.size(), avgConfidence);

        return new OcrResult(
                combinedRawText,
                avgConfidence,
                pageCount,
                elapsed,
                !combinedRawText.isBlank(),
                combinedRawText.isBlank() ? "No text detected" : null,
                orientation,
                imgW,
                imgH,
                combinedBoxes,
                processedImagePath
        );
    }
}
