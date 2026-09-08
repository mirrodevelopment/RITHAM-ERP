package com.ritham.erp.module.order.service;

import com.ritham.erp.common.constants.RoleConstants;
import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.common.response.PageResponse;
import com.ritham.erp.module.customer.entity.Customer;
import com.ritham.erp.module.customer.repository.CustomerRepository;
import com.ritham.erp.module.order.dto.CreateOrderRequest;
import com.ritham.erp.module.order.dto.OrderMapper;
import com.ritham.erp.module.order.dto.OrderResponse;
import com.ritham.erp.module.order.entity.CustomerOrder;
import com.ritham.erp.module.order.repository.CustomerOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ritham.erp.module.order.entity.OrderMeasurement;
import com.ritham.erp.module.order.entity.OrderPayment;
import com.ritham.erp.module.order.repository.OrderPaymentRepository;
import com.ritham.erp.module.order.dto.OrderPaymentResponse;
import com.ritham.erp.module.order.dto.RecordPaymentRequest;
import com.ritham.erp.module.customer.dto.CustomerMeasurementResponse;
import com.ritham.erp.module.customer.dto.SaveCustomerMeasurementRequest;
import com.ritham.erp.module.customer.service.CustomerMeasurementService;
import com.ritham.erp.module.order.repository.OrderMeasurementRepository;
import com.ritham.erp.module.production.entity.ProductionStage;
import com.ritham.erp.module.production.repository.ProductionStageRepository;
import tools.jackson.databind.ObjectMapper;

