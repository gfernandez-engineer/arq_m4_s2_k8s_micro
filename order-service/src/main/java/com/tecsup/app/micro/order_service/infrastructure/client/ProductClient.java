package com.tecsup.app.micro.order_service.infrastructure.client;

import com.tecsup.app.micro.order_service.domain.exception.ProductServiceException;
import com.tecsup.app.micro.order_service.domain.model.Product;
import com.tecsup.app.micro.order_service.infrastructure.client.dto.ProductDto;
import com.tecsup.app.micro.order_service.infrastructure.client.mapper.ProductDtoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductClient {

    private final RestTemplate restTemplate;
    private final ProductDtoMapper productDtoMapper;

    @Value("${product.service.url}")
    private String productServiceUrl;

    public Product getProductById(Long productId) {
        log.info("Calling Product Service to get product with id: {}", productId);

        String url = this.productServiceUrl + "/api/products/" + productId;

        try {
            ProductDto productDto = restTemplate.getForObject(url, ProductDto.class);
            log.info("Product retrieved successfully: {}", productDto);
            return productDtoMapper.toDomain(productDto);
        } catch (HttpClientErrorException.NotFound e) {
            log.error("Product not found with id: {}", productId);
            throw new ProductServiceException("Product not found with id: " + productId);
        } catch (Exception e) {
            log.error("Error calling Product Service: {}", e.getMessage());
            throw new ProductServiceException("Error calling Product Service: " + e.getMessage());
        }
    }
}
