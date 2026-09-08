package com.ritham.erp.module.order.service;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderNumberSequenceTest {

    @Mock
    private CustomerOrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("^ORD-\\d{8}-\\d{4}$");

    @Test
    @DisplayName("generateOrderNumber produces correct format matching ORD-YYYYMM-XXXX")
    void generatesCorrectFormat() {
        when(orderRepository.getNextOrderSequenceValue()).thenReturn(101L);
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);

        String orderNumber = orderService.generateOrderNumber();

        assertNotNull(orderNumber);
        assertTrue(ORDER_NUMBER_PATTERN.matcher(orderNumber).matches(),
                "Order number should match format ORD-YYYYMM-XXXX, but was: " + orderNumber);
        assertTrue(orderNumber.endsWith("-0101"), "Expected suffix -0101, but got: " + orderNumber);
    }

    @Test
    @DisplayName("generateOrderNumber resolves collisions by fetching next sequence value")
    void resolvesCollisions() {
        // First call returns 100, second returns 101
        when(orderRepository.getNextOrderSequenceValue()).thenReturn(100L, 101L);
        // Candidate with 100 already exists, candidate with 101 does not exist
        when(orderRepository.existsByOrderNumber(org.mockito.ArgumentMatchers.contains("-0100"))).thenReturn(true);
        when(orderRepository.existsByOrderNumber(org.mockito.ArgumentMatchers.contains("-0101"))).thenReturn(false);

        String orderNumber = orderService.generateOrderNumber();

        assertNotNull(orderNumber);
        assertTrue(orderNumber.endsWith("-0101"), "Expected suffix -0101 after collision resolution");
    }

    @Test
    @DisplayName("generateOrderNumber falls back to order count + 1 when DB sequence query fails")
    void fallsBackToCountWhenSequenceFails() {
        when(orderRepository.getNextOrderSequenceValue()).thenThrow(new RuntimeException("DB sequence unavailable"));
        when(orderRepository.count()).thenReturn(55L);
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(false);

        String orderNumber = orderService.generateOrderNumber();

        assertNotNull(orderNumber);
        assertTrue(ORDER_NUMBER_PATTERN.matcher(orderNumber).matches());
        assertTrue(orderNumber.endsWith("-0056"), "Expected suffix -0056 from count fallback");
    }

    @Test
    @DisplayName("generateOrderNumber throws INTERNAL_SERVER_ERROR if max retries exceeded")
    void throwsExceptionWhenMaxRetriesExceeded() {
        when(orderRepository.getNextOrderSequenceValue()).thenReturn(1L);
        when(orderRepository.existsByOrderNumber(anyString())).thenReturn(true); // Always collides

        AppException ex = assertThrows(AppException.class, () -> orderService.generateOrderNumber());
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("unique order number"));
    }
}
