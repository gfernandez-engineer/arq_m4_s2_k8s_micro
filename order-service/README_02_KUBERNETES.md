# ORDER SERVICE - Despliegue en Kubernetes

Guía paso a paso para desplegar Order Service en Kubernetes usando Docker Desktop.
Cada comando tiene una explicación de **qué hace** y **por qué lo usamos**.

> PRE-REQUISITO: Haber completado [README_01_DOCKER.md](README_01_DOCKER.md) (imagen order-service:1.0 ya construida)

---

## Conceptos clave de Kubernetes

```
ANALOGÍA: Docker vs Kubernetes

  Docker = Un COCINERO que sabe preparar UN plato a la vez.
           Tú le dices: "prepara esto" y "sírvelo en este plato".
           Si se cae el plato, tú tienes que volver a pedirlo.

  Kubernetes = Un CHEF JEFE que gestiona una COCINA COMPLETA.
               Tú le dices: "quiero 3 platos de pasta siempre listos".
               Si un plato se cae, él automáticamente prepara otro.
               Si llegan más clientes, él añade más cocineros.

En términos técnicos:
  Docker      → Ejecuta contenedores individuales
  Kubernetes  → Orquesta múltiples contenedores, los mantiene vivos,
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
|  |  |  localhost:30083 ──────────────────────+                | |  |
|  |  |                                                          | |  |
|  |  +----------------------------------------------------------+ |  |
|  +--------------------------------------------------------------+  |
+--------------------------------------------------------------------+

Flujo de una petición:
  curl localhost:30083 → Service (NodePort) → Pod (contenedor:8083)

¿Qué es cada recurso?
  Namespace   = Carpeta que agrupa todo lo de un servicio (aislamiento)
  ConfigMap   = Archivo de configuración (variables no sensibles)
  Secret      = Caja fuerte (contraseñas codificadas en Base64)
  Deployment  = "Quiero N réplicas de este contenedor siempre corriendo"
  Pod         = La unidad mínima: un contenedor corriendo
  Service     = Puerta de entrada al pod (red + balanceo de carga)
```

---

## PASO 1: Pre-requisitos

### 1.1 Verificar que Kubernetes está habilitado

```bash
# kubectl = herramienta CLI para hablar con Kubernetes
# config use-context = seleccionar el cluster al que nos conectamos
# docker-desktop = el cluster K8s local que viene con Docker Desktop
#
# Si tienes múltiples clusters (ej: uno en la nube), esto asegura
# que los comandos van al cluster correcto (tu PC local)
kubectl config use-context docker-desktop

# cluster-info = verificar que el cluster está corriendo y accesible
# Si responde con una URL, el cluster funciona
# Si dice "refused", Kubernetes no está habilitado en Docker Desktop
kubectl cluster-info

# Output esperado:
# Kubernetes control plane is running at https://127.0.0.1:6443
```

### Docker Desktop - Pestaña KUBERNETES
```
Abre Docker Desktop > Kubernetes (icono de timón de barco ⎈)

¿QUÉ MUESTRA ESTA PESTAÑA?
  Una vista gráfica de TODOS los recursos del cluster K8s.
  Es como "un explorador de archivos" pero para Kubernetes.

Antes de desplegar, deberías ver:
  - Namespaces existentes: default, user-service, product-service
  - Pods de otros servicios ya desplegados y corriendo

EQUIVALENTE EN TERMINAL:
  Esta pestaña muestra lo mismo que ejecutar:
  kubectl get all --all-namespaces

CONSEJO: Mantén esta pestaña abierta mientras sigues la guía.
Así ves en tiempo real cómo aparecen los recursos que vas creando.
```

### 1.2 Verificar que product-service está en K8s

