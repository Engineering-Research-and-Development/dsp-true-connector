# Certificate Generation Guide

## Overview

This directory contains a script to generate a complete PKI (Public Key Infrastructure) hierarchy for the DSP True Connector:

```
Root CA (self-signed)
    ↓ signs
Intermediate CA
    ↓ signs
Server Certificates (connector-a, connector-b)
```

## Files

- `generate-certificates.cmd` - Main script to generate all certificates
- `setup-truststore.cmd` - Legacy script (replaced by generate-certificates.cmd)

## Quick Start

### 1. Edit Configuration (Optional)

Open `generate-certificates.cmd` and edit the configuration section at the top:

```batch
REM Subject Alternative Names (SAN) - Edit these lists as needed for each service
REM Each server should only have the SANs it actually needs for security best practices
set SAN_CONNECTOR_A=DNS:localhost,DNS:connector-a,IP:127.0.0.1
set SAN_CONNECTOR_B=DNS:localhost,DNS:connector-b,IP:127.0.0.1
set SAN_MINIO=DNS:localhost,DNS:minio,IP:127.0.0.1

REM Root CA Distinguished Name
set ROOT_DNAME=CN=DSP Root CA, OU=Security, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS

REM Validity periods (in days)
set ROOT_VALIDITY=3650        # 10 years
set INTERMEDIATE_VALIDITY=1825 # 5 years
set SERVER_VALIDITY=365        # 1 year

REM Passwords (change these for production!)
set ROOT_PASSWORD=password
set INTERMEDIATE_PASSWORD=password
set SERVER_PASSWORD=password
set TRUSTSTORE_PASSWORD=password
```

**Note:** Each server certificate now has **specific SANs** for better security. The certificate for `connector-a` can only serve requests for `localhost`, `connector-a`, and `127.0.0.1`. This prevents certificate misuse and follows security best practices.

### 2. Run the Script

```cmd
cd connector/src/main/resources
generate-certificates.cmd
```

The script will:
1. ✅ Generate Root CA (self-signed)
2. ✅ Generate Intermediate CA (signed by Root CA)
3. ✅ Generate server certificates for connector-a and connector-b (signed by Intermediate CA)
4. ✅ Create truststore with Intermediate CA certificate
5. ✅ Verify all certificates

### 3. Generated Files

After running the script, you'll have:

| File | Description | Usage |
|------|-------------|-------|
| `dsp-root-ca.p12` | Root CA keystore | **Keep secure!** Used only for signing Intermediate CA |
| `dsp-intermediate-ca.p12` | Intermediate CA keystore | **Keep secure!** Used for signing server certificates |
| `connector-a.p12` | Server certificate for connector-a | Use in consumer application |
| `connector-b.p12` | Server certificate for connector-b | Use in provider application |
| `dsp-truststore.p12` | Truststore with Intermediate CA | Use for TLS validation in both applications |

## Certificate Chain

The complete chain looks like this:

```
┌─────────────────────────────────────┐
│  Root CA (dsp-root-ca.p12)          │
│  Subject: CN=DSP Root CA            │
│  Issuer: CN=DSP Root CA (self!)     │
│  Valid: 10 years                    │
└─────────────────────────────────────┘
              ↓ signs
┌─────────────────────────────────────┐
│  Intermediate CA                    │
│  (dsp-intermediate-ca.p12)          │
│  Subject: CN=DSP Intermediate CA    │
│  Issuer: CN=DSP Root CA             │
│  Valid: 5 years                     │
│  → This goes in TRUSTSTORE          │
└─────────────────────────────────────┘
              ↓ signs
┌──────────────────────────────────────┐
│  Server Certificates                │
│  (connector-a.p12, connector-b.p12) │
│  Subject: CN=connector-a/b          │
│  Issuer: CN=DSP Intermediate CA     │
│  Valid: 1 year                      │
│  SAN: Specific to each server:      │
│    - connector-a: localhost,        │
│      connector-a, 127.0.0.1         │
│    - connector-b: localhost,        │
│      connector-b, 127.0.0.1         │
│    - minio: localhost, minio,       │
│      127.0.0.1                      │
└──────────────────────────────────────┘
```

