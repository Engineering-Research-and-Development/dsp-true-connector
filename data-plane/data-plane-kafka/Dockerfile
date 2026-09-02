FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY target/data-plane-kafka-*-exec.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
