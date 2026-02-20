# ORDER SERVICE - Validación con Docker

Guía paso a paso para compilar, dockerizar y validar el flujo completo del Order Service.
Cada comando tiene una explicación de **qué hace** y **por qué lo usamos**.

---

## Pestañas de Docker Desktop - Guía rápida

```
Docker Desktop tiene 5 pestañas principales:

+------------------------------------------------------------------+
| Containers | Images | Volumes | Builds | Kubernetes               |
+------------------------------------------------------------------+

ANALOGÍA CON LA VIDA REAL:

- Images     = RECETAS de cocina (instrucciones para crear algo)
- Containers = PLATOS servidos (la receta ejecutándose)
- Volumes    = REFRIGERADOR (datos que persisten aunque tires el plato)
- Builds     = HISTORIAL de cocina (cuándo preparaste cada receta)
- Kubernetes = CHEF AUTOMÁTICO (gestiona múltiples platos a la vez)

REGLA CLAVE:
  Imagen → es una PLANTILLA (no consume recursos, no corre)
  Contenedor → es una INSTANCIA corriendo de esa imagen (consume CPU/RAM)

  De 1 imagen puedes crear N contenedores.
  Es como de 1 receta puedes hacer muchos platos.
```

---

## PASO 1: Levantar las bases de datos

```bash
# docker-compose up = lee el archivo docker-compose.yml y crea los contenedores definidos ahí
# -d = "detached" = ejecutar en segundo plano (no bloquea tu terminal)
# Sin -d, los logs se muestran en tu terminal y si cierras la terminal, se detienen los contenedores
docker-compose up -d
```

### Verificar en terminal:
```bash
# docker ps = "process status" = lista contenedores corriendo
# --format = personalizar qué columnas mostrar (sin esto muestra DEMASIADA info)
# {{.Names}} = nombre del contenedor
# {{.Status}} = si está corriendo, cuánto tiempo lleva, si está "healthy"
# {{.Ports}} = qué puertos están mapeados
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Output esperado:
# NAMES              STATUS                  PORTS
# postgres-user      Up X minutes (healthy)  0.0.0.0:5434->5432/tcp
# postgres-product   Up X minutes (healthy)  0.0.0.0:5433->5432/tcp
# postgres-order     Up X minutes (healthy)  0.0.0.0:5435->5432/tcp
#
# ¿Qué significa "0.0.0.0:5435->5432/tcp"?
#   Tu PC:5435 se redirige al contenedor:5432
#   PostgreSQL SIEMPRE corre en 5432 dentro del contenedor
#   Pero en tu PC cada BD usa un puerto diferente para no chocar
```

### Docker Desktop - Pestaña CONTAINERS
```
Abre Docker Desktop > Containers

Deberías ver 3 contenedores con status "Running" (punto verde):
  - postgres-user      (puerto 5434)
  - postgres-product   (puerto 5433)
  - postgres-order     (puerto 5435)

Haz clic en "postgres-order" para explorar las sub-pestañas:
  - Logs    : Mensajes del contenedor ("database system is ready to accept connections")
  - Inspect : Configuración completa (variables de entorno, red, volúmenes)
  - Terminal: Abre una shell DENTRO del contenedor (como SSH pero local)
  - Stats   : CPU y RAM que está usando en tiempo real
```

### Docker Desktop - Pestaña VOLUMES
```
Abre Docker Desktop > Volumes

Deberías ver 3 volúmenes:
  - arq_m4_s2_k8s_micro_postgres-user-data
  - arq_m4_s2_k8s_micro_postgres-product-data
  - arq_m4_s2_k8s_micro_postgres-order-data

¿QUÉ ES UN VOLUMEN?
  Un contenedor es TEMPORAL: si lo borras, todo lo de adentro desaparece.
  Un volumen es un DISCO EXTERNO que se conecta al contenedor.
  PostgreSQL guarda sus datos en el volumen, no dentro del contenedor.

  Así puedes borrar y recrear el contenedor postgres-order,
  y al reconectarlo al volumen, tus datos siguen ahí.

  PELIGRO: Si borras el VOLUMEN (docker-compose down -v), pierdes los datos.
```

---

## PASO 2: Verificar que Product Service está disponible

