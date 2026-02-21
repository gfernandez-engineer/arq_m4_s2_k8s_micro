package com.tecsup.app.micro.order_service.presentation.controller;

import com.tecsup.app.micro.order_service.application.service.OrderApplicationService;
import com.tecsup.app.micro.order_service.domain.model.Order;
import com.tecsup.app.micro.order_service.domain.model.OrderItem;
import com.tecsup.app.micro.order_service.presentation.dto.CreateOrderRequest;
import com.tecsup.app.micro.order_service.presentation.dto.OrderResponse;
import com.tecsup.app.micro.order_service.presentation.mapper.OrderDtoMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    // Capa de aplicación que contiene los casos de uso (reglas de negocio orquestadas)
    private final OrderApplicationService orderApplicationService;

    // Mapper para convertir entre DTOs (entrada/salida) y modelo de dominio
    private final OrderDtoMapper orderDtoMapper;


    /**
     * Crea una nueva orden.
     * - Recibe un JSON (CreateOrderRequest).
     * - Valida el payload con Bean Validation (@Valid).
     * - Mapea a objetos de dominio, delega al servicio de aplicación y responde con 201.
     */

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("REST request to create order for userId: {}", request.getUserId());
        // Convierte el DTO de entrada a lista de entidades de ítems de pedido
        List<OrderItem> items = orderDtoMapper.toOrderItems(request);

        //  Lógica de negocio: crea la orden con el userId y los ítems
        Order createdOrder = orderApplicationService.createOrder(request.getUserId(), items);

        // Devuelve 201 Created con el cuerpo mapeado a OrderResponse
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderDtoMapper.toResponse(createdOrder));
    }

    /**
     * Lista todas las órdenes.
     * - Ideal para administración o reportes.
     * - Considerar paginación si el volumen crece.
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        log.info("REST request to get all orders");
        List<Order> orders = orderApplicationService.getAllOrders();
        return ResponseEntity.ok(orderDtoMapper.toResponseList(orders));
    }


    /**
     * Obtiene una orden por su ID.
     * - Si no existe, el servicio debería lanzar una excepción
     *   que tu @ControllerAdvice traduzca a 404 Not Found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        log.info("REST request to get order by id: {}", id);
        Order order = orderApplicationService.getOrderById(id);
        // Devuelve 200 con el DTO
        return ResponseEntity.ok(orderDtoMapper.toResponse(order));
    }


    /**
     * Lista órdenes por usuario.
     * - Útil para que un cliente vea su historial.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByUserId(@PathVariable Long userId) {
        log.info("REST request to get orders by userId: {}", userId);
        // Caso de uso: filtra por usuario
        List<Order> orders = orderApplicationService.getOrdersByUserId(userId);
        // Mapea y responde 200 OK
        return ResponseEntity.ok(orderDtoMapper.toResponseList(orders));
    }


    /**
     * Endpoint básico de salud.
     * - Útil para probes (readiness/liveness) o checks rápidos.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Order Service running with Clean Architecture!");
    }
}
