package com.ritham.erp.module.customer.dto;

import com.ritham.erp.module.order.dto.CreateOrderRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MobileValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {"9876543210", "8123456789", "7000000000", "6999999999"})
    @DisplayName("CreateOrderRequest accepts valid 10-digit Indian mobile numbers (starting 6-9)")
    void createOrderRequest_ValidIndianMobile_Passes(String validMobile) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerMobile(validMobile);
        request.setDeliveryDate(LocalDateTime.now().plusDays(3));
        request.setTotalAmount(new BigDecimal("1500.00"));

        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validateProperty(request, "customerMobile");
        assertTrue(violations.isEmpty(), "Expected no violation for mobile: " + validMobile);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "0123456789", "5555555555", "2345678901", "98765", "987654321000", "abcdefghij", "98765abcde"})
    @DisplayName("CreateOrderRequest rejects invalid mobile numbers with standard message")
    void createOrderRequest_InvalidMobile_Fails(String invalidMobile) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerMobile(invalidMobile);
        request.setDeliveryDate(LocalDateTime.now().plusDays(3));
        request.setTotalAmount(new BigDecimal("1500.00"));

        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validateProperty(request, "customerMobile");
        assertFalse(violations.isEmpty(), "Expected validation failure for mobile: " + invalidMobile);
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Please enter a valid 10-digit Indian mobile number")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("CreateOrderRequest rejects blank mobile number")
    void createOrderRequest_BlankMobile_Fails(String blankMobile) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerMobile(blankMobile);

        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validateProperty(request, "customerMobile");
        assertFalse(violations.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"9876543210", "8123456789", "7000000000", "6999999999"})
    @DisplayName("CreateCustomerRequest accepts valid 10-digit Indian mobile numbers (starting 6-9)")
    void createCustomerRequest_ValidIndianMobile_Passes(String validMobile) {
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setCustomerName("Aarav");
        request.setCustomerMobile(validMobile);

        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validateProperty(request, "customerMobile");
        assertTrue(violations.isEmpty(), "Expected no violation for mobile: " + validMobile);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "0123456789", "5555555555", "2345678901", "98765", "987654321000", "abcdefghij"})
    @DisplayName("CreateCustomerRequest rejects invalid mobile numbers with standard message")
    void createCustomerRequest_InvalidMobile_Fails(String invalidMobile) {
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setCustomerName("Aarav");
        request.setCustomerMobile(invalidMobile);

        Set<ConstraintViolation<CreateCustomerRequest>> violations = validator.validateProperty(request, "customerMobile");
        assertFalse(violations.isEmpty(), "Expected validation failure for mobile: " + invalidMobile);
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Please enter a valid 10-digit Indian mobile number")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"9876543210", "8123456789", "7000000000", "6999999999"})
    @DisplayName("SaveCustomerMeasurementRequest accepts valid 10-digit Indian mobile numbers")
    void saveCustomerMeasurementRequest_ValidIndianMobile_Passes(String validMobile) {
        SaveCustomerMeasurementRequest request = new SaveCustomerMeasurementRequest();
        request.setCustomerName("Priya");
        request.setGarmentType("BLOUSE");
        request.setCustomerMobile(validMobile);

        Set<ConstraintViolation<SaveCustomerMeasurementRequest>> violations = validator.validateProperty(request, "customerMobile");
        assertTrue(violations.isEmpty(), "Expected no violation for mobile: " + validMobile);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "0123456789", "5555555555", "98765"})
    @DisplayName("SaveCustomerMeasurementRequest rejects invalid mobile numbers")
    void saveCustomerMeasurementRequest_InvalidMobile_Fails(String invalidMobile) {
        SaveCustomerMeasurementRequest request = new SaveCustomerMeasurementRequest();
        request.setCustomerName("Priya");
        request.setGarmentType("BLOUSE");
        request.setCustomerMobile(invalidMobile);

        Set<ConstraintViolation<SaveCustomerMeasurementRequest>> violations = validator.validateProperty(request, "customerMobile");
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Please enter a valid 10-digit Indian mobile number")));
    }
}
