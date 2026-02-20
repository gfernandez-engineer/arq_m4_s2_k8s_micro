# ORDER SERVICE - Despliegue en Kubernetes

Guía paso a paso para desplegar Order Service en Kubernetes (Docker Desktop).
Incluye indicaciones de qué verificar en cada pestaña de Docker Desktop.

> PRE-REQUISITO: Haber completado README_01_DOCKER.md (imagen order-service:1.0 ya construida)

---

## Conceptos clave de Kubernetes

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
```

---

## PASO 1: Pre-requisitos

### 1.1 Verificar que Kubernetes está habilitado
```bash
kubectl config use-context docker-desktop
kubectl cluster-info

# Output esperado:
# Kubernetes control plane is running at https://127.0.0.1:6443
```

### Docker Desktop - Pestaña KUBERNETES
```
Abre Docker Desktop > Kubernetes (icono de timón de barco)

Antes de desplegar, deberías ver:
  - Los namespaces existentes (user-service, product-service, default)
  - Pods de otros servicios ya desplegados

Esta pestaña muestra una vista gráfica de todo el cluster.
Es equivalente a ejecutar "kubectl get all --all-namespaces".
```

### 1.2 Verificar que product-service está en K8s
```bash
kubectl get all -n product-service

# Deberías ver:
# pod/product-service-xxxxx     1/1   Running
# service/product-service       NodePort  80:30082/TCP
# deployment/product-service    1/1

# Probar que responde:
curl http://localhost:30082/api/products/health
```

### 1.3 Verificar que la BD orderdb está corriendo
```bash
docker ps | grep postgres-order

# Output esperado:
# postgres-order  Up (healthy)  0.0.0.0:5435->5432/tcp
```

### 1.4 Verificar que la imagen Docker existe
```bash
docker images | grep order-service

# Output esperado:
# order-service  1.0  xxxxx  390MB
```

### Docker Desktop - Pestaña IMAGES
```
Verifica que order-service:1.0 aparece en la lista.
Kubernetes usará esta imagen local para crear los pods.
Por eso en el Deployment tenemos: imagePullPolicy: Never
(le dice a K8s "usa la imagen local, no la busques en internet")
```

---

## PASO 2: Crear el Namespace

El namespace es como una "carpeta" que agrupa todos los recursos de un servicio.

```bash
kubectl apply -f k8s/00-namespace.yaml

# Output:
# namespace/order-service created
```

Verificar:
```bash
kubectl get namespaces

# Deberías ver:
# NAME              STATUS   AGE
# default           Active   Xd
# user-service      Active   Xd
# product-service   Active   Xd
# order-service     Active   5s    ← NUEVO
```

### Docker Desktop - Pestaña KUBERNETES
```
Refresca la pestaña Kubernetes.
Deberías ver un nuevo namespace "order-service" (vacío por ahora).
```

---

## PASO 3: Crear el ConfigMap

El ConfigMap guarda configuración NO sensible como variables de entorno.

```bash
kubectl apply -f k8s/01-configmap.yaml

# Output:
# configmap/order-service-config created
```

Verificar contenido:
```bash
kubectl describe configmap order-service-config -n order-service

# Verás las variables:
# PRODUCT_SERVICE_URL: http://product-service.product-service.svc.cluster.local
# DB_URL:              jdbc:postgresql://host.docker.internal:5435/orderdb
# DDL_AUTO:            update
# POOL_SIZE:           10
# LOG_LEVEL:           INFO
```

### Entender la URL del Product Service dentro de Kubernetes
```
http://product-service.product-service.svc.cluster.local
      └──────┬──────┘.└───────┬──────┘.└┬┘.└────┬────┘
         Service          Namespace   Tipo   Cluster
         name             name

Kubernetes tiene su propio DNS interno.
Cada Service tiene un nombre DNS automático.
Desde order-service, para hablar con product-service usamos este DNS.
NO usamos localhost ni IPs, porque los pods pueden moverse.
```

---

## PASO 4: Crear el Secret

El Secret guarda datos SENSIBLES (contraseñas, tokens) codificados en Base64.

```bash
kubectl apply -f k8s/02-secret.yaml

# Output:
# secret/order-service-secret created
```

Verificar:
```bash
kubectl get secret -n order-service

# Output:
# NAME                   TYPE     DATA   AGE
# order-service-secret   Opaque   2      5s

# Ver detalle (no muestra valores, solo keys)
kubectl describe secret order-service-secret -n order-service

