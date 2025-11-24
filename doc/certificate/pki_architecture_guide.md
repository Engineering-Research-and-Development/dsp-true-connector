# PKI Architecture Guide

## Overview

This document explains the Public Key Infrastructure (PKI) architecture used in DSP TrueConnector, including design decisions, certificate hierarchy, and trust model.

## 3-Tier PKI Hierarchy

DSP TrueConnector implements a **3-tier certificate hierarchy** following industry best practices:

### Tier 1: Root Certificate Authority (Root CA)

**Purpose**: The ultimate trust anchor for the entire PKI

**Characteristics**:
- Self-signed certificate
- Validity: 10 years (3650 days)
- Key Algorithm: RSA 2048-bit
- File: `dsp-root-ca.p12`
- Distinguished Name: `CN=DSP Root CA, OU=Security, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS`

**Security Considerations**:
- Should be kept **offline** after initial setup
- Private key must be protected with strong encryption
- Used only to sign the Intermediate CA certificate
- Compromise of Root CA requires complete PKI rebuild

**Certificate Extensions**:
```
BasicConstraints: critical, CA:TRUE
KeyUsage: critical, keyCertSign, cRLSign
```

### Tier 2: Intermediate Certificate Authority (Intermediate CA)

**Purpose**: Operational CA that signs server certificates

**Characteristics**:
- Signed by Root CA
- Validity: 5 years (1825 days)
- Key Algorithm: RSA 2048-bit
- File: `dsp-intermediate-ca.p12`
- Distinguished Name: `CN=DSP Intermediate CA, OU=Security, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS`

**Security Considerations**:
- Can be compromised and replaced without affecting Root CA
- Regularly used for signing server certificates
- Private key should be protected but accessible for operations
- Easier to revoke and reissue than Root CA

**Certificate Extensions**:
```
BasicConstraints: critical, CA:TRUE, pathlen:0
KeyUsage: critical, keyCertSign, cRLSign
```

The `pathlen:0` constraint prevents the Intermediate CA from creating additional CA certificates (prevents CA chain extension).

### Tier 3: Server/End-Entity Certificates

**Purpose**: Certificates for actual services and connectors

**Characteristics**:
- Signed by Intermediate CA
- Validity: 1 year (365 days)
- Key Algorithm: RSA 2048-bit
- Multiple certificates for different services

**Server Certificates**:

| Service | File | Format | Subject CN |
|---------|------|--------|------------|
| Connector A | `connector-a.p12` | PKCS12 | `CN=connector-a` |
| Connector B | `connector-b.p12` | PKCS12 | `CN=connector-b` |
| MinIO | `private.key`, `public.crt` | PEM | `CN=minio` |
| UI-A | `ui-a-cert.key`, `ui-a-fullchain.crt` | PEM | `CN=ui-a` |
| UI-B | `ui-b-cert.key`, `ui-b-fullchain.crt` | PEM | `CN=ui-b` |

**Certificate Extensions**:
```
BasicConstraints: CA:FALSE
KeyUsage: critical, digitalSignature, keyEncipherment
ExtendedKeyUsage: serverAuth, clientAuth
SubjectAlternativeName: DNS:hostname, IP:address
```

## Certificate Chain

When a service presents its certificate, the complete chain is validated:

```
┌─────────────────────────────────────────────────┐
│  Client (e.g., connector-a)                     │
│  Wants to connect to connector-b               │
└───────────────────────┬─────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────┐
│  connector-b presents certificate chain:        │
│  1. connector-b certificate (end-entity)        │
│  2. Intermediate CA certificate                 │
│  3. Root CA certificate (optional)              │
└───────────────────────┬─────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────┐
│  connector-a validates chain:                   │
│  1. Verify connector-b cert signed by Int. CA   │
│  2. Verify Int. CA cert signed by Root CA       │
│  3. Check Root CA is in truststore              │
│  4. Verify hostname matches SAN                 │
│  5. Check certificate not expired               │
└───────────────────────┬─────────────────────────┘
                        │
                        ▼
                 ✅ TLS Handshake
                    Successful
```

## Trust Model

### Truststore Configuration

The `dsp-truststore.p12` contains:
1. **Intermediate CA certificate** (primary trust anchor)
2. **Root CA certificate** (optional, for complete chain validation)

**Why trust the Intermediate CA?**
- All server certificates are signed by the Intermediate CA
- Trusting the Intermediate CA means trusting all certificates it signs
- If a server certificate is compromised, only that certificate needs to be revoked/reissued
- If the Intermediate CA is compromised, it can be revoked and a new one issued by the Root CA

### Mutual TLS (mTLS)

DSP TrueConnector supports mutual TLS where both client and server authenticate each other:

```
Connector A (Client)                    Connector B (Server)
─────────────────                       ─────────────────
1. Hello →
                                        ← 2. Server Certificate
3. Verify server cert using truststore
4. Client Certificate →
                                        5. Verify client cert using truststore
                                        ← 6. Handshake Complete
7. Encrypted Communication ↔
```

### Subject Alternative Names (SANs)

Each certificate includes SANs for flexibility in addressing:

```
connector-a certificate:
  Subject: CN=connector-a
  SAN:
    - DNS:localhost
    - DNS:connector-a
    - IP:127.0.0.1
```

