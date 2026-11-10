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
- ✅ `APPLICATION_SECURITY_JWT_SECRET` - From `connector_a_config.jwt_secret` / `connector_b_config.jwt_secret`

### Configuration in ConfigMaps (Non-sensitive)
Only non-sensitive configuration values are in ConfigMaps:
- ❌ Server ports, hostnames, database names
- ❌ Endpoint URLs (S3, MongoDB)
- ❌ SSL settings, region codes

---

## 0. Docker Images
| Property | Variable | Default (`variables.tf`) |
|----------|----------|---------|
| MongoDB Image | `mongodb_image` | `"mongo:7.0.12"` |
| RustFS Image | `rustfs_image` | `"rustfs/rustfs:1.0.0-rc.6"` |
| Connector Image | `connector_image` | `"ghcr.io/.../dsp-true-connector:0.6.4"` |
| Connector UI Image | `connector_ui_image` | `"ghcr.io/.../dsp-true-connector-ui:latest"` |

> ⚠️ `terraform.tfvars` is auto-loaded and **overrides these defaults**. Check `terraform.tfvars` for the actual
> effective image tags (`connector_image` / `connector_ui_image` are currently pinned to `:test` there). When using a
> locally built image not pushed to any registry, see "Using a Locally Built Docker Image" in `terraform.md`.

---

## 0.1 Connector Authentication and Keycloak Modes

`auth_provider` directly selects connector `application.auth.provider`; `keycloak_mode` controls Keycloak provisioning
only. Terraform rejects combinations other than:

| `auth_provider` | `keycloak_mode` | Result |
|---|---|---|
| `INTERNAL` (default) | `NONE` (default) | Internal authentication; no Keycloak resources or credentials. |
| `DISABLED` | `NONE` | Open connector endpoints; local/demo use only. The UI stays in `INTERNAL` mode. |
| `KEYCLOAK` | `MANAGED` | Terraform deploys Keycloak and PostgreSQL. |
| `KEYCLOAK` | `EXTERNAL` | Uses a pre-provisioned Keycloak; Terraform creates no Keycloak resources. |

### Keycloak inputs

| Variable | Required when | Default / use |
|---|---|---|
| `keycloak_base_url` | Either Keycloak mode | No default URL. Absolute HTTP(S) base URL, not a realm issuer; browser and connector pods must both reach it. Issuer is `<keycloak_base_url>/realms/<realm>`. |
| `keycloak_host_port` | `KEYCLOAK` / `MANAGED` | `8180`; Kind host mapping. |
| `keycloak_node_port` | `KEYCLOAK` / `MANAGED` | `30180`; Keycloak NodePort. |
| `keycloak_image` | `KEYCLOAK` / `MANAGED` | `quay.io/keycloak/keycloak:26.0.0`. |
| `keycloak_postgres_image` | `KEYCLOAK` / `MANAGED` | `postgres:16`. |
| `keycloak_db_name` | `KEYCLOAK` / `MANAGED` | `keycloak`. |
| `keycloak_admin_username`, `keycloak_admin_password` | `KEYCLOAK` / `MANAGED` | Required bootstrap credentials; supply via protected `TF_VAR_...` inputs or a secret manager. |
| `keycloak_db_username`, `keycloak_db_password` | `KEYCLOAK` / `MANAGED` | Required PostgreSQL credentials; supply via protected `TF_VAR_...` inputs or a secret manager. |
| `keycloak_backend_client_secret` | `KEYCLOAK` / `EXTERNAL` | Required secret for the external `platform/dsp-connector-backend` client. |
| `connector_a_ui_origin`, `connector_b_ui_origin` | Both Keycloak modes | `http://localhost:4200` and `http://localhost:4300`; exact browser origins. |

