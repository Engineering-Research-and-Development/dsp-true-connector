FROM eclipse-temurin:17-jre-alpine

RUN mkdir -p /home/nobody/ftp && mkdir -p /home/nobody/data/log/tc

WORKDIR /home/nobody

# The application's jar file
COPY /target/dependency-jars /home/nobody/app/dependency-jars

# Add the application's jar to the container
ADD /target/dsp-true-connector.jar /home/nobody/app/dsp-true-connector.jar

RUN chown -R nobody:nogroup /home/nobody

USER 65534

# Run the jar file
ENTRYPOINT java -jar /home/nobody/app/dsp-true-connector.jar