```bash
# kubectl get all = listar TODOS los recursos de K8s
# -n product-service = filtrar por namespace "product-service"
#   -n = "namespace" (sin esto, usa el namespace "default")
#
# ¿Por qué verificar esto?
#   Order Service NECESITA comunicarse con Product Service.
#   Si Product Service no está corriendo, crear órdenes fallará.
kubectl get all -n product-service

# Deberías ver 3 tipos de recursos:
# NAME                                READY   STATUS    RESTARTS   AGE
# pod/product-service-xxxxx           1/1     Running   0          Xh
#   → El POD está corriendo (1/1 = 1 de 1 contenedores listos)
#
# NAME                      TYPE       CLUSTER-IP     PORT(S)        AGE
# service/product-service   NodePort   10.96.x.x      80:30082/TCP   Xh
#   → El SERVICE expone el pod en puerto 30082
#
# NAME                              READY   UP-TO-DATE   AVAILABLE   AGE
# deployment.apps/product-service   1/1     1            1           Xh
#   → El DEPLOYMENT gestiona 1 réplica, 1 disponible

# Probar que product-service responde:
# curl al NodePort 30082 (el puerto expuesto por el Service K8s)
curl http://localhost:30082/api/products/health

# Esperado: Product Service running with Clean Architecture!
```

### 1.3 Verificar que la BD orderdb está corriendo

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
# Si NO aparece → debes construirla primero (ver README_01_DOCKER.md, Paso 4)
```

### Docker Desktop - Pestaña IMAGES
```
Abre Docker Desktop > Images

Verifica que "order-service:1.0" aparece en la lista.
Kubernetes usará esta imagen LOCAL para crear los pods.

¿POR QUÉ ES IMPORTANTE?
  En el Deployment (paso 5) tenemos: imagePullPolicy: Never
  Esto le dice a Kubernetes:
    "Usa la imagen que ya tienes en tu máquina local.
     NO intentes descargarla de Docker Hub ni de internet."

  Si la imagen NO existe localmente, el pod quedará en estado
  "ErrImageNeverPull" y nunca arrancará.
```

---

## PASO 2: Crear el Namespace

```bash
# kubectl apply = crear o actualizar un recurso en Kubernetes
# -f = "file" = leer la definición del recurso desde un archivo YAML
#
# ¿Qué es un Namespace?
#   Es como una CARPETA que agrupa todos los recursos de un servicio.
#   Cada microservicio tiene su propio namespace para:
#     - Aislar recursos (no se mezclan con otros servicios)
#     - Facilitar la eliminación (borrar el namespace borra TODO lo de adentro)
#     - Organizar el cluster
#
# ANALOGÍA: Si K8s es un edificio, cada namespace es un DEPARTAMENTO.
#   user-service → Depto 1
#   product-service → Depto 2
#   order-service → Depto 3
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
# default           Active   Xd    ← namespace por defecto (no lo usamos)
# kube-system       Active   Xd    ← sistema interno de K8s (no tocar)
# user-service      Active   Xd
# product-service   Active   Xd
# order-service     Active   5s    ← NUEVO - acabamos de crearlo
```

### Docker Desktop - Pestaña KUBERNETES
```
Refresca la pestaña Kubernetes.

Deberías ver un nuevo namespace "order-service" (vacío por ahora).
Todavía no tiene pods, services ni deployments dentro.
Es como haber creado una carpeta vacía - ahora vamos a llenarla.
```

---

## PASO 3: Crear el ConfigMap

```bash
# ConfigMap = recurso K8s que guarda configuración NO sensible
# Son pares clave=valor que se inyectan como variables de entorno en los pods
#
# ¿Por qué no poner la config directamente en el Deployment?
#   Separar la configuración del deployment permite:
#   - Cambiar config sin redesplegar la app
#   - Reutilizar la misma config en múltiples pods
#   - Tener la configuración versionada y visible
#
# ANALOGÍA: El ConfigMap es como un archivo .env
#   pero gestionado por Kubernetes en vez de estar en tu máquina
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

# Verás las variables que contiene:
# PRODUCT_SERVICE_URL: http://product-service.product-service.svc.cluster.local
# DB_URL:              jdbc:postgresql://host.docker.internal:5435/orderdb
# DDL_AUTO:            update
# POOL_SIZE:           10
# LOG_LEVEL:           INFO
```

### Entender la URL del Product Service dentro de Kubernetes
```
La URL más importante del ConfigMap es PRODUCT_SERVICE_URL:

  http://product-service.product-service.svc.cluster.local
        └──────┬──────┘.└───────┬──────┘.└┬┘.└────┬────┘
           Service          Namespace   Tipo   Cluster
           name             name       (svc)   (local)

¿QUÉ ES ESTO?
  Kubernetes tiene su propio DNS interno (como un directorio telefónico).
  Cada Service que creas recibe automáticamente un nombre DNS.

