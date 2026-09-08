package com.ritham.erp.module.customer.dto;

import com.ritham.erp.module.customer.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        if (customer == null) return null;
        return CustomerResponse.builder()
                .customerMobile(customer.getCustomerMobile())
                .customerName(customer.getCustomerName())
                .createdAt(customer.getCreatedAt())
                .totalOrders(customer.getTotalOrders() != null ? customer.getTotalOrders() : 0)
                .build();
    }
}
