# ORDER SERVICE - Despliegue en Kubernetes

Guia paso a paso para desplegar Order Service en Kubernetes usando Docker Desktop.
Cada comando tiene una explicacion de **que hace** y **por que lo usamos**.

> **PRE-REQUISITO:** Haber completado [README_01_DOCKER.md](README_01_DOCKER.md) (imagen order-service:1.0 ya construida)

> **NOTA:** Los comandos curl se muestran en 2 formatos: **CMD** y **PowerShell**.
> Usa el que corresponda a tu terminal.

---

## Conceptos clave de Kubernetes

```
ANALOGIA: Docker vs Kubernetes

  Docker = Un COCINERO que sabe preparar UN plato a la vez.
           Tu le dices: "prepara esto" y "sirvelo en este plato".
           Si se cae el plato, tu tienes que volver a pedirlo.

  Kubernetes = Un CHEF JEFE que gestiona una COCINA COMPLETA.
               Tu le dices: "quiero 3 platos de pasta siempre listos".
               Si un plato se cae, el automaticamente prepara otro.
               Si llegan mas clientes, el anade mas cocineros.

En terminos tecnicos:
  Docker      -> Ejecuta contenedores individuales
  Kubernetes  -> Orquesta multiples contenedores, los mantiene vivos,
                los escala, y gestiona la red entre ellos
```

### Recursos de Kubernetes que vamos a crear:

```
+--------------------------------------------------------------------+
|  Tu PC (localhost)                                                  |
|                                                                     |
|  +--------------------------------------------------------------+  |
|  | Cluster Kubernetes (Docker Desktop)                           |  |
|  |                                                               |  |
|  |  Namespace: order-service                                     |  |
|  |  +----------------------------------------------------------+ |  |
|  |  |                                                          | |  |
|  |  |  ConfigMap          Secret          Deployment           | |  |
|  |  |  (config no         (datos          (gestiona pods)      | |  |
|  |  |   sensible)          sensibles)          |               | |  |
|  |  |       |                |                 v               | |  |
|  |  |       +--------+------+           +----------+          | |  |
|  |  |                |                  |   Pod    |          | |  |
|  |  |                v                  | (order-  |          | |  |
|  |  |         env vars inyectadas       |  service)|          | |  |
|  |  |                                   +----+-----+          | |  |
|  |  |                                        |                | |  |
|  |  |  Service (NodePort)                    |                | |  |
|  |  |  localhost:30083 ----------------------+                | |  |
|  |  |                                                          | |  |
|  |  +----------------------------------------------------------+ |  |
|  +--------------------------------------------------------------+  |
+--------------------------------------------------------------------+

Flujo de una peticion:
  curl localhost:30083 -> Service (NodePort) -> Pod (contenedor:8083)

Que es cada recurso?
  Namespace   = Carpeta que agrupa todo lo de un servicio (aislamiento)
  ConfigMap   = Archivo de configuracion (variables no sensibles)
  Secret      = Caja fuerte (contrasenas codificadas en Base64)
  Deployment  = "Quiero N replicas de este contenedor siempre corriendo"
  Pod         = La unidad minima: un contenedor corriendo
  Service     = Puerta de entrada al pod (red + balanceo de carga)
```

---

## PASO 1: Pre-requisitos

### 1.1 Verificar que Kubernetes esta habilitado

```bash
# kubectl = herramienta CLI para hablar con Kubernetes
# config use-context = seleccionar el cluster al que nos conectamos
# docker-desktop = el cluster K8s local que viene con Docker Desktop
#
# Si tienes multiples clusters (ej: uno en la nube), esto asegura
# que los comandos van al cluster correcto (tu PC local)
kubectl config use-context docker-desktop

# cluster-info = verificar que el cluster esta corriendo y accesible
# Si responde con una URL, el cluster funciona
# Si dice "refused", Kubernetes no esta habilitado en Docker Desktop
kubectl cluster-info

# Output esperado:
# Kubernetes control plane is running at https://127.0.0.1:6443
```

### Docker Desktop - Pestana KUBERNETES
```
Abre Docker Desktop > Kubernetes (icono de timon de barco)

QUE MUESTRA ESTA PESTANA?
  Una vista grafica de TODOS los recursos del cluster K8s.
  Es como "un explorador de archivos" pero para Kubernetes.

Antes de desplegar, deberias ver:
  - Namespaces existentes: default, user-service, product-service
  - Pods de otros servicios ya desplegados y corriendo

EQUIVALENTE EN TERMINAL:
  Esta pestana muestra lo mismo que ejecutar:
  kubectl get all --all-namespaces

CONSEJO: Manten esta pestana abierta mientras sigues la guia.
Asi ves en tiempo real como aparecen los recursos que vas creando.
```

### 1.2 Verificar que product-service esta en K8s

