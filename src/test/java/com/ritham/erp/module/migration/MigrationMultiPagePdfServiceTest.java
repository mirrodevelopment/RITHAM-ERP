package com.ritham.erp.module.migration;

import com.ritham.erp.module.branch.entity.Branch;
import com.ritham.erp.module.branch.repository.BranchRepository;
import com.ritham.erp.module.migration.entity.MigrationBatch;
import com.ritham.erp.module.migration.entity.MigrationDocument;
import com.ritham.erp.module.migration.enums.DocumentType;
import com.ritham.erp.module.migration.enums.MigrationDocumentStatus;
import com.ritham.erp.module.migration.repository.MigrationBatchRepository;
import com.ritham.erp.module.migration.repository.MigrationDocumentRepository;
import com.ritham.erp.module.migration.repository.MigrationImportRepository;
import com.ritham.erp.module.migration.repository.MigrationReviewRepository;
import com.ritham.erp.module.migration.service.CustomerMatchingService;
import com.ritham.erp.module.migration.service.ExtractionService;
import com.ritham.erp.module.migration.service.MigrationBatchService;
import com.ritham.erp.module.migration.service.OcrService;
import com.ritham.erp.module.migration.service.PdfPageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrationMultiPagePdfServiceTest {

    @Mock private MigrationBatchRepository batchRepository;
    @Mock private MigrationDocumentRepository documentRepository;
    @Mock private MigrationReviewRepository reviewRepository;
    @Mock private MigrationImportRepository importRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private OcrService ocrService;
    @Mock private ExtractionService extractionService;
    @Mock private CustomerMatchingService matchingService;
    @Mock private PdfPageService pdfPageService;

    @InjectMocks
    private MigrationBatchService batchService;

    @TempDir
    Path tempUploadDir;

    private MigrationBatch testBatch;
    private Branch testBranch;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(batchService, "uploadDir", tempUploadDir.toString());
        ReflectionTestUtils.setField(batchService, "ocrLanguage", "eng");

        testBranch = Branch.builder()
                .id(1L)
                .branchCode("CBE")
                .name("Coimbatore Branch")
                .build();

        testBatch = MigrationBatch.builder()
                .id(100L)
                .batchCode("BATCH-2024-CBE-001")
                .branch(testBranch)
                .sourceYear((short) 2024)
                .totalDocuments(0)
                .uploadedCount(0)
                .processedCount(0)
                .reviewedCount(0)
                .approvedCount(0)
                .importedCount(0)
                .failedCount(0)
                .build();
    }

    @Test
    @DisplayName("Single image upload registers exactly 1 document with pageNumber=1 and totalPages=1")
    void testUploadSingleImage() throws IOException {
        when(batchRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(testBatch));
        when(documentRepository.findMaxSequenceForPrefix(anyString(), anyInt())).thenReturn(0);
        when(documentRepository.save(any(MigrationDocument.class))).thenAnswer(i -> {
            MigrationDocument d = i.getArgument(0);
            d.setId(501L);
            return d;
        });

        MockMultipartFile imageFile = new MockMultipartFile(
                "file", "bill_slip.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        List<MigrationDocument> result = batchService.uploadDocuments(100L, imageFile);

        assertThat(result).hasSize(1);
        MigrationDocument doc = result.get(0);
        assertThat(doc.getPageNumber()).isEqualTo(1);
        assertThat(doc.getTotalPages()).isEqualTo(1);
        assertThat(doc.getFileType()).isEqualTo(DocumentType.IMAGE);
        assertThat(doc.getSourceFileName()).isEqualTo("bill_slip.jpg");
        assertThat(testBatch.getTotalDocuments()).isEqualTo(1);
        assertThat(testBatch.getUploadedCount()).isEqualTo(1);

        verify(pdfPageService, never()).getPageCount(any());
        verify(pdfPageService, never()).renderSinglePage(any(), anyInt(), any());
    }

    @Test
    @DisplayName("Multi-page PDF upload splits every page into an independent MigrationDocument")
    void testUploadMultiPagePdf() throws IOException {
        when(batchRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(testBatch));
        when(documentRepository.findMaxSequenceForPrefix(anyString(), anyInt())).thenReturn(10);
        when(pdfPageService.getPageCount(any(Path.class))).thenReturn(3);

        List<MigrationDocument> savedDocs = new ArrayList<>();
        when(documentRepository.save(any(MigrationDocument.class))).thenAnswer(i -> {
            MigrationDocument d = i.getArgument(0);
            d.setId((long) (600 + savedDocs.size()));
            savedDocs.add(d);
            return d;
        });

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "multi_orders.pdf", "application/pdf", new byte[]{37, 80, 68, 70});

        List<MigrationDocument> result = batchService.uploadDocuments(100L, pdfFile);

        assertThat(result).hasSize(3);
        verify(pdfPageService).getPageCount(any(Path.class));
        verify(pdfPageService, times(3)).renderSinglePage(any(Path.class), anyInt(), any(Path.class));

        for (int i = 0; i < 3; i++) {
            MigrationDocument doc = result.get(i);
            int pageNum = i + 1;
            assertThat(doc.getPageNumber()).isEqualTo(pageNum);
            assertThat(doc.getTotalPages()).isEqualTo(3);
            assertThat(doc.getFileType()).isEqualTo(DocumentType.PDF);
            assertThat(doc.getFileName()).isEqualTo("multi_orders.pdf (Page " + pageNum + "/3)");
            assertThat(doc.getSourceFileName()).isEqualTo("multi_orders.pdf");
            assertThat(doc.getDocumentCode()).isEqualTo("HIST-2024-CBE-0000" + (10 + pageNum));
            assertThat(doc.getFilePath()).endsWith("page_00" + pageNum + ".png");
        }

        assertThat(testBatch.getTotalDocuments()).isEqualTo(3);
        assertThat(testBatch.getUploadedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("approveAllPages marks all sibling pages of a multi-page PDF as VERIFIED")
    void testApproveAllPages() {
        MigrationDocument p1 = MigrationDocument.builder()
                .id(701L)
                .batch(testBatch)
                .documentCode("HIST-2024-CBE-000001")
                .pageNumber(1)
                .totalPages(2)
                .sourceFilePath("/path/to/orders.pdf")
                .reviewStatus(MigrationDocumentStatus.REVIEW_REQUIRED.name())
                .build();

        MigrationDocument p2 = MigrationDocument.builder()
                .id(702L)
                .batch(testBatch)
                .documentCode("HIST-2024-CBE-000002")
                .pageNumber(2)
                .totalPages(2)
                .sourceFilePath("/path/to/orders.pdf")
                .reviewStatus(MigrationDocumentStatus.REVIEW_REQUIRED.name())
                .build();

        when(documentRepository.findByIdWithDetails(701L)).thenReturn(Optional.of(p1));
        when(documentRepository.findByBatchIdAndSourceFilePathOrderByPageNumberAsc(100L, "/path/to/orders.pdf"))
                .thenReturn(List.of(p1, p2));
        when(documentRepository.save(any(MigrationDocument.class))).thenAnswer(i -> i.getArgument(0));

        List<MigrationDocument> approved = batchService.approveAllPages(701L);

        assertThat(approved).hasSize(2);
        assertThat(p1.getReviewStatus()).isEqualTo(MigrationDocumentStatus.VERIFIED.name());
        assertThat(p2.getReviewStatus()).isEqualTo(MigrationDocumentStatus.VERIFIED.name());
        assertThat(testBatch.getApprovedCount()).isEqualTo(2);
    }
}
