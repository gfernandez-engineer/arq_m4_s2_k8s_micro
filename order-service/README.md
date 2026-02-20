# Order Service - Trabajo Final

Microservicio de gestion de ordenes de compra para un sistema e-commerce.
Se integra con **Product Service** via HTTP para validar productos y obtener precios actuales.

## Descripcion del trabajo

Este servicio implementa los requerimientos del trabajo final:

- **RF-01:** CRUD de ordenes (crear, listar, buscar por ID, buscar por usuario)
- **RF-02:** Comunicacion HTTP entre microservicios (Order Service -> Product Service)
- **RF-03 (Opcional):** Calculo automatico del monto total basandose en precios actuales del Product Service

### Flujo del punto 3 opcional (calculo automatico)

```
POST /api/orders  { userId: 1, items: [{productId: 1, quantity: 2}] }
        |
        v
  Para CADA item:
    1. Llama a Product Service (HTTP GET /api/products/{id})
    2. Valida que el producto existe
    3. Obtiene el precio actual del producto
    4. Calcula subtotal = quantity x unitPrice
        |
        v
  Calcula totalAmount = suma de todos los subtotals
        |
        v
  Genera numero de orden (ORD-2026-XXX)
        |
        v
  Guarda en BD (tabla orders + order_items)
        |
        v
  Retorna orden completa con status 201
```

---

## Arquitectura

### Clean Architecture

```
order-service/src/main/java/com/tecsup/app/micro/order_service/
|
|-- domain/                          <- CAPA DE DOMINIO (reglas de negocio)
|   |-- model/
|   |   |-- Order.java               <- Modelo de dominio: orden
|   |   |-- OrderItem.java           <- Modelo de dominio: item de orden
|   |   |-- Product.java             <- Modelo de dominio: producto (referencia)
|   |   +-- User.java                <- Modelo de dominio: usuario (referencia)
|   |-- repository/
|   |   +-- OrderRepository.java     <- Puerto: interfaz del repositorio
|   +-- exception/
|       |-- OrderNotFoundException.java
|       +-- ProductServiceException.java
|
|-- application/                     <- CAPA DE APLICACION (casos de uso)
|   |-- usecase/
|   |   |-- CreateOrderUseCase.java  <- Crea orden + valida productos + calcula totales
|   |   |-- GetAllOrdersUseCase.java
|   |   |-- GetOrderByIdUseCase.java
|   |   +-- GetOrdersByUserIdUseCase.java
|   +-- service/
|       +-- OrderApplicationService.java  <- Orquestador con @Transactional
|
|-- infrastructure/                  <- CAPA DE INFRAESTRUCTURA (adaptadores)
|   |-- config/
|   |   +-- BeanConfig.java          <- RestTemplate bean
|   |-- client/
|   |   |-- ProductClient.java       <- Cliente HTTP hacia Product Service
|   |   |-- dto/ProductDto.java
|   |   +-- mapper/ProductDtoMapper.java  <- MapStruct
|   +-- persistence/
|       |-- entity/
|       |   |-- OrderEntity.java     <- Entidad JPA: tabla orders
|       |   +-- OrderItemEntity.java <- Entidad JPA: tabla order_items
|       |-- mapper/
|       |   +-- OrderPersistenceMapper.java  <- MapStruct: Entity <-> Domain
|       +-- repository/
|           |-- JpaOrderRepository.java      <- Spring Data JPA
|           +-- OrderRepositoryImpl.java     <- Adaptador del puerto
|
+-- presentation/                    <- CAPA DE PRESENTACION (REST API)
    |-- controller/
    |   |-- OrderController.java     <- Endpoints REST
    |   +-- GlobalExceptionHandler.java
    |-- dto/
    |   |-- CreateOrderRequest.java  <- Request: { userId, items[] }
    |   |-- OrderItemRequest.java    <- Request item: { productId, quantity }
    |   |-- OrderResponse.java       <- Response completa
    |   |-- OrderItemResponse.java   <- Response item con producto
    |   +-- ProductInfo.java         <- Nested: { id, name, price }
    +-- mapper/
        +-- OrderDtoMapper.java      <- MapStruct: Domain <-> DTO
```

### Comunicacion entre microservicios

