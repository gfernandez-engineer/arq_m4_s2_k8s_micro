# ORDER SERVICE - Validacion con Docker

Guia paso a paso para compilar, dockerizar y validar el flujo completo del Order Service.
Cada comando tiene una explicacion de **que hace** y **por que lo usamos**.

> **NOTA:** Los comandos curl se muestran en 2 formatos: **CMD** y **PowerShell**.
> Usa el que corresponda a tu terminal.

---

## Pestanas de Docker Desktop - Guia rapida

```
Docker Desktop tiene 5 pestanas principales:

+------------------------------------------------------------------+
| Containers | Images | Volumes | Builds | Kubernetes               |
+------------------------------------------------------------------+

ANALOGIA CON LA VIDA REAL:

- Images     = RECETAS de cocina (instrucciones para crear algo)
- Containers = PLATOS servidos (la receta ejecutandose)
- Volumes    = REFRIGERADOR (datos que persisten aunque tires el plato)
- Builds     = HISTORIAL de cocina (cuando preparaste cada receta)
- Kubernetes = CHEF AUTOMATICO (gestiona multiples platos a la vez)

REGLA CLAVE:
  Imagen -> es una PLANTILLA (no consume recursos, no corre)
  Contenedor -> es una INSTANCIA corriendo de esa imagen (consume CPU/RAM)

  De 1 imagen puedes crear N contenedores.
  Es como de 1 receta puedes hacer muchos platos.
```

---

## PASO 1: Levantar las bases de datos

```bash
# docker-compose up = lee el archivo docker-compose.yml y crea los contenedores definidos ahi
# -d = "detached" = ejecutar en segundo plano (no bloquea tu terminal)
# Sin -d, los logs se muestran en tu terminal y si cierras la terminal, se detienen los contenedores
docker-compose up -d
```

### Verificar en terminal:
```bash
# docker ps = "process status" = lista contenedores corriendo
# --format = personalizar que columnas mostrar (sin esto muestra DEMASIADA info)
# {{.Names}} = nombre del contenedor
# {{.Status}} = si esta corriendo, cuanto tiempo lleva, si esta "healthy"
# {{.Ports}} = que puertos estan mapeados
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Output esperado:
# NAMES              STATUS                  PORTS
# postgres-user      Up X minutes (healthy)  0.0.0.0:5434->5432/tcp
# postgres-product   Up X minutes (healthy)  0.0.0.0:5433->5432/tcp
# postgres-order     Up X minutes (healthy)  0.0.0.0:5435->5432/tcp
#
# Que significa "0.0.0.0:5435->5432/tcp"?
#   Tu PC:5435 se redirige al contenedor:5432
#   PostgreSQL SIEMPRE corre en 5432 dentro del contenedor
#   Pero en tu PC cada BD usa un puerto diferente para no chocar
```

### Docker Desktop - Pestana CONTAINERS
```
Abre Docker Desktop > Containers

Deberias ver 3 contenedores con status "Running" (punto verde):
  - postgres-user      (puerto 5434)
  - postgres-product   (puerto 5433)
  - postgres-order     (puerto 5435)

Haz clic en "postgres-order" para explorar las sub-pestanas:
  - Logs    : Mensajes del contenedor ("database system is ready to accept connections")
  - Inspect : Configuracion completa (variables de entorno, red, volumenes)
  - Terminal: Abre una shell DENTRO del contenedor (como SSH pero local)
  - Stats   : CPU y RAM que esta usando en tiempo real
```

### Docker Desktop - Pestana VOLUMES
```
Abre Docker Desktop > Volumes

Deberias ver 3 volumenes:
  - arq_m4_s2_k8s_micro_postgres-user-data
  - arq_m4_s2_k8s_micro_postgres-product-data
  - arq_m4_s2_k8s_micro_postgres-order-data

QUE ES UN VOLUMEN?
  Un contenedor es TEMPORAL: si lo borras, todo lo de adentro desaparece.
  Un volumen es un DISCO EXTERNO que se conecta al contenedor.
  PostgreSQL guarda sus datos en el volumen, no dentro del contenedor.

  Asi puedes borrar y recrear el contenedor postgres-order,
  y al reconectarlo al volumen, tus datos siguen ahi.

  PELIGRO: Si borras el VOLUMEN (docker-compose down -v), pierdes los datos.
```