## Why Intermediate CA?

Using an Intermediate CA is a **best practice** for PKI:

### ✅ Security Benefits:
1. **Root CA stays offline** - Only used to sign Intermediate CA, then can be stored securely
2. **Compromise isolation** - If Intermediate CA is compromised, revoke it and create a new one without changing Root CA
3. **Separate responsibilities** - Root CA for long-term trust, Intermediate CA for day-to-day certificate signing

### ✅ Operational Benefits:
1. **Easier rotation** - Renew server certificates without touching Root CA
2. **Production-like** - Mimics real-world PKI structures
3. **Flexibility** - Can have multiple Intermediate CAs for different purposes

## Configuration in Spring Boot

### Consumer (application-consumer.properties)

```properties
# Server SSL (incoming HTTPS)
server.ssl.enabled=true
server.ssl.key-alias=connector-a
server.ssl.key-password=password
server.ssl.key-store=classpath:connector-a.p12
server.ssl.key-store-password=password
server.ssl.key-store-type=PKCS12

# SSL Bundle (outgoing HTTPS)
spring.ssl.bundle.jks.connector.keystore.location=classpath:connector-a.p12
spring.ssl.bundle.jks.connector.keystore.password=password
spring.ssl.bundle.jks.connector.keystore.type=PKCS12
spring.ssl.bundle.jks.connector.key.alias=connector-a
spring.ssl.bundle.jks.connector.key.password=password

spring.ssl.bundle.jks.connector.truststore.location=classpath:dsp-truststore.p12
spring.ssl.bundle.jks.connector.truststore.password=password
spring.ssl.bundle.jks.connector.truststore.type=PKCS12

# OCSP Validation
application.ocsp.validation.enabled=false
application.ocsp.validation.soft-fail=true
```

### Provider (application-provider.properties)

```properties
# Server SSL (incoming HTTPS)
server.ssl.enabled=true
server.ssl.key-alias=connector-b
server.ssl.key-password=password
server.ssl.key-store=classpath:connector-b.p12
server.ssl.key-store-password=password
server.ssl.key-store-type=PKCS12

# SSL Bundle (outgoing HTTPS)
spring.ssl.bundle.jks.connector.keystore.location=classpath:connector-b.p12
spring.ssl.bundle.jks.connector.keystore.password=password
spring.ssl.bundle.jks.connector.keystore.type=PKCS12
spring.ssl.bundle.jks.connector.key.alias=connector-b
spring.ssl.bundle.jks.connector.key.password=password

spring.ssl.bundle.jks.connector.truststore.location=classpath:dsp-truststore.p12
spring.ssl.bundle.jks.connector.truststore.password=password
spring.ssl.bundle.jks.connector.truststore.type=PKCS12

# OCSP Validation
application.ocsp.validation.enabled=false
application.ocsp.validation.soft-fail=true
```

## How TLS Handshake Works

When Consumer connects to Provider:

```
1. Consumer initiates connection to https://localhost:8090
   ↓
2. Provider presents certificate from connector-b.p12
   - Certificate: connector-b
   - Signed by: Intermediate CA
   ↓
3. Consumer validates certificate chain:
   - Checks: Is connector-b signed by Intermediate CA?
   - Looks in: dsp-truststore.p12
   - Finds: Intermediate CA ✅
   - Validates: Signature, expiration, SAN=localhost ✅
   ↓
4. TLS handshake succeeds!
```

## Understanding Hostname Verification

### How It Works

During TLS handshake, the client performs **two separate validations**:

1. **Certificate Chain Validation** (Trust)
   - Verifies server certificate is signed by a trusted CA
   - Uses: Truststore (contains Intermediate CA)
   - Question: "Do I trust who signed this certificate?"

2. **Hostname Verification** (Identity)
   - Verifies the hostname in the URL matches a SAN in the **server certificate**
   - Uses: Subject Alternative Names (SAN) in the server's certificate
   - Question: "Is this certificate valid for the hostname I'm connecting to?"

