package com.ritham.erp.module.customer.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CustomerResponse {
    private String        customerMobile;
    private String        customerName;
    private LocalDateTime createdAt;
    /** Read-only. Starts at 0, auto-incremented by order creation. */
    private Integer       totalOrders;
}
