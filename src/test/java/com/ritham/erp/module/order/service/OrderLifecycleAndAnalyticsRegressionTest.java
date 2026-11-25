package com.ritham.erp.module.order.service;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.common.util.PaginationUtils;
import com.ritham.erp.module.branch.entity.Branch;
import com.ritham.erp.module.branch.repository.BranchRepository;
import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.customer.service.CustomerMeasurementService;
import com.ritham.erp.module.order.dto.CreateOrderRequest;
import com.ritham.erp.module.order.dto.OrderMapper;
import com.ritham.erp.module.order.dto.OrderResponse;
import com.ritham.erp.module.order.entity.CustomerOrder;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import com.ritham.erp.module.order.repository.OrderMeasurementRepository;
import com.ritham.erp.module.order.repository.OrderPaymentRepository;
import com.ritham.erp.module.production.repository.ProductionStageRepository;
import com.ritham.erp.security.BranchContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleAndAnalyticsRegressionTest {

    @Mock private CustomerOrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private OrderMeasurementRepository orderMeasurementRepository;
    @Mock private OrderPaymentRepository orderPaymentRepository;
    @Mock private CustomerMeasurementService customerMeasurementService;
    @Mock private BranchRepository branchRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private ProductionStageRepository productionStageRepository;

    @InjectMocks
    private OrderService orderService;

    @AfterEach
    void tearDown() {
        BranchContext.clear();
    }

    @Test
    @DisplayName("PENDING or active stages cannot transition directly to COMPLETED without READY_TO_DELIVERY")
    void directTransitionToCompletedBlocked() {
        AppException exPending = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("PENDING", "COMPLETED"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, exPending.getErrorCode());

        AppException exCutting = assertThrows(AppException.class, () ->
                orderService.validateStatusTransition("CUTTING", "COMPLETED"));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, exCutting.getErrorCode());
    }

    @Test
    @DisplayName("updateStatus populates receiverName for COMPLETED deliveries and blocks discount below paidAmount")
    void completedReceiverAndDiscountOverpaymentGuards() {
        CustomerOrder order = CustomerOrder.builder()
                .id(10L)
                .orderNumber("ORD-1010")
                .customerMobile("9876543210")
                .status(OrderService.STATUS_READY_TO_DELIVERY)
                .totalAmount(new BigDecimal("1000"))
                .paidAmount(new BigDecimal("700"))
                .discountAmount(BigDecimal.ZERO)
                .build();

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        // Discount 400 makes net total 600 < already paid 700
        AppException discountEx = assertThrows(AppException.class, () ->
                orderService.updateStatus(10L, "COMPLETED", null, null, null, null, null, new BigDecimal("400")));
        assertEquals(ErrorCode.VALIDATION_FAILED, discountEx.getErrorCode());

        // Reset status for valid completion without explicit receiverName
        order.setStatus(OrderService.STATUS_READY_TO_DELIVERY);
        when(customerRepository.findByCustomerMobile("9876543210"))
                .thenReturn(Optional.of(Customer.builder().customerMobile("9876543210").customerName("Meera").build()));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toResponse(any(CustomerOrder.class), anyString())).thenReturn(OrderResponse.builder().id(10L).build());

        orderService.updateStatus(10L, "COMPLETED", null, null, null, null, null, null);
        assertEquals("Meera", order.getReceiverName());
    }

    @Test
    @DisplayName("createOrder blocks branch spoofing; due summary and measurement fallback respect BranchContext")
    void branchScopedCustomerOperations() {
        BranchContext.setBranchId(1L);

        CreateOrderRequest spoofed = new CreateOrderRequest();
        spoofed.setCustomerMobile("9876543210");
        spoofed.setBranchId(2L);
        spoofed.setTotalAmount(new BigDecimal("500"));
        spoofed.setDeliveryDate(LocalDateTime.now().plusDays(3));

        AppException ex = assertThrows(AppException.class, () -> orderService.createOrder(spoofed));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());

        when(orderRepository.findByCustomerMobileScoped(1L, "9876543210")).thenReturn(List.of());
        orderService.getCustomerDueSummary("9876543210");
        verify(orderRepository).findByCustomerMobileScoped(1L, "9876543210");

        when(customerMeasurementService.getLatestByCustomerMobile("9876543210")).thenReturn(Optional.empty());
        when(orderRepository.findLatestMeasurementOrdersScoped(eq(1L), eq("9876543210"), any(Pageable.class)))
                .thenReturn(List.of());
        orderService.getLatestCustomerMeasurements("9876543210");
        verify(orderRepository).findLatestMeasurementOrdersScoped(eq(1L), eq("9876543210"), any(Pageable.class));
    }

    @Test
    @DisplayName("getOrderAnalytics dispatches filtered breakdown queries and batch-loads customer names")
    void analyticsFilteredAndBatched() {
        Branch b1 = Branch.builder().id(1L).name("Main").branchCode("MN").build();
        CustomerOrder o1 = CustomerOrder.builder()
                .id(1L).orderNumber("ORD-1").customerMobile("9876543210")
                .status("CUTTING").totalAmount(new BigDecimal("500")).paidAmount(BigDecimal.ZERO).branch(b1).build();
        CustomerOrder o2 = CustomerOrder.builder()
                .id(2L).orderNumber("ORD-2").customerMobile("9876543211")
                .status("CUTTING").totalAmount(new BigDecimal("600")).paidAmount(BigDecimal.ZERO).branch(b1).build();

        when(orderRepository.countTodayOrdersScoped(isNull())).thenReturn(2L);
        when(orderRepository.getOrderSummaryAggregates(any(), any(), any(), eq("CUTTING"), isNull(), isNull(), isNull()))
                .thenReturn(List.<Object[]>of(new Object[]{2L, new BigDecimal("1100"), BigDecimal.ZERO, new BigDecimal("1100"), 0L, 0L, 0L, 2L}));
        when(orderPaymentRepository.sumDailyRevenueFilteredScoped(any(), any(), any(), eq("CUTTING"), isNull(), isNull(), isNull()))
                .thenReturn(List.of());
        when(orderRepository.countDailyOrdersFilteredScoped(any(), any(), any(), eq("CUTTING"), isNull(), isNull(), isNull()))
                .thenReturn(List.of());
        when(orderRepository.countOrdersByStatusFilteredScoped(any(), any(), any(), eq("CUTTING"), isNull(), isNull(), isNull()))
                .thenReturn(List.of());
        when(orderPaymentRepository.countAndSumRevenueGroupedByPaymentMethodFilteredScoped(any(), any(), any(), eq("CUTTING"), isNull(), isNull(), isNull()))
                .thenReturn(List.of());
        when(orderRepository.findTopCustomersFilteredScoped(any(), any(), any(), eq("CUTTING"), isNull(), isNull(), isNull(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"9876543210", 1L, new BigDecimal("500")}));
        when(orderRepository.findFilteredOrdersForReportScoped(any(), any(), any(), eq("CUTTING"), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(o1, o2)));
        when(customerRepository.findAllByCustomerMobileIn(anyCollection())).thenReturn(List.of(
                Customer.builder().customerMobile("9876543210").customerName("Meera").build(),
                Customer.builder().customerMobile("9876543211").customerName("Kavitha").build()
        ));
        when(orderMapper.toResponse(any(CustomerOrder.class), anyString()))
                .thenAnswer(inv -> OrderResponse.builder().customerName(inv.getArgument(1)).build());

        Map<String, Object> result = orderService.getOrderAnalytics(
                "CUSTOM", LocalDate.now().minusDays(7), LocalDate.now(), "CUTTING", null, null, null, null);

        assertNotNull(result);
        verify(orderRepository).countOrdersByStatusFilteredScoped(any(), any(), any(), eq("CUTTING"), isNull(), isNull(), isNull());
        verify(customerRepository, times(1)).findAllByCustomerMobileIn(anyCollection());
        verify(customerRepository, never()).findByCustomerMobile(anyString());
    }

    @Test
    @DisplayName("PaginationUtils clamps size safely and rejects unknown sort fields")
    void paginationUtilsClampsAndWhitelists() {
        Pageable bounded = PaginationUtils.safePageRequest(-2, 50000, null, "asc",
                PaginationUtils.ORDER_SORT_FIELDS, "createdAt");
        assertEquals(0, bounded.getPageNumber());
        assertEquals(PaginationUtils.MAX_UNBOUNDED_SAFE_PAGE_SIZE, bounded.getPageSize());

        AppException badSort = assertThrows(AppException.class, () ->
                PaginationUtils.safePageRequest(0, 20, "nonExistentColumn", "desc",
                        PaginationUtils.ORDER_SORT_FIELDS, "createdAt"));
        assertEquals(ErrorCode.VALIDATION_FAILED, badSort.getErrorCode());
    }
}
