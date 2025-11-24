# Certificate Generation Guide

## Overview

This guide provides step-by-step instructions for generating a complete PKI hierarchy for DSP TrueConnector, including Root CA, Intermediate CA, and all server certificates.

## Prerequisites

### Required Tools

1. **Java JDK** (Java 11 or later)
   - Verify: `java -version`
   - Required for `keytool` utility

2. **OpenSSL** (Recommended but optional)
   - Verify: `openssl version`
   - Required for PEM format conversion (MinIO, nginx)
   - Download: https://slproweb.com/products/Win32OpenSSL.html

3. **Windows Command Prompt** (cmd.exe)
   - The scripts are Windows batch files

### Directory Structure

Ensure you're in the correct directory:
```cmd
cd C:\path\to\dsp-true-connector\doc\certificate
```

## Quick Start

### Default Configuration (Recommended for Development/Testing)

1. Open Command Prompt as Administrator (optional, for file permission issues)

2. Navigate to the certificate directory:
   ```cmd
   cd doc\certificate
   ```

3. Run the generation script:
   ```cmd
   generate-certificates.cmd
   ```

4. Press any key when prompted to continue

5. Wait for the script to complete (approximately 2-5 minutes)

6. Verify generated files:
   ```cmd
   dir *.p12
   dir *.key
   dir *.crt
   ```

### Generated Files

After successful execution, you should have:

```
doc/certificate/
├── dsp-root-ca.p12              # Root CA keystore
├── dsp-intermediate-ca.p12      # Intermediate CA keystore
├── connector-a.p12              # Connector A certificate
├── connector-b.p12              # Connector B certificate
├── minio-temp.p12               # MinIO PKCS12 (temporary)
├── private.key                  # MinIO private key (PEM)
├── public.crt                   # MinIO certificate (PEM)
├── ui-a-temp.p12               # UI-A PKCS12 (temporary)
├── ui-a-cert.key               # UI-A private key (PEM)
├── ui-a-cert.crt               # UI-A certificate (PEM)
├── ui-a-fullchain.crt          # UI-A fullchain (PEM)
├── ui-b-temp.p12               # UI-B PKCS12 (temporary)
├── ui-b-cert.key               # UI-B private key (PEM)
├── ui-b-cert.crt               # UI-B certificate (PEM)
├── ui-b-fullchain.crt          # UI-B fullchain (PEM)
└── dsp-truststore.p12          # Truststore with CA certificates
```

##  Configuration

### Customizing Certificate Parameters

Edit `generate-certificates.cmd` to customize parameters:

#### 1. Certificate Validity Periods

```bat
REM Root CA Configuration
set ROOT_VALIDITY=3650          # 10 years (default)

REM Intermediate CA Configuration  
set INTERMEDIATE_VALIDITY=1825  # 5 years (default)

REM Server Certificate Configuration
set SERVER_VALIDITY=365         # 1 year (default)
```

**Recommendations**:
- **Development**: Keep defaults
- **Production**: 
  - Root CA: 10-20 years
  - Intermediate CA: 5-10 years
  - Server Certs: 1 year (maximum per CA/Browser Forum)

#### 2. Distinguished Names (DNs)

```bat
REM Root CA DN
set ROOT_DNAME=CN=DSP Root CA, OU=Security, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS

REM Intermediate CA DN
set INTERMEDIATE_DNAME=CN=DSP Intermediate CA, OU=Security, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS
```

**Customize for your organization**:
- `CN` (Common Name): Identifies the certificate
- `OU` (Organizational Unit): Department/Division
- `O` (Organization): Company name
- `L` (Locality): City
- `ST` (State): State/Province
- `C` (Country): Two-letter country code

#### 3. Subject Alternative Names (SANs)

**Critical for hostname validation**:

