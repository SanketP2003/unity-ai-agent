# ── Build Stage ──
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies from backend
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and package
COPY backend/src ./src
RUN mvn clean package -DskipTests

# ── Runtime Stage ──
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/target/autonomous-unity-agent-0.1.0.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
