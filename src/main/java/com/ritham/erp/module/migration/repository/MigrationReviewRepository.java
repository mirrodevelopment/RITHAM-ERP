package com.ritham.erp.module.migration.repository;

import com.ritham.erp.module.migration.entity.MigrationReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MigrationReviewRepository extends JpaRepository<MigrationReview, Long> {

    List<MigrationReview> findByDocumentIdOrderByReviewedAtDesc(Long documentId);

    Optional<MigrationReview> findFirstByDocumentIdOrderByReviewedAtDesc(Long documentId);
}
