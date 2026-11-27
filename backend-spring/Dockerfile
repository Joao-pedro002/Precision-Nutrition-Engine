# =============================================================================
# STAGE 1: Build Spring Boot Fat-JAR with Maven + Java 21
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache Maven dependencies before copying source code
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B \
    && find target -maxdepth 1 -type f -name "*.jar" ! -name "*.original" -exec cp {} target/app.jar \;

# =============================================================================
# STAGE 2: Minimal Non-Root Runtime (JRE 21 Alpine)
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runner

RUN apk add --no-cache tzdata \
    && addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=builder --chown=spring:spring /build/target/app.jar /app/app.jar

USER spring

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar"]
