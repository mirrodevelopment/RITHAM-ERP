package com.ritham.erp.module.migration.repository;

import com.ritham.erp.module.migration.entity.MigrationBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MigrationBatchRepository extends JpaRepository<MigrationBatch, Long> {

    boolean existsByBatchCode(String batchCode);

    Optional<MigrationBatch> findByBatchCode(String batchCode);

    /** All batches for a specific branch — eagerly loads Branch and CreatedBy to avoid LazyInitializationException. */
    @Query(value = "SELECT b FROM MigrationBatch b LEFT JOIN FETCH b.branch LEFT JOIN FETCH b.createdBy WHERE b.branch.id = :branchId ORDER BY b.createdAt DESC",
           countQuery = "SELECT COUNT(b) FROM MigrationBatch b WHERE b.branch.id = :branchId")
    Page<MigrationBatch> findByBranchIdWithBranch(@Param("branchId") Long branchId, Pageable pageable);

    /** All batches across all branches (global admin) — eagerly loads Branch and CreatedBy. */
    @Query(value = "SELECT b FROM MigrationBatch b LEFT JOIN FETCH b.branch LEFT JOIN FETCH b.createdBy ORDER BY b.createdAt DESC",
           countQuery = "SELECT COUNT(b) FROM MigrationBatch b")
    Page<MigrationBatch> findAllWithBranch(Pageable pageable);

    /** Single batch by ID with Branch and CreatedBy eagerly loaded. */
    @Query("SELECT b FROM MigrationBatch b LEFT JOIN FETCH b.branch LEFT JOIN FETCH b.createdBy WHERE b.id = :id")
    Optional<MigrationBatch> findByIdWithDetails(@Param("id") Long id);

    /** All batches for a specific branch (branch users) — plain version kept for tests. */
    Page<MigrationBatch> findByBranchId(Long branchId, Pageable pageable);

    /** Find the highest sequence suffix for a given year+branch prefix. */
    @Query("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(b.batchCode, :prefixLength + 2) AS integer)), 0)
            FROM MigrationBatch b
            WHERE b.batchCode LIKE :prefix%
            """)
    int findMaxSequenceForPrefix(@Param("prefix") String prefix,
                                  @Param("prefixLength") int prefixLength);
}
