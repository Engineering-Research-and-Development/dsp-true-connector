# Connector-to-Connector TLS Communication Guide

## Quick Answer

**Question:** Will TLS handshake work when connector-a sends HTTPS request to connector-b?

**Answer:** ✅ **YES!** - It will work perfectly.

**Including Docker with hostnames?** ✅ **YES!** - The certificates include `DNS:connector-a` and `DNS:connector-b` in their SANs, so using these hostnames in Docker works perfectly!

## Why It Works

### Current Certificate Configuration

```
Connector-A Certificate (connector-a.p12):
- Subject: CN=connector-a
- SANs: DNS:localhost, DNS:connector-a, IP:127.0.0.1
- Signed by: Intermediate CA

Connector-B Certificate (connector-b.p12):
- Subject: CN=connector-b
- SANs: DNS:localhost, DNS:connector-b, IP:127.0.0.1
- Signed by: Intermediate CA

Truststore (dsp-truststore.p12) - Same for both:
- Contains: Intermediate CA certificate
```

### TLS Handshake Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     CONNECTOR-A (Consumer)                              │
│  Running: https://localhost:8080                                        │
│  Certificate: connector-a.p12                                           │
│  Truststore: dsp-truststore.p12 (has Intermediate CA)                  │
└────────────────────┬────────────────────────────────────────────────────┘
                     │
                     │ 1. HTTPS Request: https://localhost:8090
                     │    (hostname = "localhost")
                     ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                     CONNECTOR-B (Provider)                              │
│  Running: https://localhost:8090                                        │
│  Certificate: connector-b.p12                                           │
│  Truststore: dsp-truststore.p12 (has Intermediate CA)                  │
└─────────────────────────────────────────────────────────────────────────┘
                     │
                     │ 2. Sends certificate: connector-b.p12
                     ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                 VALIDATION BY CONNECTOR-A                               │
│                                                                         │
│  Step 1: TRUST VALIDATION                                              │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ • Received: connector-b certificate                              │ │
│  │ • Issuer: CN=DSP Intermediate CA                                 │ │
│  │ • Look in truststore: dsp-truststore.p12                         │ │
│  │ • Find: Intermediate CA ✅                                       │ │
│  │ • Verify signature: ✅                                           │ │
│  │ • Check expiration: ✅                                           │ │
│  │ • Result: TRUST VALIDATED ✅                                     │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  Step 2: HOSTNAME VERIFICATION                                         │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ • URL hostname: "localhost"                                      │ │
│  │ • connector-b SANs:                                              │ │
│  │   - DNS:localhost ✅ MATCH!                                      │ │
│  │   - DNS:connector-b                                              │ │
│  │   - IP:127.0.0.1                                                 │ │
│  │ • Result: HOSTNAME VERIFIED ✅                                   │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ✅ TLS HANDSHAKE SUCCESSFUL!                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Connection Matrix

### ✅ URLs That WILL Work

| From | To URL | Connector-B SAN | Environment | Status |
|------|--------|----------------|-------------|---------|
| connector-a | `https://localhost:8090` | ✅ DNS:localhost | Development/Same host | ✅ **SUCCESS** |
| connector-a | `https://127.0.0.1:8090` | ✅ IP:127.0.0.1 | Development/Same host | ✅ **SUCCESS** |
| connector-a | `https://connector-b:8090` | ✅ DNS:connector-b | **Docker/DNS configured** | ✅ **SUCCESS** |

**Key Point:** The dedicated SANs (`DNS:connector-a`, `DNS:connector-b`) make Docker deployments work **without any modifications**!

### ❌ URLs That WON'T Work

| From | To URL | Why It Fails |
|------|--------|--------------|
| connector-a | `https://provider:8090` | ❌ "provider" not in connector-b SANs |
| connector-a | `https://192.168.1.100:8090` | ❌ IP not in connector-b SANs |
| connector-a | `https://connector-b.local:8090` | ❌ Full domain not in connector-b SANs |

## Configuration Examples

### Development (Default)

**application-consumer.properties:**
```properties
# Connect to provider using localhost
dsp.provider.url=https://localhost:8090
```
✅ **Works** - "localhost" matches connector-b's `DNS:localhost`