¿POR QUÉ NO USAMOS localhost O UNA IP?
  Porque los pods son EFÍMEROS: se crean y destruyen constantemente.
  Cada vez que un pod se recrea, puede tener una IP diferente.
  El DNS de K8s siempre apunta al pod correcto, sin importar su IP.

FORMATO: {service-name}.{namespace}.svc.cluster.local
  - service-name = nombre del Service (product-service)
  - namespace    = namespace donde vive (product-service)
  - svc          = tipo de recurso (service)
  - cluster.local = dominio del cluster

EQUIVALENCIA:
  En Docker usábamos: http://host.docker.internal:30082
  En K8s usamos:      http://product-service.product-service.svc.cluster.local
  El DNS interno es más elegante: no dependes de puertos ni de IPs.
```

---

## PASO 4: Crear el Secret

```bash
# Secret = recurso K8s para datos SENSIBLES (contraseñas, tokens, llaves)
# Similar al ConfigMap pero con los valores codificados en Base64
#
# ¿Qué diferencia hay entre ConfigMap y Secret?
#   ConfigMap → datos en texto plano (URLs, nombres, configuración general)
#   Secret    → datos codificados en Base64 (contraseñas, tokens)
#
# IMPORTANTE: Base64 NO es encriptación, es solo codificación.
# Cualquiera puede decodificarlo. En producción se usa con herramientas
# como Vault o Sealed Secrets para encriptar de verdad.
#
# ¿Por qué separar los secrets del ConfigMap?
#   - Kubernetes controla quién puede leer Secrets (RBAC)
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
# TYPE: Opaque = secret genérico (key-value pairs)
# DATA: 2 = contiene 2 claves (DB_USERNAME y DB_PASSWORD)

# Ver las claves (pero no los valores):
# describe muestra los nombres de las claves y el tamaño en bytes
kubectl describe secret order-service-secret -n order-service

# Para decodificar un valor manualmente (solo para verificar):
# echo = imprime texto
# base64 -d = decodifica de Base64 a texto plano
echo "cG9zdGdyZXM=" | base64 -d
# Output: postgres
#
# "cG9zdGdyZXM=" es "postgres" codificado en Base64
# Puedes verificar: echo -n "postgres" | base64 → cG9zdGdyZXM=
```

---

## PASO 5: Crear el Deployment

```bash
# Deployment = recurso K8s que gestiona la creación y vida de los pods
# Le dices: "quiero N réplicas de este contenedor" y K8s se encarga
#
# ¿Qué hace un Deployment?
#   1. Crea los pods según la especificación
#   2. Monitorea que estén vivos (health checks)
#   3. Si un pod muere, crea uno nuevo automáticamente
#   4. Permite actualizaciones sin downtime (rolling updates)
#
# ANALOGÍA:
#   Sin Deployment (Docker puro): "Cocina 1 plato. Si se cae, yo te aviso"
#   Con Deployment (K8s): "Mantén siempre 1 plato listo. Si se cae, haz otro"
kubectl apply -f k8s/03-deployment.yaml

# Output:
# deployment.apps/order-service created
```

### Verificar que el pod está corriendo:
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
#   abc12             = ID único del pod (generado por K8s)
#
# COLUMNAS:
#   READY    = contenedores listos / total (1/1 = perfecto)
#   STATUS   = estado actual del pod
#   RESTARTS = cuántas veces se ha reiniciado (0 = sin problemas)
#
# POSIBLES STATUS:
#   ContainerCreating → K8s está preparando el contenedor (espera)
#   Running           → todo bien, la app está corriendo
#   CrashLoopBackOff  → la app arranca y se cae repetidamente (ver logs)
#   Error             → algo falló al crear el contenedor (ver logs)
#   ErrImageNeverPull → la imagen Docker no existe localmente
```

