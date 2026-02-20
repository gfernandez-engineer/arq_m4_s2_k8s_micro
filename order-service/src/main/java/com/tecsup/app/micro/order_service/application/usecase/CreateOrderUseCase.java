package com.tecsup.app.micro.order_service.application.usecase;

import com.tecsup.app.micro.order_service.domain.model.Order;
import com.tecsup.app.micro.order_service.domain.model.OrderItem;
import com.tecsup.app.micro.order_service.domain.model.Product;
import com.tecsup.app.micro.order_service.domain.repository.OrderRepository;
import com.tecsup.app.micro.order_service.infrastructure.client.ProductClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public Order execute(Long userId, List<OrderItem> requestItems) {
        log.debug("Executing CreateOrderUseCase for userId: {}", userId);

        if (requestItems == null || requestItems.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }

        List<OrderItem> items = new ArrayList<>();
        Map<Long, String> productNames = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItem requestItem : requestItems) {
            Product product = productClient.getProductById(requestItem.getProductId());

            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(requestItem.getQuantity()));

            productNames.put(product.getId(), product.getName());

            OrderItem item = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(requestItem.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build();

            items.add(item);
            totalAmount = totalAmount.add(subtotal);
        }

        String orderNumber = generateOrderNumber();

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .userId(userId)
                .status("PENDING")
                .totalAmount(totalAmount)
                .items(items)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully with number: {}", savedOrder.getOrderNumber());

        // Enrich saved items with product names (not persisted in DB)
        if (savedOrder.getItems() != null) {
            savedOrder.getItems().forEach(item ->
                    item.setProductName(productNames.get(item.getProductId()))
            );
        }

        return savedOrder;
    }

    private String generateOrderNumber() {
        long count = orderRepository.count();
        return String.format("ORD-%d-%03d", Year.now().getValue(), count + 1);
    }
}
