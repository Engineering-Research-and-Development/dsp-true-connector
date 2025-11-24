# Certificate Renewal Guide

## Overview

This guide explains how to renew server certificates when they expire, without regenerating the entire PKI infrastructure (Root CA and Intermediate CA remain unchanged).

## When to Renew

### Certificate Expiration Timeline

| Certificate Type | Validity Period | Renewal Timing | Frequency |
|-----------------|-----------------|----------------|-----------|
| Server Certificates | 1 year | 30 days before expiration | Annually |
| Intermediate CA | 5 years | 90 days before expiration | Every 5 years |
| Root CA | 10 years | 180 days before expiration | Every 10 years |

### Check Certificate Expiration

**Using keytool**:
```cmd
keytool -list -v -keystore connector-a.p12 -storepass password | findstr "Valid"
```

**Expected output**:
```
Valid from: Thu Nov 21 10:00:00 CET 2024 until: Fri Nov 21 10:00:00 CET 2025
```

**Using OpenSSL** (for PEM files):
```cmd
openssl x509 -in public.crt -noout -dates
```

## Quick Start

### Renew Single Certificate

1. Navigate to certificate directory:
   ```cmd
   cd doc\certificate
   ```

2. Run renewal script:
   ```cmd
   renew-certificates.cmd
   ```

3. Select certificate to renew from menu:
   ```
   ==================================================================
   DSP True Connector - Certificate Renewal Script
   ==================================================================
   
   Select certificate(s) to renew:
   
     1. Renew connector-a certificate
     2. Renew connector-b certificate
     3. Renew minio certificate (PKCS12 + PEM)
     4. Renew ui-a certificate (PEM + fullchain for nginx)
     5. Renew ui-b certificate (PEM + fullchain for nginx)
     6. Renew ALL server certificates
     7. Exit
   ```

4. Enter choice (1-7) and press Enter

5. Wait for renewal to complete

6. Deploy renewed certificate and restart service

### Renew All Certificates

For bulk renewal (recommended during maintenance window):

1. Run renewal script:
   ```cmd
   renew-certificates.cmd
   ```

2. Select option **6** (Renew ALL server certificates)

3. Script will renew:
   - connector-a
   - connector-b
   - minio (PKCS12 + PEM)
   - ui-a (PEM + fullchain)
   - ui-b (PEM + fullchain)

4. Deploy all renewed certificates

5. Restart all services

## Prerequisites

### Required Files

Before renewing, ensure these files exist:

```
doc/certificate/
├── dsp-intermediate-ca.p12     # Required - signs new certificates
├── intermediate-ca.crt          # Required - for certificate chain
└── root-ca.crt                  # Optional - for complete chain
```

**If missing**, the script will attempt to export from keystore:
```cmd
# Export Intermediate CA certificate
keytool -exportcert \
    -alias dsp-intermediate-ca \
    -keystore dsp-intermediate-ca.p12 \
    -storetype PKCS12 \
    -storepass password \
    -file intermediate-ca.crt \
    -rfc

# Export Root CA certificate
keytool -exportcert \
    -alias dsp-root-ca \
    -keystore dsp-intermediate-ca.p12 \
    -storetype PKCS12 \
    -storepass password \
    -file root-ca.crt \
    -rfc
```

### Tools Required

- **Java JDK** with keytool
- **OpenSSL** (for PEM conversions - MinIO, nginx)
- **Windows Command Prompt**

## Renewal Process Details

### What Gets Renewed?

The renewal script:
- ✅ Generates new private key for server certificate
- ✅ Creates new Certificate Signing Request (CSR)
- ✅ Signs with existing Intermediate CA
- ✅ Maintains same Subject Alternative Names (SANs)
- ✅ Extends validity period (1 year from renewal date)

### What Stays the Same?

- ❌ Root CA (unchanged)
- ❌ Intermediate CA (unchanged)
- ❌ Truststore (unchanged - no update needed)
- ❌ Certificate chain structure (unchanged)