import com.ritham.erp.common.constants.AppConstants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class OrderService {

    // ── Order Status Constants ─────────────────────────────────────────────
    public static final String STATUS_PENDING              = "PENDING";
    public static final String STATUS_DESIGNING            = "DESIGNING";
    public static final String STATUS_LINING               = "LINING";
    public static final String STATUS_HAND_MACHINE_WORK    = "HAND_MACHINE_WORK";
    public static final String STATUS_INITIAL_IRONING      = "INITIAL_IRONING";
    public static final String STATUS_CUTTING              = "CUTTING";
    public static final String STATUS_STRETCHING           = "STRETCHING";
    public static final String STATUS_STITCHING            = "STITCHING";
    public static final String STATUS_HEMMING              = "HEMMING";
    public static final String STATUS_FINAL_IRONING        = "FINAL_IRONING";
    public static final String STATUS_QUALITY_CHECK        = "QUALITY_CHECK";
    public static final String STATUS_READY_TO_DELIVERY    = "READY_TO_DELIVERY";
    public static final String STATUS_DELIVERY             = "DELIVERY";
    public static final String STATUS_DELIVERED            = "DELIVERED";
    public static final String STATUS_COMPLETED            = "COMPLETED";
    public static final String STATUS_CANCELLED            = "CANCELLED";


    // ── Payment Status Constants ───────────────────────────────────────────
    public static final String PAYMENT_STATUS_PENDING      = "PENDING";
    public static final String PAYMENT_STATUS_PARTIAL      = "PARTIAL";
    public static final String PAYMENT_STATUS_PAID         = "PAID";

    // ── Payment Type Constants ─────────────────────────────────────────────
    public static final String PAYMENT_TYPE_ADVANCE        = "ADVANCE";
    public static final String PAYMENT_TYPE_PARTIAL        = "PARTIAL";
    public static final String PAYMENT_TYPE_FINAL          = "FINAL";

    // ── Payment Mode Constants ─────────────────────────────────────────────
    public static final String PAYMENT_MODE_CASH           = "CASH";
    public static final String PAYMENT_MODE_SPLIT          = "SPLIT";

    // ── Formatting & Operational Constants ─────────────────────────────────
    public static final String ORDER_DATE_PATTERN          = "yyyyMMdd";
    public static final String ORDER_NUMBER_PREFIX         = AppConstants.ORDER_NUMBER_PREFIX;
    public static final int MAX_ORDER_NUMBER_RETRIES       = 10;
    public static final Long DEFAULT_BRANCH_ID             = 1L;
    public static final String DEFAULT_COLLECTOR_NAME      = "Reception Desk";
    public static final String DEFAULT_CUSTOMER_NAME       = "Customer";
    public static final String EMPTY_JSON_OBJECT           = "{}";

    /**
     * All recognized stages and statuses across Ritham ERP:
     * 12-stage production pipeline + PENDING + terminal statuses.
     */
    public static final Set<String> ALL_VALID_STATUSES = Set.of(
            STATUS_PENDING,
            STATUS_DESIGNING,
            STATUS_LINING,
            STATUS_HAND_MACHINE_WORK,
            STATUS_INITIAL_IRONING,
            STATUS_CUTTING,
            STATUS_STRETCHING,
            STATUS_STITCHING,
            STATUS_HEMMING,
            STATUS_FINAL_IRONING,
            STATUS_QUALITY_CHECK,
            STATUS_READY_TO_DELIVERY,
            STATUS_DELIVERY,
            STATUS_DELIVERED,
            STATUS_COMPLETED,
            STATUS_CANCELLED
    );

    /** Active production stages between intake and delivery packaging */
    public static final Set<String> ACTIVE_PRODUCTION_STAGES = Set.of(
            STATUS_DESIGNING,
            STATUS_LINING,
            STATUS_HAND_MACHINE_WORK,
            STATUS_INITIAL_IRONING,
            STATUS_CUTTING,
            STATUS_STRETCHING,
            STATUS_STITCHING,
            STATUS_HEMMING,
            STATUS_FINAL_IRONING,
            STATUS_QUALITY_CHECK
    );

    /** Delivered / completed terminal statuses */
    public static final Set<String> DELIVERED_STATUSES = Set.of(
            STATUS_DELIVERED,
            STATUS_DELIVERY,
            STATUS_COMPLETED
    );

    /** Terminal statuses: once entered, an order cannot transition to any different status */
    public static final Set<String> TERMINAL_STATUSES = Set.of(
            STATUS_DELIVERED,
            STATUS_DELIVERY,
            STATUS_COMPLETED,
            STATUS_CANCELLED
    );

    /** BUG-3 FIX: any order that has reached a terminal status cannot be cancelled */
    public static final Set<String> NON_CANCELLABLE_STATUSES = TERMINAL_STATUSES;

    private final CustomerOrderRepository orderRepository;
    private final CustomerRepository      customerRepository;
    private final OrderMeasurementRepository orderMeasurementRepository;
    private final OrderPaymentRepository  orderPaymentRepository;
    private final CustomerMeasurementService customerMeasurementService;
    private final com.ritham.erp.module.branch.repository.BranchRepository branchRepository;
    private final OrderMapper             orderMapper;
    private final ObjectMapper            objectMapper;
    private final ProductionStageRepository productionStageRepository;

    @Autowired
    public OrderService(
            CustomerOrderRepository orderRepository,
            CustomerRepository customerRepository,
            OrderMeasurementRepository orderMeasurementRepository,
            OrderPaymentRepository orderPaymentRepository,
            CustomerMeasurementService customerMeasurementService,
            com.ritham.erp.module.branch.repository.BranchRepository branchRepository,
            OrderMapper orderMapper,
            ObjectMapper objectMapper,
            ProductionStageRepository productionStageRepository
    ) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.orderMeasurementRepository = orderMeasurementRepository;
        this.orderPaymentRepository = orderPaymentRepository;
        this.customerMeasurementService = customerMeasurementService;
        this.branchRepository = branchRepository;
        this.orderMapper = orderMapper;
        this.objectMapper = objectMapper;
        this.productionStageRepository = productionStageRepository;
    }

    /**
     * Overloaded constructor for backwards compatibility with tests and callers that do not supply ProductionStageRepository.
     */
    public OrderService(
            CustomerOrderRepository orderRepository,
            CustomerRepository customerRepository,
            OrderMeasurementRepository orderMeasurementRepository,
            OrderPaymentRepository orderPaymentRepository,
            CustomerMeasurementService customerMeasurementService,
            com.ritham.erp.module.branch.repository.BranchRepository branchRepository,
            OrderMapper orderMapper,
            ObjectMapper objectMapper
    ) {
        this(orderRepository, customerRepository, orderMeasurementRepository, orderPaymentRepository,
                customerMeasurementService, branchRepository, orderMapper, objectMapper, null);
    }


    // ── List orders (paginated, searchable) ────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrders(String search, String status, Pageable pageable) {
        Page<CustomerOrder> page;

        List<String> targetStatuses = null;
        String statusParam = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : null;
        String searchParam = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        // Backward-compatibility: if status is not explicitly passed, check if search is a status keyword
        if (statusParam == null && searchParam != null) {
            String q = searchParam.toUpperCase();
            if (STATUS_PENDING.equals(q) || "IN_PROGRESS".equals(q) || "IN_PRODUCTION".equals(q) ||
                STATUS_READY_TO_DELIVERY.equals(q) || STATUS_COMPLETED.equals(q) ||
                STATUS_DELIVERED.equals(q) || STATUS_DELIVERY.equals(q) || STATUS_CANCELLED.equals(q)) {
                statusParam = q;
                searchParam = null;
            }
        }

        if (statusParam != null) {
            if (STATUS_PENDING.equals(statusParam)) {
                targetStatuses = List.of(STATUS_PENDING, STATUS_DESIGNING);
            } else if ("IN_PROGRESS".equalsIgnoreCase(statusParam) || "IN_PRODUCTION".equalsIgnoreCase(statusParam)) {
                targetStatuses = new ArrayList<>(ACTIVE_PRODUCTION_STAGES);
                if (productionStageRepository != null) {
                    List<ProductionStage> dynamicStages = productionStageRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc();
                    for (ProductionStage stage : dynamicStages) {
                        String key = stage.getStageKey() != null ? stage.getStageKey().trim().toUpperCase() : null;
                        if (key != null && !targetStatuses.contains(key)
                                && !STATUS_PENDING.equals(key)
                                && !STATUS_READY_TO_DELIVERY.equals(key)
                                && !DELIVERED_STATUSES.contains(key)
                                && !STATUS_CANCELLED.equals(key)) {
                            targetStatuses.add(key);
                        }
                    }
                }
            } else if (STATUS_READY_TO_DELIVERY.equals(statusParam)) {
                targetStatuses = List.of(STATUS_READY_TO_DELIVERY);
            } else if (STATUS_COMPLETED.equals(statusParam)) {
                // BE-1 FIX: COMPLETED includes both ready for delivery AND delivered orders so completed orders don't vanish
                targetStatuses = List.of(STATUS_READY_TO_DELIVERY, STATUS_COMPLETED, STATUS_DELIVERY, STATUS_DELIVERED);
            } else if (STATUS_DELIVERED.equals(statusParam) || STATUS_DELIVERY.equals(statusParam)) {
                targetStatuses = List.of(STATUS_DELIVERED, STATUS_DELIVERY);
            } else if (STATUS_CANCELLED.equals(statusParam)) {
                targetStatuses = List.of(STATUS_CANCELLED);
            } else {
                targetStatuses = List.of(statusParam);
            }
        }

        Long branchId = com.ritham.erp.security.BranchContext.getBranchId();

        if (targetStatuses != null && searchParam != null) {
            page = orderRepository.searchInStatusesScoped(branchId, targetStatuses, searchParam, pageable);
        } else if (targetStatuses != null) {
            page = orderRepository.findByStatusInScoped(branchId, targetStatuses, pageable);
        } else if (searchParam != null) {
            page = orderRepository.searchScoped(branchId, searchParam, pageable);
        } else {
            page = orderRepository.searchScoped(branchId, "", pageable);
        }

        // BUG-1 FIX: batch-fetch all customer names in ONE query instead of N+1
        List<String> mobiles = new ArrayList<>();
        for (CustomerOrder o : page.getContent()) {
            if (o != null && o.getCustomerMobile() != null && !mobiles.contains(o.getCustomerMobile())) {
                mobiles.add(o.getCustomerMobile());
            }
        }

        Map<String, String> nameMap = new HashMap<>();
        if (!mobiles.isEmpty()) {
            List<Customer> customers = customerRepository.findAllByCustomerMobileIn(mobiles);
            if (customers != null) {
                for (Customer c : customers) {
                    if (c != null && c.getCustomerMobile() != null) {
                        nameMap.put(c.getCustomerMobile(), c.getCustomerName());
                    }
                }
            }
        }

        List<Long> orderIds = new ArrayList<>();
        for (CustomerOrder o : page.getContent()) {
            if (o != null && o.getId() != null) {
                orderIds.add(o.getId());
            }
        }
        Map<Long, List<OrderPaymentResponse>> paymentMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<OrderPayment> allPayments = orderPaymentRepository.findByOrderIdIn(orderIds);
            if (allPayments != null) {
                for (OrderPayment p : allPayments) {
                    if (p != null && p.getOrderId() != null) {
                        paymentMap.computeIfAbsent(p.getOrderId(), k -> new ArrayList<>())
                                .add(orderMapper.toPaymentResponse(p, null, null));
                    }
                }
            }
        }

        Page<OrderResponse> dtoPage = page.map(order -> {
            String name = (order != null && order.getCustomerMobile() != null)
                    ? nameMap.getOrDefault(order.getCustomerMobile(), order.getCustomerMobile())
                    : DEFAULT_CUSTOMER_NAME;
            List<OrderPaymentResponse> payments = (order != null && order.getId() != null)
                    ? paymentMap.get(order.getId()) : null;
            return orderMapper.toResponse(order, name, null, payments);
        });
        return PageResponse.of(dtoPage);
    }

    // ── Single order ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        verifyBranchAccess(order);

        String name = customerRepository.findByCustomerMobile(order.getCustomerMobile())
                .map(c -> c.getCustomerName())
                .orElse(order.getCustomerMobile());
        
        Map<String, String> measurements = orderMeasurementRepository.findByOrderId(order.getId())
                .map(m -> parseMeasurementsJson(m.getMeasurementsJson()))
                .orElse(Collections.emptyMap());

        List<OrderPaymentResponse> payments = getOrderPayments(order.getId());

        return orderMapper.toResponse(order, name, measurements, payments);
    }

    // ── Latest measurements for repeat customer ────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getLatestCustomerMeasurements(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return Collections.emptyMap();
        }
        String cleanMobile = mobile.trim();

        // 1. Check dedicated customer_measurements table first (saved customer profile)
        Optional<CustomerMeasurementResponse> latestProfile = customerMeasurementService.getLatestByCustomerMobile(cleanMobile);
        if (latestProfile.isPresent() && latestProfile.get().getMeasurements() != null && !latestProfile.get().getMeasurements().isEmpty()) {
            CustomerMeasurementResponse profile = latestProfile.get();
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("customerMobile", profile.getCustomerMobile());
            res.put("customerName", profile.getCustomerName());
            res.put("garmentType", profile.getGarmentType());
            res.put("measurements", profile.getMeasurements());
            res.put("updatedAt", profile.getUpdatedAt());
            return res;
        }

        // 2. Fallback to order history
        return orderRepository.findFirstByCustomerMobileAndGarmentTypeIsNotNullOrderByCreatedAtDesc(cleanMobile)
                .map(order -> {
                    Map<String, String> measurements = orderMeasurementRepository.findByOrderId(order.getId())
                            .map(m -> parseMeasurementsJson(m.getMeasurementsJson()))
                            .orElse(Collections.emptyMap());
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("orderNumber", order.getOrderNumber());
                    res.put("garmentType", order.getGarmentType());
                    res.put("lining", order.getLining());
                    res.put("measurements", measurements);
                    res.put("orderDate", order.getCreatedAt());
                    return res;
                })
                .orElse(Collections.emptyMap());
    }

    // ── Create order ───────────────────────────────────────────────────────

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req) {
        // Validate customer exists
        Customer customer = customerRepository.findByCustomerMobile(req.getCustomerMobile().trim())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        BigDecimal advance  = req.getAdvanceAmount()  != null ? req.getAdvanceAmount()  : BigDecimal.ZERO;
        BigDecimal discount = req.getDiscountAmount() != null ? req.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal total    = req.getTotalAmount()    != null ? req.getTotalAmount()    : BigDecimal.ZERO;

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Total amount cannot be negative");
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Discount amount cannot be negative");
        }
        if (discount.compareTo(total) > 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Discount amount cannot exceed total order amount");
        }
        if (advance.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Advance amount cannot be negative");
        }

        BigDecimal netTotal = total.subtract(discount);
        if (netTotal.compareTo(BigDecimal.ZERO) < 0) netTotal = BigDecimal.ZERO;

        if (advance.compareTo(netTotal) > 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Advance payment (" + advance + ") cannot exceed order net total (" + netTotal + ")");
        }

        String paymentStatus = PAYMENT_STATUS_PENDING;
        if (advance.compareTo(netTotal) >= 0 && (advance.compareTo(BigDecimal.ZERO) > 0 || netTotal.compareTo(BigDecimal.ZERO) == 0)) {
            paymentStatus = PAYMENT_STATUS_PAID;
        } else if (advance.compareTo(BigDecimal.ZERO) > 0) {
            paymentStatus = PAYMENT_STATUS_PARTIAL;
        }

        // Determine branch for order
        Long branchId = req.getBranchId() != null ? req.getBranchId() : com.ritham.erp.security.BranchContext.getBranchId();
        com.ritham.erp.module.branch.entity.Branch branch = null;
        if (branchId != null) {
            branch = branchRepository.findById(branchId).orElse(null);
        } else {
            branch = branchRepository.findById(DEFAULT_BRANCH_ID).orElse(null);
        }

        String initialPaymentMode = (req.getPaymentMode() != null && !req.getPaymentMode().isBlank())
                ? req.getPaymentMode().trim().toUpperCase() : PAYMENT_MODE_CASH;

        CustomerOrder order = CustomerOrder.builder()
                .orderNumber(generateOrderNumber())
                .customerMobile(customer.getCustomerMobile())
                .deliveryDate(req.getDeliveryDate())
                .totalAmount(total)
                .discountAmount(discount)
                .advanceAmount(advance)
                .paidAmount(advance)
                .paymentMode(initialPaymentMode)
                .status(STATUS_PENDING)
                .paymentStatus(paymentStatus)
                .garmentType(req.getGarmentType() != null && !req.getGarmentType().isBlank() ? req.getGarmentType().trim() : null)
                .lining(req.getLining() != null && !req.getLining().isBlank() ? req.getLining().trim() : null)
                .branch(branch)
                .build();

        CustomerOrder saved = orderRepository.save(order);

        // Record advance payment in payment ledger
        if (advance != null && advance.compareTo(BigDecimal.ZERO) > 0) {
            OrderPayment advancePayment = OrderPayment.builder()
                    .orderId(saved.getId())
                    .paymentType(PAYMENT_TYPE_ADVANCE)
                    .paymentMethod(initialPaymentMode)
                    .amount(advance)
                    .paymentDate(LocalDateTime.now())
                    .build();
            orderPaymentRepository.save(advancePayment);
        }

        // Save measurements if garment type or measurements are provided

        Map<String, String> savedMeasurements = Collections.emptyMap();
        if (req.getGarmentType() != null && !req.getGarmentType().isBlank()) {
            Map<String, String> reqMeasurements = req.getMeasurements() != null ? req.getMeasurements() : Collections.emptyMap();
            String json = serializeMeasurements(reqMeasurements);
            OrderMeasurement om = OrderMeasurement.builder()
                    .orderId(saved.getId())
                    .garmentType(req.getGarmentType().trim())
                    .measurementsJson(json)
                    .build();
            orderMeasurementRepository.save(om);
            savedMeasurements = reqMeasurements;

            // Also persist/update customer profile measurements
            if (!reqMeasurements.isEmpty()) {
                try {
                    SaveCustomerMeasurementRequest cmReq = new SaveCustomerMeasurementRequest();
                    cmReq.setCustomerMobile(customer.getCustomerMobile());
                    cmReq.setCustomerName(customer.getCustomerName());
                    cmReq.setGarmentType(req.getGarmentType().trim());
                    cmReq.setMeasurements(reqMeasurements);
                    customerMeasurementService.saveOrUpdate(cmReq);
                } catch (Exception e) {
                    // Non-fatal if customer measurement sync logs a warning
                }
            }
        }

        // Increment customer total orders counter
        customer.incrementOrderCount();
        customerRepository.save(customer);

        return orderMapper.toResponse(saved, customer.getCustomerName(), savedMeasurements);
    }

    // ── Update status (+ optional final payment collection) ───────────────

    @Transactional
    public OrderResponse updateStatus(Long id, String newStatus, BigDecimal finalPayment, String paymentMode, Long assignedEmployeeId, String assignedEmployeeName, String receiverName, BigDecimal discountAmount) {
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        verifyBranchAccess(order);

        if (newStatus != null && !newStatus.isBlank()) {
            validateStatusTransition(order.getStatus(), newStatus);
            order.setStatus(newStatus.trim().toUpperCase());
        }

        if (assignedEmployeeId != null) {
            order.setAssignedEmployeeId(assignedEmployeeId);
        }
        if (assignedEmployeeName != null && !assignedEmployeeName.isBlank()) {
            order.setAssignedEmployeeName(assignedEmployeeName.trim());
        }

        if (STATUS_DELIVERED.equals(order.getStatus()) || STATUS_DELIVERY.equals(order.getStatus())) {
            if (receiverName != null && !receiverName.isBlank()) {
                order.setReceiverName(receiverName.trim());
            } else if (order.getReceiverName() == null || order.getReceiverName().isBlank()) {
                String fallback = customerRepository.findByCustomerMobile(order.getCustomerMobile())
                        .map(c -> c.getCustomerName())
                        .orElse(order.getCustomerMobile());
                order.setReceiverName(fallback != null && !fallback.isBlank() ? fallback : DEFAULT_CUSTOMER_NAME);
            }
        } else if (receiverName != null && !receiverName.isBlank()) {
            order.setReceiverName(receiverName.trim());
        }

        if (discountAmount != null) {
            if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Discount amount cannot be negative");
            }
            if (discountAmount.compareTo(order.getTotalAmount()) > 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Discount amount cannot exceed total order amount");
            }
            order.setDiscountAmount(discountAmount);
        }

        if (finalPayment != null && finalPayment.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Payment amount cannot be negative");
        }

        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal netTotal = order.getTotalAmount().subtract(discount);
        if (netTotal.compareTo(BigDecimal.ZERO) < 0) netTotal = BigDecimal.ZERO;

        // Record final payment if provided and positive
        if (finalPayment != null && finalPayment.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentPaid = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal remainingBalance = netTotal.subtract(currentPaid);
            if (remainingBalance.compareTo(BigDecimal.ZERO) < 0) remainingBalance = BigDecimal.ZERO;

            if (finalPayment.compareTo(remainingBalance) > 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Payment amount (" + finalPayment + ") exceeds remaining balance (" + remainingBalance + ")");
            }

            order.setPaidAmount(currentPaid.add(finalPayment));

            // Record transaction in payment ledger
            String method = (paymentMode != null && !paymentMode.isBlank())
                    ? paymentMode.trim().toUpperCase()
                    : PAYMENT_MODE_CASH;
            String pType = (order.getPaidAmount().compareTo(netTotal) >= 0) ? PAYMENT_TYPE_FINAL : PAYMENT_TYPE_PARTIAL;
            OrderPayment ledgerPayment = OrderPayment.builder()
                    .orderId(order.getId())
                    .paymentType(pType)
                    .paymentMethod(method)
                    .amount(finalPayment)
                    .paymentDate(LocalDateTime.now())
                    .build();
            orderPaymentRepository.save(ledgerPayment);
            syncOrderPaymentState(order);
        } else {
            // BUG-4 FIX: always recalculate payment status after any discount or stage change
            BigDecimal currentPaid = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
            if (currentPaid.compareTo(netTotal) >= 0 && (currentPaid.compareTo(BigDecimal.ZERO) > 0 || netTotal.compareTo(BigDecimal.ZERO) == 0)) {
                order.setPaymentStatus(PAYMENT_STATUS_PAID);
            } else if (currentPaid.compareTo(BigDecimal.ZERO) > 0) {
                order.setPaymentStatus(PAYMENT_STATUS_PARTIAL);
            } else {
                order.setPaymentStatus(PAYMENT_STATUS_PENDING);
            }
        }

        CustomerOrder saved = orderRepository.save(order);
        String name = customerRepository.findByCustomerMobile(saved.getCustomerMobile())
                .map(c -> c.getCustomerName())
                .orElse(saved.getCustomerMobile());
        return orderMapper.toResponse(saved, name);
    }

    /** Convenience overload — backward compatible */
    @Transactional
    public OrderResponse updateStatus(Long id, String newStatus, BigDecimal finalPayment, String paymentMode, Long assignedEmployeeId, String assignedEmployeeName, String receiverName) {
        return updateStatus(id, newStatus, finalPayment, paymentMode, assignedEmployeeId, assignedEmployeeName, receiverName, null);
    }

    /** Convenience overload — backward compatible */
    @Transactional
    public OrderResponse updateStatus(Long id, String newStatus, BigDecimal finalPayment, String paymentMode, Long assignedEmployeeId, String assignedEmployeeName) {
        return updateStatus(id, newStatus, finalPayment, paymentMode, assignedEmployeeId, assignedEmployeeName, null, null);
    }

    /** Convenience overload — backward compatible */
    @Transactional
    public OrderResponse updateStatus(Long id, String newStatus, BigDecimal finalPayment, String paymentMode) {
        return updateStatus(id, newStatus, finalPayment, paymentMode, null, null);
    }

    /** Convenience overload — backward compatible */
    @Transactional
    public OrderResponse updateStatus(Long id, String newStatus, BigDecimal finalPayment) {
        return updateStatus(id, newStatus, finalPayment, null, null, null);
    }

    /** Convenience overload — backward compatible */
    @Transactional
    public OrderResponse updateStatus(Long id, String newStatus) {
        return updateStatus(id, newStatus, null, null, null, null);
    }


    // ── Cancel order ───────────────────────────────────────────────────────

    @Transactional
    public OrderResponse cancelOrder(Long id) {
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        verifyBranchAccess(order);

        // Reject terminal statuses (delivered/cancelled)
        if (NON_CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new AppException(ErrorCode.ORDER_CANNOT_BE_CANCELLED);
        }

        order.setStatus(STATUS_CANCELLED);
        CustomerOrder saved = orderRepository.save(order);
        String name = customerRepository.findByCustomerMobile(saved.getCustomerMobile())
                .map(c -> c.getCustomerName())
                .orElse(saved.getCustomerMobile());
        return orderMapper.toResponse(saved, name);
    }

    // ── Stats ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public long countByStatus(String status) {
        Long branchId = com.ritham.erp.security.BranchContext.getBranchId();
        return orderRepository.countByStatusScoped(branchId, status);
    }

    @Transactional(readOnly = true)
    public long countTotalOrders() {
        Long branchId = com.ritham.erp.security.BranchContext.getBranchId();
        return branchId != null ? orderRepository.countByBranchId(branchId) : orderRepository.count();
    }

    @Transactional(readOnly = true)
    public long countPendingOrders() {
        Long branchId = com.ritham.erp.security.BranchContext.getBranchId();
        return orderRepository.countPendingOrdersScoped(branchId);
    }

    @Transactional(readOnly = true)
    public long countInProgressOrders() {
        Long branchId = com.ritham.erp.security.BranchContext.getBranchId();
        return orderRepository.countInProgressOrdersScoped(branchId);
    }

    @Transactional(readOnly = true)
    public long countCompletedOrders() {
        Long branchId = com.ritham.erp.security.BranchContext.getBranchId();
        return orderRepository.countCompletedOrdersScoped(branchId);
    }

    @Transactional(readOnly = true)
    public long countCancelledOrders() {
        Long branchId = com.ritham.erp.security.BranchContext.getBranchId();
        return orderRepository.countCancelledOrdersScoped(branchId);
    }

    @Transactional(readOnly = true)
    public long countTodayOrders() {
        Long branchId = com.ritham.erp.security.BranchContext.getBranchId();
        return orderRepository.countTodayOrdersScoped(branchId);
    }

    @Transactional(readOnly = true)
    public BigDecimal sumTodayRevenue() {
        Long branchId = com.ritham.erp.security.BranchContext.getBranchId();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(java.time.LocalTime.MAX);
        BigDecimal rev = orderPaymentRepository.sumRevenueByDateRangeScoped(branchId, startOfDay, endOfDay);
        return (rev != null) ? rev : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRevenueReport(LocalDate startDate, LocalDate endDate, String paymentMethod, Long requestBranchId) {
        Long callerBranchId = com.ritham.erp.security.BranchContext.getBranchId();
        Long effectiveBranchId = callerBranchId != null ? callerBranchId : requestBranchId;

        LocalDate start = (startDate != null) ? startDate : LocalDate.now();
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(java.time.LocalTime.MAX);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(java.time.LocalTime.MAX);

        BigDecimal todayRevenue = orderPaymentRepository.sumRevenueByDateRangeScoped(effectiveBranchId, todayStart, todayEnd);
        if (todayRevenue == null) todayRevenue = BigDecimal.ZERO;

        BigDecimal periodRevenue;
        if (paymentMethod != null && !paymentMethod.isBlank() && !"ALL".equalsIgnoreCase(paymentMethod.trim())) {
            periodRevenue = orderPaymentRepository.sumRevenueByDateRangeAndMethodScoped(effectiveBranchId, startDateTime, endDateTime, paymentMethod.trim());
        } else {
            periodRevenue = orderPaymentRepository.sumRevenueByDateRangeScoped(effectiveBranchId, startDateTime, endDateTime);
        }
        if (periodRevenue == null) periodRevenue = BigDecimal.ZERO;

        List<Object[]> methodRows = orderPaymentRepository.sumRevenueGroupedByPaymentMethod(effectiveBranchId, startDateTime, endDateTime);
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        if (methodRows != null) {
            for (Object[] row : methodRows) {
                if (row != null && row.length >= 2 && row[0] != null) {
                    byMethod.put(row[0].toString(), (BigDecimal) row[1]);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayRevenue", todayRevenue);
        result.put("periodRevenue", periodRevenue);
        result.put("startDate", start.toString());
        result.put("endDate", end.toString());
        result.put("paymentMethod", (paymentMethod != null && !paymentMethod.isBlank()) ? paymentMethod.toUpperCase() : "ALL");
        result.put("branchId", effectiveBranchId);
        result.put("byPaymentMethod", byMethod);
        return result;
    }

    // ── Analytics & Reporting ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getOrderAnalytics(
            String preset,
            LocalDate customStartDate,
            LocalDate customEndDate,
            String stage,
            String paymentStatus,
            String paymentMode,
            String searchQuery,
            Long requestBranchId
    ) {
        Long callerBranchId = com.ritham.erp.security.BranchContext.getBranchId();
        Long effectiveBranchId = callerBranchId != null ? callerBranchId : requestBranchId;

        String effectivePreset = (preset != null && !preset.isBlank()) ? preset.trim().toUpperCase() : "WEEK";

        LocalDate start = null;
        LocalDate end = LocalDate.now();

        switch (effectivePreset) {
            case "TODAY":
                start = LocalDate.now();
                end = LocalDate.now();
                break;
            case "YESTERDAY":
                start = LocalDate.now().minusDays(1);
                end = LocalDate.now().minusDays(1);
                break;
            case "WEEK":
                start = LocalDate.now().minusDays(6);
                end = LocalDate.now();
                break;
            case "14DAYS":
                start = LocalDate.now().minusDays(13);
                end = LocalDate.now();
                break;
            case "MONTH":
                start = LocalDate.now().minusDays(29);
                end = LocalDate.now();
                break;
            case "90DAYS":
                start = LocalDate.now().minusDays(89);
                end = LocalDate.now();
                break;
            case "CUSTOM":
                start = (customStartDate != null) ? customStartDate : LocalDate.now().minusDays(6);
                end = (customEndDate != null) ? customEndDate : LocalDate.now();
                if (start.isAfter(end)) {
                    LocalDate tmp = start;
                    start = end;
                    end = tmp;
                }
                break;
            case "ALL":
            default:
                if ("ALL".equalsIgnoreCase(effectivePreset)) {
                    start = null;
                    end = null;
                } else {
                    start = LocalDate.now().minusDays(6);
                    end = LocalDate.now();
                }
                break;
        }

        LocalDateTime startDateTime = (start != null) ? start.atStartOfDay() : null;
        LocalDateTime endDateTime = (end != null) ? end.atTime(LocalTime.MAX) : null;

        String cleanStage = (stage != null && !stage.isBlank() && !"ALL".equalsIgnoreCase(stage.trim()))
                ? stage.trim().toUpperCase() : null;
        String cleanPaymentStatus = (paymentStatus != null && !paymentStatus.isBlank() && !"ALL".equalsIgnoreCase(paymentStatus.trim()))
                ? paymentStatus.trim().toUpperCase() : null;
        String cleanPaymentMode = (paymentMode != null && !paymentMode.isBlank() && !"ALL".equalsIgnoreCase(paymentMode.trim()))
                ? paymentMode.trim().toUpperCase() : null;
        String cleanQuery = (searchQuery != null && !searchQuery.isBlank()) ? searchQuery.trim() : null;

        // 1. Summary aggregates
        long todayOrders = orderRepository.countTodayOrdersScoped(effectiveBranchId);
        List<Object[]> aggRows = orderRepository.getOrderSummaryAggregates(
                effectiveBranchId, startDateTime, endDateTime, cleanStage, cleanPaymentStatus, cleanPaymentMode, cleanQuery);

        long totalOrders = 0L;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal totalBalance = BigDecimal.ZERO;
        long deliveredCount = 0L;
        long paidCount = 0L;
        long partialCount = 0L;
        long unpaidCount = 0L;

        if (aggRows != null && !aggRows.isEmpty() && aggRows.get(0) != null) {
            Object[] row = aggRows.get(0);
            totalOrders = row[0] != null ? ((Number) row[0]).longValue() : 0L;
            totalRevenue = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
            totalCollected = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
            totalBalance = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;
            deliveredCount = row[4] != null ? ((Number) row[4]).longValue() : 0L;
            paidCount = row[5] != null ? ((Number) row[5]).longValue() : 0L;
            partialCount = row[6] != null ? ((Number) row[6]).longValue() : 0L;
            unpaidCount = row[7] != null ? ((Number) row[7]).longValue() : 0L;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOrders", totalOrders);
        summary.put("todayOrders", todayOrders);
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalCollected", totalCollected);
        summary.put("totalBalance", totalBalance);
        summary.put("deliveredCount", deliveredCount);
        summary.put("paidCount", paidCount);
        summary.put("partialCount", partialCount);
        summary.put("unpaidCount", unpaidCount);

        // 2. Daily revenue income trend from payment ledger
        LocalDateTime effectiveStartDateTime = (startDateTime != null) ? startDateTime : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime effectiveEndDateTime = (endDateTime != null) ? endDateTime : LocalDateTime.now();

        List<Object[]> dailyIncomeRows = orderPaymentRepository.sumDailyRevenueScoped(
                effectiveBranchId, effectiveStartDateTime, effectiveEndDateTime);
        List<Map<String, Object>> dailyIncome = new ArrayList<>();
        if (dailyIncomeRows != null) {
            for (Object[] row : dailyIncomeRows) {
                if (row != null && row.length >= 2 && row[0] != null) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", row[0].toString());
                    item.put("amount", row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO);
                    dailyIncome.add(item);
                }
            }
        }

        // 3. Daily order volume
        List<Object[]> dailyOrderRows = orderRepository.countDailyOrdersScoped(
                effectiveBranchId, effectiveStartDateTime, effectiveEndDateTime);
        List<Map<String, Object>> dailyOrders = new ArrayList<>();
        if (dailyOrderRows != null) {
            for (Object[] row : dailyOrderRows) {
                if (row != null && row.length >= 2 && row[0] != null) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", row[0].toString());
                    item.put("count", row[1] != null ? ((Number) row[1]).longValue() : 0L);
                    dailyOrders.add(item);
                }
            }
        }

        // 4. Status breakdown
        List<Object[]> statusRows = orderRepository.countOrdersByStatusScoped(
                effectiveBranchId, startDateTime, endDateTime);
        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        if (statusRows != null) {
            for (Object[] row : statusRows) {
                if (row != null && row.length >= 2 && row[0] != null) {
                    statusBreakdown.put(row[0].toString(), row[1] != null ? ((Number) row[1]).longValue() : 0L);
                }
            }
        }

        // 5. Payment method breakdown
        List<Object[]> payMethodRows = orderPaymentRepository.countAndSumRevenueGroupedByPaymentMethod(
                effectiveBranchId, effectiveStartDateTime, effectiveEndDateTime);
        Map<String, Map<String, Object>> paymentBreakdown = new LinkedHashMap<>();
        if (payMethodRows != null) {
            for (Object[] row : payMethodRows) {
                if (row != null && row.length >= 3 && row[0] != null) {
                    String method = row[0].toString();
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("count", row[1] != null ? ((Number) row[1]).longValue() : 0L);
                    details.put("amount", row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO);
                    paymentBreakdown.put(method, details);
                }
            }
        }

        // 6. Top customers
        List<Object[]> topCustRows = orderRepository.findTopCustomersScoped(
                effectiveBranchId, startDateTime, endDateTime, PageRequest.of(0, 8));
        List<Map<String, Object>> topCustomers = new ArrayList<>();
        if (topCustRows != null) {
            for (Object[] row : topCustRows) {
                if (row != null && row.length >= 3 && row[0] != null) {
                    String mobile = row[0].toString();
                    String name = customerRepository.findByCustomerMobile(mobile)
                            .map(c -> c.getCustomerName())
                            .orElse(mobile);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("mobile", mobile);
                    item.put("name", name);
                    item.put("count", row[1] != null ? ((Number) row[1]).longValue() : 0L);
                    item.put("totalSpend", row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO);
                    topCustomers.add(item);
                }
            }
        }

        // 7. Recent orders for table (top 20)
        Page<CustomerOrder> pagedOrders = orderRepository.findFilteredOrdersForReportScoped(
                effectiveBranchId, startDateTime, endDateTime, cleanStage, cleanPaymentStatus, cleanPaymentMode, cleanQuery,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")));

        List<OrderResponse> recentOrders = pagedOrders.getContent().stream()
                .map(o -> {
                    String custName = customerRepository.findByCustomerMobile(o.getCustomerMobile())
                            .map(c -> c.getCustomerName())
                            .orElse(o.getCustomerMobile());
                    return orderMapper.toResponse(o, custName);
                })
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("preset", effectivePreset);
        response.put("startDate", start != null ? start.toString() : null);
        response.put("endDate", end != null ? end.toString() : null);
        response.put("branchId", effectiveBranchId);
        response.put("summary", summary);
        response.put("dailyIncome", dailyIncome);
        response.put("dailyOrders", dailyOrders);
        response.put("statusBreakdown", statusBreakdown);
        response.put("paymentBreakdown", paymentBreakdown);
        response.put("topCustomers", topCustomers);
        response.put("recentOrders", recentOrders);
        return response;
    }

    // ── Archive operations ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getDeliveredArchiveStats(int keepDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(keepDays);
        long toDelete = orderRepository.countDeliveredOlderThan(cutoff);
        long toKeep   = orderRepository.countDeliveredNewerThan(cutoff);
        String cutoffDateStr = cutoff.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));

        return java.util.Map.of(
                "toDelete",   toDelete,
                "toKeep",     toKeep,
                "cutoffDate", cutoffDateStr,
                "keepDays",   keepDays
        );
    }

    @Transactional
    public int archiveDeliveredOrders(int keepDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(keepDays);
        return orderRepository.deleteDeliveredOlderThan(cutoff);
    }

    // ── Customer Outstanding Due Summary ───────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerDueSummary(String mobile) {
        String cleanMobile = mobile != null ? mobile.trim() : "";
        List<CustomerOrder> orders = orderRepository.findByCustomerMobile(cleanMobile);

        BigDecimal totalDue = BigDecimal.ZERO;
        int dueOrderCount = 0;
        List<Map<String, Object>> dueOrders = new ArrayList<>();

        for (CustomerOrder o : orders) {
            if (STATUS_CANCELLED.equalsIgnoreCase(o.getStatus())) continue;
            BigDecimal total = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal discount = o.getDiscountAmount() != null ? o.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal paid = o.getPaidAmount() != null ? o.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal net = total.subtract(discount);
            if (net.compareTo(BigDecimal.ZERO) < 0) net = BigDecimal.ZERO;
            BigDecimal balance = net.subtract(paid);
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                totalDue = totalDue.add(balance);
                dueOrderCount++;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("orderNumber", o.getOrderNumber());
                item.put("status", o.getStatus());
                item.put("garmentType", o.getGarmentType() != null && !o.getGarmentType().isBlank() ? o.getGarmentType() : "Standard");
                item.put("orderDate", o.getOrderDate() != null ? o.getOrderDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : (o.getCreatedAt() != null ? o.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : ""));
                item.put("totalAmount", total);
                item.put("paidAmount", paid);
                item.put("balanceAmount", balance);
                dueOrders.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerMobile", cleanMobile);
        result.put("totalDue", totalDue);
        result.put("hasDue", totalDue.compareTo(BigDecimal.ZERO) > 0);
        result.put("dueOrderCount", dueOrderCount);
        result.put("dueOrders", dueOrders);
        return result;
    }

    // ── Payment Ledger Operations ─────────────────────────────────────────

    @Transactional
    public OrderPaymentResponse recordPayment(Long orderId, RecordPaymentRequest req) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        verifyBranchAccess(order);

        if (STATUS_CANCELLED.equals(order.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Cannot record payment on a cancelled order");
        }

        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Payment amount must be greater than zero");
        }

        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal netTotal = order.getTotalAmount().subtract(discount);
        if (netTotal.compareTo(BigDecimal.ZERO) < 0) netTotal = BigDecimal.ZERO;

        BigDecimal currentPaid = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remainingBalance = netTotal.subtract(currentPaid);
        if (remainingBalance.compareTo(BigDecimal.ZERO) < 0) remainingBalance = BigDecimal.ZERO;

        if (req.getAmount().compareTo(remainingBalance) > 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Payment amount (" + req.getAmount() + ") exceeds remaining balance (" + remainingBalance + ")");
        }

        BigDecimal newPaid = currentPaid.add(req.getAmount());
        order.setPaidAmount(newPaid);

        String method = (req.getPaymentMethod() != null && !req.getPaymentMethod().isBlank())
                ? req.getPaymentMethod().trim().toUpperCase()
                : PAYMENT_MODE_CASH;

        String type = (req.getPaymentType() != null && !req.getPaymentType().isBlank())
                ? req.getPaymentType().trim().toUpperCase()
                : (newPaid.compareTo(netTotal) >= 0 ? PAYMENT_TYPE_FINAL : PAYMENT_TYPE_PARTIAL);

        OrderPayment payment = OrderPayment.builder()
                .orderId(order.getId())
                .paymentType(type)
                .paymentMethod(method)
                .amount(req.getAmount())
                .paymentDate(LocalDateTime.now())
                .build();
        OrderPayment savedPayment = orderPaymentRepository.save(payment);

        syncOrderPaymentState(order);

        CustomerOrder savedOrder = orderRepository.save(order);

        String collector = resolveCollector(req.getCollector(), savedOrder);
        return orderMapper.toPaymentResponse(savedPayment, savedOrder, collector);
    }

    @Transactional(readOnly = true)
    public List<OrderPaymentResponse> getOrderPayments(Long orderId) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        verifyBranchAccess(order);

        List<OrderPayment> payments = orderPaymentRepository.findByOrderIdOrderByPaymentDateAsc(orderId);
        String collector = resolveCollector(null, order);

        return payments.stream()
                .map(p -> orderMapper.toPaymentResponse(p, order, collector))
                .toList();
    }

    private void syncOrderPaymentState(CustomerOrder order) {
        if (order == null || order.getId() == null) return;
        List<OrderPayment> payments = orderPaymentRepository.findByOrderIdOrderByPaymentDateAsc(order.getId());
        if (payments != null && !payments.isEmpty()) {
            BigDecimal totalPaid = BigDecimal.ZERO;
            List<String> methods = new ArrayList<>();
            for (OrderPayment p : payments) {
                if (p.getAmount() != null) {
                    totalPaid = totalPaid.add(p.getAmount());
                }
                if (p.getPaymentMethod() != null && !p.getPaymentMethod().isBlank()) {
                    String m = p.getPaymentMethod().trim().toUpperCase();
                    if (!methods.contains(m)) {
                        methods.add(m);
                    }
                }
            }
            order.setPaidAmount(totalPaid);
            if (!methods.isEmpty()) {
                String compositeMode = String.join(", ", methods);
                if (compositeMode.length() > 30) {
                    compositeMode = PAYMENT_MODE_SPLIT;
                }
                order.setPaymentMode(compositeMode);
            }
        }

        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal netTotal = order.getTotalAmount() != null ? order.getTotalAmount().subtract(discount) : BigDecimal.ZERO;
        if (netTotal.compareTo(BigDecimal.ZERO) < 0) netTotal = BigDecimal.ZERO;

        BigDecimal paid = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
        if (paid.compareTo(netTotal) >= 0 && (paid.compareTo(BigDecimal.ZERO) > 0 || netTotal.compareTo(BigDecimal.ZERO) == 0)) {
            order.setPaymentStatus(PAYMENT_STATUS_PAID);
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(PAYMENT_STATUS_PARTIAL);
        } else {
            order.setPaymentStatus(PAYMENT_STATUS_PENDING);
        }
    }

    private String resolveCollector(String explicitCollector, CustomerOrder order) {
        if (explicitCollector != null && !explicitCollector.isBlank()) {
            return explicitCollector.trim();
        }
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.ritham.erp.security.CustomUserDetails ud) {
                if (ud.getEmployee() != null && ud.getEmployee().getFullName() != null) {
                    return ud.getEmployee().getFullName();
                }
                return ud.getUsername();
            } else if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
                return auth.getName();
            }
        } catch (Exception ignored) {}

        if (order.getReceiverName() != null && !order.getReceiverName().isBlank()) {
            return order.getReceiverName();
        }
        if (order.getAssignedEmployeeName() != null && !order.getAssignedEmployeeName().isBlank()) {
            return order.getAssignedEmployeeName();
        }
        return DEFAULT_COLLECTOR_NAME;
    }

    // ── Branch isolation ───────────────────────────────────────────────────


    /**
     * Verifies that the currently authenticated user's branch matches the order's branch.
     * System Administrators (ROLE_ADMIN) or users with null branchId in BranchContext have unrestricted access across all branches.
     * Branch staff may only access orders belonging to their own branch.
     *
     * @throws AppException ACCESS_DENIED (403) if the branches do not match.
     */
    private void verifyBranchAccess(CustomerOrder order) {
        if (isCurrentCallerAdmin()) {
            // System Administrator can access all orders across all branches.
            return;
        }
        Long callerBranchId = com.ritham.erp.security.BranchContext.getBranchId();
        if (callerBranchId == null) {
            // Global admin or consolidated view — unrestricted access.
            return;
        }
        Long orderBranchId = (order.getBranch() != null) ? order.getBranch().getId() : null;
        if (!callerBranchId.equals(orderBranchId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private boolean isCurrentCallerAdmin() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> RoleConstants.ADMIN.equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
    }

    // ── Order state transition validator ──────────────────────────────────

    /**
     * Validates whether an order status transition is allowed according to business rules.
     * Prevents invalid transitions such as CANCELLED -> DELIVERED, DELIVERED -> CUTTING, DELIVERED -> STITCHING.
     *
     * @param currentStatus Current order status (defaults to PENDING if null)
     * @param targetStatus  Target order status to transition into
     */
    public void validateStatusTransition(String currentStatus, String targetStatus) {
        if (targetStatus == null || targetStatus.isBlank()) {
            return;
        }

        String current = (currentStatus != null && !currentStatus.isBlank())
                ? currentStatus.trim().toUpperCase() : STATUS_PENDING;
        String target = targetStatus.trim().toUpperCase();

        // 1. Idempotent update: updating order details without changing status is allowed
        if (current.equals(target)) {
            return;
        }

        // 2. Validate target is a recognized status in Ritham ERP (standard or active dynamic stage)
        if (!isRecognizedStatus(target)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                    "Unknown or invalid target status: " + target);
        }

        // 3. CANCELLED is terminal: CANNOT transition to DELIVERED, CUTTING, STITCHING, or any other status
        if (STATUS_CANCELLED.equals(current)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                    "Cannot transition order from " + STATUS_CANCELLED + " to " + target);
        }

        // 4. DELIVERED / COMPLETED is terminal: CANNOT transition to CUTTING, STITCHING, CANCELLED, etc.
        if (DELIVERED_STATUSES.contains(current)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                    "Cannot transition order from " + STATUS_DELIVERED + " to " + target);
        }

        // 5. From PENDING: can transition to any active production stage, READY_TO_DELIVERY, or CANCELLED
        if (STATUS_PENDING.equals(current)) {
            if (STATUS_DELIVERED.equals(target) || STATUS_DELIVERY.equals(target)) {
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                        "Order must be in " + STATUS_READY_TO_DELIVERY + " state before it can be delivered");
            }
            return;
        }

        // 6. From Active Production Stages (standard or custom): can advance/rework to any active stage, READY_TO_DELIVERY, or CANCELLED
        if (isActiveProductionStage(current)) {
            if (STATUS_PENDING.equals(target)) {
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                        "Cannot revert order from active production stage " + current + " back to " + STATUS_PENDING);
            }
            if (STATUS_DELIVERED.equals(target) || STATUS_DELIVERY.equals(target)) {
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                        "Order must be in " + STATUS_READY_TO_DELIVERY + " state before it can be delivered");
            }
            return;
        }

        // 7. From READY_TO_DELIVERY: can transition to DELIVERED, back to active stage for rework, or CANCELLED
        if (STATUS_READY_TO_DELIVERY.equals(current)) {
            if (STATUS_PENDING.equals(target)) {
                throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                        "Cannot revert order from " + current + " back to " + STATUS_PENDING);
            }
            return;
        }

        throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                "Invalid order status transition from " + current + " to " + target);
    }

    /**
     * Checks whether a status is recognized by Ritham ERP:
     * either a static standard status/stage or an active custom production stage in the database.
     */
    public boolean isRecognizedStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.trim().toUpperCase();
        if (ALL_VALID_STATUSES.contains(s)) {
            return true;
        }
        return productionStageRepository != null
                && productionStageRepository.existsByStageKeyIgnoreCaseAndIsActiveTrue(s);
    }

    /**
     * Checks whether a status represents an active production stage:
     * either a standard active production stage or an active custom production stage in the database.
     * Excludes PENDING, READY_TO_DELIVERY, and terminal statuses.
     */
    public boolean isActiveProductionStage(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.trim().toUpperCase();
        if (ACTIVE_PRODUCTION_STAGES.contains(s)) {
            return true;
        }
        if (STATUS_PENDING.equals(s)
                || STATUS_READY_TO_DELIVERY.equals(s)
                || DELIVERED_STATUSES.contains(s)
                || STATUS_CANCELLED.equals(s)) {
            return false;
        }
        return productionStageRepository != null
                && productionStageRepository.existsByStageKeyIgnoreCaseAndIsActiveTrue(s);
    }

    // ── Order number generator ─────────────────────────────────────────────

    String generateOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern(ORDER_DATE_PATTERN));
        long seqVal;
        try {
            Long next = orderRepository.getNextOrderSequenceValue();
            seqVal = (next != null && next > 0) ? next : (orderRepository.count() + 1);
        } catch (Exception e) {
            log.warn("Database sequence order_number_seq query failed, falling back to count: {}", e.getMessage());
            seqVal = orderRepository.count() + 1;
        }

        String seq = String.format("%04d", seqVal);
        String candidate = ORDER_NUMBER_PREFIX + date + "-" + seq;

        int retries = 0;
        while (orderRepository.existsByOrderNumber(candidate)) {
            if (++retries > MAX_ORDER_NUMBER_RETRIES) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to generate unique order number");
            }
            try {
                Long next = orderRepository.getNextOrderSequenceValue();
                seqVal = (next != null && next > 0) ? next : (seqVal + 1);
            } catch (Exception e) {
                seqVal++;
            }
            seq = String.format("%04d", seqVal);
            candidate = ORDER_NUMBER_PREFIX + date + "-" + seq;
        }
        return candidate;
    }

    private Map<String, String> parseMeasurementsJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            Map<?, ?> raw = objectMapper.readValue(json, Map.class);
            Map<String, String> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    map.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
            return map;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String serializeMeasurements(Map<String, String> measurements) {
        if (measurements == null || measurements.isEmpty()) return EMPTY_JSON_OBJECT;
        try {
            return objectMapper.writeValueAsString(measurements);
        } catch (Exception e) {
            return EMPTY_JSON_OBJECT;
        }
    }
}