Order Service necesita hablar con Product Service para validar productos.
Si Product Service no está corriendo, el POST de crear orden fallará.

```bash
# curl = herramienta para hacer peticiones HTTP desde terminal
# Es como un "navegador de texto" - manda la petición y muestra la respuesta

# Si product-service corre en K8s (NodePort 30082):
curl http://localhost:30082/api/products/health

# Si product-service corre en Docker (puerto 8082):
curl http://localhost:8082/api/products/health

# Output esperado:
# Product Service running with Clean Architecture!
#
# Si dice "Connection refused" → Product Service no está corriendo
# Si dice "timeout" → el puerto es incorrecto
```

> IMPORTANTE: Anota en qué puerto responde Product Service (8082 o 30082).
> Lo necesitarás en el PASO 5 para la variable PRODUCT_SERVICE_URL.

---

## PASO 3: Compilar Order Service

```bash
# cd = "change directory" = moverse a la carpeta del proyecto
cd order-service

# mvn = Maven, herramienta de build de Java (como npm para JavaScript)
# clean = borrar compilaciones anteriores (carpeta target/)
# package = compilar + empaquetar en un JAR (Java ARchive)
# -DskipTests = no ejecutar tests (más rápido para desarrollo)
#
# El resultado es un archivo .jar que contiene TODA tu aplicación
# lista para ejecutarse con "java -jar archivo.jar"
mvn clean package -DskipTests
```

Verificar que se generó el JAR:
```bash
# ls = listar archivos
# -l = formato largo (permisos, tamaño, fecha)
# -h = tamaño "human readable" (57M en vez de 59768832)
ls -lh target/order-service-0.0.1-SNAPSHOT.jar

# Output esperado:
# -rw-r--r-- 1 user 57M order-service-0.0.1-SNAPSHOT.jar
#
# Si no aparece → el build falló. Revisa errores arriba con: mvn clean package
```

---

## PASO 4: Construir la imagen Docker

```bash
# docker build = crear una imagen Docker leyendo el Dockerfile
# -t order-service:1.0 = "tag" = nombre:versión de la imagen
#   - order-service = nombre de la imagen
#   - 1.0 = versión (puedes usar cualquier texto: 1.0, latest, v2, etc.)
# . = directorio actual (donde está el Dockerfile)
#
# ¿Qué hace internamente?
#   1. Lee el Dockerfile
#   2. Descarga la imagen base (eclipse-temurin:21-jre-alpine) si no la tiene
#   3. Copia tu JAR dentro de la imagen
#   4. Guarda la imagen en tu máquina local
docker build -t order-service:1.0 .
```

Verificar que la imagen se creó:
```bash
# docker images = listar todas las imágenes locales
# grep = filtrar líneas que contengan "order-service"
docker images | grep order-service

# Output esperado:
# order-service  1.0  abc123def456  10 seconds ago  393MB
#
# La imagen PESA 393MB porque incluye:
#   - Linux Alpine (~5MB) - sistema operativo mínimo
#   - Java 21 JRE (~330MB) - runtime de Java
#   - Tu JAR (~57MB) - tu aplicación
```

### Docker Desktop - Pestaña BUILDS
```
Abre Docker Desktop > Builds

Verás el build que acabas de ejecutar:
  - Estado: Completed (verde)
  - Duración: ~10-15 segundos
  - Imagen: order-service:1.0

Haz clic en el build para ver los PASOS (layers):
  1. FROM eclipse-temurin:21-jre-alpine  → descarga imagen base
  2. WORKDIR /app                        → crea carpeta /app
  3. COPY target/*.jar ...               → copia tu JAR

¿QUÉ SON LAS LAYERS (CAPAS)?
  Cada instrucción del Dockerfile crea una "capa".
  Docker CACHEA cada capa. Si cambias solo tu JAR (paso 3),
  Docker reutiliza las capas 1 y 2 del cache.
  Por eso el segundo build es mucho más rápido que el primero.
```

