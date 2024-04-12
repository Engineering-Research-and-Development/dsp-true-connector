FROM eclipse-temurin:17-jre-alpine

RUN mkdir -p /home/nobody/ftp && mkdir -p /home/nobody/data/log/ecc && mkdir -p /.sigstore

WORKDIR /home/nobody

# The application's jar file
COPY target/dependency-jars /home/nobody/app/dependency-jars
COPY src/main/resources/ssl-server.jks /home/nobody/ssl-server.jks
COPY src/main/resources/initial_data.json /home/nobody/intial_data.json

# Add the application's jar to the container
ADD target/connector-0.0.1-SNAPSHOT.jar /home/nobody/app/application.jar

RUN chown -R nobody:nogroup /home/nobody && chown -R nobody:nogroup /.sigstore 

USER 65534

# Run the jar file
ENTRYPOINT java -jar /home/nobody/app/application.jar
