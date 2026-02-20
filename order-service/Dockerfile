# ============================================
# ETAPA 2: RUNTIME
# ============================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copiar JAR desde etapa de build
COPY target/*.jar /app/order-service.jar

# Puerto
EXPOSE 8083

# Variables de entorno
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Comando de inicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/order-service.jar"]
