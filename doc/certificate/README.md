# DSP TrueConnector - Certificate Management

## Overview

DSP TrueConnector implements a **3-tier PKI (Public Key Infrastructure)** architecture for secure TLS communication between connectors and services. This documentation provides a comprehensive guide for certificate management, including generation, renewal, and configuration.

## Table of Contents

1. [PKI Architecture](#pki-architecture)
2. [Quick Start](#quick-start)
3. [Certificate Generation](#certificate-generation)
4. [Certificate Renewal](#certificate-renewal)
5. [Configuration](#configuration)
6. [Troubleshooting](#troubleshooting)
7. [Additional Resources](#additional-resources)

## PKI Architecture

The DSP TrueConnector uses a **3-tier certificate hierarchy**:

```
┌─────────────────────────────┐
│      Root CA (Tier 1)       │
│    Self-signed, 10 years    │
│   dsp-root-ca.p12           │
└──────────────┬──────────────┘
               │ signs
               ▼
┌─────────────────────────────┐
│  Intermediate CA (Tier 2)   │
│    Signed by Root CA        │
│      5 years validity       │
│ dsp-intermediate-ca.p12     │
└──────────────┬──────────────┘
               │ signs
               ▼
┌─────────────────────────────┐
│  Server Certificates (Tier 3)│
│ Signed by Intermediate CA   │
│      1 year validity        │
│ - connector-a.p12           │
│ - connector-b.p12           │
│ - minio (PEM format)        │
│ - ui-a (PEM format)         │
│ - ui-b (PEM format)         │
└─────────────────────────────┘
```

### Why 3-Tier?

- **Security**: Root CA can be kept offline after initial setup
- **Flexibility**: Easy to revoke and reissue intermediate CA if compromised
- **Scalability**: Generate multiple server certificates without exposing Root CA
- **Best Practice**: Industry-standard approach for enterprise PKI

### Certificate Files

| Component | File Name | Format | Validity | Purpose |
|-----------|-----------|--------|----------|---------|
| Root CA | `dsp-root-ca.p12` | PKCS12 | 10 years | Signs Intermediate CA |
| Intermediate CA | `dsp-intermediate-ca.p12` | PKCS12 | 5 years | Signs server certificates |
| Connector A | `connector-a.p12` | PKCS12 | 1 year | TLS for connector-a |
| Connector B | `connector-b.p12` | PKCS12 | 1 year | TLS for connector-b |
| MinIO | `private.key`, `public.crt` | PEM | 1 year | TLS for MinIO S3 storage |
| UI-A | `ui-a-cert.key`, `ui-a-fullchain.crt` | PEM | 1 year | TLS for nginx (UI-A) |
| UI-B | `ui-b-cert.key`, `ui-b-fullchain.crt` | PEM | 1 year | TLS for nginx (UI-B) |
| Truststore | `dsp-truststore.p12` | PKCS12 | N/A | Contains Intermediate + Root CA |

## Quick Start

### Prerequisites

- **Java JDK** (with keytool utility)
- **OpenSSL** (for PEM conversions - optional but recommended)
- **Windows** with cmd.exe **OR** **Linux/macOS** with bash (scripts available for both platforms)

> 📘 **Linux/macOS Users**: See [LINUX_QUICKSTART.md](./LINUX_QUICKSTART.md) for platform-specific installation instructions and examples.

### Generate New Certificates

1. Navigate to the certificate directory:
   ```cmd
   # Windows
   cd doc\certificate
   
   # Linux/macOS
   cd doc/certificate
   ```

2. Run the generation script:
   ```cmd
   # Windows
   generate-certificates.cmd
   
   # Linux/macOS
   chmod +x generate-certificates.sh
   ./generate-certificates.sh
   ```

3. The script will create all necessary certificates with default configuration

### Renew Existing Certificates

1. Navigate to the certificate directory:
   ```cmd
   # Windows
   cd doc\certificate
   
   # Linux/macOS
   cd doc/certificate
   ```

2. Run the renewal script:
   ```cmd
   # Windows
   renew-certificates.cmd
   
   # Linux/macOS
   chmod +x renew-certificates.sh
   ./renew-certificates.sh
   ```

3. Choose which certificates to renew from the interactive menu

## Certificate Generation

For detailed information about generating certificates, see [CERTIFICATE_GENERATION_GUIDE.md](./CERTIFICATE_GENERATION_GUIDE.md).

### Overview

The `generate-certificates.cmd` script performs the following steps:

1. **Generate Root CA** (self-signed, 10-year validity)
2. **Generate Intermediate CA** (signed by Root CA, 5-year validity)
3. **Generate Server Certificates** (signed by Intermediate CA, 1-year validity):
   - connector-a (PKCS12)
   - connector-b (PKCS12)
   - minio (PEM format)
   - ui-a (PEM format with fullchain for nginx)
   - ui-b (PEM format with fullchain for nginx)
4. **Create Truststore** (contains Intermediate CA and Root CA)

### Configuration

Edit the script variables at the top to customize:

```bat
REM Root CA Configuration
set ROOT_VALIDITY=3650  REM 10 years
set ROOT_PASSWORD=password

REM Intermediate CA Configuration
set INTERMEDIATE_VALIDITY=1825  REM 5 years

REM Server Certificate Configuration
set SERVER_VALIDITY=365  REM 1 year
set SERVER_PASSWORD=password

REM Subject Alternative Names (SANs)
set SAN_CONNECTOR_A=DNS:localhost,DNS:connector-a,IP:127.0.0.1
set SAN_CONNECTOR_B=DNS:localhost,DNS:connector-b,IP:127.0.0.1
set SAN_MINIO=DNS:localhost,DNS:minio,IP:127.0.0.1
set SAN_UI_A=DNS:localhost,DNS:ui-a,IP:127.0.0.1
set SAN_UI_B=DNS:localhost,DNS:ui-b,IP:127.0.0.1
```

## Certificate Renewal

For detailed information about renewing certificates, see [CERTIFICATE_RENEWAL_GUIDE.md](./CERTIFICATE_RENEWAL_GUIDE.md).

### When to Renew

- **Server Certificates**: Renew annually (1-year validity)
- **Intermediate CA**: Renew every 5 years
- **Root CA**: Renew every 10 years (requires regenerating entire PKI)

### Renewal Process

The `renew-certificates.cmd` script allows selective renewal:

```
1. Renew connector-a certificate
2. Renew connector-b certificate  
3. Renew minio certificate (PKCS12 + PEM)
4. Renew ui-a certificate (PEM + fullchain for nginx)
5. Renew ui-b certificate (PEM + fullchain for nginx)
6. Renew ALL server certificates
```

**Important**: The renewal script preserves the existing Root CA and Intermediate CA, only regenerating server certificates.

## Configuration

### Connector Configuration (Spring Boot)

Update `application.properties` for each connector:

```properties
# Enable TLS
server.ssl.enabled=true

# Keystore (connector's certificate)
spring.ssl.bundle.jks.connector.key.alias=connector-a
spring.ssl.bundle.jks.connector.key.password=password
spring.ssl.bundle.jks.connector.keystore.location=/cert/connector-a.p12
spring.ssl.bundle.jks.connector.keystore.password=password
spring.ssl.bundle.jks.connector.keystore.type=JKS

# Truststore (to trust other connectors)
spring.ssl.bundle.jks.connector.truststore.type=JKS
spring.ssl.bundle.jks.connector.truststore.location=/cert/dsp-truststore.p12
spring.ssl.bundle.jks.connector.truststore.password=password
```

### MinIO Configuration

Place MinIO certificates in the correct directory:

```
/root/.minio/certs/
├── private.key
└── public.crt
```

Or mount as Docker volumes:

```yaml
volumes:
  - ./private.key:/root/.minio/certs/private.key:ro
  - ./public.crt:/root/.minio/certs/public.crt:ro
```

### Nginx Configuration (UI-A and UI-B)

Configure nginx for HTTPS:

```nginx
server {
    listen 443 ssl;
    server_name ui-a;

    # Use fullchain certificate (includes intermediate CA)
    ssl_certificate /etc/nginx/ssl/ui-a-fullchain.crt;
    ssl_certificate_key /etc/nginx/ssl/ui-a-cert.key;

    # SSL configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # ... rest of nginx configuration
}
```

Mount certificates as Docker volumes:

```yaml
volumes:
  - ./ui-a-fullchain.crt:/etc/nginx/ssl/ui-a-fullchain.crt:ro
  - ./ui-a-cert.key:/etc/nginx/ssl/ui-a-cert.key:ro
```

### Docker Compose Integration

Example docker-compose.yml configuration:

```yaml
services:
  connector-a:
    image: ghcr.io/engineering-research-and-development/dsp-true-connector:latest
    volumes:
      - ./tc_cert:/cert:ro
    environment:
      - KEYSTORE_NAME=connector-a.p12
      - KEY_PASSWORD=password
      - KEYSTORE_PASSWORD=password
      - KEYSTORE_ALIAS=connector-a
      - TRUSTSTORE_NAME=dsp-truststore.p12
      - TRUSTSTORE_PASSWORD=password

  minio:
    image: minio/minio:latest
    volumes:
      - ./private.key:/root/.minio/certs/private.key:ro
      - ./public.crt:/root/.minio/certs/public.crt:ro

  ui-a:
    image: nginx:latest
    volumes:
      - ./ui-a-fullchain.crt:/etc/nginx/ssl/ui-a-fullchain.crt:ro
      - ./ui-a-cert.key:/etc/nginx/ssl/ui-a-cert.key:ro
```

## Troubleshooting

### Common Issues

#### Certificate Expired

**Symptom**: TLS handshake fails with certificate expiration error

**Solution**: 
```cmd
cd doc\certificate
renew-certificates.cmd
```
Select the expired certificate to renew, then restart the affected service.

#### Hostname Verification Failed

**Symptom**: TLS error about hostname mismatch

**Solution**: Ensure the SAN (Subject Alternative Name) in the certificate matches the hostname being used. Update the SAN variables in the generation/renewal scripts.

#### TLS Handshake Fails Between Connectors

**Symptom**: Connector A cannot connect to Connector B

**Check**:
1. Verify truststore contains the Intermediate CA certificate
2. Confirm both connectors use certificates signed by the same Intermediate CA
3. Check that server certificates include correct hostnames in SANs

#### OpenSSL Not Found

**Symptom**: Warning during certificate generation about OpenSSL

**Impact**: MinIO and UI certificates may not be properly converted to PEM format

**Solution**: 
- Install OpenSSL for Windows
- Or manually convert using: 
  ```cmd
  openssl pkcs12 -in cert.p12 -nocerts -nodes -out private.key
  openssl pkcs12 -in cert.p12 -clcerts -nokeys -out public.crt
  ```

### Verification Commands

Check certificate validity:
```cmd
keytool -list -v -keystore connector-a.p12 -storepass password
```

Verify certificate chain:
```cmd
keytool -list -v -keystore connector-a.p12 -storepass password | findstr "Alias Owner Issuer Valid"
```

Test TLS connection:
```cmd
curl -v --cacert dsp-truststore.p12 https://connector-a:8080
```

## Additional Resources

- [PKI Architecture Guide](./PKI_ARCHITECTURE_GUIDE.md) - Detailed PKI concepts and design decisions
- [Certificate Generation Guide](./CERTIFICATE_GENERATION_GUIDE.md) - Step-by-step certificate generation
- [Certificate Renewal Guide](./CERTIFICATE_RENEWAL_GUIDE.md) - Certificate renewal procedures
- [Linux Quick Start Guide](./LINUX_QUICKSTART.md) - **Linux/macOS-specific setup and examples**
- [Linux Scripts Summary](./LINUX_SCRIPTS_SUMMARY.md) - **Overview of Linux script creation and usage**
- [Connector TLS Communication](./CONNECTOR_TLS_COMMUNICATION.md) - How connectors establish TLS
- [MinIO Setup Guide](./MINIO_SETUP.md) - MinIO-specific certificate configuration
- [Security Best Practices](../security.md) - TLS and OCSP configuration

## Security Considerations

1. **Protect Private Keys**: Store `.p12` files and `.key` files securely
2. **Use Strong Passwords**: Change default passwords in production
3. **Rotate Regularly**: Renew server certificates annually
4. **Backup Root CA**: Keep offline backup of Root CA keystore
5. **Monitor Expiration**: Set up alerts for certificate expiration
6. **Use OCSP**: Enable OCSP validation in application.properties

## Support

For issues or questions:
- Review troubleshooting section above
- Check [GitHub Issues](https://github.com/Engineering-Research-and-Development/dsp-true-connector/issues)
- Consult the development team

---

**Last Updated**: November 2025  
**Version**: 1.0

