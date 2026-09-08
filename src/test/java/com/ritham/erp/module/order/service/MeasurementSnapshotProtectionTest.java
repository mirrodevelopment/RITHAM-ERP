package com.ritham.erp.module.order.service;

import com.ritham.erp.module.branch.entity.Branch;
import com.ritham.erp.module.branch.repository.BranchRepository;
import com.ritham.erp.module.customer.dto.CustomerMeasurementResponse;

import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.repository.CustomerMeasurementRepository;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.customer.service.CustomerMeasurementService;
import com.ritham.erp.module.order.dto.CreateOrderRequest;
import com.ritham.erp.module.order.dto.OrderMapper;
import com.ritham.erp.module.order.dto.OrderResponse;
import com.ritham.erp.module.order.entity.CustomerOrder;
import com.ritham.erp.module.order.entity.OrderMeasurement;
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
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verification of TASK-012: Measurement Snapshot Protection.
 * Guarantees that updating current customer measurements does not alter historical order measurements.
 */
@ExtendWith(MockitoExtension.class)
class MeasurementSnapshotProtectionTest {

    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private OrderMeasurementRepository orderMeasurementRepository;
    @Mock
    private OrderPaymentRepository orderPaymentRepository;
    @Mock
    private CustomerMeasurementRepository customerMeasurementRepository;
    @Mock
    private CustomerMeasurementService customerMeasurementService;
    @Mock
    private BranchRepository branchRepository;
    @Spy
    private OrderMapper orderMapper = new OrderMapper();
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private OrderService orderService;

    private Branch testBranch;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        BranchContext.clear();

        orderService = new OrderService(
                orderRepository,
                customerRepository,
                orderMeasurementRepository,
                orderPaymentRepository,
                customerMeasurementService,
                branchRepository,
                orderMapper,
                objectMapper
        );

        testBranch = Branch.builder()
                .id(1L)
                .branchCode("BR-001")
                .name("Chennai Main Branch")
                .build();

