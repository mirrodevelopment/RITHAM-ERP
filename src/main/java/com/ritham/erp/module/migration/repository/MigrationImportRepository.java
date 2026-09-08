package com.ritham.erp.module.migration.repository;

import com.ritham.erp.module.migration.entity.MigrationImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MigrationImportRepository extends JpaRepository<MigrationImport, Long> {

    Optional<MigrationImport> findByDocumentId(Long documentId);

    boolean existsByDocumentIdAndImportStatus(Long documentId, String importStatus);

    List<MigrationImport> findByCustomerMobile(String customerMobile);

    Page<MigrationImport> findByImportStatus(String importStatus, Pageable pageable);
}
