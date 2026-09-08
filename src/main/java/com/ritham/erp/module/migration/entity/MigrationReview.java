package com.ritham.erp.module.migration.entity;

import com.ritham.erp.module.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Immutable record of a human review pass on a migration document.
 * One document may have multiple review rows (rejected, then re-submitted, then approved).
 */
@Entity
@Table(name = "migration_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MigrationReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private MigrationDocument document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private Employee reviewer;

    /** APPROVED | REJECTED | NEEDS_REWORK */
    @Column(name = "review_status", nullable = false, length = 30)
    private String reviewStatus;

    /** JSON of the corrected ExtractedData saved by the reviewer. */
    @Column(name = "corrected_data_json", columnDefinition = "TEXT")
    private String correctedDataJson;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime reviewedAt = OffsetDateTime.now();
}
