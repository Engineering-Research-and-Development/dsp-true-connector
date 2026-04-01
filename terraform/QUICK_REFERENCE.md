# Quick Reference - Terraform Configuration

## ⚠️ SECURITY: Credentials Now in Kubernetes Secrets

**IMPORTANT**: Sensitive credentials are now stored in **Kubernetes Secrets**, NOT ConfigMaps.

### Secrets vs ConfigMaps

| Component | Storage | Sensitive? | Encryption | RBAC |
|-----------|---------|-----------|-----------|------|
| **ConfigMaps** | `dsp-connector-a-config` | ❌ No | No | Shared |
| **Secrets** | `dsp-connector-a-credentials` | ✅ Yes | Optional | Strict |

### Credentials in Secrets (Secure)
The following are stored in Kubernetes Secrets and injected as environment variables:
- ✅ `S3_ACCESS_KEY` - From Secret key `s3_access_key`
- ✅ `S3_SECRET_KEY` - From Secret key `s3_secret_key`
- ✅ `KEYSTORE_PASSWORD` - From Secret key `keystore_password`
- ✅ `KEY_PASSWORD` - From Secret key `key_password`
- ✅ `TRUSTSTORE_PASSWORD` - From Secret key `truststore_password`
- ✅ `DAPS_KEYSTORE_PASSWORD` - From Secret key `daps_keystore_password`

### Configuration in ConfigMaps (Non-sensitive)
Only non-sensitive configuration values are in ConfigMaps:
- ❌ Server ports, hostnames, database names
- ❌ Endpoint URLs (S3, MongoDB)
- ❌ SSL settings, region codes

---

## 0. Docker Images
| Property | Variable | Default |
|----------|----------|---------|
| MongoDB Image | `mongodb_image` | `"mongo:7.0.12"` |
| MinIO Image | `minio_image` | `"minio/minio:RELEASE.2025-04-22T22-12-26Z"` |
| Connector Image | `connector_image` | `"ghcr.io/.../dsp-true-connector:0.6.4"` |
| Connector UI Image | `connector_ui_image` | `"ghcr.io/.../dsp-true-connector-ui:0.6.1"` |

---

## 1. Application Configuration
| Property | Variable | Default |
|----------|----------|---------|
| `application.automatic.transfer` | `connector_a_config.automatic_transfer` | `false` |
| `application.automatic.negotiation` | `connector_a_config.automatic_negotiation` | `false` |

---

## 2. MongoDB Connection
| Property | Variable | Default |
|----------|----------|---------|
| `spring.data.mongodb.host` | `connector_a_config.mongodb_host` | `"mongodb"` |
| `spring.data.mongodb.port` | `connector_a_config.mongodb_port` | `27017` |
| `spring.data.mongodb.database` | `connector_a_config.mongodb_database` | `"true_connector_a"` |

---

## 3. SSL Configuration
| Property | Variable | Default |
|----------|----------|---------|
| `server.ssl.enabled` | `connector_a_config.ssl_enabled` | `false` |

---

## 4. S3 Configuration (Non-Sensitive in ConfigMap)
## 4. S3 Configuration

### Non-Sensitive (ConfigMap)
| Property | Variable | Default (A / B) |
|----------|----------|---------|
| `s3.endpoint` | `connector_a_config.s3_endpoint` | `"http://minio:9000"` |
| `s3.region` | `connector_a_config.s3_region` | `"us-east-1"` |
| `s3.bucketName` | `connector_a_config.s3_bucket_name` | `"dsp-true-connector-a"` / `"dsp-true-connector-b"` |
| `s3.externalPresignedEndpoint` | `connector_a_config.s3_external_endpoint` | `"http://localhost:9000"` / `"http://172.17.0.1:9000"` |

### Sensitive (Kubernetes Secret - Environment Variables)
| Property | Secret Key | Injected As | Default (A / B) |
|----------|-----------|------------|---------|
| `s3.accessKey` | `s3_access_key` | `$${S3_ACCESS_KEY}` | `"minioadmin"` |
| `s3.secretKey` | `s3_secret_key` | `$${S3_SECRET_KEY}` | `"minioadmin"` |

---

## 5. Keystore/Truststore Configuration (Secrets)

All keystore and truststore passwords are stored in Kubernetes Secrets and injected as environment variables:

| Property | Secret Key | Injected As |
|----------|-----------|------------|
| Keystore Password | `keystore_password` | `$${KEYSTORE_PASSWORD}` |
| Key Password | `key_password` | `$${KEY_PASSWORD}` |
| Truststore Password | `truststore_password` | `$${TRUSTSTORE_PASSWORD}` |
| DAPS Keystore Password | `daps_keystore_password` | `$${DAPS_KEYSTORE_PASSWORD}` |

