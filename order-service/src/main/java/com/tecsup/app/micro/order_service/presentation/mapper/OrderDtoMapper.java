package com.tecsup.app.micro.order_service.presentation.mapper;

import com.tecsup.app.micro.order_service.domain.model.Order;
import com.tecsup.app.micro.order_service.domain.model.OrderItem;
import com.tecsup.app.micro.order_service.presentation.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderDtoMapper {

    OrderResponse toResponse(Order order);

    List<OrderResponse> toResponseList(List<Order> orders);

    @Mapping(target = "product", expression = "java(mapProductInfo(item))")
    OrderItemResponse toItemResponse(OrderItem item);

    default ProductInfo mapProductInfo(OrderItem item) {
        return ProductInfo.builder()
                .id(item.getProductId())
                .name(item.getProductName())
                .price(item.getUnitPrice())
                .build();
    }

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
