package com.tecsup.app.micro.order_service.presentation.controller;

import com.tecsup.app.micro.order_service.application.service.OrderApplicationService;
import com.tecsup.app.micro.order_service.domain.model.Order;
import com.tecsup.app.micro.order_service.domain.model.OrderItem;
import com.tecsup.app.micro.order_service.presentation.dto.CreateOrderRequest;
import com.tecsup.app.micro.order_service.presentation.dto.OrderResponse;
import com.tecsup.app.micro.order_service.presentation.mapper.OrderDtoMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final OrderDtoMapper orderDtoMapper;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("REST request to create order for userId: {}", request.getUserId());
        List<OrderItem> items = orderDtoMapper.toOrderItems(request);
        Order createdOrder = orderApplicationService.createOrder(request.getUserId(), items);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderDtoMapper.toResponse(createdOrder));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        log.info("REST request to get all orders");
        List<Order> orders = orderApplicationService.getAllOrders();
        return ResponseEntity.ok(orderDtoMapper.toResponseList(orders));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        log.info("REST request to get order by id: {}", id);
        Order order = orderApplicationService.getOrderById(id);
        return ResponseEntity.ok(orderDtoMapper.toResponse(order));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByUserId(@PathVariable Long userId) {
        log.info("REST request to get orders by userId: {}", userId);
        List<Order> orders = orderApplicationService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orderDtoMapper.toResponseList(orders));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Order Service running with Clean Architecture!");
    }
}
