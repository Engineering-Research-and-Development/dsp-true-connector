FROM eclipse-temurin:21-jre-alpine

WORKDIR /home/nobody

# Writable runtime directories for the non-root user
RUN mkdir -p /home/nobody/ftp /home/nobody/data/log/tc && chown -R nobody:nogroup /home/nobody

# The application's dependencies and jar, owned by the runtime user. --chown avoids a separate
# "chown -R" layer, which would store the dependency jars a second time.
COPY --chown=nobody:nogroup target/dependency-jars /home/nobody/app/dependency-jars
COPY --chown=nobody:nogroup target/dsp-true-connector.jar /home/nobody/app/dsp-true-connector.jar

USER 65534

# Run the jar file using JSON array format
ENTRYPOINT ["java", "-jar", "/home/nobody/app/dsp-true-connector.jar"]