### Important: SANs Must Be in Server Certificates

❌ **WRONG:** "The Intermediate CA has all SANs, so all servers can use any hostname"
✅ **CORRECT:** "Each server certificate must have its own SANs for the hostnames it will serve"

The Intermediate CA certificate's SANs (if any) are **NOT used** for hostname verification of server certificates. Only the **server certificate's own SANs** matter.

### Example Scenario

```
Server Certificate: connector-a.p12
- SAN: DNS:localhost, DNS:connector-a, IP:127.0.0.1

✅ Valid connections:
   https://localhost:8090       → SAN matches "localhost"
   https://connector-a:8090      → SAN matches "connector-a"
   https://127.0.0.1:8090        → SAN matches "127.0.0.1"

❌ Invalid connections:
   https://connector-b:8090      → SAN does NOT match "connector-b"
   https://minio:9000            → SAN does NOT match "minio"
```

### Why Specific SANs Per Server?

**Security Benefits:**
- ✅ **Least Privilege:** Each certificate can only authenticate for its intended hostnames
- ✅ **Prevents Impersonation:** connector-a cannot impersonate connector-b
- ✅ **Certificate Compromise Isolation:** If one cert is compromised, it can't be used for other services
- ✅ **Clear Audit Trail:** Certificate usage is traceable to specific services

**Development Consideration:**
- For development, you can include multiple SANs in each certificate for flexibility
- For production, use only the SANs each service actually needs

### Intermediate CA Role

The Intermediate CA in the truststore is used for:
- ✅ **Trust validation:** Verifying the certificate chain is signed by a trusted CA
- ✅ **Certificate signing:** Signing server certificates (during generation)
- ❌ **NOT for hostname verification:** The intermediate CA's SANs don't apply to servers

## Connector-to-Connector Communication

### Will connector-a connecting to connector-b work?

**Answer: ✅ YES** - The TLS handshake will succeed when connector-a (Consumer) sends HTTPS requests to connector-b (Provider).

### How It Works

**Setup:**
```
Connector-A (Consumer) on https://localhost:8080
- Certificate: connector-a.p12
  - SANs: DNS:localhost, DNS:connector-a, IP:127.0.0.1
- Truststore: dsp-truststore.p12 (contains Intermediate CA)

Connector-B (Provider) on https://localhost:8090
- Certificate: connector-b.p12
  - SANs: DNS:localhost, DNS:connector-b, IP:127.0.0.1
- Truststore: dsp-truststore.p12 (contains Intermediate CA)
```

**When Connector-A connects to `https://localhost:8090`:**

1. **Trust Validation:**
   - Connector-B presents connector-b.p12 certificate
   - Certificate is signed by: Intermediate CA
   - Connector-A looks in its truststore: finds Intermediate CA ✅
   - Trust chain validates successfully ✅

2. **Hostname Verification:**
   - URL hostname: "localhost"
   - Connector-B certificate SANs: DNS:**localhost**, DNS:connector-b, IP:127.0.0.1
   - Hostname matches SAN ✅

3. **Result:** ✅ **TLS Handshake Succeeds!**

### Connection URLs That Work

| URL | Why It Works |
|-----|--------------|
| `https://localhost:8090` | ✅ "localhost" matches `DNS:localhost` in connector-b certificate |
| `https://127.0.0.1:8090` | ✅ "127.0.0.1" matches `IP:127.0.0.1` in connector-b certificate |
| `https://connector-b:8090` | ✅ "connector-b" matches `DNS:connector-b` in connector-b certificate (needs DNS/hosts entry) |

### Connection URLs That DON'T Work

| URL | Why It Fails |
|-----|--------------|
| `https://provider:8090` | ❌ "provider" does NOT match any SAN in connector-b certificate |
| `https://connector-b.example.com:8090` | ❌ "connector-b.example.com" does NOT match any SAN |
| `https://192.168.1.100:8090` | ❌ "192.168.1.100" does NOT match any SAN (only 127.0.0.1 is in SANs) |