---

## PASO 2: Verificar que Product Service esta disponible

Order Service necesita hablar con Product Service para validar productos.
Si Product Service no esta corriendo, el POST de crear orden fallara.

#### En CMD:
```cmd
REM curl = herramienta para hacer peticiones HTTP desde terminal
REM Es como un "navegador de texto" - manda la peticion y muestra la respuesta

REM Si product-service corre en K8s (NodePort 30082):
curl http://localhost:30082/api/products/health

REM Si product-service corre en Docker (puerto 8082):
curl http://localhost:8082/api/products/health
```

#### En PowerShell:
```powershell
# Invoke-RestMethod = cmdlet de PowerShell para hacer peticiones HTTP
# -Uri = la URL a la que queremos hacer la peticion

# Si product-service corre en K8s (NodePort 30082):
Invoke-RestMethod -Uri http://localhost:30082/api/products/health

# Si product-service corre en Docker (puerto 8082):
Invoke-RestMethod -Uri http://localhost:8082/api/products/health
```

**Output esperado:**
```
Product Service running with Clean Architecture!

Si dice "Connection refused" -> Product Service no esta corriendo
Si dice "timeout" -> el puerto es incorrecto
```

> IMPORTANTE: Anota en que puerto responde Product Service (8082 o 30082).
> Lo necesitaras en el PASO 5 para la variable PRODUCT_SERVICE_URL.

---

## PASO 3: Compilar Order Service

```bash
# cd = "change directory" = moverse a la carpeta del proyecto
cd order-service

# mvn = Maven, herramienta de build de Java (como npm para JavaScript)
# clean = borrar compilaciones anteriores (carpeta target/)
# package = compilar + empaquetar en un JAR (Java ARchive)
# -DskipTests = no ejecutar tests (mas rapido para desarrollo)
#
# El resultado es un archivo .jar que contiene TODA tu aplicacion
# lista para ejecutarse con "java -jar archivo.jar"
mvn clean package -DskipTests
```

Verificar que se genero el JAR:

#### En CMD:
```cmd
REM dir = listar archivos en Windows
dir target\order-service-0.0.1-SNAPSHOT.jar
```

#### En PowerShell:
```powershell
# ls (alias de Get-ChildItem) = listar archivos
ls target/order-service-0.0.1-SNAPSHOT.jar
```

**Output esperado:** Un archivo de ~57MB. Si no aparece, el build fallo.

---

## PASO 4: Construir la imagen Docker

```bash
# docker build = crear una imagen Docker leyendo el Dockerfile
# -t order-service:1.0 = "tag" = nombre:version de la imagen
#   - order-service = nombre de la imagen
#   - 1.0 = version (puedes usar cualquier texto: 1.0, latest, v2, etc.)
# . = directorio actual (donde esta el Dockerfile)
#
# Que hace internamente?
#   1. Lee el Dockerfile
#   2. Descarga la imagen base (eclipse-temurin:21-jre-alpine) si no la tiene
#   3. Copia tu JAR dentro de la imagen
#   4. Guarda la imagen en tu maquina local
docker build -t order-service:1.0 .
```

Verificar que la imagen se creo:
```bash
# docker images = listar todas las imagenes locales
# grep = filtrar lineas que contengan "order-service"
docker images | grep order-service

# Output esperado:
# order-service  1.0  abc123def456  10 seconds ago  393MB
#
# La imagen PESA 393MB porque incluye:
#   - Linux Alpine (~5MB) - sistema operativo minimo
#   - Java 21 JRE (~330MB) - runtime de Java
#   - Tu JAR (~57MB) - tu aplicacion
```

### Docker Desktop - Pestana BUILDS
```
Abre Docker Desktop > Builds

Veras el build que acabas de ejecutar:
  - Estado: Completed (verde)
  - Duracion: ~10-15 segundos
  - Imagen: order-service:1.0

Haz clic en el build para ver los PASOS (layers):
  1. FROM eclipse-temurin:21-jre-alpine  -> descarga imagen base
  2. WORKDIR /app                        -> crea carpeta /app
  3. COPY target/*.jar ...               -> copia tu JAR

QUE SON LAS LAYERS (CAPAS)?
  Cada instruccion del Dockerfile crea una "capa".
  Docker CACHEA cada capa. Si cambias solo tu JAR (paso 3),
  Docker reutiliza las capas 1 y 2 del cache.
  Por eso el segundo build es mucho mas rapido que el primero.
```