```bash
# kubectl get all = listar TODOS los recursos de K8s
# -n product-service = filtrar por namespace "product-service"
#   -n = "namespace" (sin esto, usa el namespace "default")
#
# Por que verificar esto?
#   Order Service NECESITA comunicarse con Product Service.
#   Si Product Service no esta corriendo, crear ordenes fallara.
kubectl get all -n product-service

# Deberias ver 3 tipos de recursos:
# NAME                                READY   STATUS    RESTARTS   AGE
# pod/product-service-xxxxx           1/1     Running   0          Xh
#   -> El POD esta corriendo (1/1 = 1 de 1 contenedores listos)
#
# NAME                      TYPE       CLUSTER-IP     PORT(S)        AGE
# service/product-service   NodePort   10.96.x.x      80:30082/TCP   Xh
#   -> El SERVICE expone el pod en puerto 30082
#
# NAME                              READY   UP-TO-DATE   AVAILABLE   AGE
# deployment.apps/product-service   1/1     1            1           Xh
#   -> El DEPLOYMENT gestiona 1 replica, 1 disponible
```

Probar que product-service responde:

#### En CMD:
```cmd
curl http://localhost:30082/api/products/health
```

#### En PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30082/api/products/health
```

**Esperado:** `Product Service running with Clean Architecture!`

### 1.3 Verificar que la BD orderdb esta corriendo

```bash
# Las BD corren en Docker (docker-compose), NO en Kubernetes
# Por eso usamos "docker ps" y no "kubectl"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep postgres-order

# Output esperado:
# postgres-order  Up X hours (healthy)  0.0.0.0:5435->5432/tcp
#
# "healthy" = el health check de PostgreSQL pasa (acepta conexiones)
```

### 1.4 Verificar que la imagen Docker existe

```bash
# Kubernetes necesita la imagen Docker para crear los pods
# Como usamos imagePullPolicy: Never, la imagen DEBE existir localmente
docker images | grep order-service

# Output esperado:
# order-service  1.0  abc123def456  X hours ago  393MB
#
# Si NO aparece -> debes construirla primero (ver README_01_DOCKER.md, Paso 4)
```

### Docker Desktop - Pestana IMAGES
```
Abre Docker Desktop > Images

Verifica que "order-service:1.0" aparece en la lista.
Kubernetes usara esta imagen LOCAL para crear los pods.

POR QUE ES IMPORTANTE?
  En el Deployment (paso 5) tenemos: imagePullPolicy: Never
  Esto le dice a Kubernetes:
    "Usa la imagen que ya tienes en tu maquina local.
     NO intentes descargarla de Docker Hub ni de internet."

  Si la imagen NO existe localmente, el pod quedara en estado
  "ErrImageNeverPull" y nunca arrancara.
```

---

## PASO 2: Crear el Namespace

```bash
# kubectl apply = crear o actualizar un recurso en Kubernetes
# -f = "file" = leer la definicion del recurso desde un archivo YAML
#
# Que es un Namespace?
#   Es como una CARPETA que agrupa todos los recursos de un servicio.
#   Cada microservicio tiene su propio namespace para:
#     - Aislar recursos (no se mezclan con otros servicios)
#     - Facilitar la eliminacion (borrar el namespace borra TODO lo de adentro)
#     - Organizar el cluster
#
# ANALOGIA: Si K8s es un edificio, cada namespace es un DEPARTAMENTO.
#   user-service -> Depto 1
#   product-service -> Depto 2
#   order-service -> Depto 3
kubectl apply -f k8s/00-namespace.yaml

# Output:
# namespace/order-service created
```

Verificar:
```bash
# kubectl get namespaces = listar todos los namespaces del cluster
# Cada microservicio tiene su propio namespace
kubectl get namespaces

# Output:
# NAME              STATUS   AGE
# default           Active   Xd    <- namespace por defecto (no lo usamos)
# kube-system       Active   Xd    <- sistema interno de K8s (no tocar)
# user-service      Active   Xd
# product-service   Active   Xd
# order-service     Active   5s    <- NUEVO - acabamos de crearlo
```

### Docker Desktop - Pestana KUBERNETES
```
Refresca la pestana Kubernetes.

Deberias ver un nuevo namespace "order-service" (vacio por ahora).
Todavia no tiene pods, services ni deployments dentro.
Es como haber creado una carpeta vacia - ahora vamos a llenarla.
```

---

## PASO 3: Crear el ConfigMap

```bash
# ConfigMap = recurso K8s que guarda configuracion NO sensible
# Son pares clave=valor que se inyectan como variables de entorno en los pods
#
# Por que no poner la config directamente en el Deployment?
#   Separar la configuracion del deployment permite:
#   - Cambiar config sin redesplegar la app
#   - Reutilizar la misma config en multiples pods
#   - Tener la configuracion versionada y visible
#
# ANALOGIA: El ConfigMap es como un archivo .env
#   pero gestionado por Kubernetes en vez de estar en tu maquina
kubectl apply -f k8s/01-configmap.yaml

