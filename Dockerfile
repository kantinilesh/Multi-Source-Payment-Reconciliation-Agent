# ==============================================================================
# Stage 1: Build the React Frontend (Vite + React)
# ==============================================================================
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm install

COPY frontend/ ./
RUN npm run build

# ==============================================================================
# Stage 2: Build the Spring Boot Backend (Java 17 + Maven)
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS backend-builder
WORKDIR /app

# Cache dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY src ./src

# Copy built frontend assets into Spring Boot's static resources
COPY --from=frontend-builder /app/frontend/dist ./src/main/resources/static
COPY --from=frontend-builder /app/frontend/dist ./frontend/dist

# Build the executable fat JAR (skipping tests during container image assembly)
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 3: Minimal Production JRE Runtime (Alpine Linux)
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app

# Create unprivileged application user
RUN addgroup -S recongroup && adduser -S reconuser -G recongroup

# Copy built JAR from builder stage
COPY --from=backend-builder /app/target/recon-backend-0.1.0.jar app.jar

# Ensure proper permissions
RUN chown -R reconuser:recongroup /app
USER reconuser

# Default port configuration (Render / Railway inject $PORT dynamically)
ENV PORT=8080
EXPOSE ${PORT}

# JVM tuning for containerized environments
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dserver.port=${PORT} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