### Docker Desktop - Pestana IMAGES
```
Abre Docker Desktop > Images

Veras la nueva imagen:
  - order-service    Tag: 1.0    Size: ~393MB

Haz clic en order-service:1.0 para ver:
  - Layers: Las capas que componen la imagen (mismas del build)
  - Packages: Librerias incluidas
  - Vulnerabilities: Escaneo de seguridad (si esta habilitado)

RECUERDA: La imagen NO consume CPU/RAM. Es solo una plantilla en disco.
No "corre" nada. Solo cuando haces "docker run" se crea un contenedor.
```

---

## PASO 5: Ejecutar Order Service en Docker

```bash
# docker run = crear y arrancar un contenedor a partir de una imagen
#
# Parametros explicados uno por uno:
#   -d                            Detached: corre en background (no bloquea tu terminal)
#   --name order-service          Le pone nombre al contenedor (sin esto, Docker le pone uno random)
#   -p 8083:8083                  Puerto: tu_PC:8083 se redirige al contenedor:8083
#                                 Asi puedes hacer curl localhost:8083 desde tu PC
#   -e VARIABLE=valor             Pasa una variable de entorno al contenedor
#                                 Spring Boot las lee automaticamente
#   order-service:1.0             La imagen que vas a usar
```

#### En CMD:
```cmd
REM ---- Si product-service esta en K8s (puerto 30082): ----
docker run -d --name order-service -p 8083:8083 -e PRODUCT_SERVICE_URL=http://host.docker.internal:30082 -e DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb -e DB_USERNAME=postgres -e DB_PASSWORD=postgres -e DDL_AUTO=update order-service:1.0

REM ---- Si product-service esta en Docker (puerto 8082): ----
docker run -d --name order-service -p 8083:8083 -e PRODUCT_SERVICE_URL=http://host.docker.internal:8082 -e DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb -e DB_USERNAME=postgres -e DB_PASSWORD=postgres -e DDL_AUTO=update order-service:1.0
```

#### En PowerShell:
```powershell
# ---- Si product-service esta en K8s (puerto 30082): ----
docker run -d --name order-service `
  -p 8083:8083 `
  -e PRODUCT_SERVICE_URL=http://host.docker.internal:30082 `
  -e DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb `
  -e DB_USERNAME=postgres `
  -e DB_PASSWORD=postgres `
  -e DDL_AUTO=update `
  order-service:1.0

# ---- Si product-service esta en Docker (puerto 8082): ----
docker run -d --name order-service `
  -p 8083:8083 `
  -e PRODUCT_SERVICE_URL=http://host.docker.internal:8082 `
  -e DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb `
  -e DB_USERNAME=postgres `
  -e DB_PASSWORD=postgres `
  -e DDL_AUTO=update `
  order-service:1.0
```

### 5.1 Si sale error: "container name is already in use"

Significa que ya existe un contenedor con ese nombre. Debes eliminarlo primero:

```bash
# Detener el contenedor (si esta corriendo)
docker stop order-service

# Eliminar el contenedor detenido
docker rm order-service

# O forzar eliminacion (si esta corriendo o quedo trabado)
docker rm -f order-service

# Verificar que ya no aparece
docker ps -a
```

Importante: esto elimina el contenedor, pero NO la imagen.
Ahora si puedes volver a ejecutar `docker run ...`

### Que es host.docker.internal?
```
PROBLEMA:
  Dentro de un contenedor, "localhost" se refiere AL PROPIO CONTENEDOR.
  Si pones DB_URL=jdbc:postgresql://localhost:5435/orderdb
  el contenedor busca PostgreSQL DENTRO de si mismo, y no lo encuentra.

SOLUCION:
  "host.docker.internal" es una direccion especial de Docker que apunta
  a TU PC (el host). Asi el contenedor puede alcanzar los puertos de tu PC.

  Dentro del contenedor:
    localhost           -> el propio contenedor (NO tu PC)
    host.docker.internal -> tu PC (donde corren las BD y otros servicios)
