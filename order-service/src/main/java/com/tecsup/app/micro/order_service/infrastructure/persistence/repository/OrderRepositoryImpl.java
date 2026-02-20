package com.tecsup.app.micro.order_service.infrastructure.persistence.repository;

import com.tecsup.app.micro.order_service.domain.model.Order;
import com.tecsup.app.micro.order_service.domain.repository.OrderRepository;
import com.tecsup.app.micro.order_service.infrastructure.persistence.entity.OrderEntity;
import com.tecsup.app.micro.order_service.infrastructure.persistence.entity.OrderItemEntity;
import com.tecsup.app.micro.order_service.infrastructure.persistence.mapper.OrderPersistenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class OrderRepositoryImpl implements OrderRepository {

    private final JpaOrderRepository jpaOrderRepository;
    private final OrderPersistenceMapper mapper;

    @Override
    public List<Order> findAll() {
        log.debug("Finding all orders");
        return mapper.toDomainList(jpaOrderRepository.findAll());
    }

    @Override
    public Optional<Order> findById(Long id) {
        log.debug("Finding order by id: {}", id);
        return jpaOrderRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        log.debug("Finding orders by userId: {}", userId);
        return mapper.toDomainList(jpaOrderRepository.findByUserId(userId));
    }

    @Override
    public Order save(Order order) {
        log.debug("Saving order: {}", order.getOrderNumber());
        OrderEntity entity = mapper.toEntity(order);

        // Set bidirectional relationship
        if (entity.getItems() != null) {
            for (OrderItemEntity item : entity.getItems()) {
                item.setOrder(entity);
            }
        }

        OrderEntity savedEntity = jpaOrderRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }
}