# Output:
# configmap/order-service-config created
```

Verificar contenido:
```bash
# kubectl describe = ver detalle completo de un recurso
# configmap = tipo de recurso
# order-service-config = nombre del configmap
# -n order-service = en el namespace order-service
kubectl describe configmap order-service-config -n order-service

# Veras las variables que contiene:
# PRODUCT_SERVICE_URL: http://product-service.product-service.svc.cluster.local
# DB_URL:              jdbc:postgresql://host.docker.internal:5435/orderdb
# DDL_AUTO:            update
# POOL_SIZE:           10
# LOG_LEVEL:           INFO
```

### Entender la URL del Product Service dentro de Kubernetes
```
La URL mas importante del ConfigMap es PRODUCT_SERVICE_URL:

  http://product-service.product-service.svc.cluster.local
        |------v------| |-------v------| |-v-| |----v----|
           Service          Namespace   Tipo   Cluster
           name             name       (svc)   (local)

QUE ES ESTO?
  Kubernetes tiene su propio DNS interno (como un directorio telefonico).
  Cada Service que creas recibe automaticamente un nombre DNS.

POR QUE NO USAMOS localhost O UNA IP?
  Porque los pods son EFIMEROS: se crean y destruyen constantemente.
  Cada vez que un pod se recrea, puede tener una IP diferente.
  El DNS de K8s siempre apunta al pod correcto, sin importar su IP.

FORMATO: {service-name}.{namespace}.svc.cluster.local
  - service-name = nombre del Service (product-service)
  - namespace    = namespace donde vive (product-service)
  - svc          = tipo de recurso (service)
  - cluster.local = dominio del cluster

EQUIVALENCIA:
  En Docker usabamos: http://host.docker.internal:30082
  En K8s usamos:      http://product-service.product-service.svc.cluster.local
  El DNS interno es mas elegante: no dependes de puertos ni de IPs.
```

---

## PASO 4: Crear el Secret

```bash
# Secret = recurso K8s para datos SENSIBLES (contrasenas, tokens, llaves)
# Similar al ConfigMap pero con los valores codificados en Base64
#
# Que diferencia hay entre ConfigMap y Secret?
#   ConfigMap -> datos en texto plano (URLs, nombres, configuracion general)
#   Secret    -> datos codificados en Base64 (contrasenas, tokens)
#
# IMPORTANTE: Base64 NO es encriptacion, es solo codificacion.
# Cualquiera puede decodificarlo. En produccion se usa con herramientas
# como Vault o Sealed Secrets para encriptar de verdad.
#
# Por que separar los secrets del ConfigMap?
#   - Kubernetes controla quien puede leer Secrets (RBAC)
#   - Los Secrets no aparecen en logs ni en "kubectl describe"
#   - Se pueden montar como archivos en vez de variables de entorno
kubectl apply -f k8s/02-secret.yaml

# Output:
# secret/order-service-secret created
```

Verificar:
```bash
# kubectl get secret = listar secrets del namespace
# Nota: solo muestra nombres y tipo, NO los valores (por seguridad)
kubectl get secret -n order-service

# Output:
# NAME                   TYPE     DATA   AGE
# order-service-secret   Opaque   2      5s
#
# TYPE: Opaque = secret generico (key-value pairs)
# DATA: 2 = contiene 2 claves (DB_USERNAME y DB_PASSWORD)

# Ver las claves (pero no los valores):
# describe muestra los nombres de las claves y el tamano en bytes
kubectl describe secret order-service-secret -n order-service

# Para decodificar un valor manualmente (solo para verificar):
# echo = imprime texto
# base64 -d = decodifica de Base64 a texto plano
echo "cG9zdGdyZXM=" | base64 -d
# Output: postgres
#
# "cG9zdGdyZXM=" es "postgres" codificado en Base64
# Puedes verificar: echo -n "postgres" | base64 -> cG9zdGdyZXM=
```

En PowerShell para decodificar Base64:
```powershell
# [System.Text.Encoding]::UTF8.GetString convierte bytes a texto
# [System.Convert]::FromBase64String convierte Base64 a bytes
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String("cG9zdGdyZXM="))
# Output: postgres
```

---

## PASO 5: Crear el Deployment

```bash
# Deployment = recurso K8s que gestiona la creacion y vida de los pods
# Le dices: "quiero N replicas de este contenedor" y K8s se encarga
#
# Que hace un Deployment?
#   1. Crea los pods segun la especificacion
#   2. Monitorea que esten vivos (health checks)
#   3. Si un pod muere, crea uno nuevo automaticamente
#   4. Permite actualizaciones sin downtime (rolling updates)
#
# ANALOGIA:
#   Sin Deployment (Docker puro): "Cocina 1 plato. Si se cae, yo te aviso"
#   Con Deployment (K8s): "Manten siempre 1 plato listo. Si se cae, haz otro"
kubectl apply -f k8s/03-deployment.yaml

# Output:
# deployment.apps/order-service created
```

### Verificar que el pod esta corriendo:
```bash
# kubectl get pods = listar los pods del namespace
# -n order-service = filtrar por namespace
#
# El pod tarda ~20-30 segundos en arrancar (Spring Boot necesita inicializar)
kubectl get pods -n order-service

