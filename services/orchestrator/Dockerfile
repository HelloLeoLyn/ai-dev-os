FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw --batch-mode dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode clean package -DskipTests \
    && cp target/orchestrator-*.jar app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
