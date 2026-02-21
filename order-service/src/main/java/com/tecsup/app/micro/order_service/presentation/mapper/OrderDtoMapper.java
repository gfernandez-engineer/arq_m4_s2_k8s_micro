package com.tecsup.app.micro.order_service.presentation.mapper;

import com.tecsup.app.micro.order_service.domain.model.Order;
import com.tecsup.app.micro.order_service.domain.model.OrderItem;
import com.tecsup.app.micro.order_service.presentation.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring") // Genera un bean Spring del mapper (para @Autowired / inyección)
public interface OrderDtoMapper {

    // Mapea entidad de dominio Order -> DTO OrderResponse
    OrderResponse toResponse(Order order);

    // Mapea lista de órdenes -> lista de DTOs OrderResponse
    List<OrderResponse> toResponseList(List<Order> orders);

    // Mapea OrderItem -> OrderItemResponse y resuelve el campo "product" con un metodo custom
    @Mapping(target = "product", expression = "java(mapProductInfo(item))")
    OrderItemResponse toItemResponse(OrderItem item);

    // Construye el sub-DTO ProductInfo a partir del OrderItem (método helper)
    default ProductInfo mapProductInfo(OrderItem item) {
        return ProductInfo.builder()
                .id(item.getProductId())
                .name(item.getProductName())
                .price(item.getUnitPrice())
                .build();
    }

    // Convierte un DTO de entrada (item del request) -> entidad de dominio OrderItem
    default OrderItem toOrderItem(OrderItemRequest request) {
        return OrderItem.builder()
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .build();
    }

    // Convierte los ítems del request de creación de orden -> lista de OrderItem de dominio
    default List<OrderItem> toOrderItems(CreateOrderRequest request) {
        return request.getItems().stream()
                .map(this::toOrderItem)
                .toList();
    }
}