# Output esperado:
# NAME                             READY   STATUS    RESTARTS   AGE
# order-service-6b8f9d7c4f-abc12  1/1     Running   0          30s
#
# DESGLOSE DEL NOMBRE DEL POD:
#   order-service     = nombre del deployment
#   6b8f9d7c4f        = ID del ReplicaSet (generado por K8s)
#   abc12             = ID unico del pod (generado por K8s)
#
# COLUMNAS:
#   READY    = contenedores listos / total (1/1 = perfecto)
#   STATUS   = estado actual del pod
#   RESTARTS = cuantas veces se ha reiniciado (0 = sin problemas)
#
# POSIBLES STATUS:
#   ContainerCreating -> K8s esta preparando el contenedor (espera)
#   Running           -> todo bien, la app esta corriendo
#   CrashLoopBackOff  -> la app arranca y se cae repetidamente (ver logs)
#   Error             -> algo fallo al crear el contenedor (ver logs)
#   ErrImageNeverPull -> la imagen Docker no existe localmente
```

### Ver logs del pod:
```bash
# kubectl logs = ver la salida de consola del pod (como docker logs)
# -f = "follow" = mantener la conexion abierta y mostrar logs en tiempo real
#   (como tail -f en Linux)
# -n order-service = namespace
#
# Para salir de los logs: presiona Ctrl+C
kubectl logs -f <POD_NAME> -n order-service

# Reemplaza <POD_NAME> con el nombre real del pod
# (lo obtuviste del comando kubectl get pods)
# Ejemplo: kubectl logs -f order-service-6b8f9d7c4f-abc12 -n order-service

# Deberias ver los logs de Spring Boot:
# Starting OrderServiceApplication using Java 21...
# Tomcat started on port 8083                    <- servidor web listo
# Started OrderServiceApplication in X seconds   <- app lista para recibir peticiones
#
# Si ves "Connection refused" -> la BD no es accesible desde el pod
# Si ves "Table not found" -> DDL_AUTO no esta en "update"
```

### Docker Desktop - Pestana KUBERNETES
```
Abre Docker Desktop > Kubernetes

Ahora en el namespace "order-service" deberias ver:
  - 1 Deployment: order-service (1/1 ready)
  - 1 Pod: order-service-xxxxx (Running, punto verde)

Haz clic en el pod para ver detalles:
  - Status: Running
  - Containers: order-service (imagen order-service:1.0)
  - Events: Pulled -> Created -> Started
    (K8s jalo la imagen, creo el contenedor, y lo arranco)
```

### Docker Desktop - Pestana CONTAINERS
```
Abre Docker Desktop > Containers

Veras un nuevo contenedor creado por Kubernetes:
  k8s_order-service_order-service-xxxxx_order-service_xxxxx

POR QUE EL NOMBRE ES TAN LARGO?
  Kubernetes nombra los contenedores asi:
  k8s_{container}_{pod}_{namespace}_{uid}

  - k8s_              -> prefijo que indica "creado por K8s" (no manual)
  - order-service     -> nombre del contenedor (del Deployment)
  - order-service-xxx -> nombre del pod
  - order-service     -> namespace
  - xxxxx             -> ID unico

IMPORTANTE:
  NO detengas este contenedor con "docker stop".
  Kubernetes lo gestiona. Si lo detienes, K8s lo recreara automaticamente
  (porque el Deployment dice "quiero 1 replica siempre corriendo").

Haz clic en el para ver Logs, Inspect, Terminal, Stats
(funciona igual que cualquier otro contenedor Docker).
```

### Verificar variables de entorno dentro del pod:
```bash
# kubectl exec = ejecutar un comando DENTRO del pod
# -it = modo interactivo con terminal
#   -i = "interactive" (mantener stdin abierto)
#   -t = "tty" (asignar una pseudo-terminal)
# -- = separador entre opciones de kubectl y el comando a ejecutar
# /bin/sh = abrir una shell (como abrir Terminal en tu PC, pero dentro del pod)
#
# Es como hacer SSH a un servidor remoto, pero dentro del contenedor K8s
kubectl exec -it <POD_NAME> -n order-service -- /bin/sh

# Dentro del pod, verificar que las variables llegaron correctamente:

# env = mostrar todas las variables de entorno
# grep PRODUCT = filtrar solo las que contienen "PRODUCT"
env | grep PRODUCT
# Output: PRODUCT_SERVICE_URL=http://product-service.product-service.svc.cluster.local

# Verificar variables de BD
env | grep DB_
# Output:
# DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb
# DB_USERNAME=postgres
# DB_PASSWORD=postgres

