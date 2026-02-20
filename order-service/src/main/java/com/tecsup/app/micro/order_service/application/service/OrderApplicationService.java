package com.tecsup.app.micro.order_service.application.service;

import com.tecsup.app.micro.order_service.application.usecase.CreateOrderUseCase;
import com.tecsup.app.micro.order_service.application.usecase.GetAllOrdersUseCase;
import com.tecsup.app.micro.order_service.application.usecase.GetOrderByIdUseCase;
import com.tecsup.app.micro.order_service.application.usecase.GetOrdersByUserIdUseCase;
import com.tecsup.app.micro.order_service.domain.model.Order;
import com.tecsup.app.micro.order_service.domain.model.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderApplicationService {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderByIdUseCase getOrderByIdUseCase;
    private final GetAllOrdersUseCase getAllOrdersUseCase;
    private final GetOrdersByUserIdUseCase getOrdersByUserIdUseCase;

    @Transactional
    public Order createOrder(Long userId, List<OrderItem> items) {
        return createOrderUseCase.execute(userId, items);
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        return getOrderByIdUseCase.execute(id);
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return getAllOrdersUseCase.execute();
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUserId(Long userId) {
        return getOrdersByUserIdUseCase.execute(userId);
    }
}
