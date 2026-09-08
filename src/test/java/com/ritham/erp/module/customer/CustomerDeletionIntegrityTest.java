package com.ritham.erp.module.customer;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.customer.controller.CustomerController;
import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.repository.CustomerMeasurementRepository;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.customer.service.CustomerService;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerDeletionIntegrityTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @Mock
    private CustomerMeasurementRepository customerMeasurementRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    @DisplayName("deleteCustomer throws OPERATION_NOT_ALLOWED when customer has existing orders")
    void deleteCustomer_WhenHasOrders_ThrowsOperationNotAllowed() {
        String mobile = "9876543210";
        Customer customer = Customer.builder()
                .customerMobile(mobile)
                .customerName("Arun Kumar")
                .totalOrders(1)
                .build();

        when(customerRepository.findByCustomerMobile(mobile)).thenReturn(Optional.of(customer));
        when(customerOrderRepository.existsByCustomerMobile(mobile)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> customerService.deleteCustomer(mobile));
        assertEquals(ErrorCode.OPERATION_NOT_ALLOWED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("order history"));
        verify(customerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteCustomer throws OPERATION_NOT_ALLOWED when totalOrders > 0 even if active order list query is false")
    void deleteCustomer_WhenTotalOrdersPositive_ThrowsOperationNotAllowed() {
        String mobile = "9876543211";
        Customer customer = Customer.builder()
                .customerMobile(mobile)
                .customerName("Kavitha S")
                .totalOrders(3)
                .build();

        when(customerRepository.findByCustomerMobile(mobile)).thenReturn(Optional.of(customer));
        when(customerOrderRepository.existsByCustomerMobile(mobile)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> customerService.deleteCustomer(mobile));
        assertEquals(ErrorCode.OPERATION_NOT_ALLOWED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("order history"));
        verify(customerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteCustomer throws OPERATION_NOT_ALLOWED when customer has existing measurement records")
    void deleteCustomer_WhenHasMeasurements_ThrowsOperationNotAllowed() {
        String mobile = "9876543212";
        Customer customer = Customer.builder()
                .customerMobile(mobile)
                .customerName("Divya B")
                .totalOrders(0)
                .build();

        when(customerRepository.findByCustomerMobile(mobile)).thenReturn(Optional.of(customer));
        when(customerOrderRepository.existsByCustomerMobile(mobile)).thenReturn(false);
        when(customerMeasurementRepository.existsByCustomerMobile(mobile)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> customerService.deleteCustomer(mobile));
        assertEquals(ErrorCode.OPERATION_NOT_ALLOWED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("measurement records"));
        verify(customerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteCustomer throws CUSTOMER_NOT_FOUND when customer does not exist")
    void deleteCustomer_WhenNotFound_ThrowsCustomerNotFound() {
        String mobile = "9999999999";
        when(customerRepository.findByCustomerMobile(mobile)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> customerService.deleteCustomer(mobile));
        assertEquals(ErrorCode.CUSTOMER_NOT_FOUND, ex.getErrorCode());
        verify(customerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteCustomer succeeds for clean customer record with zero orders and zero measurements")
    void deleteCustomer_WhenCleanRecord_Succeeds() {
        String mobile = "9876543213";
        Customer customer = Customer.builder()
                .customerMobile(mobile)
                .customerName("New Lead")
                .totalOrders(0)
                .build();

        when(customerRepository.findByCustomerMobile(mobile)).thenReturn(Optional.of(customer));
        when(customerOrderRepository.existsByCustomerMobile(mobile)).thenReturn(false);
        when(customerMeasurementRepository.existsByCustomerMobile(mobile)).thenReturn(false);

        assertDoesNotThrow(() -> customerService.deleteCustomer(mobile));
        verify(customerRepository, times(1)).delete(customer);
    }

    @Test
    @DisplayName("CustomerController.deleteCustomer must be protected with @PreAuthorize(\"hasRole('ADMIN')\")")
    void deleteCustomerEndpoint_IsProtectedByAdminRole() throws NoSuchMethodException {
        Method method = CustomerController.class.getMethod("deleteCustomer", String.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuth, "deleteCustomer endpoint must be annotated with @PreAuthorize");
        assertEquals("hasRole('ADMIN')", preAuth.value(), "deleteCustomer endpoint must require ADMIN role");
    }
}
