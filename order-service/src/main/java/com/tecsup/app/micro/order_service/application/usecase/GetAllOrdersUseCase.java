package com.tecsup.app.micro.order_service.application.usecase;

import com.tecsup.app.micro.order_service.domain.model.Order;
import com.tecsup.app.micro.order_service.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetAllOrdersUseCase {

    private final OrderRepository orderRepository;

    public List<Order> execute() {
        log.debug("Executing GetAllOrdersUseCase");
        return orderRepository.findAll();
    }
}
