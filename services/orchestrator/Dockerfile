# Multi-stage build: Maven build -> Java 21 runtime image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 appuser
COPY --from=build /build/target/orchestrator-*.jar /app/orchestrator.jar
USER appuser
EXPOSE 18080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/orchestrator.jar"]