# exit = salir de la shell del pod (volver a tu terminal)
exit
```

---

## PASO 6: Crear el Service (NodePort)

```bash
# Service = recurso K8s que expone los pods a la red
# Sin Service, los pods solo son accesibles DENTRO del cluster
#
# Tipos de Service:
#   ClusterIP  -> solo accesible dentro del cluster (para comunicacion entre pods)
#   NodePort   -> accesible desde fuera del cluster (desde tu PC via localhost)
#   LoadBalancer -> crea un balanceador externo (para produccion en la nube)
#
# Usamos NodePort porque queremos hacer "curl localhost:30083" desde nuestra PC
kubectl apply -f k8s/04-service.yaml

# Output:
# service/order-service created
```

Verificar:
```bash
# kubectl get service = listar los services del namespace
# (tambien puedes usar "kubectl get svc" como abreviacion)
kubectl get service -n order-service

# Output:
# NAME            TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)        AGE
# order-service   NodePort   10.96.xxx.xxx   <none>        80:30083/TCP   5s
#
# COLUMNAS:
#   TYPE       = NodePort (accesible desde tu PC)
#   CLUSTER-IP = IP interna del cluster (K8s la asigna automaticamente)
#   PORT(S)    = 80:30083 = puerto_interno:puerto_externo
```

### Entender los puertos (MUY IMPORTANTE):
```
Hay 3 puertos involucrados. Esto confunde a mucha gente:

Tu PC (localhost)           Kubernetes
     |                           |
     | curl localhost:30083      |
     +-------------------------->+ NodePort 30083  (puerta de entrada desde tu PC)
                                 |
                                 v
                            Service (port: 80)   (puerto interno del Service)
                                 |
                                 v
                            Pod (targetPort: 8083)  (puerto real de la app)
                                 |
                                 v
                          order-service.jar (server.port=8083)

EXPLICACION DE CADA PUERTO:

  nodePort: 30083
    -> El puerto que usas en tu PC para acceder al servicio
    -> Rango permitido: 30000-32767 (regla de Kubernetes)
    -> Es como la puerta de entrada del edificio

  port: 80
    -> Puerto interno del Service dentro del cluster
    -> Otros pods lo usan para comunicarse (via DNS K8s)
    -> Es como el numero de departamento

  targetPort: 8083
    -> Puerto real donde corre tu app Spring Boot
    -> Definido en application.yaml como server.port: 8083
    -> Es como la puerta de la oficina dentro del departamento

RESUMEN:
  Desde tu PC:    curl localhost:30083
  Desde otro pod: curl http://order-service.order-service.svc.cluster.local
                  (usa port 80 automaticamente)
```

### Docker Desktop - Pestana KUBERNETES
```
Abre Docker Desktop > Kubernetes

En el namespace "order-service" ahora deberias ver el conjunto COMPLETO:
  - 1 Deployment: order-service (1/1)  -> gestor de pods
  - 1 Pod: Running (punto verde)       -> contenedor corriendo
  - 1 Service: NodePort 80:30083       -> puerta de entrada

Todo en verde = todo funcionando correctamente.

Si algo esta en rojo o amarillo -> hay un problema (ver troubleshooting al final).
```

---

## PASO 7: Validar el flujo completo en Kubernetes

> NOTA: Ahora usamos puerto **30083** (NodePort de K8s) en vez de 8083 (Docker directo).
> La aplicacion es la misma, solo cambia como llegas a ella.

### 7.1 Health Check

#### En CMD:
```cmd
curl -s http://localhost:30083/api/orders/health
```

#### En PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30083/api/orders/health
```

**Esperado:** `Order Service running with Clean Architecture!`

---

### 7.2 Actuator Health (health check de K8s)

#### En CMD:
```cmd
REM /actuator/health = endpoint de Spring Boot Actuator
REM K8s lo usa para sus health checks automaticos:
REM   - livenessProbe: "la app esta viva?" (si no -> reiniciar pod)
REM   - readinessProbe: "la app puede recibir trafico?" (si no -> no enviar peticiones)
curl -s http://localhost:30083/actuator/health
```

#### En PowerShell:
```powershell
# /actuator/health = endpoint de Spring Boot Actuator
# K8s lo usa para sus health checks automaticos
Invoke-RestMethod -Uri http://localhost:30083/actuator/health
```

**Esperado:** `{"status":"UP","groups":["liveness","readiness"]}`

---

### 7.3 Crear una orden (el test mas importante)

```
Este es el test CLAVE porque demuestra la comunicacion entre microservicios
Y el PUNTO 3 OPCIONAL (calculo automatico de totales):
  1. Tu curl llega a order-service (en K8s, via NodePort 30083)
  2. Order-service llama a product-service usando el DNS interno de K8s
  3. Product-service VALIDA que los productos existen y devuelve PRECIOS ACTUALES
  4. Order-service calcula subtotal = quantity x unitPrice
  5. Order-service calcula totalAmount = suma de subtotals
  6. Guarda en la BD orderdb y devuelve la orden creada
```

#### En CMD:
```cmd
curl.exe -s -X POST http://localhost:30083/api/orders -H "Content-Type: application/json" -d "{\"userId\": 1, \"items\": [{\"productId\": 1, \"quantity\": 2}, {\"productId\": 3, \"quantity\": 1}]}"
```

