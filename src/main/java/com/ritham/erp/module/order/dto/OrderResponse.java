package com.ritham.erp.module.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OrderResponse {
    private Long          id;
    private String        orderNumber;
    private String        customerMobile;
    private String        customerName;       // joined from customers table
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
    private String        status;
    private String        paymentStatus;
    private String        paymentMode;
    private BigDecimal    totalAmount;
    private BigDecimal    discountAmount;
    private BigDecimal    advanceAmount;
    private BigDecimal    paidAmount;
    private BigDecimal    balanceAmount;      // computed: totalAmount - discountAmount - paidAmount
    private Long          assignedEmployeeId;
    private String        assignedEmployeeName;
    private String        garmentType;
    private String        lining;
    private String        receiverName;
    private Long          branchId;
    private String        branchName;
    private String        branchCode;
    private java.util.Map<String, String> measurements;
    private java.util.List<OrderPaymentResponse> payments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
