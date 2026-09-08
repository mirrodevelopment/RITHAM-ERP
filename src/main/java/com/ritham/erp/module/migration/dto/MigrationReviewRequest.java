package com.ritham.erp.module.migration.dto;

/**
 * Request body when a reviewer saves corrections and approves/rejects a document.
 * The {@code correctedDataJson} is the full JSON of the reviewer-edited
 * {@link ExtractedData}, serialised client-side.
 */
public record MigrationReviewRequest(

        /** APPROVED | REJECTED | NEEDS_REWORK */
        String action,

        /** Reviewer-edited ExtractedData JSON (required when action = APPROVED). */
        String correctedDataJson,

        /** Optional note explaining rejection or correction reason. */
        String remarks,

        /**
         * Mobile number of the existing ERP customer to link to.
         * Null means "create a new customer from the extracted name + mobile".
         */
        String matchedCustomerMobile,

        /**
         * If true, also update the customer's measurement profile
         * (customer_measurements) with the historical measurements.
         * Default: false — only the immutable order snapshot is created.
         */
        boolean updateCustomerProfile
) {}
