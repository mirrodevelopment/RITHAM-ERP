package com.ritham.erp.module.production.service;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.production.dto.*;
import com.ritham.erp.module.production.entity.ProductionStage;
import com.ritham.erp.module.production.repository.ProductionStageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionStageService {

    private static final Logger log = LoggerFactory.getLogger(ProductionStageService.class);
    private final ProductionStageRepository stageRepository;

    @Transactional(readOnly = true)
    public List<ProductionStageResponse> getAllActiveStages() {
        return stageRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductionStageResponse> getAllStages() {
        return stageRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductionStageResponse createCustomStage(CreateProductionStageRequest request) {
        String cleanKey = request.getTitle()
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "_")
                .replaceAll("_+", "_");

        if (stageRepository.existsByStageKey(cleanKey)) {
            cleanKey = cleanKey + "_" + (System.currentTimeMillis() % 1000);
        }

        Integer maxSeq = stageRepository.findMaxDisplayOrder();
        int nextSeq = (maxSeq != null) ? maxSeq + 1 : 1;

        ProductionStage stage = ProductionStage.builder()
                .stageKey(cleanKey)
                .title(request.getTitle().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .icon(request.getIcon() != null && !request.getIcon().trim().isEmpty() ? request.getIcon().trim() : "🧵")
                .displayOrder(nextSeq)
                .color(request.getColor() != null && !request.getColor().trim().isEmpty() ? request.getColor().trim() : "#818CF8")
                .bgColor(request.getBgColor() != null && !request.getBgColor().trim().isEmpty() ? request.getBgColor().trim() : "rgba(99, 102, 241, 0.15)")
                .isActive(true)
                .build();

        try {
            ProductionStage saved = stageRepository.save(stage);
            return toResponse(saved);
        } catch (Exception e) {
            log.error("Error saving custom production stage: ", e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to create stage: " + e.getMessage());
        }
    }

    @Transactional
    public ProductionStageResponse updateStage(Long id, UpdateProductionStageRequest request) {
        ProductionStage stage = stageRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Production stage not found"));

        stage.setTitle(request.getTitle().trim());
        if (request.getDescription() != null) stage.setDescription(request.getDescription().trim());
        stage.setIcon(request.getIcon() != null && !request.getIcon().isBlank() ? request.getIcon().trim() : null);
        if (request.getColor() != null) stage.setColor(request.getColor().trim());
        if (request.getBgColor() != null) stage.setBgColor(request.getBgColor().trim());
        if (request.getIsActive() != null) stage.setIsActive(request.getIsActive());

        ProductionStage updated = stageRepository.save(stage);
        return toResponse(updated);
    }

    @Transactional
    public List<ProductionStageResponse> reorderStages(ReorderStagesRequest request) {
        List<Long> stageIds = request.getStageIds();

        // Batch-fetch all stages in one query, build a lookup map
        Map<Long, ProductionStage> stageMap = stageRepository.findAllById(stageIds)
                .stream()
                .collect(Collectors.toMap(s -> s.getId(), s -> s));

        // Update display orders in memory
        for (int i = 0; i < stageIds.size(); i++) {
            ProductionStage stg = stageMap.get(stageIds.get(i));
            if (stg != null) {
                stg.setDisplayOrder(i + 1);
            }
        }

        // Persist all in a single batch
        stageRepository.saveAll(stageMap.values());
        return getAllStages();
    }

    @Transactional
    public void deleteStage(Long id) {
        ProductionStage stage = stageRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Production stage not found"));
        stage.setIsActive(false);
        stageRepository.save(stage);
    }

    private ProductionStageResponse toResponse(ProductionStage entity) {
        return ProductionStageResponse.builder()
                .id(entity.getId())
                .stageKey(entity.getStageKey())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .icon(entity.getIcon())
                .seqOrder(entity.getDisplayOrder())
                .color(entity.getColor())
                .bgColor(entity.getBgColor())
                .isActive(entity.getIsActive())
                .build();
    }
}
