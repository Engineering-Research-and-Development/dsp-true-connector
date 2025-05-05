# Security

## TLS configuration

Connector can operate both in http or httpS mode.
To enable https mode, certificates must be provided and following properties needs to be set with correct values:

```
## SSL Configuration
spring.ssl.bundle.jks.connector.key.alias = connector-a
spring.ssl.bundle.jks.connector.key.password = password
spring.ssl.bundle.jks.connector.keystore.location = classpath:connector-a.jks
spring.ssl.bundle.jks.connector.keystore.password = password
spring.ssl.bundle.jks.connector.keystore.type = JKS
spring.ssl.bundle.jks.connector.truststore.type=JKS
spring.ssl.bundle.jks.connector.truststore.location=classpath:truststore.jks
spring.ssl.bundle.jks.connector.truststore.password=password

server.ssl.enabled=true
server.ssl.key-alias=connector-a
server.ssl.key-password=password
server.ssl.key-store=classpath:connector-a.jks
server.ssl.key-store-password=password

```

Make sure to update values with correct one, provided keystore files are self signed and should not be used in production.


## OCSP

For more information how to verify OCSP certifcate and generate new ones, revoke and invalidate, please check following [link.](ocsp/OCSP_GUIDE.md)

Following set of properties will configure OCSP validation for TLS certificate:

```
# OCSP Validation Configuration
# Enable or disable OCSP validation
application.ocsp.validation.enabled=false
# Soft-fail mode: if true, allows connections when OCSP validation fails
# If false, connections will be rejected when OCSP validation fails
application.ocsp.validation.soft-fail=true
# Default cache duration in minutes for OCSP responses without nextUpdate field
application.ocsp.validation.default-cache-duration-minutes=60
# Timeout in seconds for OCSP responder connections
application.ocsp.validation.timeout-seconds=10
```

Current implementation, if OCSP is **DISABLED** (default configuration) will create OkHttpRestClient with truststore that allows ALL certificates. 

If you want to have proper TLS communication, with hostname validation enabled, this can be achieved by setting 

```
application.ocsp.validation.enabled=true
```

This will create proper *OcspX509TrustManager* that will load provided truststore, and perform:

 - hostname validation (PKIX)
 - OCSP check
 
If certificate does not have 

```
Authority Information Access [1]: 
    Access Method: OCSP (1.3.6.1.5.5.7.48.1) 
    Access Location:         URI: http://ocsp-server:8888 

```

then OCSP validation will be skipped. If URL is provided, there must exists at least 2 certificates in chain, for validation to be performed. Otherwise it will be skipped.

To perform strict OCSP validation set following property to 

```
application.ocsp.validation.soft-fail=false
```