### Docker Desktop - Pestaña IMAGES
```
Abre Docker Desktop > Images

Verás la nueva imagen:
  - order-service    Tag: 1.0    Size: ~393MB

Haz clic en order-service:1.0 para ver:
  - Layers: Las capas que componen la imagen (mismas del build)
  - Packages: Librerías incluidas
  - Vulnerabilities: Escaneo de seguridad (si está habilitado)

RECUERDA: La imagen NO consume CPU/RAM. Es solo una plantilla en disco.
No "corre" nada. Solo cuando haces "docker run" se crea un contenedor.
```

---

## PASO 5: Ejecutar Order Service en Docker

```bash
# docker run = crear y arrancar un contenedor a partir de una imagen
#
# Parámetros explicados uno por uno:
#   -d                            Detached: corre en background (no bloquea tu terminal)
#   --name order-service          Le pone nombre al contenedor (sin esto, Docker le pone uno random)
#   -p 8083:8083                  Puerto: tu_PC:8083 se redirige al contenedor:8083
#                                 Así puedes hacer curl localhost:8083 desde tu PC
#   -e VARIABLE=valor             Pasa una variable de entorno al contenedor
#                                 Spring Boot las lee automáticamente
#   order-service:1.0             La imagen que vas a usar

# ---- Si product-service está en K8s (puerto 30082): ----
docker run -d --name order-service \
  -p 8083:8083 \
  -e PRODUCT_SERVICE_URL=http://host.docker.internal:30082 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  -e DDL_AUTO=update \
  order-service:1.0

# ---- Si product-service está en Docker (puerto 8082): ----
docker run -d --name order-service \
  -p 8083:8083 \
  -e PRODUCT_SERVICE_URL=http://host.docker.internal:8082 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  -e DDL_AUTO=update \
  order-service:1.0
```

### ¿Qué es host.docker.internal?
```
PROBLEMA:
  Dentro de un contenedor, "localhost" se refiere AL PROPIO CONTENEDOR.
  Si pones DB_URL=jdbc:postgresql://localhost:5435/orderdb
  el contenedor busca PostgreSQL DENTRO de sí mismo, y no lo encuentra.

SOLUCIÓN:
  "host.docker.internal" es una dirección especial de Docker que apunta
  a TU PC (el host). Así el contenedor puede alcanzar los puertos de tu PC.

  Dentro del contenedor:
    localhost           → el propio contenedor (NO tu PC)
    host.docker.internal → tu PC (donde corren las BD y otros servicios)
```

### ¿Qué es DDL_AUTO=update?
```
DDL = Data Definition Language (CREATE TABLE, ALTER TABLE, etc.)

Opciones de ddl-auto:
  - none     → No toca la BD. Tú creas las tablas manualmente.
  - validate → Verifica que las tablas coinciden con las entidades JPA. Error si no.
  - update   → Crea tablas si no existen, agrega columnas nuevas. NO borra nada.
  - create   → BORRA todas las tablas y las recrea cada vez que arranca.

Usamos "update" en desarrollo: la primera vez crea las tablas,
las siguientes veces verifica y solo agrega lo nuevo.
```

### Docker Desktop - Pestaña CONTAINERS
```
Abre Docker Desktop > Containers

Ahora deberías ver 4 contenedores corriendo:
  - postgres-user       (Running)
  - postgres-product    (Running)
  - postgres-order      (Running)
  - order-service       (Running)  ← NUEVO

Haz clic en "order-service" para explorar:

  TAB "Logs":
    Los logs de Spring Boot arrancando. Busca estas líneas:
    ✅ "Tomcat started on port 8083" → el servidor web arrancó
    ✅ "Started OrderServiceApplication in X seconds" → TODO OK
    ❌ "Connection refused" → no puede conectarse a la BD
    ❌ "Table not found" → DDL_AUTO no está en "update"

  TAB "Inspect":
    Verás las variables de entorno que pasaste con -e:
    - PRODUCT_SERVICE_URL=http://host.docker.internal:30082
    - DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb
    Útil para verificar que pasaste los valores correctos.

  TAB "Terminal":
    Abre una shell dentro del contenedor:
    $ ls /app/                → verás order-service.jar
    $ env | grep PRODUCT      → verás la URL del product-service
    $ java -version           → verás Java 21

  TAB "Stats":
    Monitorea CPU y RAM en tiempo real.
    Spring Boot típicamente usa 200-400MB de RAM.
```