```bat
REM Configure SANs for each service
set SAN_CONNECTOR_A=DNS:localhost,DNS:connector-a,IP:127.0.0.1
set SAN_CONNECTOR_B=DNS:localhost,DNS:connector-b,IP:127.0.0.1
set SAN_MINIO=DNS:localhost,DNS:minio,IP:127.0.0.1
set SAN_UI_A=DNS:localhost,DNS:ui-a,IP:127.0.0.1
set SAN_UI_B=DNS:localhost,DNS:ui-b,IP:127.0.0.1
```

**Add production hostnames**:
```bat
set SAN_CONNECTOR_A=DNS:localhost,DNS:connector-a,DNS:connector-a.example.com,IP:127.0.0.1,IP:10.0.1.100
```

**Important**: 
- Include all hostnames/IPs the service will be accessed by
- Use fully qualified domain names (FQDNs) for production
- Include Docker service names for container environments

#### 4. Passwords

```bat
REM Password configuration
set ROOT_PASSWORD=password
set INTERMEDIATE_PASSWORD=password
set SERVER_PASSWORD=password
set TRUSTSTORE_PASSWORD=password
```

**Security Best Practices**:
- ⚠️ Change default passwords for production!
- Use different passwords for each component
- Store passwords securely (e.g., HashiCorp Vault, Azure Key Vault)
- Minimum 16 characters with mixed case, numbers, symbols

#### 5. Key Algorithm and Size

```bat
REM Key configuration
set KEY_ALG=RSA
set KEY_SIZE=2048
```

**Options**:
- RSA 2048: Standard, fast, widely supported
- RSA 4096: Higher security, slower performance
- ECC: Consider for future versions

## Step-by-Step Generation Process

The script executes the following steps:

### Step 1: Generate Root CA (Self-Signed)

```cmd
keytool -genkeypair \
    -alias dsp-root-ca \
    -keyalg RSA \
    -keysize 2048 \
    -dname "CN=DSP Root CA, OU=Security, O=DSP True Connector, ..." \
    -validity 3650 \
    -keystore dsp-root-ca.p12 \
    -storetype PKCS12 \
    -storepass password \
    -keypass password \
    -ext BasicConstraints:critical=ca:true \
    -ext KeyUsage:critical=keyCertSign,cRLSign
```

**What happens**:
- Creates a new RSA key pair
- Generates a self-signed certificate
- Stores in PKCS12 keystore
- Marks as CA with certificate signing capability

**Export Root CA certificate** (for reference):
```cmd
keytool -exportcert \
    -alias dsp-root-ca \
    -keystore dsp-root-ca.p12 \
    -file root-ca.crt \
    -rfc
```

### Step 2: Generate Intermediate CA

**2a. Generate key pair**:
```cmd
keytool -genkeypair \
    -alias dsp-intermediate-ca \
    -keyalg RSA \
    -keysize 2048 \
    -dname "CN=DSP Intermediate CA, OU=Security, O=DSP True Connector, ..." \
    -validity 1825 \
    -keystore dsp-intermediate-ca.p12 \
    -ext BasicConstraints:critical=ca:true,pathlen:0 \
    -ext KeyUsage:critical=keyCertSign,cRLSign
```

**2b. Create Certificate Signing Request (CSR)**:
```cmd
keytool -certreq \
    -alias dsp-intermediate-ca \
    -keystore dsp-intermediate-ca.p12 \
    -file intermediate-ca.csr
```

**2c. Sign with Root CA**:
```cmd
keytool -gencert \
    -alias dsp-root-ca \
    -keystore dsp-root-ca.p12 \
    -infile intermediate-ca.csr \
    -outfile intermediate-ca.crt \
    -validity 1825 \
    -ext BasicConstraints:critical=ca:true,pathlen:0 \
    -ext KeyUsage:critical=keyCertSign,cRLSign
```

**2d. Import certificate chain**:
```cmd
# Import Root CA
keytool -importcert \
    -alias dsp-root-ca \
    -keystore dsp-intermediate-ca.p12 \
    -file root-ca.crt \
    -noprompt

# Import Intermediate CA certificate
keytool -importcert \
    -alias dsp-intermediate-ca \
    -keystore dsp-intermediate-ca.p12 \
    -file intermediate-ca.crt \
    -noprompt
```

