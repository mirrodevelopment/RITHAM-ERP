package com.ritham.erp.module.order.service;


import com.ritham.erp.module.branch.repository.BranchRepository;
import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.customer.service.CustomerMeasurementService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAnalyticsServiceTest {

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

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        BranchContext.clear();
    }

    @AfterEach
    void tearDown() {
        BranchContext.clear();
    }

    @Test
    @DisplayName("Default preset (WEEK) computes 7-day range and populates summary aggregates")
    void getOrderAnalytics_DefaultPreset_Week() {
        BranchContext.setBranchId(1L);

        Object[] aggRow = new Object[]{
                15L,                         // totalOrders
                new BigDecimal("45000.00"),  // totalRevenue
                new BigDecimal("30000.00"),  // totalCollected
                new BigDecimal("15000.00"),  // totalBalance
                8L,                          // deliveredCount
                6L,                          // paidCount
                5L,                          // partialCount
                4L                           // unpaidCount
        };
        when(orderRepository.countTodayOrdersScoped(1L)).thenReturn(3L);
        when(orderRepository.getOrderSummaryAggregates(eq(1L), any(), any(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Collections.singletonList(aggRow));

        // Daily income mock
        Object[] incomeRow1 = new Object[]{LocalDate.now().minusDays(1), new BigDecimal("12000.00")};
        Object[] incomeRow2 = new Object[]{LocalDate.now(), new BigDecimal("18000.00")};
        when(orderPaymentRepository.sumDailyRevenueScoped(eq(1L), any(), any()))
                .thenReturn(Arrays.asList(incomeRow1, incomeRow2));

        // Daily orders mock
        Object[] orderRow1 = new Object[]{LocalDate.now().minusDays(1), 2L};
        Object[] orderRow2 = new Object[]{LocalDate.now(), 3L};
        when(orderRepository.countDailyOrdersScoped(eq(1L), any(), any()))
                .thenReturn(Arrays.asList(orderRow1, orderRow2));

        // Status breakdown mock
        Object[] statusRow = new Object[]{"DELIVERED", 8L};
        when(orderRepository.countOrdersByStatusScoped(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(statusRow));

        // Payment breakdown mock
        Object[] payRow = new Object[]{"UPI", 5L, new BigDecimal("25000.00")};
        when(orderPaymentRepository.countAndSumRevenueGroupedByPaymentMethod(eq(1L), any(), any()))
                .thenReturn(Collections.singletonList(payRow));

        // Top customers mock
        Object[] custRow = new Object[]{"9876543210", 4L, new BigDecimal("15000.00")};
        when(orderRepository.findTopCustomersScoped(eq(1L), any(), any(), any()))
                .thenReturn(Collections.singletonList(custRow));
        when(customerRepository.findByCustomerMobile("9876543210"))
                .thenReturn(Optional.of(Customer.builder().customerMobile("9876543210").customerName("Aarav Patel").build()));

        // Recent orders mock
        CustomerOrder order = CustomerOrder.builder()
                .id(101L)
                .orderNumber("ORD-00101")
                .customerMobile("9876543210")
                .totalAmount(new BigDecimal("5000.00"))
                .paidAmount(new BigDecimal("5000.00"))
                .status("DELIVERED")
                .paymentStatus("PAID")
                .orderDate(LocalDateTime.now())
                .build();
        when(orderRepository.findFilteredOrdersForReportScoped(eq(1L), any(), any(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(Collections.singletonList(order)));

        Map<String, Object> result = orderService.getOrderAnalytics(
                null, null, null, "ALL", "ALL", "ALL", null, null);

        assertNotNull(result);

        // Verify summary
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertNotNull(summary);
        assertEquals(15L, summary.get("totalOrders"));
        assertEquals(3L, summary.get("todayOrders"));
        assertEquals(new BigDecimal("45000.00"), summary.get("totalRevenue"));
        assertEquals(new BigDecimal("30000.00"), summary.get("totalCollected"));
        assertEquals(new BigDecimal("15000.00"), summary.get("totalBalance"));
        assertEquals(8L, summary.get("deliveredCount"));
        assertEquals(6L, summary.get("paidCount"));
        assertEquals(5L, summary.get("partialCount"));
        assertEquals(4L, summary.get("unpaidCount"));

        // Verify daily income
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dailyIncome = (List<Map<String, Object>>) result.get("dailyIncome");
        assertEquals(2, dailyIncome.size());
        assertEquals(new BigDecimal("12000.00"), dailyIncome.get(0).get("amount"));

        // Verify daily orders
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dailyOrders = (List<Map<String, Object>>) result.get("dailyOrders");
        assertEquals(2, dailyOrders.size());
        assertEquals(2L, dailyOrders.get(0).get("count"));

        // Verify status breakdown
        @SuppressWarnings("unchecked")
        Map<String, Long> statusBreakdown = (Map<String, Long>) result.get("statusBreakdown");
        assertEquals(8L, statusBreakdown.get("DELIVERED"));

        // Verify payment breakdown
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> paymentBreakdown = (Map<String, Map<String, Object>>) result.get("paymentBreakdown");
        assertTrue(paymentBreakdown.containsKey("UPI"));
        assertEquals(5L, paymentBreakdown.get("UPI").get("count"));
        assertEquals(new BigDecimal("25000.00"), paymentBreakdown.get("UPI").get("amount"));

        // Verify top customers
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topCustomers = (List<Map<String, Object>>) result.get("topCustomers");
        assertEquals(1, topCustomers.size());
        assertEquals("Aarav Patel", topCustomers.get(0).get("name"));
        assertEquals(4L, topCustomers.get(0).get("count"));

        // Verify recent orders
        @SuppressWarnings("unchecked")
        List<OrderResponse> recentOrders = (List<OrderResponse>) result.get("recentOrders");
        assertEquals(1, recentOrders.size());
        assertEquals("ORD-00101", recentOrders.get(0).getOrderNumber());
    }

    @Test
    @DisplayName("Presets calculate accurate start and end date boundaries")
    void getOrderAnalytics_Presets_Calculations() {
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        when(orderRepository.findFilteredOrdersForReportScoped(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // 1. TODAY
        orderService.getOrderAnalytics("TODAY", null, null, null, null, null, null, 1L);
        verify(orderRepository).getOrderSummaryAggregates(
                eq(1L), startCaptor.capture(), endCaptor.capture(), any(), any(), any(), any());
        assertEquals(LocalDate.now().atStartOfDay(), startCaptor.getValue());
        assertEquals(LocalDate.now().atTime(LocalTime.MAX), endCaptor.getValue());

        // 2. YESTERDAY
        orderService.getOrderAnalytics("YESTERDAY", null, null, null, null, null, null, 1L);
        verify(orderRepository, times(2)).getOrderSummaryAggregates(
                eq(1L), startCaptor.capture(), endCaptor.capture(), any(), any(), any(), any());
        assertEquals(LocalDate.now().minusDays(1).atStartOfDay(), startCaptor.getValue());
        assertEquals(LocalDate.now().minusDays(1).atTime(LocalTime.MAX), endCaptor.getValue());

        // 3. ALL
        orderService.getOrderAnalytics("ALL", null, null, null, null, null, null, 1L);
        verify(orderRepository, times(3)).getOrderSummaryAggregates(
                eq(1L), startCaptor.capture(), endCaptor.capture(), any(), any(), any(), any());
        assertNull(startCaptor.getValue());
        assertNull(endCaptor.getValue());

        // 4. CUSTOM with swapped dates
        LocalDate earlier = LocalDate.of(2026, 8, 1);
        LocalDate later = LocalDate.of(2026, 8, 15);
        orderService.getOrderAnalytics("CUSTOM", later, earlier, null, null, null, null, 1L);
        verify(orderRepository, times(4)).getOrderSummaryAggregates(
                eq(1L), startCaptor.capture(), endCaptor.capture(), any(), any(), any(), any());
        assertEquals(earlier.atStartOfDay(), startCaptor.getValue());
        assertEquals(later.atTime(LocalTime.MAX), endCaptor.getValue());
    }

    @Test
    @DisplayName("Branch scoping prioritizes BranchContext over requestBranchId and supports global view")
    void getOrderAnalytics_BranchScoping() {
        when(orderRepository.findFilteredOrdersForReportScoped(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Case A: BranchContext is set (e.g. branch manager) -> overrides requestBranchId
        BranchContext.setBranchId(2L);
        orderService.getOrderAnalytics("WEEK", null, null, null, null, null, null, 99L);
        verify(orderRepository).getOrderSummaryAggregates(eq(2L), any(), any(), any(), any(), any(), any());

        // Case B: BranchContext is null, requestBranchId is provided (e.g. admin selecting branch in header)
        BranchContext.clear();
        orderService.getOrderAnalytics("WEEK", null, null, null, null, null, null, 3L);
        verify(orderRepository).getOrderSummaryAggregates(eq(3L), any(), any(), any(), any(), any(), any());

        // Case C: Both null -> global analytics across all branches
        orderService.getOrderAnalytics("WEEK", null, null, null, null, null, null, null);
        verify(orderRepository).getOrderSummaryAggregates(isNull(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Filter arguments are cleaned, upper-cased, and ALL is converted to null")
    void getOrderAnalytics_FilterSanitization() {
        when(orderRepository.findFilteredOrdersForReportScoped(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        orderService.getOrderAnalytics("WEEK", null, null, "  tailoring  ", " paid ", " cash ", "  98765  ", null);
        verify(orderRepository).getOrderSummaryAggregates(
                isNull(), any(), any(), eq("TAILORING"), eq("PAID"), eq("CASH"), eq("98765"));

        orderService.getOrderAnalytics("WEEK", null, null, "ALL", "ALL", "ALL", "", null);
        verify(orderRepository).getOrderSummaryAggregates(
                isNull(), any(), any(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("Empty aggregate results return zero-initialized summary map without exceptions")
    void getOrderAnalytics_EmptyAggregates_SafeDefaults() {
        when(orderRepository.countTodayOrdersScoped(any())).thenReturn(0L);
        when(orderRepository.getOrderSummaryAggregates(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findFilteredOrdersForReportScoped(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        Map<String, Object> result = orderService.getOrderAnalytics(
                "WEEK", null, null, null, null, null, null, null);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertNotNull(summary);
        assertEquals(0L, summary.get("totalOrders"));
        assertEquals(0L, summary.get("todayOrders"));
        assertEquals(BigDecimal.ZERO, summary.get("totalRevenue"));
        assertEquals(BigDecimal.ZERO, summary.get("totalCollected"));
        assertEquals(BigDecimal.ZERO, summary.get("totalBalance"));
        assertEquals(0L, summary.get("deliveredCount"));
        assertEquals(0L, summary.get("paidCount"));
        assertEquals(0L, summary.get("partialCount"));
        assertEquals(0L, summary.get("unpaidCount"));
    }
}
