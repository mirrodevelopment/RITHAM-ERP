package com.ritham.erp.module.migration.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for multi-page PDF processing:
 * - Counting total pages in a PDF
 * - Rendering each page to a high-resolution (300 DPI) PNG image
 * - Ensuring low memory footprint by processing and flushing page-by-page.
 */
@Service
@Slf4j
public class PdfPageService {

    /** 300 DPI produces crisp OCR text and measurement preview */
    public static final float PDF_RENDER_DPI = 300.0f;

    /**
     * Get the total number of pages in the given PDF file.
     */
    public int getPageCount(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            return document.getNumberOfPages();
        }
    }

    /**
     * Renders every page of a PDF document to individual PNG images in the specified output directory.
     *
     * @param pdfPath    Path to the source PDF file
     * @param outputDir  Directory where page images will be stored
     * @param filePrefix Prefix for generated image files (e.g., "doc")
     * @return Ordered list of paths to the rendered PNG images (index 0 = page 1)
     */
    public List<Path> renderAllPages(Path pdfPath, Path outputDir, String filePrefix) throws IOException {
        List<Path> renderedPages = new ArrayList<>();
        Files.createDirectories(outputDir);

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            log.info("Rendering {} pages from PDF: {}", totalPages, pdfPath.getFileName());

            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                int pageNum = pageIndex + 1;
                Path pageImagePath = outputDir.resolve(String.format("%s_page_%03d.png", filePrefix, pageNum));

                // Render page at 300 DPI RGB
                BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, PDF_RENDER_DPI, ImageType.RGB);
                pageImage = ensureLandscape(pageImage);
                ImageIO.write(pageImage, "png", pageImagePath.toFile());

                // Flush image memory immediately to avoid high heap usage on large PDFs
                pageImage.flush();

                renderedPages.add(pageImagePath);
                log.debug("Rendered PDF page {}/{} -> {}", pageNum, totalPages, pageImagePath.getFileName());
            }
        }

        return renderedPages;
    }

    /**
     * Renders a single specific page (1-based pageNumber) from a PDF.
     */
    public Path renderSinglePage(Path pdfPath, int pageNumber, Path outputPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageIndex = pageNumber - 1;
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                throw new IllegalArgumentException("Invalid page number " + pageNumber + ". Total pages: " + document.getNumberOfPages());
            }

            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, PDF_RENDER_DPI, ImageType.RGB);
            pageImage = ensureLandscape(pageImage);
            ImageIO.write(pageImage, "png", outputPath.toFile());
            pageImage.flush();

            return outputPath;
        }
    }

    /**
     * Renders multiple specific pages in a single document pass to their respective output paths.
     * Dramatically faster for large (e.g. 50-page) PDFs as the PDF is loaded only once.
     */
    public void renderPagesToPaths(Path pdfPath, List<Path> targetImagePaths) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int count = Math.min(document.getNumberOfPages(), targetImagePaths.size());
            log.info("Single-pass rendering {} pages for PDF: {}", count, pdfPath.getFileName());

            for (int i = 0; i < count; i++) {
                Path outputPath = targetImagePaths.get(i);
                if (outputPath.getParent() != null) {
                    Files.createDirectories(outputPath.getParent());
                }
                BufferedImage pageImage = renderer.renderImageWithDPI(i, PDF_RENDER_DPI, ImageType.RGB);
                pageImage = ensureLandscape(pageImage);
                ImageIO.write(pageImage, "png", outputPath.toFile());
                pageImage.flush();
            }
        }
    }

    private BufferedImage ensureLandscape(BufferedImage img) {
        // Measurement sheets are landscape forms — if PDF page is vertical, rotate to landscape
        if (img.getWidth() < img.getHeight()) {
            return rotateImage(img, 270);
        }
        return img;
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
