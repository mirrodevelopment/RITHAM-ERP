package com.ritham.erp.module.migration.repository;

import com.ritham.erp.module.migration.entity.MigrationDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MigrationDocumentRepository extends JpaRepository<MigrationDocument, Long> {

    Optional<MigrationDocument> findByDocumentCode(String documentCode);

    List<MigrationDocument> findByBatchIdAndSourceFilePathOrderByPageNumberAsc(Long batchId, String sourceFilePath);

    @Query(value = "SELECT d FROM MigrationDocument d LEFT JOIN FETCH d.batch b LEFT JOIN FETCH b.branch LEFT JOIN FETCH d.createdBy WHERE d.batch.id = :batchId ORDER BY d.createdAt DESC",
           countQuery = "SELECT COUNT(d) FROM MigrationDocument d WHERE d.batch.id = :batchId")
    Page<MigrationDocument> findByBatchIdWithDetails(@Param("batchId") Long batchId, Pageable pageable);

    @Query(value = "SELECT d FROM MigrationDocument d LEFT JOIN FETCH d.batch b LEFT JOIN FETCH b.branch LEFT JOIN FETCH d.createdBy WHERE d.batch.id = :batchId AND d.reviewStatus = :reviewStatus ORDER BY d.createdAt DESC",
           countQuery = "SELECT COUNT(d) FROM MigrationDocument d WHERE d.batch.id = :batchId AND d.reviewStatus = :reviewStatus")
    Page<MigrationDocument> findByBatchIdAndReviewStatusWithDetails(@Param("batchId") Long batchId,
                                                                   @Param("reviewStatus") String reviewStatus,
                                                                   Pageable pageable);

    @Query("SELECT d FROM MigrationDocument d LEFT JOIN FETCH d.batch b LEFT JOIN FETCH b.branch LEFT JOIN FETCH d.createdBy WHERE d.id = :id")
    Optional<MigrationDocument> findByIdWithDetails(@Param("id") Long id);

    Page<MigrationDocument> findByBatchId(Long batchId, Pageable pageable);

    Page<MigrationDocument> findByBatchIdAndReviewStatus(Long batchId,
                                                          String reviewStatus,
                                                          Pageable pageable);

    List<MigrationDocument> findByBatchIdAndReviewStatus(Long batchId, String reviewStatus);

    /** Count documents by OCR status within a batch. */
    long countByBatchIdAndOcrStatus(Long batchId, String ocrStatus);

    /** Count documents by review status within a batch. */
    long countByBatchIdAndReviewStatus(Long batchId, String reviewStatus);

    /** Count documents by import status within a batch. */
    long countByBatchIdAndImportStatus(Long batchId, String importStatus);

    // ── 3-Stage Pipeline Queries ──────────────────────────────────────────────
    @Query(value = """
            SELECT d FROM MigrationDocument d
            LEFT JOIN FETCH d.batch b
            LEFT JOIN FETCH b.branch
            LEFT JOIN FETCH d.createdBy
            WHERE d.batch.id = :batchId
              AND (d.importStatus IS NULL OR d.importStatus != 'IMPORTED')
              AND (d.reviewStatus IS NULL OR d.reviewStatus NOT IN ('VERIFIED', 'REJECTED', 'IMPORTED'))
            ORDER BY d.createdAt DESC
            """,
           countQuery = """
            SELECT COUNT(d) FROM MigrationDocument d
            WHERE d.batch.id = :batchId
              AND (d.importStatus IS NULL OR d.importStatus != 'IMPORTED')
              AND (d.reviewStatus IS NULL OR d.reviewStatus NOT IN ('VERIFIED', 'REJECTED', 'IMPORTED'))
            """)
    Page<MigrationDocument> findByBatchIdNeedsReviewWithDetails(@Param("batchId") Long batchId, Pageable pageable);

    @Query(value = """
            SELECT d FROM MigrationDocument d
            LEFT JOIN FETCH d.batch b
            LEFT JOIN FETCH b.branch
            LEFT JOIN FETCH d.createdBy
            WHERE d.batch.id = :batchId
              AND d.reviewStatus = 'VERIFIED'
              AND (d.importStatus IS NULL OR d.importStatus != 'IMPORTED')
            ORDER BY d.createdAt DESC
            """,
           countQuery = """
            SELECT COUNT(d) FROM MigrationDocument d
            WHERE d.batch.id = :batchId
              AND d.reviewStatus = 'VERIFIED'
              AND (d.importStatus IS NULL OR d.importStatus != 'IMPORTED')
            """)
    Page<MigrationDocument> findByBatchIdVerifiedWithDetails(@Param("batchId") Long batchId, Pageable pageable);

    @Query(value = """
            SELECT d FROM MigrationDocument d
            LEFT JOIN FETCH d.batch b
            LEFT JOIN FETCH b.branch
            LEFT JOIN FETCH d.createdBy
            WHERE d.batch.id = :batchId
              AND (d.importStatus = 'IMPORTED' OR d.reviewStatus = 'IMPORTED')
            ORDER BY d.createdAt DESC
            """,
           countQuery = """
            SELECT COUNT(d) FROM MigrationDocument d
            WHERE d.batch.id = :batchId
              AND (d.importStatus = 'IMPORTED' OR d.reviewStatus = 'IMPORTED')
            """)
    Page<MigrationDocument> findByBatchIdImportedWithDetails(@Param("batchId") Long batchId, Pageable pageable);

    @Query("""
            SELECT COUNT(d) FROM MigrationDocument d
            WHERE d.batch.id = :batchId
              AND (d.importStatus IS NULL OR d.importStatus != 'IMPORTED')
              AND (d.reviewStatus IS NULL OR d.reviewStatus NOT IN ('VERIFIED', 'REJECTED', 'IMPORTED'))
            """)
    long countByBatchIdNeedsReview(@Param("batchId") Long batchId);

    @Query("""
            SELECT COUNT(d) FROM MigrationDocument d
            WHERE d.batch.id = :batchId
              AND d.reviewStatus = 'VERIFIED'
              AND (d.importStatus IS NULL OR d.importStatus != 'IMPORTED')
            """)
    long countByBatchIdVerified(@Param("batchId") Long batchId);

    @Query("""
            SELECT COUNT(d) FROM MigrationDocument d
            WHERE d.batch.id = :batchId
              AND (d.importStatus = 'IMPORTED' OR d.reviewStatus = 'IMPORTED')
            """)
    long countByBatchIdImported(@Param("batchId") Long batchId);

    /** All documents needing review across all batches (for admin dashboard). */
    Page<MigrationDocument> findByReviewStatus(String reviewStatus, Pageable pageable);

    @Query("""
            SELECT COUNT(d) FROM MigrationDocument d
            WHERE d.batch.branch.id = :branchId
              AND d.reviewStatus = :status
            """)
    long countByBranchIdAndReviewStatus(@Param("branchId") Long branchId,
                                         @Param("status") String status);

    /** Find the highest document sequence for a given code prefix. */
    @Query("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(d.documentCode, :prefixLength + 2) AS integer)), 0)
            FROM MigrationDocument d
            WHERE d.documentCode LIKE :prefix%
            """)
    int findMaxSequenceForPrefix(@Param("prefix") String prefix,
                                  @Param("prefixLength") int prefixLength);
}
