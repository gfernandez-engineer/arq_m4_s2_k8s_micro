package com.tecsup.app.micro.order_service.presentation.mapper;

import com.tecsup.app.micro.order_service.domain.model.Order;
import com.tecsup.app.micro.order_service.domain.model.OrderItem;
import com.tecsup.app.micro.order_service.presentation.dto.CreateOrderRequest;
import com.tecsup.app.micro.order_service.presentation.dto.OrderItemRequest;
import com.tecsup.app.micro.order_service.presentation.dto.OrderResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderDtoMapper {

    OrderResponse toResponse(Order order);

    List<OrderResponse> toResponseList(List<Order> orders);

    default OrderItem toOrderItem(OrderItemRequest request) {
        return OrderItem.builder()
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .build();
    }

    default List<OrderItem> toOrderItems(CreateOrderRequest request) {
        return request.getItems().stream()
                .map(this::toOrderItem)
                .toList();
    }
}