---

## How Application Properties Are Generated

The `application.properties` file is generated from templates and uses environment variable placeholders for secrets:

**Placeholder Syntax in Template:**
```properties
s3.accessKey=$${S3_ACCESS_KEY}
s3.secretKey=$${S3_SECRET_KEY}
spring.data.mongodb.password=$${MONGODB_PASSWORD}
```

**At Runtime:**
- Placeholders are replaced with actual values from environment variables
- Environment variables are sourced from Kubernetes Secrets
- Never stored in ConfigMaps or application code

---

## Kubernetes Resources Created

## Kubernetes Resources Created

### ConfigMaps (Non-Sensitive)
```bash
# Connector A
kubectl get configmap dsp-connector-a-config      # application.properties
kubectl get configmap connector-a-env             # Non-sensitive env vars
kubectl get configmap dsp-connector-a-certs       # Certificate files
kubectl get configmap dsp-connector-a-initial-data

# Connector B
kubectl get configmap dsp-connector-b-config
kubectl get configmap connector-b-env
kubectl get configmap dsp-connector-b-certs
kubectl get configmap dsp-connector-b-initial-data
```

### Secrets (Sensitive - NEW!)
```bash
# Connector A
kubectl get secret dsp-connector-a-credentials    # S3 keys, passwords, keystore credentials
kubectl get secret connector-a-tls                # TLS certificates (if applicable)

# Connector B
kubectl get secret dsp-connector-b-credentials
kubectl get secret connector-b-tls
```

---

## Pod Deployment Configuration

### Environment Variables from Secrets
Your pod deployment must inject credentials from Secrets:

```yaml
spec:
  containers:
  - name: connector-a
    env:
      # From ConfigMap (non-sensitive)
      - name: CALLBACK_ADDRESS
        valueFrom:
          configMapKeyRef:
            name: connector-a-env
            key: CALLBACK_ADDRESS
      - name: KEYSTORE_NAME
        valueFrom:
          configMapKeyRef:
            name: connector-a-env
            key: KEYSTORE_NAME
            
      # From Secret (sensitive credentials)
      - name: S3_ACCESS_KEY
        valueFrom:
          secretKeyRef:
            name: dsp-connector-a-credentials
            key: s3_access_key
      - name: S3_SECRET_KEY
        valueFrom:
          secretKeyRef:
            name: dsp-connector-a-credentials
            key: s3_secret_key
      - name: KEYSTORE_PASSWORD
        valueFrom:
          secretKeyRef:
            name: dsp-connector-a-credentials
            key: keystore_password
      - name: KEY_PASSWORD
        valueFrom:
          secretKeyRef:
            name: dsp-connector-a-credentials
            key: key_password
      - name: TRUSTSTORE_PASSWORD
        valueFrom:
          secretKeyRef:
            name: dsp-connector-a-credentials
            key: truststore_password
      - name: DAPS_KEYSTORE_PASSWORD
        valueFrom:
          secretKeyRef:
            name: dsp-connector-a-credentials
            key: daps_keystore_password
    
    volumeMounts:
    - name: config
      mountPath: /etc/config
    - name: certs
      mountPath: /etc/certs
  
  volumes:
  - name: config
    configMap:
      name: dsp-connector-a-config
  - name: certs
    configMap:
      name: dsp-connector-a-certs
```

---

## Common Commands

### View Generated Files
```bash
# ConfigMap files
cat .terraform/connector_a_application.properties
cat .terraform/connector_b_application.properties

# Verify properties use env var placeholders
grep "S3_SECRET_KEY\|KEYSTORE_PASSWORD" .terraform/connector_a_application.properties
```

### Verify Secrets Deployment
```bash
# List all secrets
kubectl get secret -n default | grep dsp-connector

# View secret details (shows keys, not values)
kubectl describe secret dsp-connector-a-credentials -n default

# Decode a specific secret value (for debugging only!)
kubectl get secret dsp-connector-a-credentials -o jsonpath='{.data.s3_access_key}' | base64 -d

# Verify secrets exist before deploying pods
kubectl get secret dsp-connector-a-credentials && echo "✓ Connector A secrets ready"
```

### Verify ConfigMaps Deployment
```bash
# List all configmaps
kubectl get configmap -n default | grep dsp-connector

# View application.properties from ConfigMap
kubectl get configmap dsp-connector-a-config -o jsonpath='{.data.application\.properties}' | head -20

# Verify non-sensitive values only
kubectl get configmap connector-a-env -o yaml
```

