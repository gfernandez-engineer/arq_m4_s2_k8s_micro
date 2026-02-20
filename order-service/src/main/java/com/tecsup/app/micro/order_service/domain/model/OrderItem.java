package com.tecsup.app.micro.order_service.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;  // Transient - populated from ProductClient, not persisted
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