### Esperar ~20 segundos y verificar:
```bash
# -s = "silent" = no mostrar la barra de progreso de curl
# Si responde, el servicio ya arrancó correctamente
curl -s http://localhost:8083/api/orders/health

# Output esperado:
# Order Service running with Clean Architecture!
#
# Si dice "Connection refused" → el contenedor aún está arrancando (espera más)
# Si después de 60s sigue fallando → revisa los logs en Docker Desktop
```

---

## PASO 6: Validar el flujo completo

### 6.1 Health Check
```bash
# GET simple para verificar que el servicio responde
curl -s http://localhost:8083/api/orders/health

# Esperado: Order Service running with Clean Architecture!
```

### 6.2 Crear una orden (el test más importante)
```bash
# -X POST = método HTTP POST (por defecto curl usa GET)
# -H "Content-Type: application/json" = le dice al servidor que el body es JSON
# -d '{...}' = el body de la petición (los datos que enviamos)
#
# Este endpoint hace TODO el flujo:
#   1. Recibe userId + lista de items
#   2. Para CADA item, llama a Product Service (HTTP GET) para validar el producto
#   3. Obtiene el precio actual del producto
#   4. Calcula subtotal = quantity x unitPrice
#   5. Calcula totalAmount = suma de subtotals
#   6. Genera número de orden (ORD-2026-XXX)
#   7. Guarda todo en la BD (tabla orders + order_items)
#   8. Devuelve la orden creada con status 201
curl -s -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": [{"productId": 1, "quantity": 2}, {"productId": 3, "quantity": 1}]}'
```

Respuesta esperada (201 Created):
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
      "product": { "id": 3, "name": "Teclado Mecánico Keychron K8", "price": 89.99 },
      "quantity": 1,
      "unitPrice": 89.99,
      "subtotal": 89.99
    }
  ],
  "createdAt": "2026-02-20T...",
  "updatedAt": "2026-02-20T..."
}
```

### Docker Desktop - Pestaña CONTAINERS > order-service > Logs
```
Después de crear la orden, revisa los logs. Deberías ver la COMUNICACIÓN ENTRE SERVICIOS:

  INFO  REST request to create order for userId: 1
  INFO  Calling Product Service to get product with id: 1        ← Llama a product-service
  INFO  Product retrieved successfully: ProductDto(id=1, ...)    ← Respuesta OK
  INFO  Calling Product Service to get product with id: 3        ← Llama de nuevo
  INFO  Product retrieved successfully: ProductDto(id=3, ...)    ← Respuesta OK
  INFO  Order created successfully with number: ORD-2026-001     ← Guardado en BD

Esto demuestra que order-service se comunicó con product-service via HTTP.
```

### 6.3 Obtener todas las órdenes
```bash
# GET sin parámetros = devuelve todas las órdenes de la BD
# Cada orden incluye sus items, y cada item tiene info del producto
# (obtenida en tiempo real desde Product Service)
curl -s http://localhost:8083/api/orders
```

### 6.4 Obtener orden por ID
```bash
# {id} en la URL = path variable
# Busca la orden con id=1 en la BD
# Si no existe, devuelve 404
curl -s http://localhost:8083/api/orders/1
```

### 6.5 Obtener órdenes por usuario
```bash
# Filtra órdenes por userId
# Útil para: "mostrar las órdenes de este usuario en su perfil"
curl -s http://localhost:8083/api/orders/user/1
```

### 6.6 Error: producto inexistente
```bash
# productId 9999 no existe en Product Service
# Order Service llama a Product Service, recibe 404, y devuelve 503
# 503 = Service Unavailable (el servicio externo no pudo resolver)
curl -s -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": [{"productId": 9999, "quantity": 1}]}'

# Esperado:
# {"status":503,"message":"Product not found with id: 9999"}
```

### 6.7 Error: items vacíos
```bash
# Enviar items: [] viola la validación @NotEmpty del DTO
# Spring Boot rechaza la petición ANTES de llegar al use case
# 400 = Bad Request (datos inválidos)
curl -s -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": []}'

# Esperado:
# {"status":400,"message":"Validation failed","errors":{"items":"Order must have at least one item"}}
```

### 6.8 Error: orden inexistente
```bash
# Buscar orden con id=9999 que no existe
# 404 = Not Found
curl -s http://localhost:8083/api/orders/9999