        testCustomer = Customer.builder()
                .customerName("Ananya Iyer")
                .customerMobile("9876543210")
                .build();
    }

    @AfterEach
    void tearDown() {
        BranchContext.clear();
    }

    @Test
    @DisplayName("TASK-012: Create customer (34) -> Create order -> Update customer (36) -> Old order remains 34")
    void testHistoricalOrderMeasurementRemainsIntactWhenCustomerMeasurementUpdated() {
        String mobile = "9876543210";
        String garmentType = "BLOUSE";

        // 1. Initial State: Customer measurement profile is 34
        Map<String, String> initialMeasurements = new LinkedHashMap<>();
        initialMeasurements.put("chest", "34");
        initialMeasurements.put("waist", "28");

        // 2. Create Order #1 with measurement 34
        CreateOrderRequest createReq = new CreateOrderRequest();
        createReq.setCustomerMobile(mobile);
        createReq.setDeliveryDate(LocalDateTime.now().plusDays(5));
        createReq.setTotalAmount(new BigDecimal("1500.00"));
        createReq.setAdvanceAmount(new BigDecimal("500.00"));
        createReq.setGarmentType(garmentType);
        createReq.setMeasurements(initialMeasurements);

        when(customerRepository.findByCustomerMobile(mobile)).thenReturn(Optional.of(testCustomer));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));

        CustomerOrder savedOrder1 = CustomerOrder.builder()
                .id(101L)
                .orderNumber("ORD-20260905-0001")
                .customerMobile(mobile)
                .garmentType(garmentType)
                .totalAmount(new BigDecimal("1500.00"))
                .advanceAmount(new BigDecimal("500.00"))
                .paidAmount(new BigDecimal("500.00"))
                .status("PENDING")
                .branch(testBranch)
                .build();

        when(orderRepository.save(any(CustomerOrder.class))).thenReturn(savedOrder1);

        OrderResponse orderResponse1 = orderService.createOrder(createReq);
        assertNotNull(orderResponse1);
        assertEquals("34", orderResponse1.getMeasurements().get("chest"));
        assertEquals("28", orderResponse1.getMeasurements().get("waist"));

        // Verify OrderMeasurement snapshot was persisted for order 101 with measurement 34
        ArgumentCaptor<OrderMeasurement> omCaptor = ArgumentCaptor.forClass(OrderMeasurement.class);
        verify(orderMeasurementRepository, times(1)).save(omCaptor.capture());
        OrderMeasurement capturedOm1 = omCaptor.getValue();
        assertEquals(101L, capturedOm1.getOrderId());
        assertEquals(garmentType, capturedOm1.getGarmentType());
        assertTrue(capturedOm1.getMeasurementsJson().contains("\"chest\":\"34\""));
        assertTrue(capturedOm1.getMeasurementsJson().contains("\"waist\":\"28\""));

        // 3. Customer's measurement is updated to 36
        Map<String, String> updatedMeasurements = new LinkedHashMap<>();
        updatedMeasurements.put("chest", "36");
        updatedMeasurements.put("waist", "30");



        CustomerMeasurementResponse customerProfileResponse = CustomerMeasurementResponse.builder()
                .customerMobile(mobile)
                .customerName("Ananya Iyer")
                .garmentType(garmentType)
                .measurements(updatedMeasurements)
                .build();

        when(customerMeasurementService.getLatestByCustomerMobile(mobile))
                .thenReturn(Optional.of(customerProfileResponse));

        // Customer's current profile now reflects 36
        Map<String, Object> latestProfile = orderService.getLatestCustomerMeasurements(mobile);
        assertNotNull(latestProfile);
        @SuppressWarnings("unchecked")
        Map<String, String> currentCustomerMeasurements = (Map<String, String>) latestProfile.get("measurements");
        assertEquals("36", currentCustomerMeasurements.get("chest"));
        assertEquals("30", currentCustomerMeasurements.get("waist"));

        // 4. Retrieve historical Order #1 via getOrderById(101L)
        when(orderRepository.findById(101L)).thenReturn(Optional.of(savedOrder1));
        when(orderMeasurementRepository.findByOrderId(101L)).thenReturn(Optional.of(capturedOm1));

        OrderResponse fetchedOrder1 = orderService.getOrderById(101L);
        assertNotNull(fetchedOrder1);

        // Historical order measurement must remain strictly 34!
        assertNotNull(fetchedOrder1.getMeasurements());
        assertEquals("34", fetchedOrder1.getMeasurements().get("chest"),
                "Old order measurement must remain 34 even after customer measurement profile was updated to 36");
        assertEquals("28", fetchedOrder1.getMeasurements().get("waist"),
                "Old order measurement must remain 28 even after customer measurement profile was updated to 30");
    }

    @Test
    @DisplayName("Multiple orders preserve distinct measurement snapshots over customer timeline")
    void testMultipleOrdersPreserveDistinctSnapshots() {
        String mobile = "9876543210";
        String garmentType = "KURTI";

        // Order 1 snapshot: chest = 32
        CustomerOrder order1 = CustomerOrder.builder()
                .id(201L)
                .orderNumber("ORD-20260101-0001")
                .customerMobile(mobile)
                .garmentType(garmentType)
                .totalAmount(new BigDecimal("1200.00"))
                .branch(testBranch)
                .build();
        OrderMeasurement om1 = OrderMeasurement.builder()
                .id(11L)
                .orderId(201L)
                .garmentType(garmentType)
                .measurementsJson("{\"chest\":\"32\",\"length\":\"40\"}")
                .build();

        // Order 2 snapshot: chest = 34
        CustomerOrder order2 = CustomerOrder.builder()
                .id(202L)
                .orderNumber("ORD-20260501-0002")
                .customerMobile(mobile)
                .garmentType(garmentType)
                .totalAmount(new BigDecimal("1400.00"))
                .branch(testBranch)
                .build();
        OrderMeasurement om2 = OrderMeasurement.builder()
                .id(12L)
                .orderId(202L)
                .garmentType(garmentType)
                .measurementsJson("{\"chest\":\"34\",\"length\":\"41\"}")
                .build();

        // Customer's current profile: chest = 36
        CustomerMeasurementResponse currentProfile = CustomerMeasurementResponse.builder()
                .customerMobile(mobile)
                .customerName("Ananya Iyer")
                .garmentType(garmentType)
                .measurements(Map.of("chest", "36", "length", "42"))
                .build();
        when(customerMeasurementService.getLatestByCustomerMobile(mobile))
                .thenReturn(Optional.of(currentProfile));

        // Setup mock lookups
        when(customerRepository.findByCustomerMobile(mobile)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findById(201L)).thenReturn(Optional.of(order1));
        when(orderMeasurementRepository.findByOrderId(201L)).thenReturn(Optional.of(om1));
        when(orderRepository.findById(202L)).thenReturn(Optional.of(order2));
        when(orderMeasurementRepository.findByOrderId(202L)).thenReturn(Optional.of(om2));

        // Verify Order 1
        OrderResponse resp1 = orderService.getOrderById(201L);
        assertEquals("32", resp1.getMeasurements().get("chest"));
        assertEquals("40", resp1.getMeasurements().get("length"));

        // Verify Order 2
        OrderResponse resp2 = orderService.getOrderById(202L);
        assertEquals("34", resp2.getMeasurements().get("chest"));
        assertEquals("41", resp2.getMeasurements().get("length"));

        // Verify Customer's current active profile
        Map<String, Object> latestProfile = orderService.getLatestCustomerMeasurements(mobile);
        @SuppressWarnings("unchecked")
        Map<String, String> profileMeasurements = (Map<String, String>) latestProfile.get("measurements");
        assertEquals("36", profileMeasurements.get("chest"));
        assertEquals("42", profileMeasurements.get("length"));
    }

    @Test
    @DisplayName("Order with no measurements returns empty map without error")
    void testOrderWithNoMeasurements() {
        CustomerOrder order = CustomerOrder.builder()
                .id(301L)
                .orderNumber("ORD-20260905-0003")
                .customerMobile("9876543210")
                .garmentType(null)
                .totalAmount(new BigDecimal("800.00"))
                .branch(testBranch)
                .build();

        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findById(301L)).thenReturn(Optional.of(order));
        when(orderMeasurementRepository.findByOrderId(301L)).thenReturn(Optional.empty());

        OrderResponse resp = orderService.getOrderById(301L);
        assertNotNull(resp);
        assertNotNull(resp.getMeasurements());
        assertTrue(resp.getMeasurements().isEmpty());
    }
}
