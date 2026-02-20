package com.tecsup.app.micro.order_service.infrastructure.persistence.mapper;

import com.tecsup.app.micro.order_service.domain.model.Order;
import com.tecsup.app.micro.order_service.domain.model.OrderItem;
import com.tecsup.app.micro.order_service.infrastructure.persistence.entity.OrderEntity;
import com.tecsup.app.micro.order_service.infrastructure.persistence.entity.OrderItemEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderPersistenceMapper {

    @Mapping(target = "items", source = "items")
    Order toDomain(OrderEntity entity);

    @Mapping(target = "orderId", source = "order.id")
    OrderItem toItemDomain(OrderItemEntity entity);

    @Mapping(target = "items", source = "items")
    OrderEntity toEntity(Order order);

    @Mapping(target = "order", ignore = true)
    OrderItemEntity toItemEntity(OrderItem item);

    List<Order> toDomainList(List<OrderEntity> entities);
}
