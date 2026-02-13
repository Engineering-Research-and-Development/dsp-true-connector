# Security

## Overview

The DSP True Connector supports multiple authentication and security mechanisms:

1. **TLS/SSL** - Transport layer security for encrypted communication
2. **Keycloak OAuth2/OIDC** - Modern token-based authentication (optional)
3. **MongoDB Authentication** - Traditional username/password (fallback)
4. **DAPS** - Dataspace Protocol authentication for DSP endpoints
5. **OCSP** - Certificate validation and revocation checking

---

## TLS Configuration

Connector can operate both in http or httpS mode.
To enable https mode, certificates must be provided and following properties needs to be set with correct values:

```properties
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

More information on how to generate keystore and truststore files can be found [here](./certificate/PKI_CERTIFICATE_GUIDE.md).

---

## Keycloak Authentication (Optional, Recommended)

The connector can use **Keycloak** as an OAuth2/OIDC resource server for modern, token-based authentication.

### When to Use Keycloak
- ✅ **Service-to-service authentication** (connector-to-connector)
- ✅ **Enterprise SSO integration**
- ✅ **Centralized user management**
- ✅ **Role-based access control**
- ✅ **Token-based API access**
- ✅ **Multi-realm deployments**

### Enabling Keycloak

Enable it by running with the `keycloak` Spring profile:

```bash
mvn -pl connector spring-boot:run -Dspring-boot.run.profiles=keycloak
```

Or set in `application.properties`:

```properties
application.keycloak.enable=true
```

This loads `connector/src/main/resources/application-keycloak.properties` and activates OAuth2 JWT authentication.

### Configuration Properties

Add to `application-keycloak.properties`:

```properties
# Enable Keycloak authentication
application.keycloak.enable=true

# OAuth2 Resource Server (JWT validation)
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/dsp-connector
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8180/realms/dsp-connector/protocol/openid-connect/certs

# Keycloak Client Configuration (for outbound authentication)
keycloak.auth-server-url=http://localhost:8180
keycloak.realm=dsp-connector
keycloak.resource=dsp-connector-backend
keycloak.credentials.secret=dsp-connector-backend-secret
```

### What Happens When Keycloak is Enabled

**Authentication**:
- ✅ JWT token validation for incoming requests
- ✅ Bearer token required in `Authorization` header
- ✅ Tokens validated against Keycloak JWKS endpoint
- ✅ Service account authentication for connector-to-connector communication

**Authorization**:
- `/api/**` endpoints require `ROLE_ADMIN`
- `/connector/**`, `/catalog/**`, `/negotiations/**`, `/transfers/**` require `ROLE_CONNECTOR`
- Roles extracted from `realm_access.roles` claim in JWT

**User Management**:
- ❌ `/api/v1/users` endpoints are **disabled** (return 404)
- ❌ MongoDB user authentication is **disabled**
- ✅ Users managed in **Keycloak Admin Console** (http://localhost:8180)

### What Happens When Keycloak is Disabled

When `application.keycloak.enable=false` or property is missing:

- ✅ MongoDB username/password authentication active
- ✅ `/api/v1/users` endpoints active for user CRUD
- ✅ Traditional session-based authentication
- ❌ Keycloak components not loaded

### Getting Tokens

**Password Grant (User Login)**:
```bash
curl -X POST http://localhost:8180/realms/dsp-connector/protocol/openid-connect/token \
  -d "client_id=dsp-connector-ui" \
  -d "username=admin@test.com" \
  -d "password=admin123" \
  -d "grant_type=password"
```

**Client Credentials (Service Account)**:
```bash
curl -X POST http://localhost:8180/realms/dsp-connector/protocol/openid-connect/token \
  -d "client_id=dsp-connector-backend" \
  -d "client_secret=dsp-connector-backend-secret" \
  -d "grant_type=client_credentials"
```

Use the returned `access_token` as: `Authorization: Bearer <token>`

### Setup Guide

For complete setup, configuration, token examples, and troubleshooting, see:
- **[Keycloak Setup Guide](keycloak.md)** - Complete Keycloak configuration
- **[Keycloak Integration History](refactoring/KEYCLOAK_INTEGRATION_HISTORY.md)** - Integration details and fixes
- **[Complete Summary](../KEYCLOAK_INTEGRATION_COMPLETE_SUMMARY.md)** - Full project overview

### Multi-Realm Support

For deployments with multiple connectors, each can use its own realm:

```yaml
# Connector A
KEYCLOAK_REALM=connector-a
KEYCLOAK_CLIENT_ID=connector-a-backend

# Connector B  
KEYCLOAK_REALM=connector-b
KEYCLOAK_CLIENT_ID=connector-b-backend
```

See [Multi-Realm Configuration](../KEYCLOAK_REALM_ENVIRONMENT_MAPPING.md) for details.

---

## MongoDB Authentication (Fallback)

Traditional username/password authentication using MongoDB for user storage.

### When MongoDB Auth is Active
- `application.keycloak.enable=false` or property not set
- Default authentication mechanism
- User credentials stored in MongoDB
- Session-based authentication

### Configuration
No special configuration needed - active by default when Keycloak is disabled.

**Note**: MongoDB and Keycloak authentication are **mutually exclusive**. Only one can be active at a time.

---

## DAPS Authentication (Protocol Endpoints)

For DSP protocol endpoints, DAPS (Dynamic Attribute Provisioning Service) authentication can be enabled independently of Keycloak:

```properties
application.protocol.authentication.enabled=true
```

This adds DAPS token validation to protocol endpoints, working alongside either Keycloak or MongoDB authentication.

---

## OCSP Certificate Validation

For more information how to verify OCSP certificate and generate new ones, revoke and invalidate, please check following [link.](ocsp/OCSP_GUIDE.md)

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