# Esperado:
# {"status":404,"message":"Order not found with id: 9999"}
```

---

## PASO 7: Detener y limpiar (cuando termines)

```bash
# docker stop = envía señal SIGTERM al contenedor (cierre limpio)
# Spring Boot recibe la señal y cierra conexiones a BD antes de apagarse
docker stop order-service

# docker rm = elimina el contenedor detenido
# Sin esto, el nombre "order-service" queda ocupado y no puedes crear otro con el mismo nombre
# NOTA: solo borra el contenedor, NO la imagen ni los datos de la BD
docker rm order-service
```

### Docker Desktop - Pestaña CONTAINERS
```
Después de "docker stop":
  → El contenedor aparece en gris (Exited)
  → Ya no consume CPU/RAM

Después de "docker rm":
  → Desaparece de la lista

IMPORTANTE:
  - La IMAGEN order-service:1.0 SIGUE en la pestaña Images
    (puedes crear otro contenedor con "docker run" cuando quieras)
  - Los DATOS de orderdb SIGUEN en el volumen postgres-order-data
    (las órdenes que creaste no se pierden)
```

### Si quieres limpiar todo (BD incluida):
```bash
# docker-compose down = detiene y elimina los contenedores definidos en docker-compose.yml
# Solo afecta a los contenedores de BD (postgres-user, postgres-product, postgres-order)
docker-compose down

# docker-compose down -v = ADEMÁS elimina los volúmenes (BORRA TODOS LOS DATOS)
# ⚠️ CUIDADO: Esto elimina todas las tablas y datos de las 3 bases de datos
docker-compose down -v
```

### Docker Desktop - Pestaña VOLUMES
```
Después de "docker-compose down" (sin -v):
  → Contenedores desaparecen, volúmenes SIGUEN (datos conservados)

Después de "docker-compose down -v":
  → Volúmenes también eliminados (datos PERDIDOS)
  → Próximo "docker-compose up" → BD vacías, tablas se recrean con DDL_AUTO=update
```

---

## Resumen de Validación

| # | Test | Endpoint | Status | Qué valida |
|---|------|----------|--------|------------|
| 1 | Health Check | GET /api/orders/health | 200 | Servicio arrancó correctamente |
| 2 | Crear Orden | POST /api/orders | 201 | Comunicación HTTP con Product Service + cálculo automático de totales |
| 3 | Listar Órdenes | GET /api/orders | 200 | Lectura de BD + enriquecimiento con Product Service |
| 4 | Orden por ID | GET /api/orders/{id} | 200 | Búsqueda individual + manejo de relación 1:N con items |
| 5 | Órdenes por Usuario | GET /api/orders/user/{userId} | 200 | Filtrado por campo foráneo |
| 6 | Producto inválido | POST /api/orders | 503 | Manejo de error cuando Product Service devuelve 404 |
| 7 | Items vacíos | POST /api/orders | 400 | Validación de request con Bean Validation (@NotEmpty) |
| 8 | Orden no existe | GET /api/orders/9999 | 404 | Manejo de excepción OrderNotFoundException |

---

## Resumen: Qué verificar en cada pestaña de Docker Desktop

| Momento | Pestaña | Qué buscar |
|---------|---------|------------|
| Después de `docker-compose up` | **Containers** | 3 postgres corriendo (punto verde) |
| Después de `docker-compose up` | **Volumes** | 3 volúmenes de datos creados |
| Después de `docker build` | **Builds** | Build completado (verde), clic para ver layers |
| Después de `docker build` | **Images** | order-service:1.0 aparece (~393MB) |
| Después de `docker run` | **Containers** | order-service corriendo (punto verde) |
| Después de `docker run` | **Containers > Logs** | "Started OrderServiceApplication in X seconds" |
| Después de curl POST | **Containers > Logs** | "Calling Product Service to get product..." |
| Después de `docker stop` | **Containers** | order-service en gris (Exited) |
| Después de `docker rm` | **Containers** | order-service desaparece |
| Después de `docker-compose down -v` | **Volumes** | Volúmenes eliminados |
