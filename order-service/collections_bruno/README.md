# Coleccion de pruebas - Order Service

Coleccion lista para importar en **Bruno** o **Postman** con los 8 requests de validacion del Order Service.

---

## Contenido de la coleccion

| # | Request | Metodo | Endpoint | Status esperado |
|---|---------|--------|----------|-----------------|
| 1 | Health Check | GET | /api/orders/health | 200 |
| 2 | Crear Orden (Punto 3 Opcional) | POST | /api/orders | 201 |
| 3 | Listar Ordenes | GET | /api/orders | 200 |
| 4 | Obtener Orden por ID | GET | /api/orders/1 | 200 |
| 5 | Ordenes por Usuario | GET | /api/orders/user/1 | 200 |
| 6 | Error: Producto Inexistente | POST | /api/orders | 503 |
| 7 | Error: Items Vacios | POST | /api/orders | 400 |
| 8 | Error: Orden Inexistente | GET | /api/orders/9999 | 404 |

---

## Environments (entornos)

La coleccion usa una variable `{{baseUrl}}` que cambia segun donde este corriendo el servicio:

| Entorno | baseUrl | Cuando usarlo |
|---------|---------|---------------|
| Docker | `http://localhost:8083` | Order Service corriendo con `docker run` |
| Kubernetes | `http://localhost:30083` | Order Service desplegado en K8s (NodePort) |

---

## Opcion A: Importar en Bruno

### Paso 1: Abrir la coleccion

1. Abrir **Bruno**
2. Ir a **Collection > Open Collection**
3. Navegar hasta la carpeta:
   ```
   order-service/collections_bruno/
   ```
4. Seleccionar esa carpeta y hacer clic en **Open**

Bruno leera el archivo `bruno.json` y cargara todos los `.bru` automaticamente.

### Paso 2: Seleccionar el environment

1. En la esquina superior derecha de Bruno, buscar el selector de **Environment**
2. Elegir:
   - **Docker** si el servicio corre con `docker run -p 8083:8083`
   - **Kubernetes** si el servicio esta desplegado en K8s con NodePort 30083

### Paso 3: Ejecutar los requests

1. En el panel izquierdo veras los 8 requests ordenados (1 al 8)
2. Haz clic en el primero: **1 - Health Check**
3. Haz clic en el boton **Send** (o presiona `Ctrl+Enter`)
4. Verifica la respuesta en el panel derecho
5. Repite con los siguientes requests en orden

### Orden recomendado de ejecucion

```
1. Health Check          -> Verificar que el servicio responde
2. Crear Orden           -> Crear datos (necesario para los siguientes)
3. Listar Ordenes        -> Ver la orden creada
4. Obtener Orden por ID  -> Buscar la orden por ID
5. Ordenes por Usuario   -> Filtrar por usuario
6. Producto Inexistente  -> Probar manejo de error 503
7. Items Vacios          -> Probar validacion 400
8. Orden Inexistente     -> Probar manejo de error 404
```

---

## Opcion B: Importar en Postman

### Paso 1: Importar la coleccion

1. Abrir **Postman**
2. Hacer clic en **Import** (boton arriba a la izquierda)
3. Arrastrar o seleccionar el archivo:
   ```
   order-service/collections_bruno/Order-Service.postman_collection.json
   ```
4. Hacer clic en **Import**

### Paso 2: Configurar el entorno

La coleccion viene con la variable `baseUrl` preconfigurada en `http://localhost:30083` (Kubernetes).

Para cambiarla:

1. En el panel izquierdo, hacer clic en la coleccion **Order Service**
2. Ir a la pestana **Variables**
3. Cambiar el valor de `baseUrl`:
   - Docker: `http://localhost:8083`
   - Kubernetes: `http://localhost:30083`
4. Hacer clic en **Save** (`Ctrl+S`)

### Paso 3: Ejecutar los requests

1. Expandir la coleccion en el panel izquierdo
2. Hacer clic en cada request y presionar **Send**
3. Seguir el orden recomendado (1 al 8)

---

## Pre-requisitos antes de ejecutar

Antes de hacer Send en cualquier request, asegurate de que:

1. **Bases de datos corriendo:**
   ```bash
   docker-compose up -d
   ```

2. **Product Service corriendo** (Order Service lo necesita para crear ordenes):
   - En K8s: `kubectl get pods -n product-service` (debe estar Running)
   - En Docker: `docker ps` (debe estar corriendo en puerto 8082)

3. **Order Service corriendo:**
   - En K8s: `kubectl get pods -n order-service` (debe estar Running)
   - En Docker: `docker ps` (debe estar corriendo en puerto 8083)

---

## Estructura de archivos

```
collections_bruno/
  bruno.json                          <- Config de la coleccion (Bruno)
  Order-Service.postman_collection.json  <- Coleccion exportada (Postman)
  environments/
    Docker.bru                        <- Entorno Docker (puerto 8083)
    Kubernetes.bru                    <- Entorno Kubernetes (puerto 30083)
  1-Health-Check.bru
  2-Crear-Orden.bru
  3-Listar-Ordenes.bru
  4-Obtener-Orden-por-ID.bru
  5-Ordenes-por-Usuario.bru
  6-Error-Producto-Inexistente.bru
  7-Error-Items-Vacios.bru
  8-Error-Orden-Inexistente.bru
```
