package com.ritham.erp.module.order.repository;

import com.ritham.erp.module.order.entity.OrderPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderPaymentRepository extends JpaRepository<OrderPayment, Long> {

    List<OrderPayment> findByOrderIdOrderByPaymentDateAsc(Long orderId);

    List<OrderPayment> findByOrderIdOrderByPaymentDateDesc(Long orderId);

    List<OrderPayment> findByOrderIdIn(List<Long> orderIds);

    void deleteByOrderId(Long orderId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(p.amount), 0) FROM OrderPayment p WHERE p.orderId = :orderId")
    java.math.BigDecimal sumAmountByOrderId(@org.springframework.data.repository.query.Param("orderId") Long orderId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(p.amount), 0) FROM OrderPayment p, CustomerOrder o WHERE p.orderId = o.id AND (:branchId IS NULL OR o.branch.id = :branchId) AND p.paymentDate >= :startDateTime AND p.paymentDate <= :endDateTime")
    java.math.BigDecimal sumRevenueByDateRangeScoped(@org.springframework.data.repository.query.Param("branchId") Long branchId, @org.springframework.data.repository.query.Param("startDateTime") java.time.LocalDateTime startDateTime, @org.springframework.data.repository.query.Param("endDateTime") java.time.LocalDateTime endDateTime);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(p.amount), 0) FROM OrderPayment p, CustomerOrder o WHERE p.orderId = o.id AND (:branchId IS NULL OR o.branch.id = :branchId) AND p.paymentDate >= :startDateTime AND p.paymentDate <= :endDateTime AND UPPER(p.paymentMethod) = UPPER(:paymentMethod)")
    java.math.BigDecimal sumRevenueByDateRangeAndMethodScoped(@org.springframework.data.repository.query.Param("branchId") Long branchId, @org.springframework.data.repository.query.Param("startDateTime") java.time.LocalDateTime startDateTime, @org.springframework.data.repository.query.Param("endDateTime") java.time.LocalDateTime endDateTime, @org.springframework.data.repository.query.Param("paymentMethod") String paymentMethod);

    @org.springframework.data.jpa.repository.Query("SELECT p.paymentMethod, COALESCE(SUM(p.amount), 0) FROM OrderPayment p, CustomerOrder o WHERE p.orderId = o.id AND (:branchId IS NULL OR o.branch.id = :branchId) AND p.paymentDate >= :startDateTime AND p.paymentDate <= :endDateTime GROUP BY p.paymentMethod")
    List<Object[]> sumRevenueGroupedByPaymentMethod(@org.springframework.data.repository.query.Param("branchId") Long branchId, @org.springframework.data.repository.query.Param("startDateTime") java.time.LocalDateTime startDateTime, @org.springframework.data.repository.query.Param("endDateTime") java.time.LocalDateTime endDateTime);

    @org.springframework.data.jpa.repository.Query("SELECT CAST(p.paymentDate AS LocalDate), COALESCE(SUM(p.amount), 0) FROM OrderPayment p, CustomerOrder o WHERE p.orderId = o.id AND (:branchId IS NULL OR o.branch.id = :branchId) AND p.paymentDate >= :startDateTime AND p.paymentDate <= :endDateTime GROUP BY CAST(p.paymentDate AS LocalDate) ORDER BY CAST(p.paymentDate AS LocalDate) ASC")
    List<Object[]> sumDailyRevenueScoped(@org.springframework.data.repository.query.Param("branchId") Long branchId, @org.springframework.data.repository.query.Param("startDateTime") java.time.LocalDateTime startDateTime, @org.springframework.data.repository.query.Param("endDateTime") java.time.LocalDateTime endDateTime);

    @org.springframework.data.jpa.repository.Query("SELECT p.paymentMethod, COUNT(p), COALESCE(SUM(p.amount), 0) FROM OrderPayment p, CustomerOrder o WHERE p.orderId = o.id AND (:branchId IS NULL OR o.branch.id = :branchId) AND p.paymentDate >= :startDateTime AND p.paymentDate <= :endDateTime GROUP BY p.paymentMethod")
    List<Object[]> countAndSumRevenueGroupedByPaymentMethod(@org.springframework.data.repository.query.Param("branchId") Long branchId, @org.springframework.data.repository.query.Param("startDateTime") java.time.LocalDateTime startDateTime, @org.springframework.data.repository.query.Param("endDateTime") java.time.LocalDateTime endDateTime);
}
