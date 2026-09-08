package com.ritham.erp.module.order.entity;

import com.ritham.erp.module.order.service.OrderService;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Core business entity: Customer Order Master.
 * Maps to customer_orders table (created in V2 migration).
 */
@Entity
@Table(name = "customer_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String orderNumber;

    @Column(name = "customer_mobile", nullable = false, length = 15)
    private String customerMobile;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "delivery_date", nullable = false)
    private LocalDateTime deliveryDate;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = OrderService.STATUS_PENDING;

    @Column(name = "payment_status", nullable = false, length = 30)
    @Builder.Default
    private String paymentStatus = OrderService.PAYMENT_STATUS_PENDING;

    @Column(name = "payment_mode", length = 30)
    @Builder.Default
    private String paymentMode = OrderService.PAYMENT_MODE_CASH;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "advance_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal advanceAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "assigned_employee_id")
    private Long assignedEmployeeId;

    @Column(name = "assigned_employee_name", length = 100)
    private String assignedEmployeeName;

    @Column(name = "garment_type", length = 50)
    private String garmentType;

    @Column(name = "lining", length = 50)
    private String lining;

    /**
     * Identifies the origin of this order.
     * 'ERP' = created via normal ERP workflow.
     * 'HISTORICAL_MIGRATION' = imported from a paper record via the migration module.
     */
    @Column(name = "source", nullable = false, length = 30)
    @Builder.Default
    private String source = "ERP";

    @Column(name = "receiver_name", length = 100)
    private String receiverName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private com.ritham.erp.module.branch.entity.Branch branch;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (orderDate == null)   orderDate   = LocalDateTime.now();
        if (status == null)      status      = OrderService.STATUS_PENDING;
        if (paymentStatus == null) paymentStatus = OrderService.PAYMENT_STATUS_PENDING;
        if (paymentMode == null)   paymentMode   = OrderService.PAYMENT_MODE_CASH;
        if (totalAmount == null)   totalAmount   = BigDecimal.ZERO;
        if (advanceAmount == null) advanceAmount = BigDecimal.ZERO;
        if (paidAmount == null)    paidAmount    = BigDecimal.ZERO;
        if (discountAmount == null) discountAmount = BigDecimal.ZERO;
        if (version == null)        version = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
