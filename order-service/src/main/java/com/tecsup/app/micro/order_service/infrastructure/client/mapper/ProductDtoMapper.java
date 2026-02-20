package com.tecsup.app.micro.order_service.infrastructure.client.mapper;

import com.tecsup.app.micro.order_service.domain.model.Product;
import com.tecsup.app.micro.order_service.infrastructure.client.dto.ProductDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductDtoMapper {

    @Mapping(target = "createdByUser", ignore = true)
    Product toDomain(ProductDto dto);
}
