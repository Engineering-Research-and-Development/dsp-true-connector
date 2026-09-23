
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /home/nobody

# Copy the executable JAR (note: Maven creates dcp-issuer-exec.jar due to classifier)
COPY target/dcp-issuer-exec.jar /home/nobody/dcp-issuer.jar

# Expose the application port
EXPOSE 8084

USER 65534

# Run the application
ENTRYPOINT ["java", "-jar",  "/home/nobody/dcp-issuer.jar"]