#### En PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30083/api/orders `
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
      "product": {
        "id": 1,
        "name": "Laptop Dell XPS 15",
        "price": 1299.99
      },
      "quantity": 2,
      "unitPrice": 1299.99,
      "subtotal": 2599.98
    },
    {
      "product": {
        "id": 3,
        "name": "Teclado Mecanico Keychron K8",
        "price": 89.99
      },
      "quantity": 1,
      "unitPrice": 89.99,
      "subtotal": 89.99
    }
  ]
}
```

### Docker Desktop - Pestana CONTAINERS > order-service > Logs
```
Despues de crear la orden, revisa los logs del contenedor K8s.
Haz clic en el contenedor k8s_order-service_... > Logs

Deberias ver la COMUNICACION ENTRE SERVICIOS dentro del cluster:

  INFO  REST request to create order for userId: 1
  INFO  Calling Product Service to get product with id: 1        <- llama a product-service
  INFO  Product retrieved successfully: ProductDto(id=1, ...)    <- respuesta exitosa
  INFO  Calling Product Service to get product with id: 3        <- llama de nuevo
  INFO  Product retrieved successfully: ProductDto(id=3, ...)    <- respuesta exitosa
  INFO  Order created successfully with number: ORD-2026-001     <- guardado en BD

ESTO ES LO MAS IMPORTANTE:
  Demuestra que order-service (en namespace order-service)
  se comunica exitosamente con product-service (en namespace product-service)
  usando el DNS interno de Kubernetes. Los microservicios hablan entre si!
```

### Ver logs desde terminal (alternativa):
```bash
# Misma info que Docker Desktop > Logs, pero desde terminal
# Util si prefieres la terminal o necesitas filtrar/buscar texto
kubectl logs -f <POD_NAME> -n order-service

# Truco: si no quieres copiar el nombre del pod, puedes usar:
# -l = "label" = filtrar por etiqueta (definida en el Deployment)
kubectl logs -f -l app=order-service -n order-service
# Esto muestra logs de TODOS los pods con label app=order-service
```

---

### 7.4 Obtener todas las ordenes

#### En CMD:
```cmd
REM GET sin path variable = devuelve TODAS las ordenes de la BD
curl -s http://localhost:30083/api/orders
```

#### En PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30083/api/orders
```

---

### 7.5 Obtener orden por ID

#### En CMD:
```cmd
REM /orders/1 = busca la orden con id=1. Si no existe -> devuelve 404
curl -s http://localhost:30083/api/orders/1
```

#### En PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30083/api/orders/1
```

---

### 7.6 Obtener ordenes por usuario

#### En CMD:
```cmd
REM /orders/user/1 = filtra ordenes donde userId=1
REM Util para mostrar "mis pedidos" en un frontend
curl -s http://localhost:30083/api/orders/user/1
```

#### En PowerShell:
```powershell
Invoke-RestMethod -Uri http://localhost:30083/api/orders/user/1
```

---

### 7.7 Error: producto inexistente

#### En CMD:
```cmd
REM productId 9999 no existe en Product Service
REM Order-service intenta obtenerlo, product-service devuelve 404
REM Order-service convierte eso en un error 503
curl.exe -s -X POST http://localhost:30083/api/orders -H "Content-Type: application/json" -d "{\"userId\": 1, \"items\": [{\"productId\": 9999, \"quantity\": 1}]}"
```

#### En PowerShell:
```powershell
# productId 9999 no existe en Product Service
# 503 = Service Unavailable (dependencia externa fallo)
Invoke-RestMethod -Uri http://localhost:30083/api/orders `
  -Method POST `
  -Headers @{ "Content-Type" = "application/json" } `
  -Body '{"userId": 1, "items": [{"productId": 9999, "quantity": 1}]}'
```

**Esperado:** `{"status":503,"message":"Product not found with id: 9999"}`

---

### 7.8 Error: orden inexistente

#### En CMD:
```cmd
REM Buscar orden id=9999 que no existe en la BD. 404 = Not Found
curl -s http://localhost:30083/api/orders/9999
```

#### En PowerShell:
```powershell
# Buscar orden id=9999 que no existe en la BD. 404 = Not Found
Invoke-RestMethod -Uri http://localhost:30083/api/orders/9999
```

**Esperado:** `{"status":404,"message":"Order not found with id: 9999"}`

---

## PASO 8: Ver todos los servicios desplegados

```bash
# Ver el panorama completo: los 3 microservicios en Kubernetes
# echo = imprimir texto (para separar visualmente la salida)
# kubectl get all = listar todos los recursos de un namespace
echo "=== USER SERVICE ===" && kubectl get all -n user-service
echo ""
echo "=== PRODUCT SERVICE ===" && kubectl get all -n product-service
echo ""
echo "=== ORDER SERVICE ===" && kubectl get all -n order-service
```

### Docker Desktop - Pestana KUBERNETES
```
Abre Docker Desktop > Kubernetes

