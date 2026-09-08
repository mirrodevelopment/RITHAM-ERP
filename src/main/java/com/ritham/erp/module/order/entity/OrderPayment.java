package com.ritham.erp.module.order.entity;

import com.ritham.erp.module.order.service.OrderService;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing an individual payment transaction recorded against an order.
 * Maps to the existing {@code order_payments} database table defined in V1 baseline schema.
 */
@Entity
@Table(name = "order_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_type", nullable = false, length = 20)
    private String paymentType;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @PrePersist
    protected void onCreate() {
        if (paymentDate == null) {
            paymentDate = LocalDateTime.now();
        }
        if (paymentType == null || paymentType.isBlank()) {
            paymentType = OrderService.PAYMENT_TYPE_PARTIAL;
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            paymentMethod = OrderService.PAYMENT_MODE_CASH;
        }
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
    }
}
