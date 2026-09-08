package com.ritham.erp.module.migration;

import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.module.migration.controller.MigrationController;
import com.ritham.erp.module.migration.controller.MigrationController.MigrationImportSummary;
import com.ritham.erp.module.migration.entity.MigrationDocument;
import com.ritham.erp.module.migration.entity.MigrationImport;
import com.ritham.erp.module.migration.enums.MigrationDocumentStatus;
import com.ritham.erp.module.migration.service.MigrationBatchService;
import com.ritham.erp.module.migration.service.MigrationImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrationControllerMultiPageTest {

    @Mock
    private MigrationBatchService batchService;

    @Mock
    private MigrationImportService importService;

    private MigrationController controller;

    @BeforeEach
    void setUp() {
        controller = new MigrationController(batchService, importService);
    }

    @Test
    @DisplayName("importAllPages imports all VERIFIED pages in a multi-page PDF group")
    void testImportAllPages() {
        MigrationDocument p1 = MigrationDocument.builder()
                .id(801L)
                .documentCode("HIST-2024-CBE-000001")
                .pageNumber(1)
                .totalPages(3)
                .reviewStatus(MigrationDocumentStatus.VERIFIED.name())
                .importStatus(MigrationDocumentStatus.UPLOADED.name())
                .build();

        MigrationDocument p2 = MigrationDocument.builder()
                .id(802L)
                .documentCode("HIST-2024-CBE-000002")
                .pageNumber(2)
                .totalPages(3)
                .reviewStatus(MigrationDocumentStatus.REVIEW_REQUIRED.name()) // Not yet verified
                .importStatus(MigrationDocumentStatus.UPLOADED.name())
                .build();

        MigrationDocument p3 = MigrationDocument.builder()
                .id(803L)
                .documentCode("HIST-2024-CBE-000003")
                .pageNumber(3)
                .totalPages(3)
                .reviewStatus(MigrationDocumentStatus.VERIFIED.name())
                .importStatus(MigrationDocumentStatus.UPLOADED.name())
                .build();

        when(batchService.getSiblingPages(801L)).thenReturn(List.of(p1, p2, p3));

        MigrationImport imp1 = MigrationImport.builder()
                .id(901L)
                .document(p1)
                .orderId(101L)
                .customerMobile("9840123456")
                .importStatus("IMPORTED")
                .importedAt(java.time.OffsetDateTime.now())
                .build();

        MigrationImport imp3 = MigrationImport.builder()
                .id(903L)
                .document(p3)
                .orderId(103L)
                .customerMobile("9840999999")
                .importStatus("IMPORTED")
                .importedAt(java.time.OffsetDateTime.now())
                .build();

        when(importService.importDocument(801L, null)).thenReturn(imp1);
        when(importService.importDocument(803L, null)).thenReturn(imp3);

        ResponseEntity<ApiResponse<List<MigrationImportSummary>>> response = controller.importAllPages(801L);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        List<MigrationImportSummary> summaries = response.getBody().getData();
        assertThat(summaries).hasSize(2);

        // Verify page 1 and page 3 imported, page 2 (NEEDS_REVIEW) was skipped
        verify(importService).importDocument(801L, null);
        verify(importService, never()).importDocument(802L, null);
        verify(importService).importDocument(803L, null);
    }
}
