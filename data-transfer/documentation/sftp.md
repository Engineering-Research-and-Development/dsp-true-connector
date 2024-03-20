# SFTP

Connector supports SFTP transfer of the artifacts. Implementation is based on [Apache Mina.](https://mina.apache.org/)
Authorization is handled using server certificate, same certificate used for TLS communication. Certificate must be provided and configured correctly for SFTP server (and client) to start.

Stopping, starting and initiating download or upload is handled via events, sent from other modules.

## SFTP Server

Server has mandatory configuration like, location where artifacts (files) are available (for example /home/connector/data) and port on which SFTP is running. Configuration for PKI authorization will be used from TLS configuration, meaning same certificate is used for TLS/HTTPS communication and for SFTP.

## SCP Client

Client requires following information to establish connection and pull or push artifact to the SFTP:

- host
- port
- artifact name

Like in server configuration, same PKI will be used like in TLS communication.

Connector supports both push and pull setups;

SFTP should be enabled once dataTransferStart event is received.

Once artifact expires or condition is not evaluated as true artifact should no longer be available for download.

Server supports following transfer directions:

## Push


## Pull
