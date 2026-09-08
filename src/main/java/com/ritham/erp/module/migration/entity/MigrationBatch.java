package com.ritham.erp.module.migration.entity;

import com.ritham.erp.module.branch.entity.Branch;
import com.ritham.erp.module.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * A migration batch groups all scanned documents from the same branch and year.
 * Document counts are updated atomically as documents progress through the pipeline.
 */
@Entity
@Table(name = "migration_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique code, e.g. BATCH-2023-CBE-001. */
    @Column(name = "batch_code", nullable = false, unique = true, length = 30)
    private String batchCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "description", length = 255)
    private String description;

    /** Paper-record year represented by this batch (e.g. 2022). */
    @Column(name = "source_year", nullable = false)
    private Short sourceYear;

    // ── Document counters ─────────────────────────────────────────────────────

    @Column(name = "total_documents", nullable = false)
    @Builder.Default
    private Integer totalDocuments = 0;

    @Column(name = "uploaded_count", nullable = false)
    @Builder.Default
    private Integer uploadedCount = 0;

    @Column(name = "processed_count", nullable = false)
    @Builder.Default
    private Integer processedCount = 0;

    @Column(name = "reviewed_count", nullable = false)
    @Builder.Default
    private Integer reviewedCount = 0;

    @Column(name = "approved_count", nullable = false)
    @Builder.Default
    private Integer approvedCount = 0;

    @Column(name = "imported_count", nullable = false)
    @Builder.Default
    private Integer importedCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private Integer failedCount = 0;

    /** Batch lifecycle status: OPEN | COMPLETED | CLOSED. */
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "OPEN";

    // ── Audit ─────────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void incrementUploaded()  { this.uploadedCount++;  this.totalDocuments++; }
    public void incrementProcessed() { this.processedCount++; }
    public void incrementReviewed()  { this.reviewedCount++;  }
    public void incrementApproved()  { this.approvedCount++;  }
    public void incrementImported()  { this.importedCount++;  }
    public void incrementFailed()    { this.failedCount++;    }

    public int getProgress() {
        if (totalDocuments == 0) return 0;
        return (int) ((importedCount * 100.0) / totalDocuments);
    }
}