### Pod Environment Variables Check
```bash
# After pod is running, verify env vars from Secrets are loaded
kubectl exec -it <pod-name> -- env | grep "S3_\|KEYSTORE_\|KEY_PASSWORD"

# Check if application.properties was rendered correctly
kubectl exec -it <pod-name> -- cat /etc/config/application.properties | grep "s3.secretKey\|keystore"
```

### Terraform Configuration View

## File Locations

| Component | Path |
|-----------|------|
| Variables Definition | `terraform/variables.tf` |
| Default Values | `terraform/terraform.tfvars` |
| Connector A Template | `terraform/app-resources/connector_a_resources/application.properties` |
| Connector B Template | `terraform/app-resources/connector_b_resources/application.properties` |
| Config Maps Resource | `terraform/configmaps.tf` |
| Documentation | `terraform/PROPERTY_EXTRACTION_SUMMARY.md` |
| Examples | `terraform/USAGE_EXAMPLES.md` |

## Environment-Specific Setup

### Development
```bash
cp terraform.tfvars dev.tfvars
# Edit dev.tfvars to enable automatic features
# automatic_transfer = true
# automatic_negotiation = true
terraform apply -var-file="dev.tfvars"
```

### Staging
```bash
cp terraform.tfvars staging.tfvars
# Edit staging.tfvars to use staging endpoints
# mongodb_host = "mongodb-staging"
# ssl_enabled = true
terraform apply -var-file="staging.tfvars"
```

### Production
```bash
cp terraform.tfvars production.tfvars
# Edit production.tfvars with secure production values
# Store secrets in Terraform Cloud!
terraform apply -var-file="production.tfvars"
```

## Variable Structure

```terraform
connector_a_config = {
  automatic_transfer      = bool
  automatic_negotiation   = bool
  mongodb_host            = string
  mongodb_port            = number
  mongodb_database        = string
  ssl_enabled             = bool
  s3_endpoint             = string
  s3_access_key           = string
  s3_secret_key           = string
  s3_region               = string
  s3_bucket_name          = string
  s3_external_endpoint    = string
}

connector_b_config = {
  # Same structure as connector_a_config
}
```

## Differences Between Connectors

### Fixed Properties (NOT parameterized)
- `server.port`: 8080 (A) vs 8090 (B)
- `application.connectorid`: connector_a vs connector_b
- `application.encryption.key`: 5m7mlhmu65zsp6x vs 5xplehys9mtcatb

### Parameterized Differences
- `mongodb_database`: true_connector_a vs true_connector_b
- `s3_bucket_name`: dsp-true-connector-a vs dsp-true-connector-b
- `s3_external_endpoint`: http://localhost:9000 vs http://172.17.0.1:9000

## Terraform State & Generated Files

**Generated Files** (created during apply):
- `.terraform/connector_a_application.properties` - Generated config for A
- `.terraform/connector_b_application.properties` - Generated config for B

**Never Commit:**
- `.terraform/` directory
- `*.tfstate` files
- Sensitive variable files with real credentials

**Always Commit:**
- `variables.tf`
- `terraform.tfvars` (with default/example values only)
- `configmaps.tf`
- `*.md` documentation files

## Troubleshooting

### Configuration Issues
**Error: "vars not defined"**
→ Ensure you're in the terraform directory and have run `terraform init`

**Error: "property file not found"**
→ Check paths in configmaps.tf are relative to terraform directory

**Generated properties look wrong**
→ Check terraform.tfvars values and verify template files have ${VARIABLE} syntax

### Kubernetes Issues
**Pods failing to start with permission denied**
→ Verify Kubernetes Secrets exist: `kubectl get secret dsp-connector-a-credentials`

**Environment variables not injected**
→ Check pod env vars: `kubectl exec <pod> -- env | grep S3_SECRET`
→ Verify deployment includes `secretKeyRef` for all sensitive vars

**ConfigMap mounting but values are placeholders**
→ ✓ This is expected! Placeholders like `$${S3_SECRET_KEY}` are replaced at runtime by env vars from Secrets

**S3 authentication failing**
→ Verify S3 credentials in Secrets: `kubectl get secret dsp-connector-a-credentials -o yaml`
→ Check application log: `kubectl logs <pod> | grep -i s3`

### Infrastructure Issues
**S3 bucket already exists**
→ Ensure unique bucket names in s3_bucket_name variables

**MongoDB connection failing**
→ Verify mongodb_host and mongodb_port are accessible from Kubernetes cluster

**Secret values not appearing in application**
→ Confirm environment variables are correctly defined in pod spec
→ Restart pod after updating Secrets: `kubectl rollout restart deployment/connector-a`

