package com.ritham.erp.module.production.repository;

import com.ritham.erp.module.production.entity.ProductionStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductionStageRepository extends JpaRepository<ProductionStage, Long> {

    List<ProductionStage> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    List<ProductionStage> findAllByOrderByDisplayOrderAsc();

    Optional<ProductionStage> findByStageKey(String stageKey);

    boolean existsByStageKey(String stageKey);

    boolean existsByStageKeyIgnoreCaseAndIsActiveTrue(String stageKey);

    @Query("SELECT MAX(p.displayOrder) FROM ProductionStage p")
    Integer findMaxDisplayOrder();
}
