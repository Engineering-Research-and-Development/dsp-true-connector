FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Copy the executable JAR
COPY target/dcp-issuer.jar /app/dcp-issuer.jar

# Copy keystore if needed
COPY src/main/resources/eckey-issuer.p12 /app/eckey-issuer.p12

# Expose the application port
EXPOSE 8084

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/dcp-issuer.jar"]

