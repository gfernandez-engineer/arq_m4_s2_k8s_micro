# 📄 TRABAJO: ORDER SERVICE 

**Módulo:** 4 
**Fecha de entrega:** 23/02/2026  

---

## 🎯 OBJETIVO

Desarrollar un microservicio de **Gestión de Órdenes (Order Service)** que se integre con el microservicio **Product Service** y se despliegue localmente en Kubernete.

---

## 📋 DESCRIPCIÓN

En una arquitectura de microservicios para un sistema de e-commerce, se requiere implementar el servicio de gestión de órdenes de compra. Este servicio debe:

1. **Registrar órdenes de compra** que contengan uno o más productos
2. **Asociar cada orden a un usuario** específico del sistema
3. **(OPCIONAL)Calcular automáticamente** el monto total de la orden basándose en precios actuales

El reto principal es que el Order Service **depende de**:
- **Product Service**: Para validar productos y obtener precios actuales

---

## 🏗️ ARQUITECTURA DEL SISTEMA

### Arquitectura Completa
```
┌─────────────────────────────────────────────────────────────┐
│               ARQUITECTURA COMPLETA (CON ORDER)             │
└─────────────────────────────────────────────────────────────┘

     ┌────────────┐ ┌────────────┐ ┌────────────┐
     │   User     │ │  Product   │ │   Order    │
     │  Service   │ │  Service   │ │  Service   │ ◄── NUEVO
     │   :8081    │ │   :8082    │ │   :8083    │
     └──────┬─────┘ └──────┬─────┘ └──────┬─────┘
            │              │              │
            │              │              │
            ▼              ▼              ▼
       ┌────────┐     ┌─────────┐    ┌────────┐
       │userdb  │     │productdb│    │orderdb │ ◄── NUEVA BD
       │ :5434  │     │ :5433   │    │ :5435  │
       └────────┘     └─────────┘    └────────┘

COMUNICACIÓN:
Order Service ──(HTTP )──► Product Service
```

### Flujo de Datos
```
┌─────────────────────────────────────────────────────────────┐
│  FLUJO: Crear Orden                                          │
└─────────────────────────────────────────────────────────────┘

Cliente
  │
  │ POST /api/orders
  │ { userId: 1, items: [...] }
  ▼
Order Service
  │
  ├─----------------------─► Product Service
  │                          GET /api/products/1
  │                          ✅ Producto válido + precio
  │
  ├─► Calcular totales
  │   quantity × unit_price = subtotal
  │   Σ subtotals = total_amount
  │
  ├─► Guardar en orderdb
  │   INSERT INTO orders (...)
  │   INSERT INTO order_items (...)
  │
  ▼
Respuesta 201 Created
{
  "id": 1,
  "orderNumber": "ORD-2025-001",
  "user": { ... },
  "items": [ ... ],
  "totalAmount": 2599.98
}
```

---

## 📊 MODELO DE DATOS

### Diagrama Entidad-Relación
```
┌─────────────────────────────┐
│            ORDERS           │
├─────────────────────────────┤
│ PK  id                      │
│     order_number (UNIQUE)   │
│     user_id                 │
│     status                  │
│     total_amount            │
│     created_at              │
│     updated_at              │
└─────────────┬───────────────┘
              │ 1      
              │         
              │                         
              │ N                        
              ▼                        
┌─────────────────────────────┐
│        ORDER_ITEMS          │
├─────────────────────────────┤
│ PK  id                      │
│ FK  order_id                │
│     product_id              │
│     quantity                │
│     unit_price              │
│     subtotal                │
└─────────────────────────────┘
                               
     product_id    ────────────────────┐
                                       │
        user_id    ──────────┐         │
                             │         │
                             ▼         ▼
                    User Service   Product Service
                      (userdb)      (productdb)
```

### Tabla: orders

| Campo | Tipo | Restricciones | Descripción |
|-------|------|---------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | ID único de la orden |
| `order_number` | VARCHAR(50) | UNIQUE, NOT NULL | Número de orden (ej: ORD-2025-001) |
| `user_id` | BIGINT | NOT NULL | ID del usuario (ref. externa) |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | Estado de la orden |
| `total_amount` | NUMERIC(10,2) | NOT NULL, >= 0 | Monto total |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Fecha de creación |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Fecha de actualización |

**Estados válidos:** `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`

### Tabla: order_items

| Campo | Tipo | Restricciones | Descripción |
|-------|------|---------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | ID único del item |
| `order_id` | BIGINT | NOT NULL, FK → orders(id) CASCADE | ID de la orden |
| `product_id` | BIGINT | NOT NULL | ID del producto (ref. externa) |
| `quantity` | INTEGER | NOT NULL, > 0 | Cantidad |
| `unit_price` | NUMERIC(10,2) | NOT NULL, >= 0 | Precio unitario |
| `subtotal` | NUMERIC(10,2) | NOT NULL, >= 0 | Subtotal (qty × price) |

### Script SQL
```sql
-- Tabla de órdenes
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT chk_total_positive CHECK (total_amount >= 0)
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

-- Tabla de items
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL,
    subtotal NUMERIC(10, 2) NOT NULL,
    
    CONSTRAINT fk_order FOREIGN KEY (order_id) 
        REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_unit_price_positive CHECK (unit_price >= 0),
    CONSTRAINT chk_subtotal_positive CHECK (subtotal >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

-- Datos de prueba
INSERT INTO orders (order_number, user_id, status, total_amount) VALUES
('ORD-2025-001', 1, 'CONFIRMED', 2849.97),
('ORD-2025-002', 2, 'PENDING', 1199.98),
('ORD-2025-003', 1, 'SHIPPED', 149.99);

INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal) VALUES
(1, 1, 1, 1299.99, 1299.99),
(1, 2, 1, 999.99, 999.99),
(1, 3, 1, 399.99, 399.99),
(2, 4, 1, 799.99, 799.99),
(2, 5, 1, 399.00, 399.00),
(3, 7, 1, 149.99, 149.99);
```

---

## 🎯 REQUERIMIENTOS FUNCIONALES

### RF-01: Crear Orden de Compra

**Endpoint:** `POST /api/orders`

**Request:**
```json
{
  "userId": 1,
  "items": [
    {
      "productId": 1,
      "quantity": 2
    },
    {
      "productId": 3,
      "quantity": 1
    }
  ]
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "orderNumber": "ORD-2025-001",
  "userId": 1,
  "items": [
    {
      "id": 1,
      "product": {
        "id": 1,
        "name": "Laptop Dell XPS 15",
        "price": 1299.99
      },
      "quantity": 2,
      "unitPrice": 1299.99,
      "subtotal": 2599.98
    }
  ],
  "totalAmount": 2999.97,
  "status": "PENDING",
  "createdAt": "2025-01-20T10:30:00",
}
```

**Proceso:**
1. Para cada item:
   - Validar producto en Product Service
   - Obtener precio actual
   - Calcular subtotal
2. Calcular total de la orden
3. Generar número de orden único
4. Guardar en BD
5. Retornar orden completa

---
