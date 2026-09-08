package com.ritham.erp.module.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Minimal Customer entity according to Ritham ERP Specification.
 * Primary Key is customer_mobile. Only customer_mobile and customer_name are stored.
 * totalOrders is a read-only counter managed by the application — starts at 0,
 * incremented automatically each time an order is placed for this customer.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @Column(name = "customer_mobile", nullable = false, length = 15)
    private String customerMobile;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Total number of orders placed by this customer.
     * Starts at 0. Incremented by the order service — never editable by users.
     */
    @Column(name = "total_orders", nullable = false)
    @Builder.Default
    private Integer totalOrders = 0;

    @PrePersist
    protected void onCreate() {
        this.createdAt  = LocalDateTime.now();
        if (this.totalOrders == null) this.totalOrders = 0;
    }

    /** Called by OrderService when a new order is placed for this customer. */
    public void incrementOrderCount() {
        this.totalOrders = (this.totalOrders == null ? 0 : this.totalOrders) + 1;
    }
}