### Production (Using Hostnames)

**Add DNS entries or update /etc/hosts:**
```
127.0.0.1  connector-a
127.0.0.1  connector-b
```

**application-consumer.properties:**
```properties
# Connect using hostname
dsp.provider.url=https://connector-b:8090
```
✅ **Works** - "connector-b" matches connector-b's `DNS:connector-b`

### Docker Compose (Same Host)

**docker-compose.yml:**
```yaml
services:
  consumer:
    image: dsp-connector:latest
    container_name: consumer
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=consumer
    
  provider:
    image: dsp-connector:latest
    container_name: provider
    ports:
      - "8090:8090"
    environment:
      - SPRING_PROFILES_ACTIVE=provider
```

**application-consumer.properties:**
```properties
# Connect via host's localhost (port mapping)
dsp.provider.url=https://localhost:8090
```
✅ **Works** - "localhost" matches connector-b's `DNS:localhost`

### Docker Compose (Container Network)

### Docker Compose (Container Network)

**✅ WORKS OUT OF THE BOX with dedicated SANs!**

The generated certificates already include the service names in their SANs:
- connector-a certificate has `DNS:connector-a` ✅
- connector-b certificate has `DNS:connector-b` ✅

**Docker Compose Setup:**

```yaml
version: '3.8'

services:
  connector-a:
    image: dsp-connector:latest
    container_name: connector-a
    hostname: connector-a
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=consumer
      - DSP_PROVIDER_URL=https://connector-b:8090
    networks:
      - dsp-network

  connector-b:
    image: dsp-connector:latest
    container_name: connector-b
    hostname: connector-b
    ports:
      - "8090:8090"
    environment:
      - SPRING_PROFILES_ACTIVE=provider
    networks:
      - dsp-network

networks:
  dsp-network:
    driver: bridge
```

**Application Configuration:**

```properties
# application-consumer.properties (in connector-a)
# Use the service name directly - it matches the SAN!
dsp.provider.url=https://connector-b:8090
```

**TLS Handshake Flow in Docker:**

```
Container: connector-a
  │
  │ Connects to: https://connector-b:8090
  │ (Docker DNS resolves connector-b to container IP)
  │
  ↓
Container: connector-b
  │ Presents certificate with SAN: DNS:connector-b ✅
  │
  ↓
Hostname Verification:
  • URL hostname: "connector-b"
  • Certificate SAN: DNS:connector-b
  • Match: ✅ SUCCESS!
```

**Why It Works:**
1. Docker's internal DNS resolves `connector-b` to the container's IP address
2. TLS handshake proceeds with hostname "connector-b"
3. Certificate verification finds `DNS:connector-b` in the SAN list
4. Hostname verification passes ✅

**No additional configuration needed!** The dedicated SANs are already perfect for Docker deployments.

---

### Docker Compose (Container Network) - Alternative Container Names

If you want to use **different container names** than the certificate SANs:

**Option 1:** Update SANs to include container names
```batch
# In generate-certificates.cmd
set SAN_CONNECTOR_A=DNS:localhost,DNS:connector-a,DNS:consumer,IP:127.0.0.1
set SAN_CONNECTOR_B=DNS:localhost,DNS:connector-b,DNS:provider,IP:127.0.0.1
```

Regenerate certificates, then:

**application-consumer.properties:**
```properties
# Connect via Docker network using container name
dsp.provider.url=https://provider:8090
```
✅ **Works** - "provider" matches connector-b's `DNS:provider`

**Option 2:** Use service hostname matching SAN (RECOMMENDED - NO CHANGES NEEDED)

**docker-compose.yml:**
```yaml
services:
  consumer:
    container_name: consumer
    hostname: connector-a  # Matches SAN
    
  provider:
    container_name: provider
    hostname: connector-b  # Matches SAN
```

**application-consumer.properties:**
```properties
# Connect using service hostname
dsp.provider.url=https://connector-b:8090
```
✅ **Works** - "connector-b" matches connector-b's `DNS:connector-b`

## Key Takeaways

### ✅ Why It Works