### Backup Strategy

The script automatically backs up old certificates:

```
connector-a.p12.backup_20251121_143022
```

**Recommendation**: Keep backups for 30 days after successful deployment.

## Step-by-Step Renewal

### Renewing Connector Certificates

**Example: connector-a**

1. **Select renewal option**:
   ```cmd
   renew-certificates.cmd
   # Choose: 1. Renew connector-a certificate
   ```

2. **Script performs**:
   ```cmd
   # Backup old certificate
   copy connector-a.p12 connector-a.p12.backup_[timestamp]
   
   # Delete old certificate
   del connector-a.p12
   
   # Generate new key pair
   keytool -genkeypair -alias connector-a ...
   
   # Create CSR
   keytool -certreq -alias connector-a ...
   
   # Sign with Intermediate CA
   keytool -gencert -alias dsp-intermediate-ca ...
   
   # Import certificate chain
   keytool -importcert ... (Root CA)
   keytool -importcert ... (Intermediate CA)
   keytool -importcert ... (Server certificate)
   ```

3. **Verify new certificate**:
   ```cmd
   keytool -list -v -keystore connector-a.p12 -storepass password | findstr "Valid"
   ```

4. **Deploy and restart**:
   ```cmd
   # Copy to Docker volume or config directory
   copy connector-a.p12 C:\path\to\docker\tc_cert\
   
   # Restart connector
   docker-compose restart connector-a
   ```

### Renewing MinIO Certificate

MinIO requires PEM format (separate key and certificate files).

1. **Select renewal option**:
   ```cmd
   renew-certificates.cmd
   # Choose: 3. Renew minio certificate (PKCS12 + PEM)
   ```

2. **Script generates**:
   - `minio-temp.p12` - PKCS12 format (temporary)
   - `private.key` - Private key in PEM format
   - `public.crt` - Certificate in PEM format

3. **Deploy to MinIO**:
   ```cmd
   # Copy to MinIO certs directory
   copy private.key C:\path\to\minio\certs\
   copy public.crt C:\path\to\minio\certs\
   
   # Or update Docker volume
   # docker-compose.yml should mount:
   # - ./private.key:/root/.minio/certs/private.key:ro
   # - ./public.crt:/root/.minio/certs/public.crt:ro
   ```

4. **Restart MinIO**:
   ```cmd
   docker-compose restart minio
   ```

### Renewing UI Certificates (nginx)

UI certificates require fullchain format for nginx.

1. **Select renewal option**:
   ```cmd
   renew-certificates.cmd
   # Choose: 4. Renew ui-a certificate (PEM + fullchain for nginx)
   ```

2. **Script generates**:
   - `ui-a-temp.p12` - PKCS12 format (temporary)
   - `ui-a-cert.key` - Private key in PEM format
   - `ui-a-cert.crt` - Certificate in PEM format
   - `ui-a-fullchain.crt` - Full certificate chain (cert + intermediate CA)

3. **Deploy to nginx**:
   ```cmd
   # Copy to nginx SSL directory
   copy ui-a-cert.key C:\path\to\nginx\ssl\
   copy ui-a-fullchain.crt C:\path\to\nginx\ssl\
   
   # Or update Docker volume
   # - ./ui-a-cert.key:/etc/nginx/ssl/ui-a-cert.key:ro
   # - ./ui-a-fullchain.crt:/etc/nginx/ssl/ui-a-fullchain.crt:ro
   ```

4. **Test nginx configuration**:
   ```cmd
   docker exec ui-a nginx -t
   ```

5. **Reload nginx** (graceful):
   ```cmd
   docker exec ui-a nginx -s reload
   ```

   Or restart:
   ```cmd
   docker-compose restart ui-a
   ```

## Customizing Renewal Parameters

### Modify Subject Alternative Names (SANs)

Edit `renew-certificates.cmd`:

