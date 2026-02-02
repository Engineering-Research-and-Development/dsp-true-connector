FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Copy the executable JAR
COPY target/dcp-holder-verifier-test.jar /app/dcp-holder-verifier-test.jar

# Expose the application port
EXPOSE 8087

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/dcp-holder-verifier-test.jar"]