### Step 3: Generate Server Certificates

For each server (connector-a, connector-b, etc.):

**3a. Generate key pair**:
```cmd
keytool -genkeypair \
    -alias connector-a \
    -keyalg RSA \
    -keysize 2048 \
    -dname "CN=connector-a, OU=Connectors, O=DSP True Connector, ..." \
    -validity 365 \
    -keystore connector-a.p12 \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=DNS:localhost,DNS:connector-a,IP:127.0.0.1"
```

**3b. Create CSR**:
```cmd
keytool -certreq \
    -alias connector-a \
    -keystore connector-a.p12 \
    -file connector-a.csr \
    -ext "SAN=DNS:localhost,DNS:connector-a,IP:127.0.0.1"
```

**3c. Sign with Intermediate CA**:
```cmd
keytool -gencert \
    -alias dsp-intermediate-ca \
    -keystore dsp-intermediate-ca.p12 \
    -infile connector-a.csr \
    -outfile connector-a.crt \
    -validity 365 \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=DNS:localhost,DNS:connector-a,IP:127.0.0.1"
```

**3d. Import certificate chain**:
```cmd
# Import Root CA
keytool -importcert -alias dsp-root-ca -keystore connector-a.p12 -file root-ca.crt -noprompt

# Import Intermediate CA
keytool -importcert -alias dsp-intermediate-ca -keystore connector-a.p12 -file intermediate-ca.crt -noprompt

# Import server certificate
keytool -importcert -alias connector-a -keystore connector-a.p12 -file connector-a.crt -noprompt
```

### Step 4: Generate MinIO Certificate (PEM Format)

**4a. Generate PKCS12** (same as Step 3)

**4b. Convert to PEM**:
```cmd
# Extract private key
openssl pkcs12 -in minio-temp.p12 -nocerts -nodes -passin pass:password -out private.key

# Extract certificate
openssl pkcs12 -in minio-temp.p12 -clcerts -nokeys -passin pass:password -out public.crt
```

### Step 5: Generate UI Certificates (PEM Format with Fullchain)

**5a. Generate PKCS12** (same as Step 3)

**5b. Convert to PEM**:
```cmd
# Extract private key
openssl pkcs12 -in ui-a-temp.p12 -nocerts -nodes -out ui-a-cert.key

# Extract certificate
openssl pkcs12 -in ui-a-temp.p12 -clcerts -nokeys -out ui-a-cert.crt

# Create fullchain (cert + intermediate CA)
copy /b ui-a-cert.crt + intermediate-ca.crt ui-a-fullchain.crt
```

**Why fullchain?**  
Nginx and many web servers require the full certificate chain (server cert + intermediate CA) in a single file.

### Step 6: Create Truststore

```cmd
# Import Intermediate CA
keytool -importcert \
    -trustcacerts \
    -alias dsp-intermediate-ca \
    -file intermediate-ca.crt \
    -keystore dsp-truststore.p12 \
    -storepass password \
    -noprompt

# Import Root CA (optional)
keytool -importcert \
    -trustcacerts \
    -alias dsp-root-ca \
    -file root-ca.crt \
    -keystore dsp-truststore.p12 \
    -storepass password \
    -noprompt
```

## Verification

### Verify Certificate Chain

```cmd
keytool -list -v -keystore connector-a.p12 -storepass password
```