```
                    Kubernetes Cluster (Docker Desktop)
  +------------------------------------------------------------------+
  |                                                                    |
  |   order-service (ns: order-service)                                |
  |   localhost:30083                                                   |
  |        |                                                           |
  |        |  HTTP GET /api/products/{id}                              |
  |        |  (via DNS: product-service.product-service.svc.cluster.local)
  |        v                                                           |
  |   product-service (ns: product-service)                            |
  |   localhost:30082                                                   |
  |                                                                    |
  +------------------------------------------------------------------+
          |                    |
    PostgreSQL            PostgreSQL
    orderdb:5435          productdb:5433
```

---

## Tecnologias

| Tecnologia | Version | Uso |
|------------|---------|-----|
| Java | 21 | Lenguaje |
| Spring Boot | 3.5.x | Framework |
| Spring Data JPA | - | Persistencia |
| MapStruct | 1.5.5 | Mapeo de objetos |
| Lombok | - | Reduccion de boilerplate |
| PostgreSQL | 15 | Base de datos |
| Docker | - | Contenedorizacion |
| Kubernetes | - | Orquestacion (Docker Desktop) |

---

## Endpoints

| Metodo | Endpoint | Descripcion | Status |
|--------|----------|-------------|--------|
| POST | /api/orders | Crear una orden (valida productos, calcula totales) | 201 |
| GET | /api/orders | Listar todas las ordenes | 200 |
| GET | /api/orders/{id} | Obtener orden por ID | 200 |
| GET | /api/orders/user/{userId} | Ordenes por usuario | 200 |
| GET | /api/orders/health | Health check | 200 |

---

## Base de datos

```sql
-- Tabla: orders
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: order_items
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL
);
```

---

## Pre-requisitos

- Java 21
- Maven
- Docker Desktop (con Kubernetes habilitado)

---

## Despliegue rapido

### 1. Levantar bases de datos

```bash
docker-compose up -d
```

### 2. Compilar y construir imagen

```bash
cd order-service
mvn clean package -DskipTests
docker build -t order-service:1.0 .
```

### 3a. Ejecutar con Docker

#### CMD:
```cmd
docker run -d --name order-service -p 8083:8083 -e PRODUCT_SERVICE_URL=http://host.docker.internal:30082 -e DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb -e DB_USERNAME=postgres -e DB_PASSWORD=postgres -e DDL_AUTO=update order-service:1.0
```

#### PowerShell:
```powershell
docker run -d --name order-service `
  -p 8083:8083 `
  -e PRODUCT_SERVICE_URL=http://host.docker.internal:30082 `
  -e DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb `
  -e DB_USERNAME=postgres -e DB_PASSWORD=postgres `
  -e DDL_AUTO=update order-service:1.0
```

### 3b. Desplegar en Kubernetes

```bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-configmap.yaml
kubectl apply -f k8s/02-secret.yaml
kubectl apply -f k8s/03-deployment.yaml
kubectl apply -f k8s/04-service.yaml
```

Verificar:
```bash
kubectl get all -n order-service
```

---

## Pruebas

> Puerto **8083** si corre en Docker, puerto **30083** si corre en Kubernetes.
> Los ejemplos usan 30083 (K8s). Cambiar segun corresponda.

### Health check

CMD:
```cmd
curl http://localhost:30083/api/orders/health
```

PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30083/api/orders/health
```

### Crear orden (demuestra punto 3 opcional)

CMD:
```cmd
curl.exe -s -X POST http://localhost:30083/api/orders -H "Content-Type: application/json" -d "{\"userId\": 1, \"items\": [{\"productId\": 1, \"quantity\": 2}, {\"productId\": 3, \"quantity\": 1}]}"
```

PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30083/api/orders `
  -Method POST `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body '{"userId": 1, "items": [{"productId": 1, "quantity": 2}, {"productId": 3, "quantity": 1}]}'
```

Respuesta esperada:
```json
{
  "id": 1,
  "orderNumber": "ORD-2026-001",
  "userId": 1,
  "status": "PENDING",
  "totalAmount": 2689.97,
  "items": [
    {
      "product": { "id": 1, "name": "Laptop Dell XPS 15", "price": 1299.99 },
      "quantity": 2, "unitPrice": 1299.99, "subtotal": 2599.98
    },
    {
      "product": { "id": 3, "name": "Teclado Mecanico Keychron K8", "price": 89.99 },
      "quantity": 1, "unitPrice": 89.99, "subtotal": 89.99
    }
  ]
}
```

### Listar ordenes

CMD: `curl http://localhost:30083/api/orders`

PowerShell: `Invoke-RestMethod -Uri http://localhost:30083/api/orders`

