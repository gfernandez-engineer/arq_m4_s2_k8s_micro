# Curls de Validación - Order Service

## Pre-requisitos
1. Docker containers de BD corriendo: `docker-compose up -d`
2. Product Service corriendo (K8s NodePort 30082 o local 8082)
3. Order Service corriendo en Docker (puerto 8083)

```bash
# Construir y correr order-service
cd order-service
./mvnw package -DskipTests
docker build -t order-service:1.0 .
docker run -d --name order-service -p 8083:8083 \
  -e PRODUCT_SERVICE_URL=http://host.docker.internal:30082 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb \
  -e DB_USERNAME=postgres -e DB_PASSWORD=postgres \
  -e DDL_AUTO=update order-service:1.0
```

---

## 1. Health Check
```bash
curl -s http://localhost:8083/api/orders/health
```
**Resultado esperado:**
```
Order Service running with Clean Architecture!
```

---

## 2. Crear una orden (POST /api/orders) - RF-01
```bash
curl -s -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": [{"productId": 1, "quantity": 2}, {"productId": 3, "quantity": 1}]}'
```
**Resultado esperado (201 Created):**
```json
{
  "id": 6,
  "orderNumber": "ORD-2026-005",
  "userId": 1,
  "status": "PENDING",
  "totalAmount": 2689.97,
  "items": [
    {
      "id": 9,
      "product": { "id": 1, "name": "Laptop Dell XPS 15", "price": 1299.99 },
      "quantity": 2,
      "unitPrice": 1299.99,
      "subtotal": 2599.98
    },
    {
      "id": 10,
      "product": { "id": 3, "name": "Teclado Mecánico Keychron K8", "price": 89.99 },
      "quantity": 1,
      "unitPrice": 89.99,
      "subtotal": 89.99
    }
  ],
  "createdAt": "2026-02-20T00:43:45.268220284",
  "updatedAt": "2026-02-20T00:43:45.268220284"
}
```
**Validaciones del punto 3 (OPCIONAL):**
- Llama a Product Service (HTTP) para validar cada producto
- Obtiene precio actual del producto
- Calcula `subtotal = quantity x unitPrice` por item
- Calcula `totalAmount = suma de subtotals`

---

## 3. Obtener todas las órdenes (GET /api/orders)
```bash
curl -s http://localhost:8083/api/orders
```
**Resultado esperado (200 OK):** Array con todas las órdenes, cada una con items y producto enriquecido.

---

## 4. Obtener orden por ID (GET /api/orders/{id})
```bash
curl -s http://localhost:8083/api/orders/6
```
**Resultado esperado (200 OK):** Orden individual con items y info de producto.

---

## 5. Obtener órdenes por usuario (GET /api/orders/user/{userId})
```bash
curl -s http://localhost:8083/api/orders/user/2
```
**Resultado esperado (200 OK):** Array con las órdenes del usuario 2.

---

## 6. Error: producto inexistente
```bash
curl -s -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": [{"productId": 9999, "quantity": 1}]}'
```
**Resultado esperado (503):**
```json
{
  "status": 503,
  "message": "Product not found with id: 9999",
  "timestamp": "2026-02-20T00:44:25.504109352"
}
```

---

## 7. Error: items vacíos
```bash
curl -s -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": []}'
```
**Resultado esperado (400):**
```json
{
  "status": 400,
  "message": "Validation failed",
  "timestamp": "2026-02-20T00:44:32.684227616",
  "errors": { "items": "Order must have at least one item" }
}
```

---

## 8. Error: orden inexistente
```bash
curl -s http://localhost:8083/api/orders/9999
```
**Resultado esperado (404):**
```json
{
  "status": 404,
  "message": "Order not found with id: 9999",
  "timestamp": "2026-02-20T00:44:40.512260858"
}
```

---

## Resumen de Validación

| Test | Endpoint | Status | Resultado |
|------|----------|--------|-----------|
| 1. Health Check | GET /api/orders/health | 200 | PASS |
| 2. Crear Orden | POST /api/orders | 201 | PASS |
| 3. Listar Ordenes | GET /api/orders | 200 | PASS |
| 4. Orden por ID | GET /api/orders/{id} | 200 | PASS |
| 5. Ordenes por Usuario | GET /api/orders/user/{userId} | 200 | PASS |
| 6. Producto inexistente | POST /api/orders | 503 | PASS |
| 7. Items vacíos | POST /api/orders | 400 | PASS |
| 8. Orden inexistente | GET /api/orders/9999 | 404 | PASS |