```

### Que es DDL_AUTO=update?
```
DDL = Data Definition Language (CREATE TABLE, ALTER TABLE, etc.)

Opciones de ddl-auto:
  - none     -> No toca la BD. Tu creas las tablas manualmente.
  - validate -> Verifica que las tablas coinciden con las entidades JPA. Error si no.
  - update   -> Crea tablas si no existen, agrega columnas nuevas. NO borra nada.
  - create   -> BORRA todas las tablas y las recrea cada vez que arranca.

Usamos "update" en desarrollo: la primera vez crea las tablas,
las siguientes veces verifica y solo agrega lo nuevo.
```

### Docker Desktop - Pestana CONTAINERS
```
Abre Docker Desktop > Containers

Ahora deberias ver 4 contenedores corriendo:
  - postgres-user       (Running)
  - postgres-product    (Running)
  - postgres-order      (Running)
  - order-service       (Running)  <- NUEVO

Haz clic en "order-service" para explorar:

  TAB "Logs":
    Los logs de Spring Boot arrancando. Busca estas lineas:
    "Tomcat started on port 8083" -> el servidor web arranco
    "Started OrderServiceApplication in X seconds" -> TODO OK
    "Connection refused" -> no puede conectarse a la BD
    "Table not found" -> DDL_AUTO no esta en "update"

  TAB "Inspect":
    Veras las variables de entorno que pasaste con -e:
    - PRODUCT_SERVICE_URL=http://host.docker.internal:30082
    - DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb
    Util para verificar que pasaste los valores correctos.

  TAB "Terminal":
    Abre una shell dentro del contenedor:
    $ ls /app/                -> veras order-service.jar
    $ env | grep PRODUCT      -> veras la URL del product-service
    $ java -version           -> veras Java 21

  TAB "Stats":
    Monitorea CPU y RAM en tiempo real.
    Spring Boot tipicamente usa 200-400MB de RAM.
```

### Esperar ~20 segundos y verificar:

#### En CMD:
```cmd
REM -s = "silent" = no mostrar la barra de progreso de curl
curl -s http://localhost:8083/api/orders/health
```

#### En PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:8083/api/orders/health
```

**Output esperado:**
```
Order Service running with Clean Architecture!

Si dice "Connection refused" -> el contenedor aun esta arrancando (espera mas)
Si despues de 60s sigue fallando -> revisa los logs en Docker Desktop
```

---

## PASO 6: Validar el flujo completo

### 6.1 Health Check

#### En CMD:
```cmd
curl -s http://localhost:8083/api/orders/health
```

#### En PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:8083/api/orders/health
```

**Esperado:** `Order Service running with Clean Architecture!`

---

### 6.2 Crear una orden (el test mas importante)

```
Este endpoint hace TODO el flujo (PUNTO 3 OPCIONAL de la tarea):
  1. Recibe userId + lista de items
  2. Para CADA item, llama a Product Service (HTTP GET) para VALIDAR el producto
  3. Obtiene el PRECIO ACTUAL del producto desde Product Service
  4. Calcula subtotal = quantity x unitPrice
  5. Calcula totalAmount = suma de subtotals
  6. Genera numero de orden (ORD-2026-XXX)
  7. Guarda todo en la BD (tabla orders + order_items)
  8. Devuelve la orden creada con status 201
```

#### En CMD:
```cmd
REM -X POST = metodo HTTP POST (por defecto curl usa GET)
REM -H = header (cabecera HTTP, le dice al servidor que el body es JSON)
REM -d = data (cuerpo de la peticion en JSON)
REM En CMD se usan comillas dobles escapadas con \"
curl.exe -s -X POST http://localhost:8083/api/orders -H "Content-Type: application/json" -d "{\"userId\": 1, \"items\": [{\"productId\": 1, \"quantity\": 2}, {\"productId\": 3, \"quantity\": 1}]}"
```

#### En PowerShell:
```powershell
# Invoke-RestMethod = cmdlet nativo de PowerShell para HTTP
# -Uri = URL destino
# -Method POST = metodo HTTP
# -Headers = cabeceras HTTP (hashtable @{})
# -Body = cuerpo de la peticion en JSON
Invoke-RestMethod -Uri http://localhost:8083/api/orders `
  -Method POST `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body '{"userId": 1, "items": [{"productId": 1, "quantity": 2}, {"productId": 3, "quantity": 1}]}'
```

