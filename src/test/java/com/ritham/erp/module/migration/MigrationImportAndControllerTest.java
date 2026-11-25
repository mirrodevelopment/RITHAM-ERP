package com.ritham.erp.module.migration;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.module.branch.entity.Branch;
import com.ritham.erp.module.customer.dto.SaveCustomerMeasurementRequest;
import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.repository.CustomerMeasurementRepository;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.customer.service.CustomerMeasurementService;
import com.ritham.erp.module.migration.controller.MigrationController;
import com.ritham.erp.module.migration.dto.ExtractedData;
import com.ritham.erp.module.migration.dto.MigrationDocumentResponse;
import com.ritham.erp.module.migration.dto.MigrationReviewRequest;
import com.ritham.erp.module.migration.entity.MigrationBatch;
import com.ritham.erp.module.migration.entity.MigrationDocument;
import com.ritham.erp.module.migration.entity.MigrationImport;
import com.ritham.erp.module.migration.enums.DocumentType;
import com.ritham.erp.module.migration.enums.MigrationDocumentStatus;
import com.ritham.erp.module.migration.repository.MigrationDocumentRepository;
import com.ritham.erp.module.migration.repository.MigrationImportRepository;
import com.ritham.erp.module.migration.service.ExtractionService;
import com.ritham.erp.module.migration.service.MigrationBatchService;
import com.ritham.erp.module.migration.service.MigrationImportService;
import com.ritham.erp.module.order.entity.CustomerOrder;
import com.ritham.erp.module.order.entity.OrderMeasurement;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import com.ritham.erp.module.order.repository.OrderMeasurementRepository;
import com.ritham.erp.module.order.repository.OrderPaymentRepository;
import com.ritham.erp.security.BranchContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrationImportAndControllerTest {

    @Mock private MigrationDocumentRepository documentRepository;
    @Mock private MigrationImportRepository importRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerMeasurementService customerMeasurementService;
    @Mock private CustomerOrderRepository orderRepository;
    @Mock private OrderMeasurementRepository measurementRepository;
    @Mock private OrderPaymentRepository paymentRepository;
    @Mock private ExtractionService extractionService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private MigrationImportService importService;

    @AfterEach
    void tearDown() {
        BranchContext.clear();
    }

    @Test
    @DisplayName("importDocument rejects REVIEW_REQUIRED and REJECTED documents without explicit APPROVED action")
    void importBlocksUnverifiedDocuments() {
        MigrationBatch batch = MigrationBatch.builder().id(1L).branch(Branch.builder().id(1L).build()).build();
        MigrationDocument unverified = MigrationDocument.builder()
                .id(100L).documentCode("HIST-1").batch(batch)
                .extractedDataJson("{\"customer\":{\"customerName\":\"Test\",\"mobileNo\":\"9876543210\"}}")
                .reviewStatus(MigrationDocumentStatus.REVIEW_REQUIRED.name())
                .importStatus(MigrationDocumentStatus.UPLOADED.name())
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(unverified));

        AppException ex = assertThrows(AppException.class, () -> importService.importDocument(100L, null));
        assertEquals(ErrorCode.OPERATION_NOT_ALLOWED, ex.getErrorCode());

        unverified.setReviewStatus(MigrationDocumentStatus.REJECTED.name());
        AppException exRejected = assertThrows(AppException.class, () -> importService.importDocument(100L, null));
        assertEquals(ErrorCode.OPERATION_NOT_ALLOWED, exRejected.getErrorCode());
    }

    @Test
    @DisplayName("importDocument enforces BranchContext matching batch branch")
    void importEnforcesBranchIsolation() {
        BranchContext.setBranchId(1L);
        MigrationBatch branch2Batch = MigrationBatch.builder().id(2L).branch(Branch.builder().id(2L).build()).build();
        MigrationDocument doc = MigrationDocument.builder()
                .id(101L).documentCode("HIST-2").batch(branch2Batch)
                .reviewStatus(MigrationDocumentStatus.VERIFIED.name())
                .importStatus(MigrationDocumentStatus.UPLOADED.name())
                .build();

        when(documentRepository.findById(101L)).thenReturn(Optional.of(doc));

        AppException ex = assertThrows(AppException.class, () -> importService.importDocument(101L, null));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("importDocument keeps updateCustomerProfile=false when req is null and normalizes keys when true")
    void importProfileDefaultAndMeasurementKeyNormalization() {
        MigrationBatch batch = MigrationBatch.builder().id(1L).batchCode("BATCH-1")
                .branch(Branch.builder().id(1L).branchCode("BR1").build()).build();
        MigrationDocument doc = MigrationDocument.builder()
                .id(102L).documentCode("HIST-3").batch(batch).sourceYear((short) 2024)
                .extractedDataJson("{\"mock\":true}")
                .reviewStatus(MigrationDocumentStatus.VERIFIED.name())
                .importStatus(MigrationDocumentStatus.UPLOADED.name())
                .build();

        ExtractedData extracted = new ExtractedData(
                new ExtractedData.ExtractedCustomerData("Meera", 0.95, "9876543210", 0.95, false),
                new ExtractedData.ExtractedOrderData(
                        "2024-05-01", 0.9, "2024-05-10", 0.9,
                        "BLOUSE", 0.95, "WITHOUT_LINING",
                        Map.of("LTH", "14", "DP-1-1", "9.5", "DP-1-2", "11", "F-HOOK", "Yes"),
                        Map.of(), new BigDecimal("500"), 0.9, BigDecimal.ZERO, 0.9,
                        true, false, "102"
                ),
                0.95
        );

        when(documentRepository.findById(102L)).thenReturn(Optional.of(doc));
        when(extractionService.fromJson(anyString())).thenReturn(extracted);
        when(extractionService.calculateCompletionRatio(extracted)).thenReturn(0.8);
        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
            CustomerOrder o = inv.getArgument(0);
            o.setId(55L);
            return o;
        });
        when(measurementRepository.save(any(OrderMeasurement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(importRepository.save(any(MigrationImport.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Null reviewRequest -> must NOT overwrite customer profile
        importService.importDocument(102L, null);
        verify(customerMeasurementService, never()).saveOrUpdate(any());

        // 2. Reset import and review statuses, pass explicit APPROVED + updateCustomerProfile=true -> normalizes keys & avoids double counting
        doc.setImportStatus(MigrationDocumentStatus.UPLOADED.name());
        doc.setReviewStatus(MigrationDocumentStatus.VERIFIED.name());
        batch.setApprovedCount(1);
        batch.setReviewedCount(1);
        MigrationReviewRequest syncReq = new MigrationReviewRequest("APPROVED", null, null, null, true);

        importService.importDocument(102L, syncReq);
        assertEquals(1, batch.getApprovedCount());
        assertEquals(1, batch.getReviewedCount());

        ArgumentCaptor<SaveCustomerMeasurementRequest> measCaptor = ArgumentCaptor.forClass(SaveCustomerMeasurementRequest.class);
        verify(customerMeasurementService).saveOrUpdate(measCaptor.capture());
        Map<String, String> savedKeys = measCaptor.getValue().getMeasurements();
        assertEquals("9.5", savedKeys.get("DP-1"));
        assertEquals("11", savedKeys.get("DP-2"));
        assertEquals("Yes", savedKeys.get("F.HOOK"));
    }

    @Test
    @DisplayName("MigrationController detects measurementExists when extracted JSON uses mobileNo key")
    void migrationControllerDetectsMobileNoMeasurementExists() {
        MigrationBatchService batchService = mock(MigrationBatchService.class);
        MigrationImportService mockImportService = mock(MigrationImportService.class);
        CustomerMeasurementRepository custMeasRepo = mock(CustomerMeasurementRepository.class);
        MigrationController controller = new MigrationController(batchService, mockImportService, custMeasRepo);

        MigrationBatch batch = MigrationBatch.builder().id(1L).batchCode("B1").build();
        MigrationDocument doc = MigrationDocument.builder()
                .id(77L)
                .documentCode("HIST-77")
                .batch(batch)
                .fileName("slip.png")
                .fileType(DocumentType.IMAGE)
                .sourceYear((short) 2024)
                .extractedDataJson("{\"customer\":{\"customerName\":\"Kavitha\",\"mobileNo\":\"9840123456\"},\"order\":{\"garmentType\":\"BLOUSE\"}}")
                .build();

        when(batchService.getDocument(77L)).thenReturn(doc);
        when(custMeasRepo.existsByCustomerMobileAndGarmentType("9840123456", "BLOUSE")).thenReturn(true);

        ResponseEntity<ApiResponse<MigrationDocumentResponse>> resp = controller.getDocument(77L);
        assertTrue(resp.getStatusCode().is2xxSuccessful());
        assertNotNull(resp.getBody());
        assertTrue(Boolean.TRUE.equals(resp.getBody().getData().measurementExists()));
    }
}
