# Curls de Validación - Order Service

## Pre-requisitos
1. Docker containers corriendo: `docker-compose up -d`
2. Product Service corriendo en puerto 8082 (con productos en BD)
3. Order Service corriendo en puerto 8083

## 1. Health Check
```bash
curl -s http://localhost:8083/api/orders/health
```

## 2. Crear una orden (ajustar productId según productos existentes)
```bash
curl -s -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [
      { "productId": 1, "quantity": 2 },
      { "productId": 2, "quantity": 1 }
    ]
  }' | jq .
```

## 3. Obtener todas las órdenes
```bash
curl -s http://localhost:8083/api/orders | jq .
```

## 4. Obtener orden por ID
```bash
curl -s http://localhost:8083/api/orders/1 | jq .
```

## 5. Obtener órdenes por usuario
```bash
curl -s http://localhost:8083/api/orders/user/1 | jq .
```

## 6. Error: producto inexistente
```bash
curl -s -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [
      { "productId": 9999, "quantity": 1 }
    ]
  }' | jq .
```

## 7. Error: items vacíos
```bash
curl -s -X POST http://localhost:8083/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": []
  }' | jq .
```

## 8. Error: orden inexistente
```bash
curl -s http://localhost:8083/api/orders/9999 | jq .
```