### Docker Deployment Considerations

**Same Host (port mapping):**
```yaml
services:
  consumer:
    ports:
      - "8080:8080"
  provider:
    ports:
      - "8090:8090"
```
✅ Use `https://localhost:8090` - works with current SANs

**Separate Containers (Docker network):**
```yaml
services:
  consumer:
    container_name: consumer
  provider:
    container_name: provider
```

**Option 1:** Update SANs to include container names
```batch
# In generate-certificates.cmd
set SAN_CONNECTOR_B=DNS:localhost,DNS:connector-b,DNS:provider,IP:127.0.0.1
```

**Option 2:** Use extra_hosts to map container name to localhost
```yaml
services:
  consumer:
    extra_hosts:
      - "provider:127.0.0.1"
```
Then use `https://provider:8090` (resolves to localhost)

**Option 3:** Connect via service name if Docker DNS is configured properly
```yaml
services:
  consumer:
  provider:
    hostname: connector-b  # Match the SAN
```
Then use `https://connector-b:8090`

### Why Both Truststores Contain Intermediate CA

Both connector-a and connector-b have the **same truststore** (`dsp-truststore.p12`) containing the Intermediate CA. This creates a **circle of trust**:

```
Connector-A                          Connector-B
┌──────────────────┐                ┌──────────────────┐
│ Certificate:     │                │ Certificate:     │
│ connector-a.p12  │                │ connector-b.p12  │
│ (signed by       │                │ (signed by       │
│ Intermediate CA) │                │ Intermediate CA) │
└──────────────────┘                └──────────────────┘
         │                                   │
         │ Trusts                   Trusts   │
         ↓                                   ↓
┌────────────────────────────────────────────────────┐
│           Truststore (both sides)                  │
│           dsp-truststore.p12                       │
│           Contains: Intermediate CA                │
└────────────────────────────────────────────────────┘
```

**Result:** Each connector trusts certificates signed by the Intermediate CA, so they trust each other!

### Testing the Connection

**Start both connectors:**
```cmd
REM Terminal 1 - Provider (connector-b)
mvn spring-boot:run -Dspring-boot.run.profiles=provider

REM Terminal 2 - Consumer (connector-a)
mvn spring-boot:run -Dspring-boot.run.profiles=consumer
```

**Verify TLS is working:**
```cmd
REM Check provider is accessible
curl -v https://localhost:8090/actuator/health

REM Check for TLS handshake success (look for):
REM   * SSL connection using TLSv1.3
REM   * Server certificate:
REM   *   subject: CN=connector-b, ...
REM   *   issuer: CN=DSP Intermediate CA, ...
```

**Trigger negotiation from consumer:**
- Consumer should connect to Provider at `https://localhost:8090`
- Check logs for successful TLS handshake
- No PKIX exceptions should appear
- No hostname verification errors should appear

### Troubleshooting Connector-to-Connector TLS

**Issue:** `sun.security.validator.ValidatorException: PKIX path building failed`

**Cause:** Truststore doesn't contain Intermediate CA, or wrong truststore configured

**Solution:**
1. Verify truststore contains Intermediate CA:
   ```cmd
   keytool -list -keystore dsp-truststore.p12 -storepass password
   ```
2. Check application properties point to correct truststore:
   ```properties
   spring.ssl.bundle.jks.connector.truststore.location=classpath:dsp-truststore.p12
   ```

**Issue:** `java.security.cert.CertificateException: No subject alternative names matching`

**Cause:** URL hostname doesn't match any SAN in server certificate

**Solution:**
1. Check URL used to connect (e.g., `https://provider:8090`)
2. Check SANs in connector-b certificate:
   ```cmd
   keytool -list -v -keystore connector-b.p12 -storepass password | findstr "DNS:"
   ```
3. Either:
   - Change URL to use hostname that's in SANs (e.g., `https://localhost:8090`)
   - Or add hostname to SAN list and regenerate certificates

**Issue:** Connection refused / Connection timeout