**Respuesta esperada (201 Created):**
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
  "createdAt": "2026-02-20T...",
  "updatedAt": "2026-02-20T..."
}
```

### Docker Desktop - Pestana CONTAINERS > order-service > Logs
```
Despues de crear la orden, revisa los logs. Deberias ver la COMUNICACION ENTRE SERVICIOS:

  INFO  REST request to create order for userId: 1
  INFO  Calling Product Service to get product with id: 1        <- Llama a product-service
  INFO  Product retrieved successfully: ProductDto(id=1, ...)    <- Respuesta OK
  INFO  Calling Product Service to get product with id: 3        <- Llama de nuevo
  INFO  Product retrieved successfully: ProductDto(id=3, ...)    <- Respuesta OK
  INFO  Order created successfully with number: ORD-2026-001     <- Guardado en BD

Esto demuestra que order-service se comunico con product-service via HTTP.
```

---

### 6.3 Obtener todas las ordenes

#### En CMD:
```cmd
REM GET sin parametros = devuelve todas las ordenes de la BD
REM Cada orden incluye sus items, y cada item tiene info del producto
curl -s http://localhost:8083/api/orders
```

#### En PowerShell:
```powershell
# GET es el metodo por defecto de Invoke-RestMethod
Invoke-RestMethod -Uri http://localhost:8083/api/orders
```

---

### 6.4 Obtener orden por ID

#### En CMD:
```cmd
REM {id} en la URL = path variable
REM Busca la orden con id=1 en la BD. Si no existe, devuelve 404
curl -s http://localhost:8083/api/orders/1
```

#### En PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:8083/api/orders/1
```

---

### 6.5 Obtener ordenes por usuario

#### En CMD:
```cmd
REM Filtra ordenes por userId
REM Util para: "mostrar las ordenes de este usuario en su perfil"
curl -s http://localhost:8083/api/orders/user/1
```

#### En PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:8083/api/orders/user/1
```

---

### 6.6 Error: producto inexistente

#### En CMD:
```cmd
REM productId 9999 no existe en Product Service
REM Order Service llama a Product Service, recibe 404, y devuelve 503
REM 503 = Service Unavailable (el servicio externo no pudo resolver)
curl.exe -s -X POST http://localhost:8083/api/orders -H "Content-Type: application/json" -d "{\"userId\": 1, \"items\": [{\"productId\": 9999, \"quantity\": 1}]}"
```

#### En PowerShell:
```powershell
# productId 9999 no existe en Product Service
# Order Service llama a Product Service, recibe 404, y devuelve 503
# 503 = Service Unavailable (el servicio externo no pudo resolver)
Invoke-RestMethod -Uri http://localhost:8083/api/orders `
  -Method POST `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body '{"userId": 1, "items": [{"productId": 9999, "quantity": 1}]}'
```

**Esperado:** `{"status":503,"message":"Product not found with id: 9999"}`

---

### 6.7 Error: items vacios

#### En CMD:
```cmd
REM Enviar items: [] viola la validacion @NotEmpty del DTO
REM Spring Boot rechaza la peticion ANTES de llegar al use case
REM 400 = Bad Request (datos invalidos)
curl.exe -s -X POST http://localhost:8083/api/orders -H "Content-Type: application/json" -d "{\"userId\": 1, \"items\": []}"
```

#### En PowerShell:
```powershell
# Enviar items: [] viola la validacion @NotEmpty del DTO
# Spring Boot rechaza la peticion ANTES de llegar al use case
# 400 = Bad Request (datos invalidos)
Invoke-RestMethod -Uri http://localhost:8083/api/orders `
  -Method POST `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body '{"userId": 1, "items": []}'
```

**Esperado:** `{"status":400,"message":"Validation failed","errors":{"items":"Order must have at least one item"}}`

---

### 6.8 Error: orden inexistente

#### En CMD:
```cmd
REM Buscar orden con id=9999 que no existe
REM 404 = Not Found
curl -s http://localhost:8083/api/orders/9999
```