# Para decodificar un valor (ejemplo):
echo "cG9zdGdyZXM=" | base64 -d
# Output: postgres
```

---

## PASO 5: Crear el Deployment

El Deployment le dice a Kubernetes cómo crear y gestionar los pods.

```bash
kubectl apply -f k8s/03-deployment.yaml

# Output:
# deployment.apps/order-service created
```

### Verificar que el pod está corriendo:
```bash
# Ver pods (esperar ~30 segundos)
kubectl get pods -n order-service

# Output esperado:
# NAME                             READY   STATUS    RESTARTS   AGE
# order-service-6b8f9d7c4f-abc12  1/1     Running   0          30s

# Si STATUS es "ContainerCreating", espera unos segundos más
# Si STATUS es "CrashLoopBackOff" o "Error", revisa los logs (ver abajo)
```

### Ver logs del pod:
```bash
# Obtener nombre del pod
kubectl get pods -n order-service

# Ver logs (reemplazar con tu nombre de pod)
kubectl logs -f order-service-6b8f9d7c4f-abc12 -n order-service

# Deberías ver:
# Starting OrderServiceApplication using Java 21...
# Tomcat started on port 8083
# Started OrderServiceApplication in X seconds
```

### Docker Desktop - Pestaña KUBERNETES
```
Abre Docker Desktop > Kubernetes

Ahora en el namespace "order-service" deberías ver:
  - 1 Deployment: order-service (1/1 ready)
  - 1 Pod: order-service-xxxxx (Running)

Haz clic en el pod para ver:
  - Status: Running
  - Containers: order-service (imagen order-service:1.0)
  - Events: Pulled, Created, Started
```

### Docker Desktop - Pestaña CONTAINERS
```
Abre Docker Desktop > Containers

Verás un nuevo contenedor creado por Kubernetes:
  - k8s_order-service_order-service-xxxxx_order-service_xxxxx

El nombre es largo porque Kubernetes lo nombra así:
  k8s_{container}_{pod}_{namespace}_{uid}

Haz clic en él para ver Logs, Inspect, Terminal, Stats
(igual que cualquier otro contenedor Docker)
```

### Verificar variables de entorno dentro del pod:
```bash
# Entrar al pod
kubectl exec -it order-service-6b8f9d7c4f-abc12 -n order-service -- /bin/sh

# Ver variables de Product Service
env | grep PRODUCT

# Output:
# PRODUCT_SERVICE_URL=http://product-service.product-service.svc.cluster.local

# Ver variables de BD
env | grep DB_

# Output:
# DB_URL=jdbc:postgresql://host.docker.internal:5435/orderdb
# DB_USERNAME=postgres
# DB_PASSWORD=postgres

# Salir
exit
```

---

## PASO 6: Crear el Service (NodePort)

El Service expone el pod a la red. NodePort lo hace accesible desde tu PC.

```bash
kubectl apply -f k8s/04-service.yaml

# Output:
# service/order-service created
```

Verificar:
```bash
kubectl get service -n order-service

# Output:
# NAME            TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)        AGE
# order-service   NodePort   10.96.xxx.xxx   <none>        80:30083/TCP   5s
```

### Entender los puertos:
```
Tu PC (localhost)           Kubernetes
     |                           |
     | curl localhost:30083      |
     +-------------------------->+ NodePort 30083
                                 |
                                 v
                            Service (port: 80)
                                 |
                                 v
                            Pod (targetPort: 8083)
                                 |
                                 v
                          order-service.jar (server.port=8083)

- nodePort 30083  : Puerto en tu PC para acceder desde fuera del cluster
- port 80         : Puerto interno del Service dentro del cluster
- targetPort 8083 : Puerto real de la aplicación dentro del contenedor
```

### Docker Desktop - Pestaña KUBERNETES
```
Abre Docker Desktop > Kubernetes

En el namespace "order-service" ahora deberías ver:
  - 1 Deployment: order-service (1/1)
  - 1 Pod: Running
  - 1 Service: order-service (NodePort 80:30083)

Todo en verde = todo funcionando correctamente.
```

---

## PASO 7: Validar el flujo completo en Kubernetes

> NOTA: Ahora usamos puerto 30083 (NodePort) en vez de 8083 (Docker directo)

### 7.1 Health Check
```bash
curl -s http://localhost:30083/api/orders/health

# Esperado: Order Service running with Clean Architecture!
```

### 7.2 Actuator Health (usado por K8s para health checks)
```bash
curl -s http://localhost:30083/actuator/health

# Esperado: {"status":"UP","groups":["liveness","readiness"]}
```

### 7.3 Crear una orden (order-service llama a product-service via DNS K8s)
```bash
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