**Cause:** Network issue, not TLS issue

**Solution:**
1. Verify provider is running: `curl http://localhost:8090/actuator/health`
2. Check firewall settings
3. Verify port is correct

## Verification Commands

### Check Root CA
```cmd
keytool -list -v -keystore dsp-root-ca.p12 -storepass password -storetype PKCS12
```

### Check Intermediate CA
```cmd
keytool -list -v -keystore dsp-intermediate-ca.p12 -storepass password -storetype PKCS12
```

### Check Server Certificate (connector-a)
```cmd
keytool -list -v -keystore connector-a.p12 -storepass password -storetype PKCS12
```

### Check Truststore
```cmd
keytool -list -v -keystore dsp-truststore.p12 -storepass password -storetype PKCS12
```

### Verify Certificate Chain
```cmd
REM Export certificates
keytool -exportcert -alias connector-a -keystore connector-a.p12 -storepass password -file connector-a.cer
keytool -exportcert -alias dsp-intermediate-ca -keystore dsp-truststore.p12 -storepass password -file intermediate.cer

REM Verify with OpenSSL (if available)
openssl verify -CAfile intermediate.cer connector-a.cer
```

## Troubleshooting

### PKIX Exception: unable to find valid certification path

**Cause**: Truststore doesn't contain the Intermediate CA certificate

**Fix**: 
1. Verify Intermediate CA is in truststore: `keytool -list -keystore dsp-truststore.p12`
2. Re-run `generate-certificates.cmd` to regenerate truststore

### Certificate has expired

**Cause**: Certificate validity period has passed

**Fix**: Re-run `generate-certificates.cmd` to generate new certificates

### Hostname verification failed

**Cause**: SAN list doesn't include the hostname you're connecting to

**Fix**: 
1. Edit `SAN_LIST` in `generate-certificates.cmd`
2. Re-run the script

### Self-signed certificate in certificate chain

**Cause**: Root CA is self-signed (this is normal)

**Fix**: This is expected behavior. The Intermediate CA (in truststore) validates the chain.

## Certificate Renewal

### Renewing Server Certificates (Annual)

1. Keep the same Root CA and Intermediate CA
2. Edit `generate-certificates.cmd` to only generate server certs
3. Or manually generate new CSR and sign with Intermediate CA

### Renewing Intermediate CA (Every 5 years)

1. Keep the same Root CA
2. Re-run the script to generate new Intermediate CA
3. Update truststore with new Intermediate CA
4. Re-generate all server certificates

### Renewing Root CA (Every 10 years)

1. Re-run the entire script
2. Distribute new truststore to all systems

## Production Recommendations

### 🔒 Security:

1. **Change passwords** - Don't use default "password"
2. **Secure Root CA** - Store `dsp-root-ca.p12` offline after generating Intermediate CA
3. **Secure Intermediate CA** - Store `dsp-intermediate-ca.p12` in a secure location
4. **Use HSM** - For production, consider Hardware Security Module for CA keys
5. **Enable OCSP** - Set `application.ocsp.validation.enabled=true` for revocation checking

### 📅 Validity:

1. **Server certificates**: 1 year (automated renewal recommended)
2. **Intermediate CA**: 3-5 years
3. **Root CA**: 10-20 years

### 🔑 Key Size:

Current script uses RSA 2048. For higher security:
- Edit `KEY_SIZE=4096` in the script
- Or use `KEY_ALG=EC` with `KEY_SIZE=384` for Elliptic Curve

### 📝 Distinguished Names:

Update the DN fields to match your organization:
```batch
set ROOT_DNAME=CN=Your Root CA, OU=Security, O=Your Company, L=City, ST=State, C=Country
```

## Summary

✅ **One script** generates complete PKI hierarchy  
✅ **Production-ready** certificate chain with Intermediate CA  
✅ **Configurable** SAN list and all parameters  
✅ **Automatic** truststore creation  
✅ **Verified** certificate chain validation  

Run `generate-certificates.cmd` and you're ready to go! 🎉