### Ver logs del pod:
```bash
# kubectl logs = ver la salida de consola del pod (como docker logs)
# -f = "follow" = mantener la conexión abierta y mostrar logs en tiempo real
#   (como tail -f en Linux)
# -n order-service = namespace
#
# Para salir de los logs: presiona Ctrl+C
kubectl logs -f <POD_NAME> -n order-service

# Reemplaza <POD_NAME> con el nombre real del pod
# (lo obtuviste del comando kubectl get pods)
# Ejemplo: kubectl logs -f order-service-6b8f9d7c4f-abc12 -n order-service

# Deberías ver los logs de Spring Boot:
# Starting OrderServiceApplication using Java 21...
# Tomcat started on port 8083                    ← servidor web listo
# Started OrderServiceApplication in X seconds   ← app lista para recibir peticiones
#
# Si ves "Connection refused" → la BD no es accesible desde el pod
# Si ves "Table not found" → DDL_AUTO no está en "update"
```

### Docker Desktop - Pestaña KUBERNETES
```
Abre Docker Desktop > Kubernetes

Ahora en el namespace "order-service" deberías ver:
  - 1 Deployment: order-service (1/1 ready)
  - 1 Pod: order-service-xxxxx (Running, punto verde)

Haz clic en el pod para ver detalles:
  - Status: Running
  - Containers: order-service (imagen order-service:1.0)
  - Events: Pulled → Created → Started
    (K8s jaló la imagen, creó el contenedor, y lo arrancó)
```

### Docker Desktop - Pestaña CONTAINERS
```
Abre Docker Desktop > Containers

Verás un nuevo contenedor creado por Kubernetes:
  k8s_order-service_order-service-xxxxx_order-service_xxxxx

¿POR QUÉ EL NOMBRE ES TAN LARGO?
  Kubernetes nombra los contenedores así:
  k8s_{container}_{pod}_{namespace}_{uid}

  - k8s_              → prefijo que indica "creado por K8s" (no manual)
  - order-service     → nombre del contenedor (del Deployment)
  - order-service-xxx → nombre del pod
  - order-service     → namespace
  - xxxxx             → ID único

IMPORTANTE:
  NO detengas este contenedor con "docker stop".
  Kubernetes lo gestiona. Si lo detienes, K8s lo recreará automáticamente
  (porque el Deployment dice "quiero 1 réplica siempre corriendo").

Haz clic en él para ver Logs, Inspect, Terminal, Stats
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
#   ClusterIP  → solo accesible dentro del cluster (para comunicación entre pods)
#   NodePort   → accesible desde fuera del cluster (desde tu PC via localhost)
#   LoadBalancer → crea un balanceador externo (para producción en la nube)
#
# Usamos NodePort porque queremos hacer "curl localhost:30083" desde nuestra PC
kubectl apply -f k8s/04-service.yaml

# Output:
# service/order-service created
```

Verificar:
```bash
# kubectl get service = listar los services del namespace
# (también puedes usar "kubectl get svc" como abreviación)
kubectl get service -n order-service

# Output:
# NAME            TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)        AGE
# order-service   NodePort   10.96.xxx.xxx   <none>        80:30083/TCP   5s
#
# COLUMNAS:
#   TYPE       = NodePort (accesible desde tu PC)
#   CLUSTER-IP = IP interna del cluster (K8s la asigna automáticamente)
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

EXPLICACIÓN DE CADA PUERTO:

  nodePort: 30083
    → El puerto que usas en tu PC para acceder al servicio
    → Rango permitido: 30000-32767 (regla de Kubernetes)
    → Es como la puerta de entrada del edificio

  port: 80
    → Puerto interno del Service dentro del cluster
    → Otros pods lo usan para comunicarse (via DNS K8s)
    → Es como el número de departamento

  targetPort: 8083
    → Puerto real donde corre tu app Spring Boot
    → Definido en application.yaml como server.port: 8083
    → Es como la puerta de la oficina dentro del departamento

RESUMEN:
  Desde tu PC:    curl localhost:30083
  Desde otro pod: curl http://order-service.order-service.svc.cluster.local
                  (usa port 80 automáticamente)
```

### Docker Desktop - Pestaña KUBERNETES
```
Abre Docker Desktop > Kubernetes

En el namespace "order-service" ahora deberías ver el conjunto COMPLETO:
  - 1 Deployment: order-service (1/1)  → gestor de pods
  - 1 Pod: Running (punto verde)       → contenedor corriendo
  - 1 Service: NodePort 80:30083       → puerta de entrada

Todo en verde = todo funcionando correctamente.

Si algo está en rojo o amarillo → hay un problema (ver troubleshooting al final).
```

