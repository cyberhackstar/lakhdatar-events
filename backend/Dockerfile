# Multi-stage build. Maven/Temurin images are multi-architecture and support linux/arm64.
FROM maven:3-eclipse-temurin-24 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget \
    && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/lakhdatar-events-*.jar app.jar
RUN chown app:app /app/app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=24 CMD wget -qO- --timeout=5 http://127.0.0.1:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-XX:+UseContainerSupport","-XX:+ExitOnOutOfMemoryError","-Djava.security.egd=file:/dev/urandom","-jar","/app/app.jar"]