Vista completa del cluster con los 3 microservicios:

  Namespace: user-service
    - Pod: user-service-xxxxx (Running)
    - Service: NodePort 80:30081

  Namespace: product-service
    - Pod: product-service-xxxxx (Running)
    - Service: NodePort 80:30082

  Namespace: order-service
    - Pod: order-service-xxxxx (Running)
    - Service: NodePort 80:30083

Tienes 3 microservicios corriendo en Kubernetes!
Cada uno en su propio namespace, aislado pero comunicandose via DNS.
```

### Docker Desktop - Pestana CONTAINERS
```
Abre Docker Desktop > Containers

Veras TODOS los contenedores del sistema:

  Contenedores de BD (creados por docker-compose):
    - postgres-user      (puerto 5434)
    - postgres-product   (puerto 5433)
    - postgres-order     (puerto 5435)

  Contenedores de K8s (creados y gestionados por Kubernetes):
    - k8s_user-service_...       (pod de user-service)
    - k8s_product-service_...    (pod de product-service)
    - k8s_order-service_...      (pod de order-service)

REGLA IMPORTANTE:
  - Contenedores SIN prefijo "k8s_" -> gestionados por Docker (docker-compose)
    -> Puedes detenerlos con "docker stop" o "docker-compose down"

  - Contenedores CON prefijo "k8s_" -> gestionados por Kubernetes
    -> NO los detengas con "docker stop" (K8s los recreara)
    -> Para eliminarlos, usa "kubectl delete deployment"
```

---

## Comandos utiles de troubleshooting

### Si el pod no arranca (CrashLoopBackOff o Error):
```bash
# kubectl get events = ver el historial de eventos del namespace
# --sort-by=.lastTimestamp = ordenar por fecha (mas recientes primero)
#
# Los eventos te dicen QUE PASO: si la imagen no se encontro,
# si el pod se quedo sin memoria, si fallo el health check, etc.
kubectl get events -n order-service --sort-by=.lastTimestamp

# kubectl describe pod = informacion DETALLADA del pod
# Muestra: estado, variables de entorno, eventos, condiciones, etc.
# Busca la seccion "Events:" al final para ver que fallo
kubectl describe pod <POD_NAME> -n order-service

# Ver logs de un pod que se reinicio (crashed)
# --previous = muestra los logs de la ejecucion ANTERIOR (antes del crash)
# Sin --previous, verias los logs del intento actual que puede estar vacio
kubectl logs <POD_NAME> -n order-service --previous
```

### Verificar conectividad desde dentro del pod:
```bash
# Abrir shell dentro del pod para diagnosticar problemas de red
kubectl exec -it <POD_NAME> -n order-service -- /bin/sh

# Probar conexion a product-service (desde dentro del cluster)
# wget -qO- = descargar y mostrar en consola
# Si funciona -> la comunicacion entre servicios esta OK
wget -qO- http://product-service.product-service.svc.cluster.local/api/products/health

# Probar conexion a la BD
# nc = netcat, herramienta para probar conexiones TCP
# -z = solo probar si el puerto esta abierto (no enviar datos)
# -w 3 = timeout de 3 segundos
nc -z -w 3 host.docker.internal 5435 && echo "BD accesible" || echo "BD NO accesible"

exit
```

### Otros comandos utiles:
```bash
# Reiniciar deployment (util despues de reconstruir la imagen Docker)
# rolling restart = crea un pod nuevo y luego mata el viejo
# Asi no hay downtime (siempre hay al menos 1 pod corriendo)
kubectl rollout restart deployment order-service -n order-service

# Ver estado del rollout (ya termino de reiniciar?)
kubectl rollout status deployment order-service -n order-service

# Escalar a mas replicas (mas pods = mas capacidad)
# K8s crea pods adicionales y el Service balancea trafico entre ellos
kubectl scale deployment order-service --replicas=2 -n order-service

# Verificar que hay 2 pods corriendo
kubectl get pods -n order-service
# Deberias ver 2 pods (cada uno con nombre diferente)
```

---

## Redespliegue rapido (despues de cambios en codigo)

Cuando modificas el codigo y quieres ver los cambios en Kubernetes:

```bash
# PASO 1: Recompilar el JAR
# maven compila tu codigo Java y genera un nuevo .jar con los cambios
cd order-service
mvn clean package -DskipTests

# PASO 2: Reconstruir la imagen Docker
# Esto crea una NUEVA imagen con el JAR actualizado
# Mantiene el mismo tag (1.0), sobrescribiendo la anterior
docker build -t order-service:1.0 .

# PASO 3: Reiniciar pods para que usen la nueva imagen
# K8s descarta los pods viejos y crea nuevos que cargan la imagen actualizada
kubectl rollout restart deployment order-service -n order-service

# PASO 4: Esperar a que el nuevo pod este listo
# -w = "watch" = refrescar automaticamente hasta que presiones Ctrl+C
# Veras como el pod viejo se termina y el nuevo arranca
kubectl get pods -n order-service -w

