
FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Copy the executable JAR (with exec classifier from Spring Boot Maven Plugin)
COPY target/dcp-issuer-exec.jar /app/dcp-issuer.jar

# Expose the application port
EXPOSE 8084

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/dcp-issuer.jar"]





