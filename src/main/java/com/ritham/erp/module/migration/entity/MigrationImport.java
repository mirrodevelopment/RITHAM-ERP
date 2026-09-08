package com.ritham.erp.module.migration.entity;

import com.ritham.erp.module.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Complete audit trail of what was created in production ERP tables
 * as the result of importing a single migration document.
 *
 * <p>The FK columns are nullable because:
 * <ul>
 *   <li>{@code customer_mobile} — set if the customer was created/matched</li>
 *   <li>{@code order_id}       — set if an order was successfully created</li>
 *   <li>{@code measurement_id} — set if an order measurement snapshot was created</li>
 * </ul>
 * On import failure, only {@code import_status} and {@code error_message} are set.
 */
@Entity
@Table(name = "migration_imports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private MigrationDocument document;

    // ── Links to production records created ───────────────────────────────────

    /** Mobile number of the customer that was created or matched. */
    @Column(name = "customer_mobile", length = 15)
    private String customerMobile;

    /** ID of the CustomerOrder created in the production table. */
    @Column(name = "order_id")
    private Long orderId;

    /** ID of the OrderMeasurement snapshot created. */
    @Column(name = "measurement_id")
    private Long measurementId;

    // ── Outcome ───────────────────────────────────────────────────────────────

    /** IMPORTED | IMPORT_FAILED | DUPLICATE */
    @Column(name = "import_status", nullable = false, length = 30)
    private String importStatus;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // ── Audit ─────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by")
    private Employee importedBy;

    @Column(name = "imported_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime importedAt = OffsetDateTime.now();
}
