# Linux/macOS Quick Start Guide

This guide provides Linux/macOS-specific instructions for certificate management in DSP TrueConnector.

## Prerequisites

### Required Tools

1. **Java JDK** (Java 11 or later)
   ```bash
   # Check if installed
   java -version
   javac -version
   
   # Install on Ubuntu/Debian
   sudo apt-get update
   sudo apt-get install default-jdk
   
   # Install on RHEL/CentOS/Fedora
   sudo yum install java-11-openjdk-devel
   
   # Install on macOS
   brew install openjdk@17
   ```

2. **OpenSSL** (Usually pre-installed)
   ```bash
   # Check if installed
   openssl version
   
   # Install on Ubuntu/Debian
   sudo apt-get install openssl
   
   # Install on RHEL/CentOS/Fedora
   sudo yum install openssl
   
   # Install on macOS
   brew install openssl
   ```

## Quick Start

### 1. Make Scripts Executable

```bash
cd doc/certificate
chmod +x generate-certificates.sh
chmod +x renew-certificates.sh
```

### 2. Generate Certificates

```bash
./generate-certificates.sh
```

This will create:
- Root CA and Intermediate CA
- connector-a.p12 and connector-b.p12
- MinIO certificates (PEM format)
- UI-A and UI-B certificates (PEM format with fullchain)
- Truststore

### 3. Customize Configuration (Optional)

Edit `generate-certificates.sh` before running:

```bash
# Certificate validity periods
ROOT_VALIDITY=3650          # 10 years
INTERMEDIATE_VALIDITY=1825  # 5 years
SERVER_VALIDITY=365         # 1 year

# Subject Alternative Names
SAN_CONNECTOR_A="DNS:localhost,DNS:connector-a,DNS:prod-connector-a.example.com,IP:127.0.0.1"
SAN_CONNECTOR_B="DNS:localhost,DNS:connector-b,DNS:prod-connector-b.example.com,IP:127.0.0.1"
SAN_MINIO="DNS:localhost,DNS:minio,DNS:s3.example.com,IP:127.0.0.1"
SAN_UI_A="DNS:localhost,DNS:ui-a,DNS:app-a.example.com,IP:127.0.0.1"
SAN_UI_B="DNS:localhost,DNS:ui-b,DNS:app-b.example.com,IP:127.0.0.1"

# Passwords (CHANGE IN PRODUCTION!)
ROOT_PASSWORD="your-secure-root-password"
INTERMEDIATE_PASSWORD="your-secure-intermediate-password"
SERVER_PASSWORD="your-secure-server-password"
TRUSTSTORE_PASSWORD="your-secure-truststore-password"
```

### 4. Renew Certificates

```bash
./renew-certificates.sh
```

Select from menu:
1. Renew connector-a certificate
2. Renew connector-b certificate
3. Renew minio certificate (PKCS12 + PEM)
4. Renew ui-a certificate (PEM + fullchain for nginx)
5. Renew ui-b certificate (PEM + fullchain for nginx)
6. Renew ALL server certificates
7. Exit

## Deployment

### Docker Compose

Mount certificates as volumes:

```yaml
version: '3.8'

services:
  connector-a:
    image: ghcr.io/engineering-research-and-development/dsp-true-connector:latest
    volumes:
      - ./doc/certificate:/cert:ro
    environment:
      - KEYSTORE_NAME=connector-a.p12
      - KEY_PASSWORD=password
      - KEYSTORE_PASSWORD=password
      - KEYSTORE_ALIAS=connector-a
      - TRUSTSTORE_NAME=dsp-truststore.p12
      - TRUSTSTORE_PASSWORD=password

  connector-b:
    image: ghcr.io/engineering-research-and-development/dsp-true-connector:latest
    volumes:
      - ./doc/certificate:/cert:ro
    environment:
      - KEYSTORE_NAME=connector-b.p12
      - KEY_PASSWORD=password
      - KEYSTORE_PASSWORD=password
      - KEYSTORE_ALIAS=connector-b
      - TRUSTSTORE_NAME=dsp-truststore.p12
      - TRUSTSTORE_PASSWORD=password

  minio:
    image: minio/minio:latest
    volumes:
      - ./doc/certificate/private.key:/root/.minio/certs/private.key:ro
      - ./doc/certificate/public.crt:/root/.minio/certs/public.crt:ro
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"

  ui-a:
    image: nginx:latest
    volumes:
      - ./doc/certificate/ui-a-fullchain.crt:/etc/nginx/ssl/ui-a-fullchain.crt:ro
      - ./doc/certificate/ui-a-cert.key:/etc/nginx/ssl/ui-a-cert.key:ro
      - ./nginx-a.conf:/etc/nginx/nginx.conf:ro
    ports:
      - "443:443"

  ui-b:
    image: nginx:latest
    volumes:
      - ./doc/certificate/ui-b-fullchain.crt:/etc/nginx/ssl/ui-b-fullchain.crt:ro
      - ./doc/certificate/ui-b-cert.key:/etc/nginx/ssl/ui-b-cert.key:ro
      - ./nginx-b.conf:/etc/nginx/nginx.conf:ro
    ports:
      - "8443:443"
```

### File Permissions

Set appropriate permissions for certificate files:

```bash
# Restrict access to private keys
chmod 600 doc/certificate/*.p12
chmod 600 doc/certificate/private.key
chmod 600 doc/certificate/*-cert.key

# Public certificates can be readable
chmod 644 doc/certificate/*.crt

# Make scripts executable
chmod 755 doc/certificate/*.sh
```

### Security Considerations

1. **Store CA keystores securely**:
   ```bash
   # Move Root CA offline after initial generation
   mkdir -p ~/.dsp-ca-backup
   chmod 700 ~/.dsp-ca-backup
   mv dsp-root-ca.p12 ~/.dsp-ca-backup/
   ```

2. **Use Docker secrets for production**:
   ```yaml
   secrets:
     connector_keystore:
       file: ./doc/certificate/connector-a.p12
     connector_password:
       external: true
   
   services:
     connector-a:
       secrets:
         - connector_keystore
         - connector_password
   ```

## Verification

### Check Certificate Expiration

```bash
# For PKCS12 files
keytool -list -v -keystore connector-a.p12 -storepass password | grep "Valid"

# For PEM files
openssl x509 -in public.crt -noout -dates
```

### Verify Certificate Chain

```bash
keytool -list -v -keystore connector-a.p12 -storepass password | grep -E "Alias|Owner|Issuer"
```

Expected output should show:
- dsp-root-ca (Root CA)
- dsp-intermediate-ca (Intermediate CA)
- connector-a (Server certificate)

### Test TLS Connection

```bash
# Test with curl
curl -v --cacert doc/certificate/dsp-truststore.p12:password https://localhost:8080

# Test with openssl
openssl s_client -connect localhost:8080 -CAfile doc/certificate/public.crt
```

## Automation

### Cron Job for Certificate Expiration Monitoring

```bash
# Edit crontab
crontab -e

# Add monitoring job (runs daily at 2 AM)
0 2 * * * /path/to/check-cert-expiry.sh

# Create monitoring script
cat > /usr/local/bin/check-cert-expiry.sh << 'EOF'
#!/bin/bash
CERT_DIR="/path/to/dsp-true-connector/doc/certificate"
ALERT_DAYS=30

for cert in connector-a connector-b; do
    expiry_date=$(keytool -list -v -keystore "${CERT_DIR}/${cert}.p12" -storepass password 2>/dev/null | grep "Valid until:" | sed 's/.*until: //')
    expiry_epoch=$(date -d "${expiry_date}" +%s)
    current_epoch=$(date +%s)
    days_remaining=$(( (expiry_epoch - current_epoch) / 86400 ))
    
    if [ ${days_remaining} -lt ${ALERT_DAYS} ]; then
        echo "WARNING: Certificate ${cert}.p12 expires in ${days_remaining} days!" | mail -s "Certificate Expiration Warning" admin@example.com
    fi
done
EOF

chmod +x /usr/local/bin/check-cert-expiry.sh
```

### Automated Renewal Script

```bash
#!/bin/bash
# auto-renew-certs.sh

CERT_DIR="/path/to/dsp-true-connector/doc/certificate"
cd "${CERT_DIR}"

# Renew all certificates non-interactively
echo "6" | ./renew-certificates.sh

# Restart services
docker-compose restart connector-a connector-b minio ui-a ui-b

# Send notification
echo "Certificates renewed successfully on $(date)" | mail -s "Certificate Renewal Complete" admin@example.com
```

## Troubleshooting

### Script Permission Denied

```bash
# Error: Permission denied
chmod +x generate-certificates.sh
chmod +x renew-certificates.sh
```

### OpenSSL Not Found

```bash
# Install OpenSSL
sudo apt-get install openssl    # Ubuntu/Debian
sudo yum install openssl         # RHEL/CentOS
brew install openssl             # macOS
```

### Java Not Found

```bash
# Add Java to PATH
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Or add to ~/.bashrc
echo 'export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

### Certificate Chain Validation Error

```bash
# Verify intermediate CA exists
ls -l doc/certificate/intermediate-ca.crt

# If missing, export from keystore
keytool -exportcert \
    -alias dsp-intermediate-ca \
    -keystore dsp-intermediate-ca.p12 \
    -storetype PKCS12 \
    -storepass password \
    -file intermediate-ca.crt \
    -rfc
```

## Differences from Windows Version

| Feature | Windows (.cmd) | Linux/macOS (.sh) |
|---------|----------------|-------------------|
| Line endings | CRLF | LF |
| Path separator | `\` | `/` |
| File deletion | `del` | `rm -f` |
| Variable syntax | `%VAR%` | `${VAR}` |
| Command check | `where` | `command -v` |
| Grep | `findstr` | `grep` |
| Concatenate files | `copy /b` | `cat` |
| Interactive input | `pause` | `read -p` |

The logic and certificate generation process are identical.

## Next Steps

1. Review [README.md](./README.md) for comprehensive documentation
2. Customize SANs and passwords in scripts
3. Generate certificates using `./generate-certificates.sh`
4. Deploy to your environment
5. Set up monitoring for expiration
6. Schedule regular renewals

---

**Platform Support**: Linux, macOS, Unix-like systems  
**Shell**: bash (sh-compatible)  
**Last Updated**: November 21, 2025

