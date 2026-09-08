package com.ritham.erp.module.migration.dto;

/** Aggregated statistics for the migration dashboard. */
public record MigrationStatisticsResponse(

        // overall totals (across all batches the caller can see)
        long totalDocuments,
        long ocrCompleted,
        long reviewRequired,
        long verified,
        long imported,
        long rejected,
        long failed,
        long duplicates,

        // batch-level breakdown
        java.util.List<BatchStat> batchStats

) {
    public record BatchStat(
            Long batchId,
            String batchCode,
            Short sourceYear,
            String branchName,
            int totalDocuments,
            int needsReviewCount,
            int verifiedCount,
            int importedCount,
            int progressPercent,
            String status
    ) {}
}
