package com.ritham.erp.module.order.service;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.order.dto.OrderMapper;
import com.ritham.erp.module.order.dto.OrderResponse;
import com.ritham.erp.module.order.entity.CustomerOrder;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import com.ritham.erp.module.order.repository.OrderMeasurementRepository;
import com.ritham.erp.module.order.repository.OrderPaymentRepository;
import com.ritham.erp.security.BranchContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ritham.erp.module.production.repository.ProductionStageRepository;

@ExtendWith(MockitoExtension.class)
class OrderStatusTransitionAndDeliveryTest {

    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private OrderMeasurementRepository orderMeasurementRepository;
    @Mock
    private OrderPaymentRepository orderPaymentRepository;
    @Mock
    private ProductionStageRepository productionStageRepository;
    @Spy
    private OrderMapper orderMapper = new OrderMapper();

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        BranchContext.clear(); // Global admin unrestricted
    }

    @AfterEach
    void tearDown() {
        BranchContext.clear();
    }

    // ── Order State Transitions (TASK-010) ─────────────────────────────────

    @Test
    @DisplayName("Idempotent transition to same status is allowed")
    void idempotentTransition_IsAllowed() {
        assertDoesNotThrow(() -> orderService.validateStatusTransition("PENDING", "PENDING"));
        assertDoesNotThrow(() -> orderService.validateStatusTransition("DESIGNING", "DESIGNING"));
        assertDoesNotThrow(() -> orderService.validateStatusTransition("DELIVERED", "DELIVERED"));
    }

    @Test
    @DisplayName("Transition to unknown status throws INVALID_ORDER_STATUS_TRANSITION")
    void unknownStatus_ThrowsException() {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("PENDING", "FLYING_STAGE"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELIVERED", "DELIVERY", "CUTTING", "STITCHING", "READY_TO_DELIVERY"})
    @DisplayName("CANCELLED is a terminal status: cannot transition out")
    void cancelledIsTerminal(String targetStatus) {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("CANCELLED", targetStatus));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUTTING", "STITCHING", "DESIGNING", "CANCELLED", "PENDING"})
    @DisplayName("DELIVERED / COMPLETED is a terminal status: cannot transition out")
    void deliveredIsTerminal(String targetStatus) {
        AppException ex1 = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("DELIVERED", targetStatus));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex1.getErrorCode());

        AppException ex2 = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("COMPLETED", targetStatus));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex2.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DESIGNING", "CUTTING", "STITCHING", "FINISHING", "READY_TO_DELIVERY"})
    @DisplayName("Active production stages cannot revert back to PENDING")
    void activeStagesCannotRevertToPending(String activeStage) {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition(activeStage, "PENDING"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
    }

    @Test
    @DisplayName("Valid forward production stage transitions are permitted")
    void validForwardTransitions_ArePermitted() {
        assertDoesNotThrow(() -> orderService.validateStatusTransition("PENDING", "DESIGNING"));
        assertDoesNotThrow(() -> orderService.validateStatusTransition("DESIGNING", "CUTTING"));
        assertDoesNotThrow(() -> orderService.validateStatusTransition("CUTTING", "STITCHING"));
        assertDoesNotThrow(() -> orderService.validateStatusTransition("STITCHING", "READY_TO_DELIVERY"));
        assertDoesNotThrow(() -> orderService.validateStatusTransition("READY_TO_DELIVERY", "DELIVERED"));
    }

    @Test
    @DisplayName("Rework from READY_TO_DELIVERY back to active production stage is permitted")
    void reworkFromReadyToDelivery_IsPermitted() {
        assertDoesNotThrow(() -> orderService.validateStatusTransition("READY_TO_DELIVERY", "STITCHING"));
        assertDoesNotThrow(() -> orderService.validateStatusTransition("READY_TO_DELIVERY", "CUTTING"));
    }

    // ── Delivery Validation (TASK-011) ─────────────────────────────────────

    @Test
    @DisplayName("Direct delivery from PENDING throws INVALID_ORDER_STATUS_TRANSITION")
    void deliveryFromPending_IsRejected() {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("PENDING", "DELIVERED"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("READY_TO_DELIVERY"));
    }

    @Test
    @DisplayName("Direct delivery from active production stage (e.g. CUTTING) throws INVALID_ORDER_STATUS_TRANSITION")
    void deliveryFromActiveStage_IsRejected() {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("CUTTING", "DELIVERED"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("READY_TO_DELIVERY"));
    }

    @Test
    @DisplayName("Delivery from READY_TO_DELIVERY via updateStatus succeeds and sets status to DELIVERED")
    void deliveryFromReadyToDelivery_Succeeds() {
        CustomerOrder order = CustomerOrder.builder()
                .id(1L)
                .orderNumber("ORD-202609-0001")
                .customerMobile("9876543210")
                .status("READY_TO_DELIVERY")
                .paymentStatus("PAID")
                .totalAmount(new BigDecimal("1500.00"))
                .paidAmount(new BigDecimal("1500.00"))
                .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse res = orderService.updateStatus(1L, "DELIVERED", null, null, null, null, "Customer Self");
        assertNotNull(res);
        assertEquals("DELIVERED", res.getStatus());
    }

    // ── Dynamic Production Stages (TASK-025) ───────────────────────────────

    @Test
    @DisplayName("Active dynamic custom stage is permitted from PENDING and from other production stages")
    void dynamicCustomStage_whenActive_isPermittedInWorkflow() {
        when(productionStageRepository.existsByStageKeyIgnoreCaseAndIsActiveTrue("EMBROIDERY")).thenReturn(true);
        when(productionStageRepository.existsByStageKeyIgnoreCaseAndIsActiveTrue("SPECIAL_WASH")).thenReturn(true);

        // PENDING -> custom stage
        assertDoesNotThrow(() -> orderService.validateStatusTransition("PENDING", "EMBROIDERY"));

        // Standard stage -> custom stage
        assertDoesNotThrow(() -> orderService.validateStatusTransition("CUTTING", "EMBROIDERY"));

        // Custom stage -> another custom stage
        assertDoesNotThrow(() -> orderService.validateStatusTransition("EMBROIDERY", "SPECIAL_WASH"));

        // Custom stage -> standard stage
        assertDoesNotThrow(() -> orderService.validateStatusTransition("SPECIAL_WASH", "STITCHING"));

        // Custom stage -> READY_TO_DELIVERY
        assertDoesNotThrow(() -> orderService.validateStatusTransition("EMBROIDERY", "READY_TO_DELIVERY"));

        // Custom stage -> CANCELLED
        assertDoesNotThrow(() -> orderService.validateStatusTransition("EMBROIDERY", "CANCELLED"));
    }

    @Test
    @DisplayName("Rework from READY_TO_DELIVERY back to an active dynamic custom stage is permitted")
    void dynamicCustomStage_reworkFromReadyToDelivery_isPermitted() {
        when(productionStageRepository.existsByStageKeyIgnoreCaseAndIsActiveTrue("EMBROIDERY")).thenReturn(true);

        assertDoesNotThrow(() -> orderService.validateStatusTransition("READY_TO_DELIVERY", "EMBROIDERY"));
    }

    @Test
    @DisplayName("Active dynamic custom stage cannot revert back to PENDING")
    void dynamicCustomStage_cannotRevertToPending() {
        when(productionStageRepository.existsByStageKeyIgnoreCaseAndIsActiveTrue("EMBROIDERY")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("EMBROIDERY", "PENDING"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Cannot revert order"));
    }

    @Test
    @DisplayName("Order cannot transition directly from active dynamic custom stage to DELIVERED")
    void dynamicCustomStage_cannotDeliverDirectly() {
        when(productionStageRepository.existsByStageKeyIgnoreCaseAndIsActiveTrue("EMBROIDERY")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("EMBROIDERY", "DELIVERED"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("READY_TO_DELIVERY"));
    }

    @Test
    @DisplayName("CANCELLED order cannot transition to a dynamic custom stage")
    void dynamicCustomStage_cancelledOrderCannotTransitionToCustomStage() {
        when(productionStageRepository.existsByStageKeyIgnoreCaseAndIsActiveTrue("EMBROIDERY")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("CANCELLED", "EMBROIDERY"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Cannot transition order from CANCELLED"));
    }

    @Test
    @DisplayName("DELIVERED order cannot transition to a dynamic custom stage")
    void dynamicCustomStage_deliveredOrderCannotTransitionToCustomStage() {
        when(productionStageRepository.existsByStageKeyIgnoreCaseAndIsActiveTrue("EMBROIDERY")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("DELIVERED", "EMBROIDERY"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Cannot transition order from DELIVERED"));
    }

    @Test
    @DisplayName("Inactive or nonexistent custom stage throws INVALID_ORDER_STATUS_TRANSITION")
    void dynamicCustomStage_inactiveOrNonexistent_isRejected() {
        when(productionStageRepository.existsByStageKeyIgnoreCaseAndIsActiveTrue("INACTIVE_STAGE")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("PENDING", "INACTIVE_STAGE"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Unknown or invalid target status"));
    }
}