#### En PowerShell:
```powershell
# Buscar orden con id=9999 que no existe
# 404 = Not Found
Invoke-RestMethod -Uri http://localhost:8083/api/orders/9999
```

**Esperado:** `{"status":404,"message":"Order not found with id: 9999"}`

---

## PASO 7: Detener y limpiar (cuando termines)

```bash
# docker stop = envia senal SIGTERM al contenedor (cierre limpio)
# Spring Boot recibe la senal y cierra conexiones a BD antes de apagarse
docker stop order-service

# docker rm = elimina el contenedor detenido
# Sin esto, el nombre "order-service" queda ocupado y no puedes crear otro con el mismo nombre
# NOTA: solo borra el contenedor, NO la imagen ni los datos de la BD
docker rm order-service
```

### Docker Desktop - Pestana CONTAINERS
```
Despues de "docker stop":
  -> El contenedor aparece en gris (Exited)
  -> Ya no consume CPU/RAM

Despues de "docker rm":
  -> Desaparece de la lista

IMPORTANTE:
  - La IMAGEN order-service:1.0 SIGUE en la pestana Images
    (puedes crear otro contenedor con "docker run" cuando quieras)
  - Los DATOS de orderdb SIGUEN en el volumen postgres-order-data
    (las ordenes que creaste no se pierden)
```

### Si quieres limpiar todo (BD incluida):
```bash
# docker-compose down = detiene y elimina los contenedores definidos en docker-compose.yml
# Solo afecta a los contenedores de BD (postgres-user, postgres-product, postgres-order)
docker-compose down

# docker-compose down -v = ADEMAS elimina los volumenes (BORRA TODOS LOS DATOS)
# CUIDADO: Esto elimina todas las tablas y datos de las 3 bases de datos
docker-compose down -v
```

### Docker Desktop - Pestana VOLUMES
```
Despues de "docker-compose down" (sin -v):
  -> Contenedores desaparecen, volumenes SIGUEN (datos conservados)

Despues de "docker-compose down -v":
  -> Volumenes tambien eliminados (datos PERDIDOS)
  -> Proximo "docker-compose up" -> BD vacias, tablas se recrean con DDL_AUTO=update
```

---

## Resumen de Validacion

| # | Test | Endpoint | Status | Que valida |
|---|------|----------|--------|------------|
| 1 | Health Check | GET /api/orders/health | 200 | Servicio arranco correctamente |
| 2 | Crear Orden | POST /api/orders | 201 | Comunicacion HTTP con Product Service + calculo automatico de totales (Punto 3 Opcional) |
| 3 | Listar Ordenes | GET /api/orders | 200 | Lectura de BD + enriquecimiento con Product Service |
| 4 | Orden por ID | GET /api/orders/{id} | 200 | Busqueda individual + manejo de relacion 1:N con items |
| 5 | Ordenes por Usuario | GET /api/orders/user/{userId} | 200 | Filtrado por campo foraneo |
| 6 | Producto invalido | POST /api/orders | 503 | Manejo de error cuando Product Service devuelve 404 |
| 7 | Items vacios | POST /api/orders | 400 | Validacion de request con Bean Validation (@NotEmpty) |
| 8 | Orden no existe | GET /api/orders/9999 | 404 | Manejo de excepcion OrderNotFoundException |

---

## Resumen: Que verificar en cada pestana de Docker Desktop

| Momento | Pestana | Que buscar |
|---------|---------|------------|
| Despues de `docker-compose up` | **Containers** | 3 postgres corriendo (punto verde) |
| Despues de `docker-compose up` | **Volumes** | 3 volumenes de datos creados |
| Despues de `docker build` | **Builds** | Build completado (verde), clic para ver layers |
| Despues de `docker build` | **Images** | order-service:1.0 aparece (~393MB) |
| Despues de `docker run` | **Containers** | order-service corriendo (punto verde) |
| Despues de `docker run` | **Containers > Logs** | "Started OrderServiceApplication in X seconds" |
| Despues de curl POST | **Containers > Logs** | "Calling Product Service to get product..." |
| Despues de `docker stop` | **Containers** | order-service en gris (Exited) |
| Despues de `docker rm` | **Containers** | order-service desaparece |
| Despues de `docker-compose down -v` | **Volumes** | Volumenes eliminados |