1. **Same Truststore:** Both connectors have `dsp-truststore.p12` containing Intermediate CA
2. **Same Signing CA:** Both certificates signed by the same Intermediate CA
3. **Matching SANs:** Both certificates include `DNS:localhost` 
4. **Circle of Trust:** Each connector trusts certificates signed by Intermediate CA

### 🔑 Critical Points

1. **Trust Validation:**
   - Uses: Truststore (Intermediate CA)
   - Validates: Certificate chain and signature
   - Result: ✅ Both certificates trusted

2. **Hostname Verification:**
   - Uses: Server certificate SANs
   - Validates: URL hostname matches SAN
   - Result: ✅ "localhost" matches connector-b's `DNS:localhost`

3. **Two Separate Checks:**
   - Trust validation ≠ Hostname verification
   - Both must pass for TLS handshake to succeed
   - Intermediate CA is for trust, NOT hostname verification

## Testing

### Start Both Connectors

```cmd
REM Terminal 1 - Provider
cd connector
mvn spring-boot:run -Dspring-boot.run.profiles=provider

REM Terminal 2 - Consumer
cd connector
mvn spring-boot:run -Dspring-boot.run.profiles=consumer
```

### Verify TLS Connection

```cmd
REM Test provider is accessible
curl -v https://localhost:8090/actuator/health

REM Look for in output:
REM   * SSL connection using TLSv1.3 / TLS_AES_256_GCM_SHA384
REM   * Server certificate:
REM   *   subject: CN=connector-b, OU=Connectors, O=DSP True Connector
REM   *   issuer: CN=DSP Intermediate CA, OU=Security, O=DSP True Connector
REM   * SSL certificate verify ok.
```

### Check Application Logs

**Consumer logs should show:**
```
INFO - Negotiation started with provider at https://localhost:8090
INFO - TLS handshake successful
```

**Should NOT show:**
```
ERROR - sun.security.validator.ValidatorException: PKIX path building failed
ERROR - java.security.cert.CertificateException: No subject alternative names matching
```

## Troubleshooting

### PKIX Path Building Failed

**Error:**
```
sun.security.validator.ValidatorException: PKIX path building failed:
  sun.security.provider.certpath.SunCertPathBuilderException: 
  unable to find valid certification path to requested target
```

**Cause:** Truststore doesn't contain Intermediate CA

**Fix:**
```cmd
REM Verify truststore has Intermediate CA
keytool -list -keystore dsp-truststore.p12 -storepass password

REM Should show:
REM   Alias name: dsp-intermediate-ca
```

### Hostname Verification Failed

**Error:**
```
java.security.cert.CertificateException: 
  No subject alternative names matching DNS name provider
```

**Cause:** URL hostname "provider" not in connector-b SANs

**Fix:**
Either change URL:
```properties
# Use localhost instead
dsp.provider.url=https://localhost:8090
```

Or update SANs and regenerate:
```batch
set SAN_CONNECTOR_B=DNS:localhost,DNS:connector-b,DNS:provider,IP:127.0.0.1
```

### Connection Refused

**Error:**
```
java.net.ConnectException: Connection refused
```

**Cause:** Provider not running or wrong port

**Fix:**
```cmd
REM Check provider is running
curl http://localhost:8090/actuator/health

REM Check correct port in URL
```

## Summary

✅ **TLS handshake between connector-a and connector-b WILL WORK** with the newly generated certificates because:

1. ✅ Both have the same truststore (containing Intermediate CA)
2. ✅ Both certificates are signed by the same Intermediate CA
3. ✅ Both certificates include `DNS:localhost` in SANs
4. ✅ Default connection URL uses `localhost` which matches the SAN

**No additional configuration needed for basic localhost communication!**

For Docker deployments or custom hostnames, just ensure the URL hostname matches a SAN in the server certificate.

## Related Documentation

- [Certificate Generation README](CERTIFICATES_README.md) - Complete certificate setup
- [PKI Architecture Guide](../../../doc/pki_architecture_guide.md) - Deep dive into PKI
- [MINIO Setup](MINIO_SETUP.md) - MinIO TLS configuration