```bat
REM Add production hostnames
set SAN_CONNECTOR_A=DNS:localhost,DNS:connector-a,DNS:connector-a.prod.example.com,IP:127.0.0.1,IP:10.0.1.100
```

### Change Validity Period

```bat
REM Extend validity to 2 years (if policy allows)
set SERVER_VALIDITY=730
```

**Note**: Many CAs and browsers limit server certificate validity to 397 days (13 months). Check your organization's policy.

### Update Passwords

```bat
REM Use different passwords per environment
set INTERMEDIATE_PASSWORD=prod-intermediate-secret
set SERVER_PASSWORD=prod-server-secret
```

## Deployment Checklist

Before deploying renewed certificates:

- [ ] Backup old certificates
- [ ] Verify new certificate validity dates
- [ ] Check Subject Alternative Names (SANs)
- [ ] Test certificate chain validation
- [ ] Schedule maintenance window
- [ ] Notify stakeholders of service restart
- [ ] Prepare rollback plan

After deployment:

- [ ] Verify service starts successfully
- [ ] Test TLS connectivity
- [ ] Check application logs for certificate errors
- [ ] Verify inter-service communication (connector-to-connector)
- [ ] Update certificate inventory/documentation
- [ ] Schedule next renewal reminder (30 days before expiration)

## Troubleshooting

### Issue: "Intermediate CA keystore not found"

**Symptom**:
```
ERROR: dsp-intermediate-ca.p12 not found!
Please run generate-certificates.cmd first to create the CA hierarchy.
```

**Solution**:
You need the Intermediate CA to sign new certificates. Either:
1. Locate the original `dsp-intermediate-ca.p12` file
2. Or regenerate entire PKI using `generate-certificates.cmd`

### Issue: "Certificate chain import failed"

**Symptom**:
```
ERROR: Failed to import certificate chain for connector-a
```

**Causes**:
- Intermediate CA certificate file missing
- Root CA certificate file missing
- Certificate signed with wrong CA

**Solution**:
```cmd
# Export CA certificates from keystore
keytool -exportcert -alias dsp-intermediate-ca -keystore dsp-intermediate-ca.p12 -file intermediate-ca.crt -rfc
keytool -exportcert -alias dsp-root-ca -keystore dsp-intermediate-ca.p12 -file root-ca.crt -rfc

# Re-run renewal
renew-certificates.cmd
```

### Issue: "OpenSSL not found"

**Impact**: Cannot generate PEM files for MinIO/nginx

**Workaround**:
1. Install OpenSSL from https://slproweb.com/products/Win32OpenSSL.html
2. Or manually convert PKCS12 to PEM:
   ```cmd
   # Use online converter or install OpenSSL later
   # The .p12 files are generated successfully
   ```

### Issue: Service won't start after renewal

**Symptom**: Service fails to start with SSL/TLS errors

**Checklist**:
1. **Verify file permissions**:
   ```cmd
   # Ensure service can read certificate files
   icacls connector-a.p12
   ```

2. **Check file paths**:
   ```cmd
   # Verify paths in application.properties match actual locations
   ```

3. **Validate certificate**:
   ```cmd
   keytool -list -v -keystore connector-a.p12 -storepass password
   # Check: Valid from/until dates
   # Check: Certificate chain has 3 entries
   ```

4. **Rollback if needed**:
   ```cmd
   # Restore backup
   copy connector-a.p12.backup_[timestamp] connector-a.p12
   docker-compose restart connector-a
   ```

### Issue: TLS handshake fails after renewal

**Symptom**: Connector A cannot connect to Connector B after renewal

