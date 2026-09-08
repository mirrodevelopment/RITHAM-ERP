package com.ritham.erp.module.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentResponse {

    private Long id;
    private Long orderId;
    private String orderNumber;
    private String paymentType;
    private String paymentMethod;
    private BigDecimal amount;
    private LocalDateTime paymentDate;
    private Long branchId;
    private String branchName;
    private String collector;
}
