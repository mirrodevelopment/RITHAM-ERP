package com.ritham.erp.module.order.service;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.branch.entity.Branch;
import com.ritham.erp.module.branch.repository.BranchRepository;
import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.customer.service.CustomerMeasurementService;
import com.ritham.erp.module.order.dto.CreateOrderRequest;
import com.ritham.erp.module.order.dto.OrderMapper;
import com.ritham.erp.module.order.dto.OrderPaymentResponse;
import com.ritham.erp.module.order.dto.OrderResponse;
import com.ritham.erp.module.order.dto.RecordPaymentRequest;
import com.ritham.erp.module.order.entity.CustomerOrder;
import com.ritham.erp.module.order.entity.OrderPayment;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import com.ritham.erp.module.order.repository.OrderMeasurementRepository;
import com.ritham.erp.module.order.repository.OrderPaymentRepository;
import com.ritham.erp.security.BranchContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceTest {

    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private OrderMeasurementRepository orderMeasurementRepository;
    @Mock
    private OrderPaymentRepository orderPaymentRepository;
    @Mock
    private CustomerMeasurementService customerMeasurementService;
    @Mock
    private BranchRepository branchRepository;
    @Spy
    private OrderMapper orderMapper = new OrderMapper();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private com.ritham.erp.module.production.repository.ProductionStageRepository productionStageRepository;

    @InjectMocks
    private OrderService orderService;

    private Branch testBranch;
    private Customer testCustomer;
    private CustomerOrder testOrder;

    @BeforeEach
    void setUp() {
        BranchContext.clear();

        testBranch = Branch.builder()
                .id(1L)
                .branchCode("BR-001")
                .name("Chennai Main Branch")
                .build();

        testCustomer = Customer.builder()
                .customerName("Priya Sharma")
                .customerMobile("9876543210")
                .build();

        testOrder = CustomerOrder.builder()
                .id(101L)
                .orderNumber("ORD-20260905-0001")
                .customerMobile("9876543210")
                .totalAmount(new BigDecimal("3000.00"))
                .discountAmount(BigDecimal.ZERO)
                .advanceAmount(new BigDecimal("1000.00"))
                .paidAmount(new BigDecimal("1000.00"))
                .paymentMode("UPI")
                .paymentStatus("PARTIAL")
                .status("PENDING")
                .branch(testBranch)
                .receiverName("Staff User")
                .build();
    }

    @AfterEach
    void tearDown() {
        BranchContext.clear();
    }

    @Test
    void testCreateOrder_recordsAdvanceInPaymentLedger() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerMobile("9876543210");
        req.setTotalAmount(new BigDecimal("3000.00"));
        req.setAdvanceAmount(new BigDecimal("1000.00"));
        req.setDeliveryDate(LocalDateTime.now().plusDays(5));


        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
            CustomerOrder o = invocation.getArgument(0);
            o.setId(101L);
            return o;
        });

        orderService.createOrder(req);

        ArgumentCaptor<OrderPayment> captor = ArgumentCaptor.forClass(OrderPayment.class);
        verify(orderPaymentRepository, times(1)).save(captor.capture());

        OrderPayment captured = captor.getValue();
        assertEquals(101L, captured.getOrderId());
        assertEquals("ADVANCE", captured.getPaymentType());
        assertEquals("CASH", captured.getPaymentMethod());
        assertEquals(new BigDecimal("1000.00"), captured.getAmount());
        assertNotNull(captured.getPaymentDate());
    }

    @Test
    void testUpdateStatus_recordsFinalPaymentInLedger() {
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));
        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));
        when(orderRepository.save(any(CustomerOrder.class))).thenReturn(testOrder);

        orderService.updateStatus(101L, "COMPLETED", new BigDecimal("2000.00"), "CASH", null, null, "Receiver A", null);

        ArgumentCaptor<OrderPayment> captor = ArgumentCaptor.forClass(OrderPayment.class);
        verify(orderPaymentRepository, times(1)).save(captor.capture());

        OrderPayment captured = captor.getValue();
        assertEquals(101L, captured.getOrderId());
        assertEquals("FINAL", captured.getPaymentType());
        assertEquals("CASH", captured.getPaymentMethod());
        assertEquals(new BigDecimal("2000.00"), captured.getAmount());
    }

    @Test
    void testRecordPayment_verifiesOrderAmountBranchAndCollector() {
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(i -> i.getArgument(0));
        when(orderPaymentRepository.save(any(OrderPayment.class))).thenAnswer(i -> {
            OrderPayment p = i.getArgument(0);
            p.setId(555L);
            return p;
        });

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .amount(new BigDecimal("500.00"))
                .paymentMethod("CARD")
                .paymentType("PARTIAL")
                .collector("Cashier 1")
                .build();

        OrderPaymentResponse response = orderService.recordPayment(101L, req);

        assertNotNull(response);
        assertEquals(555L, response.getId());
        assertEquals(101L, response.getOrderId());
        assertEquals("ORD-20260905-0001", response.getOrderNumber());
        assertEquals(new BigDecimal("500.00"), response.getAmount());
        assertEquals("CARD", response.getPaymentMethod());
        assertEquals("PARTIAL", response.getPaymentType());
        assertEquals(1L, response.getBranchId());
        assertEquals("Chennai Main Branch", response.getBranchName());
        assertEquals("Cashier 1", response.getCollector());
    }

    @Test
    void testRecordPayment_deniesAccessFromDifferentBranch() {
        BranchContext.setBranchId(2L); // Caller from Coimbatore branch
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder)); // Order is in Chennai branch (1)

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .amount(new BigDecimal("500.00"))
                .paymentMethod("CASH")
                .build();

        AppException ex = assertThrows(AppException.class, () -> orderService.recordPayment(101L, req));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    void testGetOrderPayments_returnsAllPaymentsForOrder() {
        OrderPayment p1 = OrderPayment.builder()
                .id(1L)
                .orderId(101L)
                .paymentType("ADVANCE")
                .paymentMethod("UPI")
                .amount(new BigDecimal("1000.00"))
                .paymentDate(LocalDateTime.now().minusDays(2))
                .build();

        OrderPayment p2 = OrderPayment.builder()
                .id(2L)
                .orderId(101L)
                .paymentType("FINAL")
                .paymentMethod("CASH")
                .amount(new BigDecimal("2000.00"))
                .paymentDate(LocalDateTime.now())
                .build();

        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));
        when(orderPaymentRepository.findByOrderIdOrderByPaymentDateAsc(101L)).thenReturn(List.of(p1, p2));

        List<OrderPaymentResponse> list = orderService.getOrderPayments(101L);

        assertEquals(2, list.size());
        assertEquals("ADVANCE", list.get(0).getPaymentType());
        assertEquals(new BigDecimal("1000.00"), list.get(0).getAmount());
        assertEquals("FINAL", list.get(1).getPaymentType());
        assertEquals(new BigDecimal("2000.00"), list.get(1).getAmount());
        assertEquals(1L, list.get(0).getBranchId());
    }

    @Test
    void testCreateOrder_withExplicitPaymentMode_recordsModeInOrderAndLedger() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerMobile("9876543210");
        req.setTotalAmount(new BigDecimal("3000.00"));
        req.setAdvanceAmount(new BigDecimal("1000.00"));
        req.setPaymentMode("UPI");
        req.setDeliveryDate(LocalDateTime.now().plusDays(5));

        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
            CustomerOrder o = invocation.getArgument(0);
            o.setId(102L);
            return o;
        });

        OrderResponse res = orderService.createOrder(req);

        assertNotNull(res);
        assertEquals("UPI", res.getPaymentMode());

        ArgumentCaptor<OrderPayment> captor = ArgumentCaptor.forClass(OrderPayment.class);
        verify(orderPaymentRepository, times(1)).save(captor.capture());

        OrderPayment captured = captor.getValue();
        assertEquals(102L, captured.getOrderId());
        assertEquals("ADVANCE", captured.getPaymentType());
        assertEquals("UPI", captured.getPaymentMethod());
        assertEquals(new BigDecimal("1000.00"), captured.getAmount());
    }

    @Test
    void testUpdateStatus_doesNotOverwritePaymentMode_whenNoPaymentMade() {
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));
        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(i -> i.getArgument(0));

        // Attempting stage transition passing "CASH" as paymentMode, but finalPayment is null
        OrderResponse res = orderService.updateStatus(101L, "CUTTING", null, "CASH", null, null, null, null);

        assertEquals("CUTTING", res.getStatus());
        // Original intake paymentMode was "UPI", must NOT be overwritten by "CASH"
        assertEquals("UPI", res.getPaymentMode());
        assertEquals("UPI", testOrder.getPaymentMode());
    }

    @Test
    void testPreservePaymentHistory_multiplePaymentMethods_createsCompositeMode() {
        OrderPayment p1 = OrderPayment.builder()
                .id(1L)
                .orderId(101L)
                .paymentType("ADVANCE")
                .paymentMethod("UPI")
                .amount(new BigDecimal("1000.00"))
                .paymentDate(LocalDateTime.now().minusDays(2))
                .build();

        OrderPayment p2 = OrderPayment.builder()
                .id(2L)
                .orderId(101L)
                .paymentType("FINAL")
                .paymentMethod("CASH")
                .amount(new BigDecimal("2000.00"))
                .paymentDate(LocalDateTime.now())
                .build();

        testOrder.setStatus(OrderService.STATUS_READY_TO_DELIVERY);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));
        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));
        when(orderPaymentRepository.findByOrderIdOrderByPaymentDateAsc(101L)).thenReturn(List.of(p1, p2));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(i -> i.getArgument(0));

        // Final payment collected in CASH
        OrderResponse res = orderService.updateStatus(101L, "DELIVERED", new BigDecimal("2000.00"), "CASH", null, null, "Priya", null);

        assertEquals("DELIVERED", res.getStatus());
        assertEquals(new BigDecimal("3000.00"), res.getPaidAmount());
        assertEquals("PAID", res.getPaymentStatus());
        assertEquals("UPI, CASH", res.getPaymentMode());
    }

    @Test
    void testGetOrderById_includesPaymentsList() {
        OrderPayment p1 = OrderPayment.builder()
                .id(1L)
                .orderId(101L)
                .paymentType("ADVANCE")
                .paymentMethod("UPI")
                .amount(new BigDecimal("1000.00"))
                .paymentDate(LocalDateTime.now().minusDays(2))
                .build();

        OrderPayment p2 = OrderPayment.builder()
                .id(2L)
                .orderId(101L)
                .paymentType("FINAL")
                .paymentMethod("CASH")
                .amount(new BigDecimal("2000.00"))
                .paymentDate(LocalDateTime.now())
                .build();

        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));
        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));
        when(orderMeasurementRepository.findByOrderId(101L)).thenReturn(Optional.empty());
        when(orderPaymentRepository.findByOrderIdOrderByPaymentDateAsc(101L)).thenReturn(List.of(p1, p2));

        OrderResponse res = orderService.getOrderById(101L);

        assertNotNull(res);
        assertNotNull(res.getPayments());
        assertEquals(2, res.getPayments().size());
        assertEquals("ADVANCE", res.getPayments().get(0).getPaymentType());
        assertEquals("UPI", res.getPayments().get(0).getPaymentMethod());
        assertEquals("FINAL", res.getPayments().get(1).getPaymentType());
        assertEquals("CASH", res.getPayments().get(1).getPaymentMethod());
    }

    @Test
    void testSumTodayRevenue_usesActualPaymentDatesNotOrderCreatedAt() {
        BranchContext.setBranchId(1L);

        when(orderPaymentRepository.sumRevenueByDateRangeScoped(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("750.00"));

        BigDecimal rev = orderService.sumTodayRevenue();

        assertEquals(new BigDecimal("750.00"), rev);
        verify(orderPaymentRepository, times(1)).sumRevenueByDateRangeScoped(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void testDailyRevenue_mondayAdvanceAndFridayBalanceScenario() {
        // Monday: Advance = 500.00, Friday: Balance = 2000.00
        java.time.LocalDate monday = java.time.LocalDate.of(2026, 9, 1);
        java.time.LocalDate friday = java.time.LocalDate.of(2026, 9, 5);

        lenient().when(orderPaymentRepository.sumRevenueByDateRangeScoped(eq(1L), eq(monday.atStartOfDay()), eq(monday.atTime(java.time.LocalTime.MAX))))
                .thenReturn(new BigDecimal("500.00"));

        lenient().when(orderPaymentRepository.sumRevenueByDateRangeScoped(eq(1L), eq(friday.atStartOfDay()), eq(friday.atTime(java.time.LocalTime.MAX))))
                .thenReturn(new BigDecimal("2000.00"));

        Map<String, Object> mondayReport = orderService.getRevenueReport(monday, monday, null, 1L);
        Map<String, Object> fridayReport = orderService.getRevenueReport(friday, friday, null, 1L);

        assertEquals(new BigDecimal("500.00"), mondayReport.get("periodRevenue"));
        assertEquals(new BigDecimal("2000.00"), fridayReport.get("periodRevenue"));
    }

    @Test
    void testGetRevenueReport_withPaymentMethodAndDateRangeFiltering() {
        java.time.LocalDate start = java.time.LocalDate.of(2026, 9, 1);
        java.time.LocalDate end = java.time.LocalDate.of(2026, 9, 5);

        when(orderPaymentRepository.sumRevenueByDateRangeAndMethodScoped(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class), eq("UPI")))
                .thenReturn(new BigDecimal("1500.00"));

        List<Object[]> breakdown = new java.util.ArrayList<>();
        breakdown.add(new Object[]{"UPI", new BigDecimal("1500.00")});
        breakdown.add(new Object[]{"CASH", new BigDecimal("1000.00")});

        when(orderPaymentRepository.sumRevenueGroupedByPaymentMethod(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(breakdown);

        Map<String, Object> report = orderService.getRevenueReport(start, end, "UPI", 1L);

        assertNotNull(report);
        assertEquals(new BigDecimal("1500.00"), report.get("periodRevenue"));
        assertEquals("UPI", report.get("paymentMethod"));
        assertEquals(1L, report.get("branchId"));

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> byMethod = (Map<String, BigDecimal>) report.get("byPaymentMethod");
        assertNotNull(byMethod);
        assertEquals(new BigDecimal("1500.00"), byMethod.get("UPI"));
        assertEquals(new BigDecimal("1000.00"), byMethod.get("CASH"));
    }

    // ── TASK-009 Order Number Sequence & Concurrency Tests ──────────────────

    @Test
    void testGenerateOrderNumber_withSequenceValue_formatsCorrectly() {
        when(orderRepository.getNextOrderSequenceValue()).thenReturn(1001L);
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);

        String orderNumber = orderService.generateOrderNumber();

        String expectedDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertEquals("ORD-" + expectedDate + "-1001", orderNumber);
        assertTrue(orderNumber.matches("^ORD-\\d{8}-1001$"));
    }

    @Test
    void testGenerateOrderNumber_sequenceUnavailable_fallsBackToCount() {
        when(orderRepository.getNextOrderSequenceValue()).thenThrow(new RuntimeException("Sequence not found"));
        when(orderRepository.count()).thenReturn(5L);
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);

        String orderNumber = orderService.generateOrderNumber();

        String expectedDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertEquals("ORD-" + expectedDate + "-0006", orderNumber);
    }

    @Test
    void testGenerateOrderNumber_collisionHandling_advancesSequence() {
        String expectedDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String existingCandidate = "ORD-" + expectedDate + "-1001";

        when(orderRepository.getNextOrderSequenceValue()).thenReturn(1001L, 1002L);
        when(orderRepository.existsByOrderNumber(existingCandidate)).thenReturn(true);
        when(orderRepository.existsByOrderNumber("ORD-" + expectedDate + "-1002")).thenReturn(false);

        String orderNumber = orderService.generateOrderNumber();

        assertEquals("ORD-" + expectedDate + "-1002", orderNumber);
        verify(orderRepository, times(2)).getNextOrderSequenceValue();
    }

    @Test
    void testGenerateOrderNumber_concurrentInvocations_produceUniqueNumbers() throws Exception {
        int threadCount = 20;
        java.util.concurrent.atomic.AtomicLong sequenceCounter = new java.util.concurrent.atomic.AtomicLong(1001);

        when(orderRepository.getNextOrderSequenceValue()).thenAnswer(inv -> sequenceCounter.getAndIncrement());
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);

        java.util.Set<String> generatedNumbers = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(10);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String num = orderService.generateOrderNumber();
                    generatedNumbers.add(num);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Threads must finish within timeout");
        assertEquals(threadCount, generatedNumbers.size(), "All concurrent order numbers must be strictly unique");
    }

    @Test
    void testCreateOrder_usesSequenceGeneratedOrderNumber() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerMobile("9876543210");
        req.setTotalAmount(new BigDecimal("2500.00"));
        req.setAdvanceAmount(new BigDecimal("500.00"));
        req.setDeliveryDate(LocalDateTime.now().plusDays(3));

        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));
        when(orderRepository.getNextOrderSequenceValue()).thenReturn(1042L);
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);

        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        when(orderRepository.save(captor.capture())).thenAnswer(invocation -> {
            CustomerOrder o = invocation.getArgument(0);
            o.setId(201L);
            return o;
        });

        OrderResponse res = orderService.createOrder(req);

        assertNotNull(res);
        String expectedDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertEquals("ORD-" + expectedDate + "-1042", res.getOrderNumber());
        assertEquals("ORD-" + expectedDate + "-1042", captor.getValue().getOrderNumber());
    }

    // ── TASK-010 Order State Transition Validation Tests ───────────────────

    @Test
    void testValidateStatusTransition_cancelledToDelivered_throwsException() {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition(OrderService.STATUS_CANCELLED, OrderService.STATUS_DELIVERED));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Cannot transition order from CANCELLED to DELIVERED"));
    }

    @Test
    void testValidateStatusTransition_deliveredToCutting_throwsException() {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition(OrderService.STATUS_DELIVERED, OrderService.STATUS_CUTTING));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Cannot transition order from DELIVERED to CUTTING"));
    }

    @Test
    void testValidateStatusTransition_deliveredToStitching_throwsException() {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition(OrderService.STATUS_DELIVERED, OrderService.STATUS_STITCHING));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Cannot transition order from DELIVERED to STITCHING"));
    }

    @Test
    void testValidateStatusTransition_deliveredToCancelled_throwsException() {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition(OrderService.STATUS_DELIVERED, OrderService.STATUS_CANCELLED));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
    }

    @Test
    void testValidateStatusTransition_cancelledToAnyOtherStatus_throwsException() {
        List<String> disallowed = List.of(
                OrderService.STATUS_CUTTING,
                OrderService.STATUS_STITCHING,
                OrderService.STATUS_DESIGNING,
                OrderService.STATUS_PENDING,
                OrderService.STATUS_READY_TO_DELIVERY
        );
        for (String target : disallowed) {
            AppException ex = assertThrows(AppException.class, () ->
                    orderService.validateStatusTransition(OrderService.STATUS_CANCELLED, target));
            assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        }
    }

    @Test
    void testValidateStatusTransition_validTransitions_succeed() {
        assertDoesNotThrow(() -> orderService.validateStatusTransition(OrderService.STATUS_PENDING, OrderService.STATUS_DESIGNING));
        assertDoesNotThrow(() -> orderService.validateStatusTransition(OrderService.STATUS_DESIGNING, OrderService.STATUS_CUTTING));
        assertDoesNotThrow(() -> orderService.validateStatusTransition(OrderService.STATUS_CUTTING, OrderService.STATUS_STITCHING));
        assertDoesNotThrow(() -> orderService.validateStatusTransition(OrderService.STATUS_STITCHING, OrderService.STATUS_CUTTING)); // rework
        assertDoesNotThrow(() -> orderService.validateStatusTransition(OrderService.STATUS_STITCHING, OrderService.STATUS_READY_TO_DELIVERY));
        assertDoesNotThrow(() -> orderService.validateStatusTransition(OrderService.STATUS_READY_TO_DELIVERY, OrderService.STATUS_DELIVERED));
        assertDoesNotThrow(() -> orderService.validateStatusTransition(OrderService.STATUS_CUTTING, OrderService.STATUS_CANCELLED));
    }

    @Test
    void testValidateStatusTransition_idempotentSameStatus_succeeds() {
        assertDoesNotThrow(() -> orderService.validateStatusTransition(OrderService.STATUS_DELIVERED, OrderService.STATUS_DELIVERED));
        assertDoesNotThrow(() -> orderService.validateStatusTransition(OrderService.STATUS_CUTTING, OrderService.STATUS_CUTTING));
        assertDoesNotThrow(() -> orderService.validateStatusTransition(OrderService.STATUS_CANCELLED, OrderService.STATUS_CANCELLED));
    }

    @Test
    void testValidateStatusTransition_unknownStatus_throwsException() {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition(OrderService.STATUS_CUTTING, "NON_EXISTENT_STATUS_XYZ"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
    }

    @Test
    void testUpdateStatus_cancelledOrder_rejectsDeliveryViaEndpoint() {
        testOrder.setStatus(OrderService.STATUS_CANCELLED);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));

        AppException ex = assertThrows(AppException.class, () ->
                orderService.updateStatus(101L, OrderService.STATUS_DELIVERED));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Cannot transition order from CANCELLED to DELIVERED"));
    }

    @Test
    void testUpdateStatus_deliveredOrder_rejectsCuttingViaEndpoint() {
        testOrder.setStatus(OrderService.STATUS_DELIVERED);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));

        AppException ex = assertThrows(AppException.class, () ->
                orderService.updateStatus(101L, OrderService.STATUS_CUTTING));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Cannot transition order from DELIVERED to CUTTING"));
    }

    @Test
    void testValidateStatusTransition_legacyAliasesNotRecognized_throwsException() {
        List<String> legacyAliases = List.of(
                "PATTERN_MAKING",
                "FABRIC_CUTTING",
                "EMBROIDERY_STITCHING",
                "MAIN_SEWING",
                "IN_PROGRESS",
                "IN_PRODUCTION",
                "BUTTON_FITTING",
                "IRONING_PRESS",
                "FINAL_PACKAGING"
        );
        for (String alias : legacyAliases) {
            AppException ex = assertThrows(AppException.class, () ->
                    orderService.validateStatusTransition(OrderService.STATUS_CUTTING, alias));
            assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        }
    }

    // ── TASK-011: Delivery Validation Tests ─────────────────────────────────

    @Test
    void testValidateStatusTransition_pendingToDelivered_throwsException() {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition(OrderService.STATUS_PENDING, OrderService.STATUS_DELIVERED));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Order must be in READY_TO_DELIVERY state before it can be delivered"));
    }

    @Test
    void testValidateStatusTransition_cuttingToDelivered_throwsException() {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition(OrderService.STATUS_CUTTING, OrderService.STATUS_DELIVERED));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Order must be in READY_TO_DELIVERY state before it can be delivered"));
    }

    @Test
    void testValidateStatusTransition_stitchingToDelivered_throwsException() {
        AppException ex = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition(OrderService.STATUS_STITCHING, OrderService.STATUS_DELIVERED));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Order must be in READY_TO_DELIVERY state before it can be delivered"));
    }

    @Test
    void testValidateStatusTransition_readyToDeliveryToDelivered_succeeds() {
        assertDoesNotThrow(() ->
                orderService.validateStatusTransition(OrderService.STATUS_READY_TO_DELIVERY, OrderService.STATUS_DELIVERED));
    }

    @Test
    void testUpdateStatus_deliveryWithBalance_allowedAndSetsPartial() {
        testOrder.setStatus(OrderService.STATUS_READY_TO_DELIVERY);
        testOrder.setTotalAmount(new BigDecimal("3000.00"));
        testOrder.setPaidAmount(new BigDecimal("1000.00"));
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse res = orderService.updateStatus(101L, OrderService.STATUS_DELIVERED, null, null, null, null, "Priya Sharma", null);
        assertEquals(OrderService.STATUS_DELIVERED, res.getStatus());
        assertEquals(OrderService.PAYMENT_STATUS_PARTIAL, res.getPaymentStatus());
        assertEquals("Priya Sharma", res.getReceiverName());
    }

    @Test
    void testUpdateStatus_deliveryWithFullPayment_setsPaid() {
        testOrder.setStatus(OrderService.STATUS_READY_TO_DELIVERY);
        testOrder.setTotalAmount(new BigDecimal("3000.00"));
        testOrder.setPaidAmount(new BigDecimal("1000.00"));
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderPaymentRepository.findByOrderIdOrderByPaymentDateAsc(101L)).thenReturn(List.of(
                OrderPayment.builder().orderId(101L).amount(new BigDecimal("1000.00")).paymentMethod("UPI").build(),
                OrderPayment.builder().orderId(101L).amount(new BigDecimal("2000.00")).paymentMethod("CASH").build()
        ));

        OrderResponse res = orderService.updateStatus(101L, OrderService.STATUS_DELIVERED, new BigDecimal("2000.00"), "CASH", null, null, "Priya Sharma", null);
        assertEquals(OrderService.STATUS_DELIVERED, res.getStatus());
        assertEquals(OrderService.PAYMENT_STATUS_PAID, res.getPaymentStatus());
        assertEquals("Priya Sharma", res.getReceiverName());
    }

    @Test
    void testUpdateStatus_negativePayment_throwsValidationException() {
        testOrder.setStatus(OrderService.STATUS_READY_TO_DELIVERY);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));

        AppException ex = assertThrows(AppException.class, () ->
                orderService.updateStatus(101L, OrderService.STATUS_DELIVERED, new BigDecimal("-500.00"), "CASH", null, null, "Priya", null));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Payment amount cannot be negative"));
    }

    @Test
    void testUpdateStatus_negativeDiscount_throwsValidationException() {
        testOrder.setStatus(OrderService.STATUS_READY_TO_DELIVERY);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));

        AppException ex = assertThrows(AppException.class, () ->
                orderService.updateStatus(101L, OrderService.STATUS_DELIVERED, null, null, null, null, "Priya", new BigDecimal("-100.00")));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Discount amount cannot be negative"));
    }

    @Test
    void testUpdateStatus_discountExceedsTotal_throwsValidationException() {
        testOrder.setStatus(OrderService.STATUS_READY_TO_DELIVERY);
        testOrder.setTotalAmount(new BigDecimal("3000.00"));
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));

        AppException ex = assertThrows(AppException.class, () ->
                orderService.updateStatus(101L, OrderService.STATUS_DELIVERED, null, null, null, null, "Priya", new BigDecimal("3500.00")));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Discount amount cannot exceed total order amount"));
    }

    @Test
    void testUpdateStatus_deliveredWithoutReceiver_defaultsToCustomerName() {
        testOrder.setStatus(OrderService.STATUS_READY_TO_DELIVERY);
        testOrder.setReceiverName(null);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));
        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse res = orderService.updateStatus(101L, OrderService.STATUS_DELIVERED, null, null, null, null, null, null);
        assertEquals(OrderService.STATUS_DELIVERED, res.getStatus());
        assertEquals("Priya Sharma", res.getReceiverName());
    }

    // ── Overpayment Validation Tests (TASK-027) ─────────────────────────────

    @Test
    void testCreateOrder_advanceExceedsNetTotal_throwsValidationException() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerMobile("9876543210");
        req.setDeliveryDate(LocalDateTime.now().plusDays(7));
        req.setTotalAmount(new BigDecimal("1000.00"));
        req.setDiscountAmount(new BigDecimal("200.00")); // Net total = 800.00
        req.setAdvanceAmount(new BigDecimal("900.00"));  // Advance exceeds net total

        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));

        AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(req));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Advance payment") && ex.getMessage().contains("cannot exceed order net total"));
    }

    @Test
    void testCreateOrder_negativeAdvance_throwsValidationException() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerMobile("9876543210");
        req.setDeliveryDate(LocalDateTime.now().plusDays(7));
        req.setTotalAmount(new BigDecimal("1000.00"));
        req.setAdvanceAmount(new BigDecimal("-100.00"));

        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));

        AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(req));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Advance amount cannot be negative"));
    }

    @Test
    void testCreateOrder_exactAdvancePayment_setsPaid() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setCustomerMobile("9876543210");
        req.setDeliveryDate(LocalDateTime.now().plusDays(7));
        req.setTotalAmount(new BigDecimal("1000.00"));
        req.setDiscountAmount(new BigDecimal("200.00")); // Net total = 800.00
        req.setAdvanceAmount(new BigDecimal("800.00"));  // Exact full advance

        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
            CustomerOrder o = inv.getArgument(0);
            o.setId(201L);
            return o;
        });

        OrderResponse res = orderService.createOrder(req);
        assertEquals(OrderService.PAYMENT_STATUS_PAID, res.getPaymentStatus());
        assertEquals(new BigDecimal("800.00"), res.getPaidAmount());
    }

    @Test
    void testRecordPayment_exceedsRemainingBalance_throwsValidationException() {
        // testOrder: total 3000, discount 0, paid 1000 -> remaining balance 2000
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .amount(new BigDecimal("2500.00")) // Exceeds remaining 2000
                .paymentMethod("CASH")
                .build();

        AppException ex = assertThrows(AppException.class, () -> orderService.recordPayment(101L, req));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("exceeds remaining balance"));
    }

    @Test
    void testRecordPayment_equalRemainingBalance_succeedsAndSetsPaid() {
        // testOrder: total 3000, discount 0, paid 1000 -> remaining balance 2000
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));
        when(orderPaymentRepository.save(any(OrderPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderPaymentRepository.findByOrderIdOrderByPaymentDateAsc(101L)).thenReturn(List.of(
                OrderPayment.builder().orderId(101L).amount(new BigDecimal("1000.00")).paymentMethod("UPI").build(),
                OrderPayment.builder().orderId(101L).amount(new BigDecimal("2000.00")).paymentMethod("CASH").build()
        ));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .amount(new BigDecimal("2000.00")) // Exact remaining balance
                .paymentMethod("CASH")
                .build();

        OrderPaymentResponse res = orderService.recordPayment(101L, req);
        assertNotNull(res);
        assertEquals(new BigDecimal("2000.00"), res.getAmount());
        assertEquals(new BigDecimal("3000.00"), testOrder.getPaidAmount());
        assertEquals(OrderService.PAYMENT_STATUS_PAID, testOrder.getPaymentStatus());
    }

    @Test
    void testRecordPayment_partialPayment_succeedsAndKeepsPartial() {
        // testOrder: total 3000, discount 0, paid 1000 -> remaining balance 2000
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));
        when(orderPaymentRepository.save(any(OrderPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderPaymentRepository.findByOrderIdOrderByPaymentDateAsc(101L)).thenReturn(List.of(
                OrderPayment.builder().orderId(101L).amount(new BigDecimal("1000.00")).paymentMethod("UPI").build(),
                OrderPayment.builder().orderId(101L).amount(new BigDecimal("500.00")).paymentMethod("CASH").build()
        ));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .amount(new BigDecimal("500.00"))
                .paymentMethod("CASH")
                .build();

        OrderPaymentResponse res = orderService.recordPayment(101L, req);
        assertNotNull(res);
        assertEquals(new BigDecimal("1500.00"), testOrder.getPaidAmount());
        assertEquals(OrderService.PAYMENT_STATUS_PARTIAL, testOrder.getPaymentStatus());
    }

    @Test
    void testRecordPayment_onAlreadyFullyPaidOrder_throwsValidationException() {
        testOrder.setPaidAmount(new BigDecimal("3000.00"));
        testOrder.setPaymentStatus(OrderService.PAYMENT_STATUS_PAID);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .amount(new BigDecimal("10.00"))
                .paymentMethod("CASH")
                .build();

        AppException ex = assertThrows(AppException.class, () -> orderService.recordPayment(101L, req));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("exceeds remaining balance"));
    }

    @Test
    void testRecordPayment_zeroOrNegativeAmount_throwsValidationException() {
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));

        RecordPaymentRequest zeroReq = RecordPaymentRequest.builder()
                .amount(BigDecimal.ZERO)
                .paymentMethod("CASH")
                .build();

        AppException ex1 = assertThrows(AppException.class, () -> orderService.recordPayment(101L, zeroReq));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex1.getErrorCode());
        assertTrue(ex1.getMessage().contains("Payment amount must be greater than zero"));

        RecordPaymentRequest negReq = RecordPaymentRequest.builder()
                .amount(new BigDecimal("-50.00"))
                .paymentMethod("CASH")
                .build();

        AppException ex2 = assertThrows(AppException.class, () -> orderService.recordPayment(101L, negReq));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex2.getErrorCode());
        assertTrue(ex2.getMessage().contains("Payment amount must be greater than zero"));
    }

    @Test
    void testRecordPayment_onCancelledOrder_throwsValidationException() {
        testOrder.setStatus(OrderService.STATUS_CANCELLED);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));

        RecordPaymentRequest req = RecordPaymentRequest.builder()
                .amount(new BigDecimal("500.00"))
                .paymentMethod("CASH")
                .build();

        AppException ex = assertThrows(AppException.class, () -> orderService.recordPayment(101L, req));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Cannot record payment on a cancelled order"));
    }

    @Test
    void testUpdateStatus_finalPaymentExceedsRemainingBalance_throwsValidationException() {
        // testOrder: total 3000, discount 0, paid 1000 -> remaining balance 2000
        testOrder.setStatus(OrderService.STATUS_READY_TO_DELIVERY);
        when(orderRepository.findById(101L)).thenReturn(Optional.of(testOrder));

        AppException ex = assertThrows(AppException.class, () ->
                orderService.updateStatus(101L, OrderService.STATUS_DELIVERED, new BigDecimal("2500.00"), "CASH", null, null, "Priya", null));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("exceeds remaining balance"));
    }
}
