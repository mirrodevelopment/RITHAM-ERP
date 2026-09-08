package com.ritham.erp.module.customer;


import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.module.customer.controller.CustomerMeasurementController;
import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.entity.CustomerMeasurement;
import com.ritham.erp.module.customer.repository.CustomerMeasurementRepository;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification tests for TASK-035:
 * Authorize customer measurement deletion endpoint with @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS_MANAGER')").
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CustomerMeasurementAuthorizationTest {

    private static final String TEST_MOBILE = "9112233445";

    @Autowired
    private CustomerMeasurementController controller;

    @Autowired
    private CustomerMeasurementRepository measurementRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        if (!customerRepository.existsById(TEST_MOBILE)) {
            customerRepository.save(Customer.builder()
                    .customerMobile(TEST_MOBILE)
                    .customerName("Auth Test Customer")
                    .build());
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "testuser",
                "N/A",
                List.of(new SimpleGrantedAuthority(role))
        );
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    private CustomerMeasurement createMeasurement(String garmentType) {
        measurementRepository.findByCustomerMobileAndGarmentType(TEST_MOBILE, garmentType)
                .ifPresent(m -> measurementRepository.delete(m));

        return measurementRepository.save(CustomerMeasurement.builder()
                .customerMobile(TEST_MOBILE)
                .customerName("Auth Test Customer")
                .garmentType(garmentType)
                .measurementsJson("{\"chest\":\"38\"}")
                .build());
    }

    @Test
    @DisplayName("TASK-035: Reflection check - deleteMeasurement has @PreAuthorize(\"hasAnyRole('ADMIN', 'OPERATIONS_MANAGER')\")")
    void reflectionCheck_PreAuthorizeAnnotationPresent() throws NoSuchMethodException {
        Method method = CustomerMeasurementController.class.getMethod("deleteMeasurement", Long.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuth, "deleteMeasurement must have @PreAuthorize annotation");
        assertEquals("hasAnyRole('ADMIN', 'OPERATIONS_MANAGER')", preAuth.value(),
                "deleteMeasurement must allow only ADMIN and OPERATIONS_MANAGER");
    }

    @Test
    @DisplayName("TASK-035: ADMIN -> allowed to delete customer measurement")
    void admin_CanDeleteMeasurement() {
        CustomerMeasurement measurement = createMeasurement("SUIT_ADMIN");
        authenticateAs("ROLE_ADMIN");

        ResponseEntity<ApiResponse<Void>> response = controller.deleteMeasurement(measurement.getId());

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(measurementRepository.existsById(measurement.getId()), "Measurement should be deleted from repository");
    }

    @Test
    @DisplayName("TASK-035: OPERATIONS_MANAGER -> allowed to delete customer measurement")
    void operationsManager_CanDeleteMeasurement() {
        CustomerMeasurement measurement = createMeasurement("SUIT_OPS");
        authenticateAs("ROLE_OPERATIONS_MANAGER");

        ResponseEntity<ApiResponse<Void>> response = controller.deleteMeasurement(measurement.getId());

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(measurementRepository.existsById(measurement.getId()), "Measurement should be deleted from repository");
    }

    @Test
    @DisplayName("TASK-035: Lower-privilege employee (ROLE_PRODUCTION_EMPLOYEE) -> denied with AccessDeniedException")
    void lowerPrivilegeEmployee_CannotDeleteMeasurement() {
        CustomerMeasurement measurement = createMeasurement("SUIT_PROD");
        authenticateAs("ROLE_PRODUCTION_EMPLOYEE");

        assertThrows(AccessDeniedException.class, () -> controller.deleteMeasurement(measurement.getId()),
                "Production employee must be denied access to delete measurements");
        assertTrue(measurementRepository.existsById(measurement.getId()), "Measurement must NOT be deleted");
    }

    @Test
    @DisplayName("TASK-035: Lower-privilege employee (ROLE_RECEPTION) -> denied with AccessDeniedException")
    void receptionEmployee_CannotDeleteMeasurement() {
        CustomerMeasurement measurement = createMeasurement("SUIT_RECEPT");
        authenticateAs("ROLE_RECEPTION");

        assertThrows(AccessDeniedException.class, () -> controller.deleteMeasurement(measurement.getId()),
                "Reception staff must be denied access to delete measurements");
        assertTrue(measurementRepository.existsById(measurement.getId()), "Measurement must NOT be deleted");
    }

    @Test
    @DisplayName("TASK-035: Unauthenticated request -> denied with security exception")
    void unauthenticatedRequest_CannotDeleteMeasurement() {
        CustomerMeasurement measurement = createMeasurement("SUIT_UNAUTH");
        SecurityContextHolder.clearContext();

        assertThrows(Exception.class, () -> controller.deleteMeasurement(measurement.getId()),
                "Unauthenticated request must be denied access to delete measurements");
        assertTrue(measurementRepository.existsById(measurement.getId()), "Measurement must NOT be deleted");
    }
}