`INTERNAL` and `DISABLED` need no Keycloak credentials. Never put real passwords or client secrets in tracked
`terraform.tfvars`. The UI uses `AUTH_PROVIDER=KEYCLOAK` only when the connector uses Keycloak; otherwise its supported
value is `INTERNAL`. Runtime variables are `AUTH_PROVIDER`, `KEYCLOAK_URL`, `KEYCLOAK_CLIENT_ID=keycloak-api`, and
`KEYCLOAK_ADMIN_REALM=connector-admin`. The UI completes Authorization Code with PKCE S256; no client secret goes to
the browser. Use an image from the verified
[UI `feature/keycloak_auth_code_login` branch](https://github.com/Engineering-Research-and-Development/dsp-true-connector-ui/tree/feature/keycloak_auth_code_login)
at commit `ce736d0a788d592850eec1f6674c447ee2d1541c` or a later compatible revision. The public `keycloak-api` client
must allow each configured origin's exact `/auth/callback` redirect, `/login` post-logout URI, and web origin.

### Managed resource names and data

`KEYCLOAK` / `MANAGED` creates Deployments and Services named `keycloak` and `keycloak-db`, plus Secrets named
`keycloak-credentials` (admin/PostgreSQL credentials) and `keycloak-realm-import` (five realm files). The `keycloak`
Service uses NodePort `30180` and the Kind host port defaults to `8180`; `keycloak-db` is ClusterIP port `5432`.
Realm import happens only for realms that do not already exist. PostgreSQL uses ephemeral `empty_dir` storage; switching
from `MANAGED` to `NONE` or `EXTERNAL` removes the Keycloak/PostgreSQL resources and loses that ephemeral Keycloak
state. The seeded users and client credentials are demo-only.

External mode requires pre-provisioned `platform`, `connector-admin`, and tenant realms. Accepted tokens need
`aud=dsp-true-connector`; the platform client uses `azp=dsp-connector-backend` with `CONNECTOR`, while the public
`keycloak-api` client uses `azp=keycloak-api` with `SUPER_ADMIN` (connector-admin) or `ADMIN` and matching `tenant_id`
(tenant realms). It also requires HTTPS and discovery/issuer URLs reachable from both the browser and connector pods.
Terraform does not configure or validate the remote Keycloak server; see
[`terraform-deployment-guide.md`](terraform-deployment-guide.md) and [`doc/security.md`](../doc/security.md).

Kubernetes Secrets are base64-encoded and are not necessarily encrypted at rest. Terraform state and saved plan files
can contain secret values; restrict access and protect both.

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

> **Warning:** `false` is HTTP mode, which is **insecure and untrusted**: traffic is not encrypted and
> the connector accepts any certificate of the services it calls, so it cannot detect an impostor.
> Use it for first tests only. See the [HTTP mode warning](../doc/security.md#tls-configuration).

---

## 4. S3 Configuration (Non-Sensitive in ConfigMap)
## 4. S3 Configuration

### Non-Sensitive (ConfigMap)
| Property | Variable | Default (A / B) |
|----------|----------|---------|
| `s3.endpoint` | `connector_a_config.s3_endpoint` | `"http://s3storage:9000"` |
| `s3.region` | `connector_a_config.s3_region` | `"us-east-1"` |
| `s3.bucketName` | `connector_a_config.s3_bucket_name` | `"dsp-true-connector-a"` / `"dsp-true-connector-b"` |
| `s3.externalPresignedEndpoint` | `connector_a_config.s3_external_endpoint` | `"http://localhost:9000"` / `"http://172.17.0.1:9000"` |

### Sensitive (Kubernetes Secret - Environment Variables)
| Property | Secret Key | Injected As | Default (A / B) |
|----------|-----------|------------|---------|
| `s3.accessKey` | `s3_access_key` | `$${S3_ACCESS_KEY}` | `"rustfsadmin"` |
| `s3.secretKey` | `s3_secret_key` | `$${S3_SECRET_KEY}` | `"rustfsadmin"` |

---

## 5. Keystore/Truststore Configuration (Secrets)

All keystore and truststore passwords are stored in Kubernetes Secrets and injected as environment variables:

| Property | Secret Key | Injected As |
|----------|-----------|------------|
| Keystore Password | `keystore_password` | `$${KEYSTORE_PASSWORD}` |
| Key Password | `key_password` | `$${KEY_PASSWORD}` |
| Truststore Password | `truststore_password` | `$${TRUSTSTORE_PASSWORD}` |

---

## 6. JWT Authentication Secret (Kubernetes Secret)

| Property | Variable | Secret Key | Injected As | Default (A / B) |
|----------|----------|------------|-------------|---------|
| `application.security.jwt.secret` | `connector_a_config.jwt_secret` / `connector_b_config.jwt_secret` | (added to `dsp-connector-{a,b}-credentials`) | `$${APPLICATION_SECURITY_JWT_SECRET}` | `"connector-jwt-dev-secret-change-in-prod-min-32-bytes"` (both) |

> Must be at least 32 bytes (256 bits). **Change this in production** — set a unique, random value per environment in
> your `*.tfvars` file; never commit a real production secret to `terraform.tfvars`.

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
      - name: BASE_URL
        valueFrom:
          configMapKeyRef:
            name: connector-a-env
            key: BASE_URL
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
  s3_external_endpoint    = string
  jwt_secret              = string
}

connector_b_config = {
  # Same structure as connector_a_config
}
```

## Differences Between Connectors

### Fixed Properties (NOT parameterized)
- `server.port`: 8080 (A) vs 8090 (B)
- `application.participantId`: connector_a vs connector_b
- `application.encryption.key`: 5m7mlhmu65zsp6x vs 5xplehys9mtcatb

### Parameterized Differences
- `mongodb_database`: true_connector_a vs true_connector_b
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
→ Ensure unique bucket names in Tenant configuration.

**MongoDB connection failing**
→ Verify mongodb_host and mongodb_port are accessible from Kubernetes cluster

**Secret values not appearing in application**
→ Confirm environment variables are correctly defined in pod spec
→ Restart pod after updating Secrets: `kubectl rollout restart deployment/connector-a`
