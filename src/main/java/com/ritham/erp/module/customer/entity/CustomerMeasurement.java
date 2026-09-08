package com.ritham.erp.module.customer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_measurements", uniqueConstraints = {
    @UniqueConstraint(name = "uq_cust_measurements_mobile_garment", columnNames = {"customer_mobile", "garment_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_mobile", nullable = false, length = 15)
    private String customerMobile;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "garment_type", nullable = false, length = 30)
    private String garmentType;

    @Column(name = "lining", length = 30)
    @Builder.Default
    private String lining = "WITHOUT_LINING";

    @Column(name = "measurements_json", nullable = false, columnDefinition = "TEXT")
    private String measurementsJson;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.lining == null) this.lining = "WITHOUT_LINING";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