### Ver logs de comunicación entre servicios:
```bash
kubectl logs -f <POD_NAME> -n order-service

# Deberías ver:
# INFO  Calling Product Service to get product with id: 1
# INFO  Product retrieved successfully: ProductDto(id=1, name=Laptop Dell XPS 15...)
# INFO  Calling Product Service to get product with id: 3
# INFO  Product retrieved successfully: ProductDto(id=3, name=Teclado Mecánico...)
# INFO  Order created successfully with number: ORD-2026-001
```

### 7.4 Obtener todas las órdenes
```bash
curl -s http://localhost:30083/api/orders
```

### 7.5 Obtener orden por ID
```bash
curl -s http://localhost:30083/api/orders/1
```

### 7.6 Obtener órdenes por usuario
```bash
curl -s http://localhost:30083/api/orders/user/1
```

### 7.7 Error: producto inexistente
```bash
curl -s -X POST http://localhost:30083/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": [{"productId": 9999, "quantity": 1}]}'

# Esperado: 503 - Product not found with id: 9999
```

### 7.8 Error: orden inexistente
```bash
curl -s http://localhost:30083/api/orders/9999

# Esperado: 404 - Order not found with id: 9999
```

---

## PASO 8: Ver todos los servicios desplegados

```bash
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
```

### Docker Desktop - Pestaña CONTAINERS
```
Verás todos los contenedores:

  Contenedores de BD (docker-compose):
    - postgres-user      (5434)
    - postgres-product   (5433)
    - postgres-order     (5435)

  Contenedores de K8s (gestionados por Kubernetes):
    - k8s_user-service_...       (pod de user-service)
    - k8s_product-service_...    (pod de product-service)
    - k8s_order-service_...      (pod de order-service)

Los contenedores K8s tienen el prefijo "k8s_".
NO debes detenerlos manualmente con "docker stop".
Kubernetes los gestiona automáticamente.
```

---

## Comandos útiles de troubleshooting

```bash
# Ver eventos del namespace (útil para diagnosticar problemas)
kubectl get events -n order-service --sort-by=.lastTimestamp

# Describir pod (ver eventos, estado, errores)
kubectl describe pod <POD_NAME> -n order-service

# Ver logs de un pod que falló
kubectl logs <POD_NAME> -n order-service --previous

# Reiniciar deployment (después de cambiar imagen)
kubectl rollout restart deployment order-service -n order-service

# Ver estado del rollout
kubectl rollout status deployment order-service -n order-service

# Escalar a más réplicas
kubectl scale deployment order-service --replicas=2 -n order-service
```

---

## Redespliegue rápido (después de cambios en código)

```bash
# 1. Recompilar
cd order-service
mvn clean package -DskipTests

# 2. Reconstruir imagen Docker
docker build -t order-service:1.0 .

# 3. Reiniciar pods para que usen la nueva imagen
kubectl rollout restart deployment order-service -n order-service

# 4. Verificar que reinició correctamente
kubectl get pods -n order-service -w

# 5. Probar
curl http://localhost:30083/api/orders/health
```

### Docker Desktop - Pestaña BUILDS
```
Después de "docker build", verás un nuevo build en el historial.
```

### Docker Desktop - Pestaña CONTAINERS
```
Después de "kubectl rollout restart":
  - El pod viejo se detiene (Exited)
  - Un pod nuevo se crea (Running)
  Kubernetes hace rolling update: primero crea el nuevo, luego mata el viejo.
```

---

## Eliminar Order Service de Kubernetes

```bash
# Eliminar en orden inverso
kubectl delete -f k8s/04-service.yaml
kubectl delete -f k8s/03-deployment.yaml
kubectl delete -f k8s/02-secret.yaml
kubectl delete -f k8s/01-configmap.yaml
kubectl delete -f k8s/00-namespace.yaml

# O eliminar todo el namespace (borra todo lo que contiene)
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
| Antes de empezar | **Containers** | 3 postgres corriendo |
| Después de crear Namespace | **Kubernetes** | Nuevo namespace "order-service" |
| Después de crear Deployment | **Kubernetes** | Pod Running en order-service |
| Después de crear Deployment | **Containers** | k8s_order-service_... corriendo |
| Después de crear Service | **Kubernetes** | Service NodePort 80:30083 |
| Después de curl POST | **Containers > Logs** | "Calling Product Service..." |
| Después de redespliegue | **Builds** | Nuevo build en historial |
| Después de rollout restart | **Containers** | Pod viejo Exited, pod nuevo Running |
| Para troubleshooting | **Containers > Logs** | Errores de conexión, excepciones |
