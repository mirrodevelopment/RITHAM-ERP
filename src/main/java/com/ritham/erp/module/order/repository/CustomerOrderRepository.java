package com.ritham.erp.module.order.repository;

import com.ritham.erp.module.order.entity.CustomerOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findByCustomerMobile(String customerMobile);

    boolean existsByCustomerMobile(String customerMobile);

    java.util.Optional<CustomerOrder> findFirstByCustomerMobileAndGarmentTypeIsNotNullOrderByCreatedAtDesc(String customerMobile);

    boolean existsByOrderNumber(String orderNumber);

    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    Long getNextOrderSequenceValue();

    Long countByBranchId(Long branchId);

    /** Used by MigrationImportService to detect duplicate historical order imports. */
    @Query("""
        SELECT COUNT(o) > 0 FROM CustomerOrder o
        WHERE o.customerMobile = :mobile
          AND UPPER(o.garmentType) = UPPER(:garmentType)
          AND EXTRACT(YEAR FROM o.orderDate) = :year
        """)
    boolean existsByCustomerMobileAndGarmentTypeAndOrderDateYear(
            @Param("mobile") String mobile,
            @Param("garmentType") String garmentType,
            @Param("year") int year);

    @Query("""
        SELECT o FROM CustomerOrder o
        WHERE (:branchId IS NULL OR o.branch.id = :branchId)
          AND (LOWER(o.orderNumber)    LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(o.customerMobile) LIKE CONCAT('%', :query, '%')
            OR LOWER(o.status)         LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    Page<CustomerOrder> searchScoped(@Param("branchId") Long branchId, @Param("query") String query, Pageable pageable);

    @Query("""
        SELECT o FROM CustomerOrder o
        WHERE (:branchId IS NULL OR o.branch.id = :branchId)
          AND UPPER(o.status) IN :statuses
          AND (LOWER(o.orderNumber)    LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(o.customerMobile) LIKE CONCAT('%', :query, '%'))
    """)
    Page<CustomerOrder> searchInStatusesScoped(@Param("branchId") Long branchId, @Param("statuses") List<String> statuses, @Param("query") String query, Pageable pageable);

    @Query("""
        SELECT o FROM CustomerOrder o
        WHERE LOWER(o.orderNumber)    LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(o.customerMobile) LIKE CONCAT('%', :query, '%')
           OR LOWER(o.status)         LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    Page<CustomerOrder> search(@Param("query") String query, Pageable pageable);

    @Query("""
        SELECT o FROM CustomerOrder o
        WHERE UPPER(o.status) IN :statuses
          AND (LOWER(o.orderNumber)    LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(o.customerMobile) LIKE CONCAT('%', :query, '%'))
    """)
    Page<CustomerOrder> searchInStatuses(@Param("statuses") List<String> statuses, @Param("query") String query, Pageable pageable);

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE (:branchId IS NULL OR o.branch.id = :branchId) AND o.status = :status")
    long countByStatusScoped(@Param("branchId") Long branchId, @Param("status") String status);

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE (:branchId IS NULL OR o.branch.id = :branchId) AND UPPER(o.status) IN ('PENDING', 'DESIGNING')")
    long countPendingOrdersScoped(@Param("branchId") Long branchId);

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE (:branchId IS NULL OR o.branch.id = :branchId) AND UPPER(o.status) IN ('READY_TO_DELIVERY', 'DELIVERY', 'DELIVERED', 'COMPLETED')")
    long countCompletedOrdersScoped(@Param("branchId") Long branchId);

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE (:branchId IS NULL OR o.branch.id = :branchId) AND UPPER(o.status) IN ('LINING', 'HAND_MACHINE_WORK', 'INITIAL_IRONING', 'CUTTING', 'STRETCHING', 'STITCHING', 'HEMMING', 'FINAL_IRONING', 'QUALITY_CHECK', 'IN_PROGRESS', 'IN_PRODUCTION')")
    long countInProgressOrdersScoped(@Param("branchId") Long branchId);

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE (:branchId IS NULL OR o.branch.id = :branchId) AND UPPER(o.status) = 'CANCELLED'")
    long countCancelledOrdersScoped(@Param("branchId") Long branchId);

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE (:branchId IS NULL OR o.branch.id = :branchId) AND CAST(o.orderDate AS LocalDate) = CAST(CURRENT_TIMESTAMP AS LocalDate)")
    long countTodayOrdersScoped(@Param("branchId") Long branchId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM OrderPayment p, CustomerOrder o WHERE p.orderId = o.id AND (:branchId IS NULL OR o.branch.id = :branchId) AND CAST(p.paymentDate AS LocalDate) = CAST(CURRENT_TIMESTAMP AS LocalDate)")
    java.math.BigDecimal sumTodayRevenueScoped(@Param("branchId") Long branchId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM OrderPayment p, CustomerOrder o WHERE p.orderId = o.id AND (:branchId IS NULL OR o.branch.id = :branchId) AND p.paymentDate >= :startDateTime AND p.paymentDate <= :endDateTime")
    java.math.BigDecimal sumRevenueByDateRangeScoped(@Param("branchId") Long branchId, @Param("startDateTime") java.time.LocalDateTime startDateTime, @Param("endDateTime") java.time.LocalDateTime endDateTime);

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE o.status = :status")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE UPPER(o.status) IN ('PENDING', 'DESIGNING')")
    long countPendingOrders();

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE UPPER(o.status) IN ('READY_TO_DELIVERY', 'DELIVERY', 'DELIVERED', 'COMPLETED')")
    long countCompletedOrders();

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE UPPER(o.status) IN ('LINING', 'HAND_MACHINE_WORK', 'INITIAL_IRONING', 'CUTTING', 'STRETCHING', 'STITCHING', 'HEMMING', 'FINAL_IRONING', 'QUALITY_CHECK', 'IN_PROGRESS', 'IN_PRODUCTION')")
    long countInProgressOrders();

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE UPPER(o.status) = 'CANCELLED'")
    long countCancelledOrders();

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE CAST(o.orderDate AS LocalDate) = CAST(CURRENT_TIMESTAMP AS LocalDate)")
    long countTodayOrders();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM OrderPayment p WHERE CAST(p.paymentDate AS LocalDate) = CAST(CURRENT_TIMESTAMP AS LocalDate)")
    java.math.BigDecimal sumTodayRevenue();

    @Query("SELECT o FROM CustomerOrder o WHERE (:branchId IS NULL OR o.branch.id = :branchId) AND UPPER(o.status) IN :statuses")
    Page<CustomerOrder> findByStatusInScoped(@Param("branchId") Long branchId, @Param("statuses") List<String> statuses, Pageable pageable);

    @Query("SELECT o FROM CustomerOrder o WHERE UPPER(o.status) IN :statuses")
    Page<CustomerOrder> findByStatusIn(@Param("statuses") List<String> statuses, Pageable pageable);

    // ── Archive queries ────────────────────────────────────────────────────

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE o.status = 'DELIVERED' AND o.updatedAt < :cutoff")
    long countDeliveredOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(o) FROM CustomerOrder o WHERE o.status = 'DELIVERED' AND o.updatedAt >= :cutoff")
    long countDeliveredNewerThan(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM CustomerOrder o WHERE o.status = 'DELIVERED' AND o.updatedAt < :cutoff")
    int deleteDeliveredOlderThan(@Param("cutoff") LocalDateTime cutoff);

    // ── Analytics & Reporting queries ──────────────────────────────────────

    @Query("SELECT CAST(o.orderDate AS LocalDate), COUNT(o) FROM CustomerOrder o WHERE (:branchId IS NULL OR o.branch.id = :branchId) AND o.orderDate >= :startDateTime AND o.orderDate <= :endDateTime GROUP BY CAST(o.orderDate AS LocalDate) ORDER BY CAST(o.orderDate AS LocalDate) ASC")
    List<Object[]> countDailyOrdersScoped(@Param("branchId") Long branchId, @Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime);

    @Query("SELECT o.status, COUNT(o) FROM CustomerOrder o WHERE (:branchId IS NULL OR o.branch.id = :branchId) AND (:startDateTime IS NULL OR o.orderDate >= :startDateTime) AND (:endDateTime IS NULL OR o.orderDate <= :endDateTime) GROUP BY o.status")
    List<Object[]> countOrdersByStatusScoped(@Param("branchId") Long branchId, @Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime);

    @Query("SELECT COALESCE(o.paymentMode, 'CASH'), COUNT(o), COALESCE(SUM(o.paidAmount), 0) FROM CustomerOrder o WHERE (:branchId IS NULL OR o.branch.id = :branchId) AND (:startDateTime IS NULL OR o.orderDate >= :startDateTime) AND (:endDateTime IS NULL OR o.orderDate <= :endDateTime) GROUP BY o.paymentMode")
    List<Object[]> countOrdersByPaymentModeScoped(@Param("branchId") Long branchId, @Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime);

    @Query("SELECT o.customerMobile, COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM CustomerOrder o WHERE (:branchId IS NULL OR o.branch.id = :branchId) AND (:startDateTime IS NULL OR o.orderDate >= :startDateTime) AND (:endDateTime IS NULL OR o.orderDate <= :endDateTime) GROUP BY o.customerMobile ORDER BY COUNT(o) DESC")
    List<Object[]> findTopCustomersScoped(@Param("branchId") Long branchId, @Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime, Pageable pageable);

    @Query("""
        SELECT COUNT(o),
               COALESCE(SUM(o.totalAmount), 0),
               COALESCE(SUM(o.paidAmount), 0),
               COALESCE(SUM(CASE WHEN (o.totalAmount - COALESCE(o.discountAmount, 0) - COALESCE(o.paidAmount, 0)) > 0 
                                 THEN (o.totalAmount - COALESCE(o.discountAmount, 0) - COALESCE(o.paidAmount, 0)) 
                                 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN UPPER(o.status) IN ('DELIVERED', 'COMPLETED') THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN UPPER(o.paymentStatus) = 'PAID' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN UPPER(o.paymentStatus) = 'PARTIAL' THEN 1 ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN UPPER(o.paymentStatus) = 'PENDING' THEN 1 ELSE 0 END), 0)
        FROM CustomerOrder o
        WHERE (:branchId IS NULL OR o.branch.id = :branchId)
          AND (:startDateTime IS NULL OR o.orderDate >= :startDateTime)
          AND (:endDateTime IS NULL OR o.orderDate <= :endDateTime)
          AND (:stage IS NULL OR UPPER(o.status) = UPPER(:stage))
          AND (:paymentStatus IS NULL OR UPPER(o.paymentStatus) = UPPER(:paymentStatus))
          AND (:paymentMode IS NULL OR UPPER(o.paymentMode) = UPPER(:paymentMode))
          AND (:query IS NULL OR :query = '' OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(o.customerMobile) LIKE CONCAT('%', :query, '%'))
    """)
    List<Object[]> getOrderSummaryAggregates(
        @Param("branchId") Long branchId,
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime,
        @Param("stage") String stage,
        @Param("paymentStatus") String paymentStatus,
        @Param("paymentMode") String paymentMode,
        @Param("query") String query
    );

    @Query("""
        SELECT o FROM CustomerOrder o
        WHERE (:branchId IS NULL OR o.branch.id = :branchId)
          AND (:startDateTime IS NULL OR o.orderDate >= :startDateTime)
          AND (:endDateTime IS NULL OR o.orderDate <= :endDateTime)
          AND (:stage IS NULL OR UPPER(o.status) = UPPER(:stage))
          AND (:paymentStatus IS NULL OR UPPER(o.paymentStatus) = UPPER(:paymentStatus))
          AND (:paymentMode IS NULL OR UPPER(o.paymentMode) = UPPER(:paymentMode))
          AND (:query IS NULL OR :query = '' OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(o.customerMobile) LIKE CONCAT('%', :query, '%'))
    """)
    Page<CustomerOrder> findFilteredOrdersForReportScoped(
        @Param("branchId") Long branchId,
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime,
        @Param("stage") String stage,
        @Param("paymentStatus") String paymentStatus,
        @Param("paymentMode") String paymentMode,
        @Param("query") String query,
        Pageable pageable
    );
}
