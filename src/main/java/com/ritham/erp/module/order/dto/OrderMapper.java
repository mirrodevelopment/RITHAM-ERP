package com.ritham.erp.module.order.dto;

import com.ritham.erp.module.order.entity.CustomerOrder;
import com.ritham.erp.module.order.service.OrderService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OrderMapper {

    public OrderResponse toResponse(CustomerOrder order, String customerName) {
        return toResponse(order, customerName, null, null);
    }

    public OrderResponse toResponse(CustomerOrder order, String customerName, java.util.Map<String, String> measurements) {
        return toResponse(order, customerName, measurements, null);
    }

    public OrderResponse toResponse(CustomerOrder order, String customerName, java.util.Map<String, String> measurements, java.util.List<OrderPaymentResponse> payments) {
        if (order == null) return null;
        BigDecimal paid     = order.getPaidAmount()     != null ? order.getPaidAmount()     : BigDecimal.ZERO;
        BigDecimal total    = order.getTotalAmount()    != null ? order.getTotalAmount()    : BigDecimal.ZERO;
        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal netTotal = total.subtract(discount);
        if (netTotal.compareTo(BigDecimal.ZERO) < 0) netTotal = BigDecimal.ZERO;
        BigDecimal balance  = netTotal.subtract(paid);

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerMobile(order.getCustomerMobile())
                .customerName(customerName != null ? customerName : order.getCustomerMobile())
                .orderDate(order.getOrderDate())
                .deliveryDate(order.getDeliveryDate())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMode(order.getPaymentMode() != null ? order.getPaymentMode() : OrderService.PAYMENT_MODE_CASH)
                .totalAmount(total)
                .discountAmount(discount)
                .advanceAmount(order.getAdvanceAmount() != null ? order.getAdvanceAmount() : BigDecimal.ZERO)
                .paidAmount(paid)
                .balanceAmount(balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance)
                .assignedEmployeeId(order.getAssignedEmployeeId())
                .assignedEmployeeName(order.getAssignedEmployeeName())
                .garmentType(order.getGarmentType())
                .lining(order.getLining())
                .receiverName(order.getReceiverName())
                .branchId(order.getBranch() != null ? order.getBranch().getId() : null)
                .branchName(order.getBranch() != null ? order.getBranch().getName() : null)
                .branchCode(order.getBranch() != null ? order.getBranch().getBranchCode() : null)
                .measurements(measurements)
                .payments(payments)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    public OrderPaymentResponse toPaymentResponse(com.ritham.erp.module.order.entity.OrderPayment payment, CustomerOrder order, String collector) {
        if (payment == null) return null;
        return OrderPaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .orderNumber(order != null ? order.getOrderNumber() : null)
                .paymentType(payment.getPaymentType())
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .branchId(order != null && order.getBranch() != null ? order.getBranch().getId() : null)
                .branchName(order != null && order.getBranch() != null ? order.getBranch().getName() : null)
                .collector(collector != null && !collector.isBlank() ? collector : (order != null ? order.getReceiverName() : null))
                .build();
    }
}

