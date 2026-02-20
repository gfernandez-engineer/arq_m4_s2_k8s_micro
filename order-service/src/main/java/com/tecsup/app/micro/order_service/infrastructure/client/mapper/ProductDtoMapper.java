package com.tecsup.app.micro.order_service.infrastructure.client.mapper;

import com.tecsup.app.micro.order_service.domain.model.Product;
import com.tecsup.app.micro.order_service.infrastructure.client.dto.ProductDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductDtoMapper {

    Product toDomain(ProductDto dto);
}