**Expected output** should show:
```
Keystore type: PKCS12
Keystore provider: SUN

Your keystore contains 3 entries

Alias name: dsp-root-ca
Entry type: trustedCertEntry
...
Owner: CN=DSP Root CA, OU=Security, O=DSP True Connector, ...
Issuer: CN=DSP Root CA, OU=Security, O=DSP True Connector, ...

Alias name: dsp-intermediate-ca
Entry type: trustedCertEntry
...
Owner: CN=DSP Intermediate CA, OU=Security, O=DSP True Connector, ...
Issuer: CN=DSP Root CA, OU=Security, O=DSP True Connector, ...

Alias name: connector-a
Entry type: PrivateKeyEntry
Certificate chain length: 3
Certificate[1]:
Owner: CN=connector-a, OU=Connectors, O=DSP True Connector, ...
Issuer: CN=DSP Intermediate CA, OU=Security, O=DSP True Connector, ...
```

### Verify SANs

```cmd
keytool -list -v -keystore connector-a.p12 -storepass password | findstr "DNS IP"
```

**Expected**:
```
DNS:localhost, DNS:connector-a, IP:127.0.0.1
```

### Verify Truststore

```cmd
keytool -list -v -keystore dsp-truststore.p12 -storepass password
```

**Should contain**:
- dsp-intermediate-ca (Entry type: trustedCertEntry)
- dsp-root-ca (Entry type: trustedCertEntry)

### Test TLS Connection

Using curl (if available):
```cmd
curl -v --cacert dsp-truststore.p12 --key connector-a.p12:password https://localhost:8080
```

## Troubleshooting

### Issue: "keytool: command not found"

**Solution**: Add Java bin directory to PATH
```cmd
set PATH=%PATH%;C:\Program Files\Java\jdk-17\bin
```

Or use full path:
```cmd
"C:\Program Files\Java\jdk-17\bin\keytool.exe" -version
```

### Issue: "openssl: command not found"

**Impact**: Cannot generate PEM files for MinIO/nginx

**Solutions**:
1. Install OpenSSL from https://slproweb.com/products/Win32OpenSSL.html
2. Or manually convert later using online tools
3. Or use Java-only solution (keytool export, then manual conversion)

### Issue: Certificate already exists

**Symptom**: Error about alias already existing

**Solution**: The script cleans up old files automatically. If it persists:
```cmd
# Delete old certificates
del *.p12
del *.key
del *.crt
del *.csr

# Re-run script
generate-certificates.cmd
```

### Issue: "Invalid keystore format"

**Cause**: Corrupted keystore file

**Solution**:
```cmd
# Delete corrupted file
del dsp-root-ca.p12

# Re-run script
generate-certificates.cmd
```

## Advanced Topics

### Generating Certificates for Additional Services

To add a new service (e.g., connector-c):

1. Edit `generate-certificates.cmd`
2. Add SAN configuration:
   ```bat
   set SAN_CONNECTOR_C=DNS:localhost,DNS:connector-c,IP:127.0.0.1
   ```
3. Add generation call in STEP 3:
   ```bat
   call :GenerateServerCert connector-c "CN=connector-c, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "%SAN_CONNECTOR_C%"
   ```

### Using External CA

If you have an existing Intermediate CA from your organization:

1. Skip Root CA and Intermediate CA generation
2. Import your Intermediate CA into the truststore
3. Use your Intermediate CA to sign server certificates
4. Update script to reference your CA keystore

### Automating Certificate Generation

For CI/CD pipelines:

```bat
REM Run non-interactively
echo Y | generate-certificates.cmd

REM Or modify script to remove pause statement
```

## Security Checklist

Before deploying generated certificates:

- [ ] Changed all default passwords
- [ ] Verified SANs include all required hostnames
- [ ] Backed up Root CA keystore to secure location
- [ ] Documented keystore passwords in secure vault
- [ ] Set appropriate file permissions (restrict access)
- [ ] Tested TLS connectivity between services
- [ ] Configured certificate expiration monitoring

## Next Steps

1. **Deploy Certificates**: See main [README.md](./README.md) for deployment instructions
2. **Configure Services**: Update application.properties, nginx.conf, etc.
3. **Test Connectivity**: Verify TLS handshakes between services
4. **Set Up Monitoring**: Configure alerts for certificate expiration

---

**Last Updated**: November 2025  
**Version**: 1.0