### Obtener orden por ID

CMD: `curl http://localhost:30083/api/orders/1`

PowerShell: `Invoke-RestMethod -Uri http://localhost:30083/api/orders/1`

### Ordenes por usuario

CMD: `curl http://localhost:30083/api/orders/user/1`

PowerShell: `Invoke-RestMethod -Uri http://localhost:30083/api/orders/user/1`

### Error: producto inexistente (503)

CMD:
```cmd
curl.exe -s -X POST http://localhost:30083/api/orders -H "Content-Type: application/json" -d "{\"userId\": 1, \"items\": [{\"productId\": 9999, \"quantity\": 1}]}"
```

PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30083/api/orders `
  -Method POST `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body '{"userId": 1, "items": [{"productId": 9999, "quantity": 1}]}'
```

### Error: orden inexistente (404)

CMD: `curl http://localhost:30083/api/orders/9999`

PowerShell: `Invoke-RestMethod -Uri http://localhost:30083/api/orders/9999`

---

## Detener y limpiar

### Si corre en Docker

```bash
# Detener y eliminar el contenedor de order-service
docker stop order-service
docker rm order-service

# Detener las bases de datos (los datos se conservan en los volumenes)
docker-compose down

# Detener las bases de datos Y borrar todos los datos
docker-compose down -v
```

### Si corre en Kubernetes

```bash
# Opcion 1: Eliminar todos los recursos del namespace de una vez
kubectl delete namespace order-service

# Opcion 2: Eliminar recurso por recurso (orden inverso)
kubectl delete -f k8s/04-service.yaml
kubectl delete -f k8s/03-deployment.yaml
kubectl delete -f k8s/02-secret.yaml
kubectl delete -f k8s/01-configmap.yaml
kubectl delete -f k8s/00-namespace.yaml
```

### Eliminar la imagen Docker (opcional)

```bash
docker rmi order-service:1.0
```

---

## Resumen de puertos

| Servicio | Puerto App | NodePort K8s | Puerto BD |
|----------|-----------|-------------|-----------|
| user-service | 8081 | 30081 | 5434 (userdb) |
| product-service | 8082 | 30082 | 5433 (productdb) |
| order-service | 8083 | 30083 | 5435 (orderdb) |

---

## Pruebas con Bruno / Postman

En la carpeta [`collections_bruno/`](collections_bruno/) se incluye una coleccion lista para importar con los 8 requests de validacion.

### Importar en Bruno

1. Abrir Bruno > **Collection > Open Collection**
2. Seleccionar la carpeta `order-service/collections_bruno/`
3. Elegir el environment segun donde corre el servicio:
   - **Docker** (`http://localhost:8083`)
   - **Kubernetes** (`http://localhost:30083`)
4. Ejecutar los requests en orden (1 al 8)

### Importar en Postman

1. Abrir Postman > **Import**
2. Seleccionar el archivo `collections_bruno/Order-Service.postman_collection.json`
3. La variable `baseUrl` viene como `http://localhost:30083` (K8s). Cambiar a `8083` si usas Docker.
4. Ejecutar los requests en orden (1 al 8)

### Requests incluidos

| # | Request | Metodo | Endpoint | Status |
|---|---------|--------|----------|--------|
| 1 | Health Check | GET | /api/orders/health | 200 |
| 2 | Crear Orden (Punto 3 Opcional) | POST | /api/orders | 201 |
| 3 | Listar Ordenes | GET | /api/orders | 200 |
| 4 | Obtener Orden por ID | GET | /api/orders/1 | 200 |
| 5 | Ordenes por Usuario | GET | /api/orders/user/1 | 200 |
| 6 | Error: Producto Inexistente | POST | /api/orders | 503 |
| 7 | Error: Items Vacios | POST | /api/orders | 400 |
| 8 | Error: Orden Inexistente | GET | /api/orders/9999 | 404 |

> Ver [collections_bruno/README.md](collections_bruno/README.md) para instrucciones detalladas.

---

## Guias detalladas

| Guia | Descripcion |
|------|-------------|
| [README_01_DOCKER.md](README_01_DOCKER.md) | Validacion paso a paso con Docker |
| [README_02_KUBERNETES.md](README_02_KUBERNETES.md) | Despliegue paso a paso en Kubernetes |
| [collections_bruno/README.md](collections_bruno/README.md) | Como usar las colecciones Bruno / Postman |