# PASO 5: Verificar que funciona
curl http://localhost:30083/api/orders/health
```

### Docker Desktop - Pestana BUILDS
```
Despues de "docker build":
  Veras un nuevo build en el historial con estado "Completed".
  Si usas cache de layers, sera mas rapido que la primera vez.
```

### Docker Desktop - Pestana CONTAINERS
```
Despues de "kubectl rollout restart":
  1. Se crea un pod NUEVO (contenedor nuevo aparece)
  2. K8s espera a que el nuevo pod este "Ready"
  3. Se termina el pod VIEJO (contenedor viejo pasa a "Exited")

  Esto se llama "Rolling Update":
    Kubernetes NUNCA deja tu app sin servicio.
    Primero levanta el reemplazo, y DESPUES apaga el original.
```

---

## Detener y limpiar Order Service de Kubernetes

### Detener temporalmente (sin eliminar configuracion)

```bash
# kubectl scale = cambiar el numero de replicas (pods) de un deployment
# --replicas=0 = cero pods corriendo = servicio detenido
# La configuracion (Deployment, Service, ConfigMap, Secret) se CONSERVA.
# Es el equivalente a "docker stop" pero en Kubernetes.
kubectl scale deployment order-service --replicas=0 -n order-service

# Verificar que no quedan pods corriendo
kubectl get pods -n order-service
# Output esperado: No resources found in order-service namespace.

# Para volver a arrancar el servicio:
# --replicas=1 = crear 1 pod nuevo usando la misma configuracion guardada
kubectl scale deployment order-service --replicas=1 -n order-service

# Verificar que el pod arranco de nuevo
kubectl get pods -n order-service
# Output esperado:
# NAME                             READY   STATUS    RESTARTS   AGE
# order-service-6b8f9d7c4f-abc12  1/1     Running   0          30s
```

```
ANALOGIA CON DOCKER:
  docker stop order-service  <->  kubectl scale deployment order-service --replicas=0 -n order-service
  docker start order-service <->  kubectl scale deployment order-service --replicas=1 -n order-service

DIFERENCIA CLAVE:
  En Docker, el contenedor "para" pero sigue existiendo (docker ps -a lo muestra).
  En Kubernetes, el pod se ELIMINA (no hay pods en estado "Stopped").
  La informacion que persiste es el Deployment, que recuerda como crear nuevos pods.
```

### Eliminar Order Service de Kubernetes

```bash
# OPCION 1: Eliminar recurso por recurso (orden inverso al que creaste)
# Es buena practica eliminar en orden inverso para evitar dependencias rotas

# Primero eliminar el Service (ya nadie puede acceder desde fuera)
kubectl delete -f k8s/04-service.yaml

# Luego el Deployment (mata los pods)
kubectl delete -f k8s/03-deployment.yaml

# Luego el Secret y ConfigMap (ya no los usa nadie)
kubectl delete -f k8s/02-secret.yaml
kubectl delete -f k8s/01-configmap.yaml

# Finalmente el Namespace (ya esta vacio)
kubectl delete -f k8s/00-namespace.yaml


# OPCION 2: Eliminar todo el namespace de una vez (mas rapido)
# Al eliminar el namespace, K8s AUTOMATICAMENTE elimina todo lo que contiene:
# pods, services, deployments, configmaps, secrets, etc.
# No pide confirmacion - es instantaneo e irreversible
kubectl delete namespace order-service
```

---

## Resumen de puertos

| Servicio | Puerto App | NodePort K8s | Puerto BD |
|----------|-----------|-------------|-----------|
| user-service | 8081 | 30081 | 5434 (userdb) |
| product-service | 8082 | 30082 | 5433 (productdb) |
| order-service | 8083 | 30083 | 5435 (orderdb) |

---

## Resumen: Que verificar en Docker Desktop en cada paso

| Paso | Pestana | Que buscar |
|------|---------|------------|
| Antes de empezar | **Images** | order-service:1.0 existe |
| Antes de empezar | **Containers** | 3 postgres corriendo (punto verde) |
| Despues de crear Namespace | **Kubernetes** | Nuevo namespace "order-service" (vacio) |
| Despues de crear ConfigMap | **Kubernetes** | ConfigMap visible dentro del namespace |
| Despues de crear Secret | **Kubernetes** | Secret visible dentro del namespace |
| Despues de crear Deployment | **Kubernetes** | Pod Running (punto verde) en order-service |
| Despues de crear Deployment | **Containers** | k8s_order-service_... corriendo |
| Despues de crear Service | **Kubernetes** | Service NodePort 80:30083 visible |
| Despues de curl POST | **Containers > Logs** | "Calling Product Service..." en los logs |
| Despues de redespliegue | **Builds** | Nuevo build completado en historial |
| Despues de rollout restart | **Containers** | Pod viejo Exited, pod nuevo Running |
| Para troubleshooting | **Containers > Logs** | Errores de conexion, stack traces |