---

## PASO 7: Validar el flujo completo en Kubernetes

> NOTA: Ahora usamos puerto **30083** (NodePort de K8s) en vez de 8083 (Docker directo).
> La aplicación es la misma, solo cambia cómo llegas a ella.

### 7.1 Health Check
```bash
# Verificar que el servicio responde a través de Kubernetes
# Si responde, toda la cadena funciona: NodePort → Service → Pod → App
curl -s http://localhost:30083/api/orders/health

# Esperado: Order Service running with Clean Architecture!
#
# Si dice "Connection refused" → el Service no se creó correctamente
# Si dice "timeout" → el pod no está corriendo
```

### 7.2 Actuator Health (health check de K8s)
```bash
# /actuator/health = endpoint de Spring Boot Actuator
# K8s lo usa para sus health checks automáticos:
#   - livenessProbe: "¿la app está viva?" (si no → reiniciar pod)
#   - readinessProbe: "¿la app puede recibir tráfico?" (si no → no enviar peticiones)
curl -s http://localhost:30083/actuator/health

# Esperado:
# {"status":"UP","groups":["liveness","readiness"]}
#
# "UP" = la app está sana
# "DOWN" = algo falla (BD no conecta, etc.)
```

### 7.3 Crear una orden (el test más importante)
```bash
# Este es el test CLAVE porque demuestra la comunicación entre microservicios:
#   1. Tu curl llega a order-service (en K8s, vía NodePort 30083)
#   2. Order-service llama a product-service usando el DNS interno de K8s:
#      http://product-service.product-service.svc.cluster.local
#   3. Product-service valida que los productos existen y devuelve precios
#   4. Order-service calcula totales y guarda en la BD orderdb
#
# -s = silent (no mostrar barra de progreso)
# -X POST = método HTTP POST
# -H = header (cabecera HTTP)
# -d = data (cuerpo de la petición en JSON)
curl -s -X POST http://localhost:30083/api/orders \
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
        "name": "Teclado Mecánico Keychron K8",
        "price": 89.99
      },
      "quantity": 1,
      "unitPrice": 89.99,
      "subtotal": 89.99
    }
  ]
}
```

### Docker Desktop - Pestaña CONTAINERS > order-service > Logs
```
Después de crear la orden, revisa los logs del contenedor K8s.
Haz clic en el contenedor k8s_order-service_... > Logs

Deberías ver la COMUNICACIÓN ENTRE SERVICIOS dentro del cluster:

  INFO  REST request to create order for userId: 1
  INFO  Calling Product Service to get product with id: 1        ← llama a product-service
  INFO  Product retrieved successfully: ProductDto(id=1, ...)    ← respuesta exitosa
  INFO  Calling Product Service to get product with id: 3        ← llama de nuevo
  INFO  Product retrieved successfully: ProductDto(id=3, ...)    ← respuesta exitosa
  INFO  Order created successfully with number: ORD-2026-001     ← guardado en BD

ESTO ES LO MÁS IMPORTANTE:
  Demuestra que order-service (en namespace order-service)
  se comunica exitosamente con product-service (en namespace product-service)
  usando el DNS interno de Kubernetes. ¡Los microservicios hablan entre sí!
```

### Ver logs desde terminal (alternativa):
```bash
# Misma info que Docker Desktop > Logs, pero desde terminal
# Útil si prefieres la terminal o necesitas filtrar/buscar texto
kubectl logs -f <POD_NAME> -n order-service

# Truco: si no quieres copiar el nombre del pod, puedes usar:
# -l = "label" = filtrar por etiqueta (definida en el Deployment)
kubectl logs -f -l app=order-service -n order-service
# Esto muestra logs de TODOS los pods con label app=order-service
```

### 7.4 Obtener todas las órdenes
```bash
# GET sin path variable = devuelve TODAS las órdenes de la BD
# Cada orden incluye sus items con info del producto enriquecida
curl -s http://localhost:30083/api/orders
```

### 7.5 Obtener orden por ID
```bash
# /orders/1 = busca la orden con id=1
# Si no existe → devuelve 404 Not Found
curl -s http://localhost:30083/api/orders/1
```

