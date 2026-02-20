package com.tecsup.app.micro.order_service.domain.repository;

import com.tecsup.app.micro.order_service.domain.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    List<Order> findAll();

    Optional<Order> findById(Long id);

    List<Order> findByUserId(Long userId);

    Order save(Order order);
}
