package com.ritham.erp.module.migration;

import com.ritham.erp.module.migration.dto.ExtractedData;
import com.ritham.erp.module.migration.service.ExtractionService;
import net.sourceforge.tess4j.Tesseract;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class OcrOrientationTest {

    @Test
    void testImprovedExtraction() throws Exception {
        ExtractionService extractionService = new ExtractionService(new tools.jackson.databind.ObjectMapper());

        File file = new File("C:/Users/admin/.gemini/antigravity-ide/brain/8cbdb716-cbc0-46d1-aced-3ac122e4a79f/test_sample_2.png");
        BufferedImage orig = ImageIO.read(file);
        BufferedImage rotated = rotate(orig, 270);

        Tesseract tess = new Tesseract();
        tess.setDatapath("d:/ritham-erp/tessdata");
        tess.setLanguage("eng");

        tess.setPageSegMode(6);
        String bodyOcr = tess.doOCR(rotated);

        int headerHeight = (int) (rotated.getHeight() * 0.25);
        BufferedImage header = rotated.getSubimage(0, 0, rotated.getWidth(), headerHeight);
        tess.setPageSegMode(11);
        String headerOcr = tess.doOCR(header);

        String combined = headerOcr + "\n" + bodyOcr;

        System.out.println("=== COMBINED RAW OCR ===");
        System.out.println(combined);

        // Let's verify our improved patterns
        ExtractedData data = extractionService.extract(combined);
        System.out.println("=== EXTRACTED DATA ===");
        System.out.println("Garment: " + data.order().garmentType());
        System.out.println("Customer Name: " + data.customer().customerName());
        System.out.println("Customer Mobile: " + data.customer().customerMobile());
        System.out.println("Order Date: " + data.order().orderDate());
        System.out.println("Due Date: " + data.order().deliveryDate());
        System.out.println("Erode: " + data.order().erode());
        System.out.println("Measurements: " + data.order().measurements());
    }

    private BufferedImage rotate(BufferedImage img, int angle) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage rotated;
        if (angle == 90 || angle == 270) {
            rotated = new BufferedImage(h, w, img.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : img.getType());
        } else {
            rotated = new BufferedImage(w, h, img.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : img.getType());
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
