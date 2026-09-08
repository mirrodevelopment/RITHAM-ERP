package com.ritham.erp.module.migration;

import com.ritham.erp.module.migration.service.PdfPageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfPageServiceTest {

    private PdfPageService pdfPageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pdfPageService = new PdfPageService();
    }

    private Path createTestPdf(int pageCount, String textPrefix) throws IOException {
        Path pdfPath = tempDir.resolve("test_" + pageCount + "pages.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= pageCount; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                    cs.newLineAtOffset(100, 700);
                    cs.showText(textPrefix + " Page " + i);
                    cs.endText();
                }
            }
            doc.save(pdfPath.toFile());
        }
        return pdfPath;
    }

    @Test
    @DisplayName("getPageCount returns accurate page counts for 1-page and multi-page PDFs")
    void testGetPageCount() throws IOException {
        Path singlePagePdf = createTestPdf(1, "Single");
        Path threePagePdf = createTestPdf(3, "Triple");

        assertThat(pdfPageService.getPageCount(singlePagePdf)).isEqualTo(1);
        assertThat(pdfPageService.getPageCount(threePagePdf)).isEqualTo(3);
    }

    @Test
    @DisplayName("renderAllPages renders every page to a 300 DPI PNG and cleans up memory")
    void testRenderAllPages() throws IOException {
        Path pdfPath = createTestPdf(3, "Tailoring Sheet");
        Path outputDir = tempDir.resolve("rendered_output");

        List<Path> pages = pdfPageService.renderAllPages(pdfPath, outputDir, "doc_slip");

        assertThat(pages).hasSize(3);
        for (int i = 0; i < 3; i++) {
            Path pagePath = pages.get(i);
            assertThat(pagePath).exists();
            assertThat(pagePath.getFileName().toString()).isEqualTo(String.format("doc_slip_page_%03d.png", i + 1));

            BufferedImage img = ImageIO.read(pagePath.toFile());
            assertThat(img).isNotNull();
            assertThat(img.getWidth()).isGreaterThan(1000); // 300 DPI standard letter/A4 > 2000px
            assertThat(img.getHeight()).isGreaterThan(1000);
        }
    }

    @Test
    @DisplayName("renderSinglePage renders a specific 1-based page index")
    void testRenderSinglePage() throws IOException {
        Path pdfPath = createTestPdf(2, "Measurement");
        Path page2Output = tempDir.resolve("custom_page_2.png");

        Path result = pdfPageService.renderSinglePage(pdfPath, 2, page2Output);

        assertThat(result).exists();
        BufferedImage img = ImageIO.read(result.toFile());
        assertThat(img).isNotNull();
    }

    @Test
    @DisplayName("renderSinglePage throws exception for out of range page numbers")
    void testRenderSinglePageOutOfRange() throws IOException {
        Path pdfPath = createTestPdf(2, "Measurement");
        Path out = tempDir.resolve("out.png");

        assertThatThrownBy(() -> pdfPageService.renderSinglePage(pdfPath, 0, out))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pdfPageService.renderSinglePage(pdfPath, 3, out))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
