# Order Service - Microservicio de Gestión de Órdenes

Microservicio de gestión de órdenes de compra para un sistema e-commerce.
Se integra con **Product Service** (HTTP) para validar productos y obtener precios actuales.

## Arquitectura

- **Clean Architecture** (domain, application, infrastructure, presentation)
- **Puerto:** 8083
- **Base de datos:** PostgreSQL (`orderdb` en puerto 5435)
- **Comunicación:** HTTP → Product Service (validación de productos)

## Endpoints

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | /api/orders | Crear una orden |
| GET | /api/orders | Listar todas las órdenes |
| GET | /api/orders/{id} | Obtener orden por ID |
| GET | /api/orders/user/{userId} | Órdenes por usuario |
| GET | /api/orders/health | Health check |

## Guías de despliegue

| Guía | Descripción |
|------|-------------|
| [README_01_DOCKER.md](README_01_DOCKER.md) | Validación con Docker (paso a paso con Docker Desktop) |
| [README_02_KUBERNETES.md](README_02_KUBERNETES.md) | Despliegue en Kubernetes (paso a paso con Docker Desktop) |

## Compilación rápida

```bash
cd order-service
mvn clean package -DskipTests
docker build -t order-service:1.0 .
```
