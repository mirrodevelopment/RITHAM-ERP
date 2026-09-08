package com.ritham.erp.security;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.branch.entity.Branch;

import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.employee.entity.Employee;
import com.ritham.erp.module.employee.repository.EmployeeRepository;
import com.ritham.erp.module.employee.service.EmployeeService;
import com.ritham.erp.module.order.dto.OrderMapper;
import com.ritham.erp.module.order.dto.OrderResponse;
import com.ritham.erp.module.order.dto.RecordPaymentRequest;
import com.ritham.erp.module.order.entity.CustomerOrder;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import com.ritham.erp.module.order.repository.OrderMeasurementRepository;
import com.ritham.erp.module.order.repository.OrderPaymentRepository;
import com.ritham.erp.module.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchIsolationTest {

    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private OrderMeasurementRepository orderMeasurementRepository;
    @Mock
    private OrderPaymentRepository orderPaymentRepository;
    @Spy
    private OrderMapper orderMapper = new OrderMapper();
    @Mock
    private com.ritham.erp.module.production.repository.ProductionStageRepository productionStageRepository;

    @InjectMocks
    private OrderService orderService;

    @Mock
    private EmployeeRepository employeeRepository;
    @Spy
    private com.ritham.erp.module.employee.dto.EmployeeMapper employeeMapper = new com.ritham.erp.module.employee.dto.EmployeeMapper();
    @InjectMocks
    private EmployeeService employeeService;


    private Branch branch2;
    private CustomerOrder orderBranch2;
    private Employee employeeBranch2;

    @BeforeEach
    void setUp() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        BranchContext.clear();


        branch2 = Branch.builder().id(2L).branchCode("MAA").name("Chennai").build();

        orderBranch2 = CustomerOrder.builder()
                .id(100L)
                .orderNumber("ORD-202609-0100")
                .customerMobile("9876543210")
                .status("PENDING")
                .paymentStatus("PENDING")
                .totalAmount(new BigDecimal("2000.00"))
                .paidAmount(BigDecimal.ZERO)
                .branch(branch2)
                .build();

        employeeBranch2 = Employee.builder()
                .id(20L)
                .employeeCode("EMP-020")
                .fullName("Chennai Staff")
                .mobileNumber("9876543220")
                .username("chennai_staff")
                .branch(branch2)
                .isActive(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        BranchContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ── Order Branch Isolation (TASK-003) ──────────────────────────────────

    @Test
    @DisplayName("Branch 1 user accessing Branch 2 order throws ACCESS_DENIED (403)")
    void getOrder_CrossBranch_ThrowsAccessDenied() {
        BranchContext.setBranchId(1L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(orderBranch2));

        AppException ex = assertThrows(AppException.class, () -> orderService.getOrderById(100L));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Branch 1 user updating status of Branch 2 order throws ACCESS_DENIED (403)")
    void updateOrderStatus_CrossBranch_ThrowsAccessDenied() {
        BranchContext.setBranchId(1L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(orderBranch2));

        AppException ex = assertThrows(AppException.class, () -> orderService.updateStatus(100L, "DESIGNING"));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Branch 1 user recording payment on Branch 2 order throws ACCESS_DENIED (403)")
    void recordPayment_CrossBranch_ThrowsAccessDenied() {
        BranchContext.setBranchId(1L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(orderBranch2));

        RecordPaymentRequest req = new RecordPaymentRequest();
        req.setAmount(new BigDecimal("500.00"));
        req.setPaymentMethod("UPI");
        req.setPaymentType("ADVANCE");

        AppException ex = assertThrows(AppException.class, () -> orderService.recordPayment(100L, req));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Branch 2 user accessing Branch 2 order succeeds")
    void getOrder_SameBranch_Succeeds() {
        BranchContext.setBranchId(2L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(orderBranch2));
        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.empty());

        OrderResponse res = orderService.getOrderById(100L);
        assertNotNull(res);
        assertEquals("ORD-202609-0100", res.getOrderNumber());
    }

    @Test
    @DisplayName("Global Admin (null branchId in BranchContext) accessing Branch 2 order succeeds")
    void getOrder_GlobalAdmin_Succeeds() {
        BranchContext.setBranchId(null); // Global admin
        when(orderRepository.findById(100L)).thenReturn(Optional.of(orderBranch2));
        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.empty());

        OrderResponse res = orderService.getOrderById(100L);
        assertNotNull(res);
        assertEquals("ORD-202609-0100", res.getOrderNumber());
    }

    @Test
    @DisplayName("System Administrator (ROLE_ADMIN) can access any order even when BranchContext is set to another branch")
    void getOrder_SystemAdministratorWithBranchContext_Succeeds() {
        BranchContext.setBranchId(1L); // Admin has Branch 1 selected in UI dropdown
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "admin", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        when(orderRepository.findById(100L)).thenReturn(Optional.of(orderBranch2)); // Order belongs to Branch 2
        when(customerRepository.findByCustomerMobile("9876543210")).thenReturn(Optional.empty());

        OrderResponse res = orderService.getOrderById(100L);
        assertNotNull(res);
        assertEquals("ORD-202609-0100", res.getOrderNumber());
    }

    // ── Employee Branch Isolation (TASK-004) ───────────────────────────────

    @Test
    @DisplayName("Branch 1 user accessing Branch 2 employee throws ACCESS_DENIED (403)")
    void getEmployee_CrossBranch_ThrowsAccessDenied() {
        BranchContext.setBranchId(1L);
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(employeeBranch2));

        AppException ex = assertThrows(AppException.class, () -> employeeService.getEmployeeById(20L));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Branch 1 user toggling status of Branch 2 employee throws ACCESS_DENIED (403)")
    void toggleEmployeeStatus_CrossBranch_ThrowsAccessDenied() {
        BranchContext.setBranchId(1L);
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(employeeBranch2));

        AppException ex = assertThrows(AppException.class, () -> employeeService.toggleStatus(20L));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Branch 1 user deleting Branch 2 employee throws ACCESS_DENIED (403)")
    void deleteEmployee_CrossBranch_ThrowsAccessDenied() {
        BranchContext.setBranchId(1L);
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(employeeBranch2));

        AppException ex = assertThrows(AppException.class, () -> employeeService.deleteEmployee(20L));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Global Admin (null branchId) accessing Branch 2 employee succeeds")
    void getEmployee_GlobalAdmin_Succeeds() {
        BranchContext.setBranchId(null);
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(employeeBranch2));

        // EmployeeMapper requires employee to be mapped
        assertDoesNotThrow(() -> employeeService.getEmployeeById(20L));
    }

    @Test
    @DisplayName("System Administrator (ROLE_ADMIN) can access any employee even when BranchContext is set to another branch")
    void getEmployee_SystemAdministratorWithBranchContext_Succeeds() {
        BranchContext.setBranchId(1L); // Admin has Branch 1 selected
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "admin", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        when(employeeRepository.findById(20L)).thenReturn(Optional.of(employeeBranch2)); // Employee belongs to Branch 2

        assertDoesNotThrow(() -> employeeService.getEmployeeById(20L));
    }
}
