# Order Service

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.10-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-habilitado-2496ED?logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-habilitado-326CE5?logo=kubernetes&logoColor=white)
![MapStruct](https://img.shields.io/badge/MapStruct-1.5.5.Final-B52626)

Microservicio de gestion de ordenes de compra para un sistema e-commerce distribuido.
Se integra con **Product Service** via HTTP para validar productos y calcular totales en tiempo real.

---

## Tabla de Contenidos

- [Requerimientos implementados](#requerimientos-implementados)
- [Arquitectura](#arquitectura)
- [Tecnologias](#tecnologias)
- [Pre-requisitos](#pre-requisitos)
- [Inicio Rapido](#inicio-rapido)
- [Variables de Entorno](#variables-de-entorno)
- [API Reference](#api-reference)
- [Base de Datos](#base-de-datos)
- [Pruebas](#pruebas)
- [Detener y Limpiar](#detener-y-limpiar)
- [Resumen de Puertos](#resumen-de-puertos)
- [Colecciones API](#colecciones-api-bruno--postman)
- [Guias Detalladas](#guias-detalladas)

---

## Requerimientos implementados

| ID | Descripcion | Estado |
|----|-------------|--------|
| RF-01 | CRUD de ordenes: crear, listar, buscar por ID y por usuario | Implementado |
| RF-02 | Comunicacion HTTP entre microservicios (Order Service -> Product Service) | Implementado |
| RF-03 | Calculo automatico del total usando precios actuales del Product Service | Implementado |

### Flujo de creacion de orden (RF-02 + RF-03)

```
POST /api/orders  { userId, items: [{ productId, quantity }] }
        |
        v
  Para cada item en items[]:
  +--------------------------------------------------+
  |  1. HTTP GET product-service/api/products/{id}   |
  |  2. Valida existencia del producto (404 -> 503)  |
  |  3. Obtiene precio actual                        |
  |  4. subtotal = quantity x unitPrice              |
  +--------------------------------------------------+
        |
        v
  totalAmount = suma de todos los subtotals
        |
        v
  Genera numero de orden: ORD-{year}-{secuencia}
        |
        v
  Persiste en BD (tablas: orders + order_items)
        |
        v
  201 Created  { id, orderNumber, totalAmount, items[] }
```

---

## Arquitectura

### Clean Architecture

```
order-service/src/main/java/com/tecsup/app/micro/order_service/
|
|-- domain/                          <- CAPA DE DOMINIO (reglas de negocio puras)
|   |-- model/
|   |   |-- Order.java               <- Modelo de dominio: orden
|   |   |-- OrderItem.java           <- Modelo de dominio: item de orden
|   |   |-- Product.java             <- Modelo de dominio: producto (referencia)
|   |   +-- User.java                <- Modelo de dominio: usuario (referencia)
|   |-- repository/
|   |   +-- OrderRepository.java     <- Puerto de salida: interfaz del repositorio
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
|   |   +-- BeanConfig.java          <- Bean de RestTemplate
|   |-- client/
|   |   |-- ProductClient.java       <- Adaptador HTTP hacia Product Service
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
|           +-- OrderRepositoryImpl.java     <- Adaptador del puerto de repositorio
|
+-- presentation/                    <- CAPA DE PRESENTACION (API REST)
    |-- controller/
    |   |-- OrderController.java     <- Endpoints REST
    |   +-- GlobalExceptionHandler.java
    |-- dto/
    |   |-- CreateOrderRequest.java  <- Request: { userId, items[] }
    |   |-- OrderItemRequest.java    <- Item del request: { productId, quantity }
    |   |-- OrderResponse.java       <- Response completa de la orden
    |   |-- OrderItemResponse.java   <- Item de respuesta con datos del producto
    |   +-- ProductInfo.java         <- Objeto embebido: { id, name, price }
    +-- mapper/
        +-- OrderDtoMapper.java      <- MapStruct: Domain <-> DTO
```

### Comunicacion entre servicios

```
                    Kubernetes Cluster (Docker Desktop)
  +------------------------------------------------------------------+
  |                                                                  |
  |  ns: order-service                            localhost:30083    |
  |  +------------------------+                                      |
  |  |  pod: order-service    |                                      |
  |  |  puerto: 8083          |                                      |
  |  +-----------+------------+                                      |
  |              |                                                   |
  |              |  HTTP GET /api/products/{id}                      |
  |              |  DNS: product-service.product-service             |
  |              |       .svc.cluster.local                          |
  |              v                                                   |
  |  ns: product-service                          localhost:30082    |
  |  +------------------------+                                      |
  |  |  pod: product-service  |                                      |
  |  |  puerto: 8082          |                                      |
  |  +------------------------+                                      |
  |                                                                  |
  +------------------------------------------------------------------+
          |                               |
    PostgreSQL orderdb              PostgreSQL productdb
    host:5435                       host:5433
    (fuera del cluster, gestionadas por docker-compose del monorepo)
```

---

## Tecnologias

| Tecnologia | Version | Uso |
|------------|---------|-----|
| Java | 21 | Lenguaje |
| Spring Boot | 3.5.10 | Framework principal |
| Spring Data JPA | gestionado por Spring Boot | Persistencia ORM con Hibernate |
| Spring Boot Actuator | gestionado por Spring Boot | Health checks para Kubernetes (liveness/readiness) |
| Spring Boot Validation | gestionado por Spring Boot | Validacion de requests con Bean Validation |
| RestTemplate | incluido en Spring Web | Cliente HTTP hacia Product Service |
| MapStruct | 1.5.5.Final | Mapeo entre capas (Entity / Domain / DTO) |
| Lombok | gestionado por Spring Boot | Reduccion de boilerplate |
| PostgreSQL | 15 | Base de datos relacional |
| Docker | - | Contenedorizacion |
| Kubernetes | - | Orquestacion (Docker Desktop) |

---

## Pre-requisitos

- **Java 21** instalado en el sistema
- **Maven** instalado, o usar el wrapper incluido en el proyecto (`./mvnw` en Linux/Mac, `mvnw.cmd` en Windows)
- **Docker Desktop** con Kubernetes habilitado
- **Product Service** corriendo (puerto `8082` o `30082`) — requerido para crear ordenes
- **Base de datos `orderdb`** accesible en el puerto `5435` (levantada via Docker Compose del monorepo)

> Las bases de datos y la dependencia de Product Service se gestionan con los recursos del monorepo.
> Ver las [Guias Detalladas](#guias-detalladas) para el flujo completo de despliegue.

---

## Inicio Rapido

> Los comandos del **Paso 1** se ejecutan desde la **raiz del monorepo**.
> Los comandos de los **Pasos 2 y 3** se ejecutan desde el directorio `order-service/`.

### Paso 1. Levantar bases de datos

```bash
docker-compose up -d
```

Verificar que las 3 bases de datos estan corriendo:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
# NAMES              STATUS                  PORTS
# postgres-order     Up X minutes (healthy)  0.0.0.0:5435->5432/tcp
# postgres-product   Up X minutes (healthy)  0.0.0.0:5433->5432/tcp
# postgres-user      Up X minutes (healthy)  0.0.0.0:5434->5432/tcp
```

### Paso 2. Compilar y construir la imagen Docker

```bash
cd order-service
mvn clean package -DskipTests
docker build -t order-service:1.0 .
```

### Paso 3a. Ejecutar con Docker

**CMD:**
```cmd
docker run -d --name order-service -p 8083:8083 ^
  -e PRODUCT_SERVICE_URL=http://host.docker.internal:30082 ^
  -e DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb ^
  -e DB_USERNAME=postgres -e DB_PASSWORD=postgres ^
  -e DDL_AUTO=update order-service:1.0
```

**PowerShell:**
```powershell
docker run -d --name order-service `
  -p 8083:8083 `
  -e PRODUCT_SERVICE_URL=http://host.docker.internal:30082 `
  -e DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb `
  -e DB_USERNAME=postgres -e DB_PASSWORD=postgres `
  -e DDL_AUTO=update order-service:1.0
```

Verificar (esperar ~20 segundos al arranque de Spring Boot):

```bash
curl http://localhost:8083/api/orders/health
# Esperado: Order Service running with Clean Architecture!
```

### Paso 3b. Desplegar en Kubernetes

```bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-configmap.yaml
kubectl apply -f k8s/02-secret.yaml
kubectl apply -f k8s/03-deployment.yaml
kubectl apply -f k8s/04-service.yaml
```

Verificar que el pod esta corriendo (puede tardar ~30 segundos):

```bash
kubectl get all -n order-service
# NAME                             READY   STATUS    RESTARTS   AGE
# pod/order-service-xxxxx          1/1     Running   0          30s
```

---

## Variables de Entorno

| Variable | Descripcion | Default | Requerida |
|----------|-------------|---------|-----------|
| `DB_URL` | JDBC URL de la base de datos PostgreSQL | `jdbc:postgresql://localhost:5435/orderdb` | Si |
| `DB_USERNAME` | Usuario de PostgreSQL | `postgres` | Si |
| `DB_PASSWORD` | Contrasena de PostgreSQL | `postgres` | Si |
| `DDL_AUTO` | Estrategia DDL de Hibernate: `update`, `validate`, `none` | `update` | Si |
| `PRODUCT_SERVICE_URL` | URL base del Product Service | `http://localhost:8082` | Si |
| `POOL_SIZE` | Tamano maximo del connection pool (HikariCP) | `10` | No |
| `SHOW_SQL` | Mostrar SQL generado por Hibernate en los logs | `false` | No |
| `LOG_LEVEL` | Nivel de logging de la aplicacion | `INFO` | No |
| `SQL_LOG_LEVEL` | Nivel de logging para sentencias SQL de Hibernate | `WARN` | No |

> En Kubernetes, `DB_PASSWORD` se gestiona como Secret (valor en Base64).
> El resto de variables no sensibles viven en el ConfigMap `order-service-config`.

---

## API Reference

### Endpoints

| Metodo | Endpoint | Body | Status | Errores posibles |
|--------|----------|------|--------|-----------------|
| `POST` | `/api/orders` | `CreateOrderRequest` | 201 | 400, 503 |
| `GET` | `/api/orders` | — | 200 | — |
| `GET` | `/api/orders/{id}` | — | 200 | 404 |
| `GET` | `/api/orders/user/{userId}` | — | 200 | — |
| `GET` | `/api/orders/health` | — | 200 | — |

### Request: POST /api/orders

```json
{
  "userId": 1,
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

> `items` no puede estar vacio (`@NotEmpty`). Cada item requiere `productId` y `quantity >= 1`.

### Response: Orden

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
      "quantity": 2,
      "unitPrice": 1299.99,
      "subtotal": 2599.98
    },
    {
      "product": { "id": 3, "name": "Teclado Mecanico Keychron K8", "price": 89.99 },
      "quantity": 1,
      "unitPrice": 89.99,
      "subtotal": 89.99
    }
  ],
  "createdAt": "2026-02-19T10:00:00",
  "updatedAt": "2026-02-19T10:00:00"
}
```

### Codigos de error

| Status | Escenario | Estructura de respuesta |
|--------|-----------|------------------------|
| `400` | Items vacios o datos invalidos en el request | `{ status, message, timestamp, errors: { campo: mensaje } }` |
| `400` | Argumento invalido (ej: cantidad negativa) | `{ status, message, timestamp }` |
| `404` | Orden no encontrada con el ID solicitado | `{ status, message, timestamp }` |
| `503` | Producto no encontrado en Product Service | `{ status, message, timestamp }` |
| `500` | Error inesperado en el servidor | `{ status, message, timestamp }` |

**Ejemplo 400 — error de validacion:**
```json
{
  "status": 400,
  "message": "Validation failed",
  "timestamp": "2026-02-19T10:00:00",
  "errors": { "items": "Order must have at least one item" }
}
```

**Ejemplo 404 / 503:**
```json
{
  "status": 404,
  "message": "Order not found with id: 9999",
  "timestamp": "2026-02-19T10:00:00"
}
```

---

## Base de Datos

```sql
-- Tabla: orders
CREATE TABLE orders (
    id           BIGSERIAL     PRIMARY KEY,
    order_number VARCHAR(50)   UNIQUE NOT NULL,
    user_id      BIGINT        NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10,2) NOT NULL,
    created_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- Tabla: order_items
CREATE TABLE order_items (
    id         BIGSERIAL     PRIMARY KEY,
    order_id   BIGINT        NOT NULL REFERENCES orders(id),
    product_id BIGINT        NOT NULL,
    quantity   INTEGER       NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal   DECIMAL(10,2) NOT NULL
);
```

> Las tablas se crean automaticamente al arrancar el servicio con `DDL_AUTO=update`.

---

## Pruebas

> Puerto **8083** si el servicio corre en Docker, puerto **30083** si corre en Kubernetes.
> Los ejemplos usan `30083` (K8s). Cambiar segun el entorno activo.
>
> Para las pruebas de GET con datos, ejecutar primero el POST de **Crear orden**.

### Casos exitosos

---

**Health Check**

CMD: `curl http://localhost:30083/api/orders/health`

PowerShell: `Invoke-RestMethod -Uri http://localhost:30083/api/orders/health`

Esperado: `Order Service running with Clean Architecture!`

---

**Crear orden**

CMD:
```cmd
curl.exe -s -X POST http://localhost:30083/api/orders ^
  -H "Content-Type: application/json" ^
  -d "{\"userId\": 1, \"items\": [{\"productId\": 1, \"quantity\": 2}, {\"productId\": 3, \"quantity\": 1}]}"
```

PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30083/api/orders `
  -Method POST `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body '{"userId": 1, "items": [{"productId": 1, "quantity": 2}, {"productId": 3, "quantity": 1}]}'
```

Esperado: `201 Created` con la estructura de respuesta completa (ver [API Reference](#api-reference)).

---

**Listar ordenes**

CMD: `curl http://localhost:30083/api/orders`

PowerShell: `Invoke-RestMethod -Uri http://localhost:30083/api/orders`

---

**Obtener orden por ID**

CMD: `curl http://localhost:30083/api/orders/1`

PowerShell: `Invoke-RestMethod -Uri http://localhost:30083/api/orders/1`

---

**Ordenes por usuario**

CMD: `curl http://localhost:30083/api/orders/user/1`

PowerShell: `Invoke-RestMethod -Uri http://localhost:30083/api/orders/user/1`

---

### Manejo de errores

---

**Producto inexistente — 503**

CMD:
```cmd
curl.exe -s -X POST http://localhost:30083/api/orders ^
  -H "Content-Type: application/json" ^
  -d "{\"userId\": 1, \"items\": [{\"productId\": 9999, \"quantity\": 1}]}"
```

PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30083/api/orders `
  -Method POST `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body '{"userId": 1, "items": [{"productId": 9999, "quantity": 1}]}'
```

Esperado: `{ "status": 503, "message": "Product not found with id: 9999", "timestamp": "..." }`

---

**Items vacios — 400**

CMD:
```cmd
curl.exe -s -X POST http://localhost:30083/api/orders ^
  -H "Content-Type: application/json" ^
  -d "{\"userId\": 1, \"items\": []}"
```

PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30083/api/orders `
  -Method POST `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body '{"userId": 1, "items": []}'
```

Esperado: `{ "status": 400, "message": "Validation failed", "errors": { "items": "..." } }`

---

**Orden inexistente — 404**

CMD: `curl http://localhost:30083/api/orders/9999`

PowerShell: `Invoke-RestMethod -Uri http://localhost:30083/api/orders/9999`

Esperado: `{ "status": 404, "message": "Order not found with id: 9999", "timestamp": "..." }`

---

## Detener y Limpiar

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

> En Kubernetes no existe un "contenedor parado" como en Docker.
> El equivalente a `docker stop` es escalar a 0 replicas: el pod se elimina,
> pero el Deployment conserva toda la configuracion para recrearlo cuando quieras.

```bash
# Detener temporalmente (escalar a 0 replicas - conserva configuracion)
kubectl scale deployment order-service --replicas=0 -n order-service

# Volver a arrancar (restaurar a 1 replica con la misma configuracion)
kubectl scale deployment order-service --replicas=1 -n order-service

# Eliminar todo: Opcion 1 - borrar el namespace completo de una vez
kubectl delete namespace order-service

# Eliminar todo: Opcion 2 - recurso por recurso (orden inverso)
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

## Resumen de Puertos

| Servicio | Puerto App | NodePort K8s | Puerto BD |
|----------|-----------|-------------|-----------|
| user-service | 8081 | 30081 | 5434 (userdb) |
| product-service | 8082 | 30082 | 5433 (productdb) |
| order-service | 8083 | 30083 | 5435 (orderdb) |

---

## Colecciones API (Bruno / Postman)

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
3. La variable `baseUrl` viene preconfigurada como `http://localhost:30083` (K8s). Cambiar a `8083` si usas Docker.
4. Ejecutar los requests en orden (1 al 8)

### Requests incluidos

| # | Request | Metodo | Endpoint | Status esperado |
|---|---------|--------|----------|----------------|
| 1 | Health Check | GET | /api/orders/health | 200 |
| 2 | Crear Orden | POST | /api/orders | 201 |
| 3 | Listar Ordenes | GET | /api/orders | 200 |
| 4 | Obtener Orden por ID | GET | /api/orders/1 | 200 |
| 5 | Ordenes por Usuario | GET | /api/orders/user/1 | 200 |
| 6 | Error: Producto Inexistente | POST | /api/orders | 503 |
| 7 | Error: Items Vacios | POST | /api/orders | 400 |
| 8 | Error: Orden Inexistente | GET | /api/orders/9999 | 404 |

> Ver [collections_bruno/README.md](collections_bruno/README.md) para instrucciones detalladas.

---

## Guias Detalladas

| Guia | Descripcion |
|------|-------------|
| [README_01_DOCKER.md](README_01_DOCKER.md) | Validacion paso a paso con Docker: build, ejecucion y prueba de cada endpoint con explicacion de cada comando |
| [README_02_KUBERNETES.md](README_02_KUBERNETES.md) | Despliegue paso a paso en Kubernetes: manifiestos, health checks, redespliegue y troubleshooting |
| [collections_bruno/README.md](collections_bruno/README.md) | Como importar y ejecutar las colecciones en Bruno y Postman |