This allows the service to be accessed via:
- `https://localhost:8080`
- `https://connector-a:8080`
- `https://127.0.0.1:8080`

## Certificate Formats

### PKCS12 (.p12)

**Used for**: Java-based services (connectors)

**Contains**: Private key + certificate + certificate chain

**Advantages**:
- Single file for all PKI materials
- Password-protected
- Native Java support

**Usage**:
```properties
spring.ssl.bundle.jks.connector.keystore.location=/cert/connector-a.p12
spring.ssl.bundle.jks.connector.keystore.password=password
spring.ssl.bundle.jks.connector.keystore.type=PKCS12
```

### PEM (Privacy Enhanced Mail)

**Used for**: MinIO, Nginx, and other non-Java services

**Contains**: Separate files for private key and certificate

**Advantages**:
- Text-based format (Base64 encoded)
- Widely supported
- Easy to inspect with text editors

**Files**:
- `private.key` - Private key (unencrypted)
- `public.crt` - Server certificate only
- `fullchain.crt` - Server cert + Intermediate CA (for nginx)

**Usage in Nginx**:
```nginx
ssl_certificate /etc/nginx/ssl/ui-a-fullchain.crt;
ssl_certificate_key /etc/nginx/ssl/ui-a-cert.key;
```

**Usage in MinIO**:
```
/root/.minio/certs/
├── private.key
└── public.crt
```

## Security Best Practices

### 1. Key Length

- **Current**: RSA 2048-bit
- **Recommendation**: Adequate for 10+ years based on NIST guidelines
- **Future**: Consider RSA 4096-bit or ECC for Root CA in next generation

### 2. Validity Periods

| Certificate Type | Validity | Rationale |
|-----------------|----------|-----------|
| Root CA | 10 years | Balance between security and operational overhead |
| Intermediate CA | 5 years | Allows rotation without Root CA exposure |
| Server Certificates | 1 year | Industry standard, follows CA/Browser Forum guidelines |

### 3. Private Key Protection

**Root CA**:
- Store offline in secure location
- Consider hardware security module (HSM) for production
- Encrypt keystore with strong password
- Maintain offline backups

**Intermediate CA**:
- Keep in secure, access-controlled location
- Encrypt keystore with strong password
- Log all usage
- Regular backup

**Server Certificates**:
- Rotate annually
- Use distinct passwords for each service
- Monitor for unauthorized access
- Deploy using secure channels (e.g., Docker secrets)

### 4. Certificate Revocation

**CRL (Certificate Revocation List)**:
- Not currently implemented
- Consider for future versions

**OCSP (Online Certificate Status Protocol)**:
- Supported for external certificate validation
- Can be enabled in application.properties
- See [security.md](../security.md) for OCSP configuration

### 5. Monitoring and Alerts

Implement monitoring for:
- Certificate expiration (alert 30 days before)
- TLS handshake failures
- Certificate validation errors
- Unauthorized certificate modifications

## Migration and Rotation

### Rotating Server Certificates

1. Generate new server certificate using `renew-certificates.cmd`
2. Update configuration files (application.properties, etc.)
3. Deploy new certificate to service
4. Restart service
5. Verify TLS connectivity
6. Keep old certificate as backup for 30 days

### Rotating Intermediate CA

1. Generate new Intermediate CA (signed by Root CA)
2. Update truststore with new Intermediate CA
3. Generate new server certificates signed by new Intermediate CA
4. Deploy new certificates and truststore to all services
5. Gracefully transition (support both old and new for overlap period)
6. Decommission old Intermediate CA

### Rotating Root CA

⚠️ **This requires complete PKI regeneration**

1. Generate new Root CA
2. Generate new Intermediate CA signed by new Root CA
3. Generate all new server certificates
4. Deploy entirely new certificate infrastructure
5. Update all truststores
6. Coordinate cutover across all services

## Troubleshooting

### Certificate Chain Validation Failures

**Symptom**: `sun.security.validator.ValidatorException: PKIX path building failed`

**Causes**:
1. Truststore doesn't contain Intermediate or Root CA
2. Certificate chain is incomplete
3. Certificate expired

**Solution**:
```cmd
# Verify certificate chain
keytool -list -v -keystore connector-a.p12 -storepass password

# Should show:
# - connector-a certificate
# - dsp-intermediate-ca certificate
# - dsp-root-ca certificate
```

### Hostname Verification Failures

**Symptom**: `javax.net.ssl.SSLHandshakeException: No subject alternative names present`

**Cause**: Certificate SAN doesn't match hostname

**Solution**: Regenerate certificate with correct SANs:
```cmd
set SAN_CONNECTOR_A=DNS:localhost,DNS:connector-a,DNS:actual-hostname,IP:127.0.0.1
```

## References

- [RFC 5280: X.509 Certificate and CRL Profile](https://datatracker.ietf.org/doc/html/rfc5280)
- [CA/Browser Forum Baseline Requirements](https://cabforum.org/baseline-requirements-documents/)
- [NIST SP 800-57: Key Management](https://csrc.nist.gov/publications/detail/sp/800-57-part-1/rev-5/final)

---

**Last Updated**: November 2025  
**Version**: 1.0

