# Use an official Ubuntu runtime as a base image
FROM ubuntu:latest

# Copy the entrypoint script into the container at /entrypoint.sh
COPY entrypoint.sh /entrypoint.sh

# Make sure the script is executable
RUN chmod +x /entrypoint.sh

# Define the entrypoint script as the main executable
ENTRYPOINT ["/entrypoint.sh"]