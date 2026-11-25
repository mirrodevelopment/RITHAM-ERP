package com.ritham.erp.module.migration;

import com.ritham.erp.module.migration.dto.ExtractedData;
import com.ritham.erp.module.migration.service.ExtractionService;
import com.ritham.erp.module.migration.service.ocr.DocumentOrientationDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class OcrOrientationTest {

    @Test
    @DisplayName("Orientation detector handles synthetic landscape images without external local files")
    void syntheticLandscapeAndExtractionWorkWithoutExternalFixtures() {
        BufferedImage synthetic = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = synthetic.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 500);
        g.setColor(Color.BLACK);
        g.drawString("BLOUSE", 360, 50);
        g.drawString("Name: Meera", 40, 100);
        g.drawString("Ph. No.: 9876543210", 40, 130);
        g.dispose();

        DocumentOrientationDetector detector = new DocumentOrientationDetector();
        DocumentOrientationDetector.OrientationResult oriented = detector.detectAndOrient(synthetic, null);
        assertThat(oriented).isNotNull();
        assertThat(oriented.angle()).isIn(0, 180);
        assertThat(oriented.orientedImage()).isNotNull();

        ExtractionService extractionService = new ExtractionService(new ObjectMapper());
        String sampleOcr = """
                BLOUSE
                Name: Meera
                Ph. No.: 9876543210
                Or. Date: 10/05/2025
                Due Date: 18/05/2025
                LTH 14
                SHO 14.5
                H.S. 6.5
                H.L. 10
                H.LO 11
                AK 15.5
                AM 12
                BN 9
                FN 6.5
                DP-1 9.5
                DP-1 11
                B-1 34
                B-2 36
                B-3 30
                F.HOOK Hook
                B.HOOK No
                """;
        ExtractedData data = extractionService.extract(sampleOcr);
        assertThat(data.order().garmentType()).isEqualTo("BLOUSE");
        assertThat(data.customer().customerName()).isEqualTo("Meera");
        assertThat(data.customer().customerMobile()).isEqualTo("9876543210");
        assertThat(data.order().measurements()).isNotEmpty();
    }
}