### 7.6 Obtener órdenes por usuario
```bash
# /orders/user/1 = filtra órdenes donde userId=1
# Útil para mostrar "mis pedidos" en un frontend
curl -s http://localhost:30083/api/orders/user/1
```

### 7.7 Error: producto inexistente
```bash
# productId 9999 no existe en Product Service
# Order-service intenta obtenerlo, product-service devuelve 404
# Order-service convierte eso en un error 503
# 503 = Service Unavailable (dependencia externa falló)
curl -s -X POST http://localhost:30083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": [{"productId": 9999, "quantity": 1}]}'

# Esperado:
# {"status":503,"message":"Product not found with id: 9999"}
```

### 7.8 Error: orden inexistente
```bash
# Buscar orden id=9999 que no existe en la BD
# 404 = Not Found
curl -s http://localhost:30083/api/orders/9999

# Esperado:
# {"status":404,"message":"Order not found with id: 9999"}
```

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

### Docker Desktop - Pestaña KUBERNETES
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

¡Tienes 3 microservicios corriendo en Kubernetes!
Cada uno en su propio namespace, aislado pero comunicándose via DNS.
```

### Docker Desktop - Pestaña CONTAINERS
```
Abre Docker Desktop > Containers

Verás TODOS los contenedores del sistema:

  Contenedores de BD (creados por docker-compose):
    - postgres-user      (puerto 5434)
    - postgres-product   (puerto 5433)
    - postgres-order     (puerto 5435)

  Contenedores de K8s (creados y gestionados por Kubernetes):
    - k8s_user-service_...       (pod de user-service)
    - k8s_product-service_...    (pod de product-service)
    - k8s_order-service_...      (pod de order-service)

REGLA IMPORTANTE:
  - Contenedores SIN prefijo "k8s_" → gestionados por Docker (docker-compose)
    → Puedes detenerlos con "docker stop" o "docker-compose down"

  - Contenedores CON prefijo "k8s_" → gestionados por Kubernetes
    → NO los detengas con "docker stop" (K8s los recreará)
    → Para eliminarlos, usa "kubectl delete deployment"
```

---

## Comandos útiles de troubleshooting

### Si el pod no arranca (CrashLoopBackOff o Error):
```bash
# kubectl get events = ver el historial de eventos del namespace
# --sort-by=.lastTimestamp = ordenar por fecha (más recientes primero)
#
# Los eventos te dicen QUÉ PASÓ: si la imagen no se encontró,
# si el pod se quedó sin memoria, si falló el health check, etc.
kubectl get events -n order-service --sort-by=.lastTimestamp

# kubectl describe pod = información DETALLADA del pod
# Muestra: estado, variables de entorno, eventos, condiciones, etc.
# Busca la sección "Events:" al final para ver qué falló
kubectl describe pod <POD_NAME> -n order-service

# Ver logs de un pod que se reinició (crashed)
# --previous = muestra los logs de la ejecución ANTERIOR (antes del crash)
# Sin --previous, verías los logs del intento actual que puede estar vacío
kubectl logs <POD_NAME> -n order-service --previous
```

### Verificar conectividad desde dentro del pod:
```bash
# Abrir shell dentro del pod para diagnosticar problemas de red
kubectl exec -it <POD_NAME> -n order-service -- /bin/sh

# Probar conexión a product-service (desde dentro del cluster)
# wget -qO- = descargar y mostrar en consola
# Si funciona → la comunicación entre servicios está OK
wget -qO- http://product-service.product-service.svc.cluster.local/api/products/health

# Probar conexión a la BD
# nc = netcat, herramienta para probar conexiones TCP
# -z = solo probar si el puerto está abierto (no enviar datos)
# -w 3 = timeout de 3 segundos
nc -z -w 3 host.docker.internal 5435 && echo "BD accesible" || echo "BD NO accesible"

exit
```

### Otros comandos útiles:
```bash
# Reiniciar deployment (útil después de reconstruir la imagen Docker)
# rolling restart = crea un pod nuevo y luego mata el viejo
# Así no hay downtime (siempre hay al menos 1 pod corriendo)
kubectl rollout restart deployment order-service -n order-service

# Ver estado del rollout (¿ya terminó de reiniciar?)
kubectl rollout status deployment order-service -n order-service

