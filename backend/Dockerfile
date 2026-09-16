# ── Build Stage ──
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and package
COPY src ./src
RUN mvn clean package -DskipTests

# ── Runtime Stage ──
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/target/autonomous-unity-agent-0.1.0.jar app.jar

# Render injects PORT dynamically (defaults to 8080 locally)
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
