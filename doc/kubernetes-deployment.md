# Kubernetes Deployment Guide — DSP True Connector

This guide describes how to deploy the **connector module** (and its dependencies: MongoDB, MinIO, UI) to Kubernetes using **Terraform Infrastructure as Code (IaC)**.

> **Important:** This project uses Terraform to manage all Kubernetes resources. Do **not** manually create resources with kubectl — Terraform will handle all ConfigMaps, Secrets, Deployments, and Services automatically.

---

## Prerequisites

- `terraform` installed (v1.0+) — [install guide](https://www.terraform.io/downloads)
- `kubectl` installed — [install guide](https://kubernetes.io/docs/tasks/tools/)
- Kubeconfig configured and accessible
- Docker image of the connector available in a registry
  (e.g. `ghcr.io/engineering-research-and-development/dsp-true-connector:<tag>`)
- TLS certificates / keystores ready (`.jks` files) in `terraform/app-resources/cert/`

---

## 1. Configure Terraform

### 1a. Initialize Terraform

```bash
cd terraform/
terraform init
```

### 1b. Create/Update `terraform.tfvars`

Configure deployment variables by creating or editing `terraform.tfvars`:

```hcl
# Docker Images
mongodb_image      = "mongo:7.0.12"
minio_image        = "minio/minio:RELEASE.2025-04-22T22-12-26Z"
connector_image    = "ghcr.io/engineering-research-and-development/dsp-true-connector:test"
connector_ui_image = "ghcr.io/engineering-research-and-development/dsp-true-connector-ui:0.6.1"

# Callback Addresses
connector_a_callback_address = "http://connector-a:8080/"
connector_b_callback_address = "http://connector-b:8090/"

# Keystore Configuration for Connector A
keystore_connector_a_config = {
  KEYSTORE_ALIAS      = "connector-a"
  KEY_PASSWORD        = "password"
  KEYSTORE_NAME       = "connector-a.jks"
  KEYSTORE_PASSWORD   = "password"
  TRUSTSTORE_NAME     = "truststore.jks"
  TRUSTSTORE_PASSWORD = "password"
}

# Keystore Configuration for Connector B
keystore_connector_b_config = {
  KEYSTORE_ALIAS      = "connector-b"
  KEY_PASSWORD        = "password"
  KEYSTORE_NAME       = "connector-b.jks"
  KEYSTORE_PASSWORD   = "password"
  TRUSTSTORE_NAME     = "truststore.jks"
  TRUSTSTORE_PASSWORD = "password"
}

# DAPS Configuration (empty if not using DAPS)
daps_config = {
  DAPS_KEYSTORE_NAME     = ""
  DAPS_KEYSTORE_PASSWORD = ""
  DAPS_KEYSTORE_ALIAS    = ""
}

# Connector A Configuration
connector_a_config = {
  automatic_transfer      = false
  automatic_negotiation   = false
  mongodb_host            = "mongodb"
  mongodb_port            = 27017
  mongodb_database        = "true_connector_a"
  ssl_enabled             = false
  s3_endpoint             = "http://minio:9000"
  s3_access_key           = "minioadmin"
  s3_secret_key           = "minioadmin"
  s3_region               = "us-east-1"
  s3_bucket_name          = "dsp-true-connector-a"
  s3_external_endpoint    = "http://localhost:9000"
}

# Connector B Configuration
connector_b_config = {
  automatic_transfer      = false
  automatic_negotiation   = false
  mongodb_host            = "mongodb"
  mongodb_port            = 27017
  mongodb_database        = "true_connector_b"
  ssl_enabled             = false
  s3_endpoint             = "http://minio:9000"
  s3_access_key           = "minioadmin"
  s3_secret_key           = "minioadmin"
  s3_region               = "us-east-1"
  s3_bucket_name          = "dsp-true-connector-b"
  s3_external_endpoint    = "http://localhost:9000"
}
```

See `terraform/variables.tf` and `QUICK_REFERENCE.md` for all available configuration options.

---

## 2. Prepare Certificate Files

Place your TLS certificates in the required directories:

```
terraform/
├── app-resources/
│   ├── cert/
│   │   ├── connector-a/
│   │   │   ├── connector-a.jks
│   │   │   └── truststore.jks
│   │   └── connector-b/
│   │       ├── connector-b.jks
│   │       └── truststore.jks
│   ├── connector_a_resources/
│   │   ├── application.properties (template)
│   │   ├── initial_data.json
│   │   └── nginx.conf (for UI)
│   └── connector_b_resources/
│       ├── application.properties (template)
│       ├── initial_data.json
│       ├── ENG-employee.json
│       └── nginx.conf (for UI)
```

The `application.properties` files are **templates** with placeholders like `${KEYSTORE_PASSWORD}` and `${S3_SECRET_KEY}` that Terraform will populate from your `terraform.tfvars` configuration.

---

## 3. Review Terraform Plan

```bash
terraform plan
```

Review the output to verify all resources that will be created:
- ✅ Kubernetes Secrets for credentials
- ✅ ConfigMaps for application properties and configuration
- ✅ Deployments for connectors, UIs, MongoDB, and MinIO
- ✅ Services for all components
- ✅ Generated `application.properties` files with values substituted

---

## 4. Deploy with Terraform

```bash
terraform apply
```

Terraform will create:

| Resource | Name | Purpose |
|----------|------|---------|
| **Secrets** | `dsp-connector-a-credentials` | S3 and keystore passwords (uppercase env vars) |
| | `dsp-connector-b-credentials` | S3 and keystore passwords (uppercase env vars) |
| | `ui-a-ssl` | UI SSL certificates |
| | `ui-b-ssl` | UI SSL certificates |
| **ConfigMaps** | `dsp-connector-a-config` | Rendered `application.properties` |
| | `dsp-connector-b-config` | Rendered `application.properties` |
| | `connector-a-env` | Non-sensitive environment variables |
| | `connector-b-env` | Non-sensitive environment variables |
| | `dsp-connector-a-certs` | Keystore/truststore JKS files |
| | `dsp-connector-b-certs` | Keystore/truststore JKS files |
| | `dsp-connector-a-initial-data` | Initial catalog/negotiation data |
| | `dsp-connector-b-initial-data` | Initial catalog/negotiation data |
| | `employee-data` | Employee data for connector-b |
| **Deployments** | `connector-a` | Connector API service |
| | `connector-b` | Connector API service |
| | `connector-a-ui` | Connector A web UI |
| | `connector-b-ui` | Connector B web UI |
| | `mongodb` | MongoDB database |
| | `minio` | S3-compatible object storage |
| **Services** | `connector-a`, `connector-b` | ClusterIP + NodePort (30080, 30090) |
| | `connector-a-ui`, `connector-b-ui` | ClusterIP + NodePort (30420, 30430) |
| | `mongodb` | ClusterIP |
| | `minio` | ClusterIP + NodePort (30000, 30001) |

---

## 5. Verify Deployment

```bash
# List all deployed resources
kubectl get all

# Check pod status
kubectl get pods
kubectl describe pod <pod-name>

# View logs
kubectl logs deployment/connector-a
kubectl logs deployment/connector-b

# Check Secrets are created with correct keys (uppercase)
kubectl get secrets
kubectl describe secret dsp-connector-a-credentials
kubectl get secret dsp-connector-a-credentials -o yaml

# Verify environment variables in running pod
kubectl exec -it <pod-name> -- env | grep -E "KEYSTORE_PASSWORD|S3_SECRET_KEY"
```

---

## 6. Access the Application

### Port-forwarding (local testing)

```bash
# Connector A API
kubectl port-forward svc/connector-a 8080:8080

# Connector B API
kubectl port-forward svc/connector-b 8090:8090

# Connector A UI
kubectl port-forward svc/connector-a-ui 4200:4200

# Connector B UI
kubectl port-forward svc/connector-b-ui 4300:4300

# MinIO console
kubectl port-forward svc/minio 9000:9000 9001:9001
```

| Service | Local URL |
|---------|-----------|
| connector-a API | `http://localhost:8080` |
| connector-b API | `http://localhost:8090` |
| connector-a UI | `http://localhost:4200` |
| connector-b UI | `http://localhost:4300` |
| MinIO console | `http://localhost:9001` (or `http://localhost:9000`) |

### NodePort Access (if exposed externally)

Services are exposed via NodePort:

| Service | NodePort |
|---------|----------|
| connector-a | 30080 |
| connector-b | 30090 |
| connector-a-ui | 30420 |
| connector-b-ui | 30430 |
| minio | 30000 (API), 30001 (console) |

---

## 7. How Secrets and Credentials Work

### ✅ Secure Credential Handling

Terraform creates **Kubernetes Secrets** with **uppercase environment variable names** that match the Spring Boot property placeholders:

```hcl
# In terraform/configmaps.tf
resource "kubernetes_secret" "connector_a_credentials" {
  data = {
    "KEYSTORE_PASSWORD" = var.keystore_connector_a_config["KEYSTORE_PASSWORD"]  # plain text
    "KEY_PASSWORD"      = var.keystore_connector_a_config["KEY_PASSWORD"]
    "S3_SECRET_KEY"     = var.connector_a_config.s3_secret_key
    "S3_ACCESS_KEY"     = var.connector_a_config.s3_access_key
    # ...
  }
}
```

Kubernetes automatically:
1. Base64-encodes Secret values for storage
2. Injects them as **decoded environment variables** into pods
3. Spring Boot resolves placeholders at startup: `${KEYSTORE_PASSWORD}` → actual password value

### Generated application.properties

Terraform generates `application.properties` with **placeholders**, not hardcoded values:

```properties
# In terraform/.terraform/connector_a_application.properties (generated)
keystore.password=${KEYSTORE_PASSWORD}              # NOT base64-encoded!
s3.secretKey=${S3_SECRET_KEY}                       # Spring resolves at runtime
```

When the pod starts:
1. `application.properties` is mounted from ConfigMap
2. Spring reads the file and encounters `${KEYSTORE_PASSWORD}`
3. Spring looks up the **environment variable** `KEYSTORE_PASSWORD`
4. Environment variable was injected from Secret (decoded by Kubernetes)
5. Spring substitutes: `keystore.password=password` (actual value)

---

## 8. Configuration Changes

### Updating Properties

To change application properties (e.g., enable automatic transfer):

1. **Edit `terraform.tfvars`:**
   ```hcl
   connector_a_config = {
     automatic_transfer = true  # ← change this
     # ...
   }
   ```

2. **Apply Terraform:**
   ```bash
   terraform plan
   terraform apply
   ```

3. **Terraform will:**
   - Regenerate `application.properties` with new values
   - Update ConfigMaps
   - Pods will automatically reload (Spring Boot monitors config changes)

> **Note:** For some changes (e.g., JVM memory), you may need to restart pods manually:
> ```bash
> kubectl rollout restart deployment/connector-a
> ```

---

## 9. Environment-Specific Deployments

Create separate tfvars files for different environments:

### Development

```bash
# dev.tfvars
connector_image            = "...your-dev-image:latest"
connector_a_config = {
  automatic_transfer    = true
  automatic_negotiation = true
  # ...
}
```

```bash
terraform apply -var-file="dev.tfvars"
```

### Production

```bash
# prod.tfvars
connector_image = "...your-prod-image:1.2.3"
connector_a_config = {
  automatic_transfer    = false
  automatic_negotiation = false
  ssl_enabled           = true
  # ...
}
```

```bash
terraform apply -var-file="prod.tfvars"
```

---

## 10. Cleanup

```bash
# Destroy all Terraform-managed resources
terraform destroy

# Confirm when prompted
# Type: yes
```

This will delete:
- All Deployments (connector, UI, MongoDB, MinIO)
- All Services
- All ConfigMaps and Secrets
- All volumes (data will be lost)

---

## 11. Troubleshooting

### Pods not starting

```bash
# Check pod logs
kubectl logs deployment/connector-a

# Common issue: Keystore password not being injected
# Verify uppercase env vars are present
kubectl exec -it <pod-name> -- env | grep KEYSTORE_PASSWORD

# Should show: KEYSTORE_PASSWORD=password (not base64-encoded!)
```

### Application.properties rendering issue

```bash
# Check if the ConfigMap was created with substituted values
kubectl get configmap dsp-connector-a-config -o yaml | grep keystore.password

# Should show: keystore.password=password
# NOT: keystore.password=$${KEYSTORE_PASSWORD}
```

### Certificate/keystore errors

```bash
# Verify certificates are mounted in pod
kubectl exec -it <pod-name> -- ls -la /cert/

# Should list: connector-a.jks, truststore.jks
```

### MongoDB connection issues

```bash
# Check MongoDB is running
kubectl get pod -l app=mongodb

# Verify MongoDB service is accessible
kubectl exec -it <connector-pod> -- nc -zv mongodb 27017
```

---

## 12. Terraform File Structure

```
terraform/
├── main.tf                    # Kind cluster configuration
├── variables.tf               # Variable definitions
├── terraform.tfvars           # Values (git-ignored, create manually)
├── configmaps.tf              # Secrets, ConfigMaps, properties files
├── deployments.tf             # Connector, UI, MongoDB, MinIO deployments
├── services.tf                # Kubernetes Services
├── modules/
│   ├── connector/             # Connector pod deployment module
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   └── frontend/              # UI deployment module
│       ├── main.tf
│       ├── variables.tf
│       └── outputs.tf
├── app-resources/
│   ├── cert/
│   │   ├── connector-a/       # Keystore files
│   │   └── connector-b/
│   ├── connector_a_resources/ # Templates & data files
│   │   ├── application.properties
│   │   ├── initial_data.json
│   │   └── nginx.conf
│   └── connector_b_resources/
│       ├── application.properties
│       ├── initial_data.json
│       ├── ENG-employee.json
│       └── nginx.conf
└── .terraform/                # Generated properties files (git-ignored)
    ├── connector_a_application.properties
    └── connector_b_application.properties
```

---

## 13. Key Differences from Manual kubectl Deployment

| Aspect | Manual kubectl | Terraform (IaC) |
|--------|----------------|-----------------|
| **Resource Management** | Create individually | Declare all at once |
| **Repeatability** | Error-prone | Idempotent, version-controlled |
| **Secrets Security** | Hardcoded values | External config, encrypted at rest |
| **Configuration** | Multiple commands | Single `terraform.tfvars` file |
| **Updates** | Manual edits + kubectl apply | Change tfvars + terraform apply |
| **Cleanup** | Manual deletion | `terraform destroy` |
| **Collaboration** | Oral/email instructions | Git version control + code review |
| **Audit Trail** | kubectl history | Terraform state + git commits |

---

## 14. Resources Reference

| Component | Configuration | Mount Path |
|-----------|---------------|-----------|
| application.properties | Generated from template + tfvars | `/config/application.properties` |
| initial_data.json | From `app-resources/*/initial_data.json` | `/config/initial_data.json` |
| Keystore | From `app-resources/cert/*/` | `/cert/*.jks` |
| Truststore | From `app-resources/cert/*/` | `/cert/truststore.jks` |
| Credentials | From Kubernetes Secrets | Environment variables (uppercase) |

For more details, see:
- `terraform/variables.tf` — all available configuration options
- `terraform/QUICK_REFERENCE.md` — quick property mapping

