package com.tecsup.app.micro.order_service.infrastructure.persistence.repository;

import com.tecsup.app.micro.order_service.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaOrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findByUserId(Long userId);
}