**Causes**:
- Only one connector certificate was renewed
- Truststore not updated (shouldn't be needed)
- Hostname verification failure

**Solution**:
1. **Verify both sides have valid certificates**:
   ```cmd
   # Check connector-a
   keytool -list -v -keystore connector-a.p12 -storepass password | findstr "Valid"
   
   # Check connector-b
   keytool -list -v -keystore connector-b.p12 -storepass password | findstr "Valid"
   ```

2. **Verify truststore**:
   ```cmd
   keytool -list -v -keystore dsp-truststore.p12 -storepass password
   # Should contain: dsp-intermediate-ca
   ```

3. **Test connectivity**:
   ```cmd
   # From connector-a container
   docker exec connector-a curl -v --cacert /cert/dsp-truststore.p12 https://connector-b:8080
   ```

## Renewing Intermediate CA

**⚠️ Major Operation** - Affects all server certificates

### When Needed

- Intermediate CA expires in 90 days
- Intermediate CA private key compromised
- Upgrading cryptographic standards

### Process Overview

1. **Generate new Intermediate CA**:
   ```cmd
   # Use generate-certificates.cmd but skip server certs
   # Or manually:
   keytool -genkeypair -alias dsp-intermediate-ca-v2 ...
   keytool -certreq -alias dsp-intermediate-ca-v2 ...
   keytool -gencert -alias dsp-root-ca ... (sign with Root CA)
   ```

2. **Update truststore**:
   ```cmd
   # Import new Intermediate CA
   keytool -importcert -alias dsp-intermediate-ca-v2 -file intermediate-ca-v2.crt -keystore dsp-truststore.p12
   ```

3. **Renew all server certificates**:
   ```cmd
   # Sign with new Intermediate CA
   renew-certificates.cmd
   # Option 6: Renew ALL server certificates
   ```

4. **Deploy in phases**:
   - Update truststore on all services first
   - Then update server certificates one by one
   - Allows old and new certificates to coexist during transition

5. **Remove old Intermediate CA from truststore** (after all services migrated):
   ```cmd
   keytool -delete -alias dsp-intermediate-ca -keystore dsp-truststore.p12
   ```

## Automation

### Scheduled Renewal

Create a scheduled task for proactive renewal:

```bat
@echo off
REM scheduled-renewal.bat

cd C:\path\to\dsp-true-connector\doc\certificate

REM Check certificate expiration
FOR %%C IN (connector-a connector-b) DO (
    keytool -list -v -keystore %%C.p12 -storepass password > temp.txt
    findstr /C:"Valid until:" temp.txt
    REM Add logic to check if expiring within 30 days
    REM If yes, run renewal and send notification
)

del temp.txt
```

### Integration with Monitoring

Export certificate expiration metrics to monitoring system:

```powershell
# PowerShell example
$cert = keytool -list -v -keystore connector-a.p12 -storepass password
$expiryDate = [DateTime]::Parse($cert -match "Valid until:" ...)
$daysUntilExpiry = ($expiryDate - (Get-Date)).Days

# Send to monitoring (e.g., Prometheus, Grafana)
```

## Best Practices

1. **Renew Early**: Don't wait until last day
   - Set reminder 30 days before expiration
   - Allows time for testing and rollback

2. **Test in Non-Production First**:
   - Renew dev environment certificates
   - Verify process works
   - Document any issues

3. **Coordinate Renewals**:
   - Schedule during maintenance window
   - Inform stakeholders
   - Have rollback plan ready

4. **Document Each Renewal**:
   - Date of renewal
   - Who performed it
   - Any issues encountered
   - Next renewal date

5. **Maintain Certificate Inventory**:
   ```
   Certificate     | Issued    | Expires   | Renewed By | Notes
   --------------- | --------- | --------- | ---------- | -----
   connector-a     | 2024-11-21| 2025-11-21| admin      | Production
   connector-b     | 2024-11-21| 2025-11-21| admin      | Production
   minio           | 2024-11-21| 2025-11-21| admin      | S3 storage
   ```

## Next Steps

After successful renewal:

1. **Update Documentation**: Record renewal in change log
2. **Set Calendar Reminder**: Next renewal 30 days before expiration
3. **Review Logs**: Check for any TLS-related warnings
4. **Update Monitoring**: Verify certificate expiration alerts working

---

**Last Updated**: November 2025  
**Version**: 1.0

