package com.tecsup.app.micro.order_service.application.usecase;

import com.tecsup.app.micro.order_service.domain.model.Order;
import com.tecsup.app.micro.order_service.domain.model.Product;
import com.tecsup.app.micro.order_service.domain.repository.OrderRepository;
import com.tecsup.app.micro.order_service.infrastructure.client.ProductClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetAllOrdersUseCase {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public List<Order> execute() {
        log.debug("Executing GetAllOrdersUseCase");
        List<Order> orders = orderRepository.findAll();
        orders.forEach(this::enrichOrderWithProductInfo);
        return orders;
    }

    private void enrichOrderWithProductInfo(Order order) {
        if (order.getItems() != null) {
            order.getItems().forEach(item -> {
                try {
                    Product product = productClient.getProductById(item.getProductId());
                    item.setProductName(product.getName());
                } catch (Exception e) {
                    log.warn("Could not fetch product info for productId: {}", item.getProductId());
                    item.setProductName("Product #" + item.getProductId());
                }
            });
        }
    }
}