# Escalar a más réplicas (más pods = más capacidad)
# K8s crea pods adicionales y el Service balancea tráfico entre ellos
kubectl scale deployment order-service --replicas=2 -n order-service

# Verificar que hay 2 pods corriendo
kubectl get pods -n order-service
# Deberías ver 2 pods (cada uno con nombre diferente)
```

---

## Redespliegue rápido (después de cambios en código)

Cuando modificas el código y quieres ver los cambios en Kubernetes:

```bash
# PASO 1: Recompilar el JAR
# maven compila tu código Java y genera un nuevo .jar con los cambios
cd order-service
mvn clean package -DskipTests

# PASO 2: Reconstruir la imagen Docker
# Esto crea una NUEVA imagen con el JAR actualizado
# Mantiene el mismo tag (1.0), sobrescribiendo la anterior
docker build -t order-service:1.0 .

# PASO 3: Reiniciar pods para que usen la nueva imagen
# K8s descarta los pods viejos y crea nuevos que cargan la imagen actualizada
kubectl rollout restart deployment order-service -n order-service

# PASO 4: Esperar a que el nuevo pod esté listo
# -w = "watch" = refrescar automáticamente hasta que presiones Ctrl+C
# Verás cómo el pod viejo se termina y el nuevo arranca
kubectl get pods -n order-service -w

# PASO 5: Verificar que funciona
curl http://localhost:30083/api/orders/health
```

### Docker Desktop - Pestaña BUILDS
```
Después de "docker build":
  Verás un nuevo build en el historial con estado "Completed".
  Si usas cache de layers, será más rápido que la primera vez.
```

### Docker Desktop - Pestaña CONTAINERS
```
Después de "kubectl rollout restart":
  1. Se crea un pod NUEVO (contenedor nuevo aparece)
  2. K8s espera a que el nuevo pod esté "Ready"
  3. Se termina el pod VIEJO (contenedor viejo pasa a "Exited")

  Esto se llama "Rolling Update":
    Kubernetes NUNCA deja tu app sin servicio.
    Primero levanta el reemplazo, y DESPUÉS apaga el original.
```

---

## Eliminar Order Service de Kubernetes

```bash
# OPCIÓN 1: Eliminar recurso por recurso (orden inverso al que creaste)
# Es buena práctica eliminar en orden inverso para evitar dependencias rotas

# Primero eliminar el Service (ya nadie puede acceder desde fuera)
kubectl delete -f k8s/04-service.yaml

# Luego el Deployment (mata los pods)
kubectl delete -f k8s/03-deployment.yaml

# Luego el Secret y ConfigMap (ya no los usa nadie)
kubectl delete -f k8s/02-secret.yaml
kubectl delete -f k8s/01-configmap.yaml

# Finalmente el Namespace (ya está vacío)
kubectl delete -f k8s/00-namespace.yaml


# OPCIÓN 2: Eliminar todo el namespace de una vez (más rápido)
# Al eliminar el namespace, K8s AUTOMÁTICAMENTE elimina todo lo que contiene:
# pods, services, deployments, configmaps, secrets, etc.
# ⚠️ No pide confirmación - es instantáneo e irreversible
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

## Resumen: Qué verificar en Docker Desktop en cada paso

| Paso | Pestaña | Qué buscar |
|------|---------|------------|
| Antes de empezar | **Images** | order-service:1.0 existe |
| Antes de empezar | **Containers** | 3 postgres corriendo (punto verde) |
| Después de crear Namespace | **Kubernetes** | Nuevo namespace "order-service" (vacío) |
| Después de crear ConfigMap | **Kubernetes** | ConfigMap visible dentro del namespace |
| Después de crear Secret | **Kubernetes** | Secret visible dentro del namespace |
| Después de crear Deployment | **Kubernetes** | Pod Running (punto verde) en order-service |
| Después de crear Deployment | **Containers** | k8s_order-service_... corriendo |
| Después de crear Service | **Kubernetes** | Service NodePort 80:30083 visible |
| Después de curl POST | **Containers > Logs** | "Calling Product Service..." en los logs |
| Después de redespliegue | **Builds** | Nuevo build completado en historial |
| Después de rollout restart | **Containers** | Pod viejo Exited, pod nuevo Running |
| Para troubleshooting | **Containers > Logs** | Errores de conexión, stack traces |
