# Quick Reference - Terraform Configuration

## Property Mapping Cheat Sheet

### 0. Docker Images (NEW!)
| Property | Variable | Default |
|----------|----------|---------|
| MongoDB Image | `mongodb_image` | `"mongo:7.0.12"` |
| MinIO Image | `minio_image` | `"minio/minio:RELEASE.2025-04-22T22-12-26Z"` |
| Connector Image | `connector_image` | `"ghcr.io/.../dsp-true-connector:0.6.4"` |
| Connector UI Image | `connector_ui_image` | `"ghcr.io/.../dsp-true-connector-ui:0.6.1"` |

### 1. Application Configuration
| Property | Variable | Default |
|----------|----------|---------|
| `application.automatic.transfer` | `connector_a_config.automatic_transfer` | `false` |
| `application.automatic.negotiation` | `connector_a_config.automatic_negotiation` | `false` |

### 2. MongoDB Connection
| Property | Variable | Default |
|----------|----------|---------|
| `spring.data.mongodb.host` | `connector_a_config.mongodb_host` | `"mongodb"` |
| `spring.data.mongodb.port` | `connector_a_config.mongodb_port` | `27017` |
| `spring.data.mongodb.database` | `connector_a_config.mongodb_database` | `"true_connector_a"` |

### 3. SSL Configuration
| Property | Variable | Default |
|----------|----------|---------|
| `server.ssl.enabled` | `connector_a_config.ssl_enabled` | `false` |

### 4. S3 Configuration
| Property | Variable | Default (A / B) |
|----------|----------|---------|
| `s3.endpoint` | `connector_a_config.s3_endpoint` | `"http://minio:9000"` |
| `s3.accessKey` | `connector_a_config.s3_access_key` | `"minioadmin"` |
| `s3.secretKey` | `connector_a_config.s3_secret_key` | `"minioadmin"` |
| `s3.region` | `connector_a_config.s3_region` | `"us-east-1"` |
| `s3.bucketName` | `connector_a_config.s3_bucket_name` | `"dsp-true-connector-a" / "dsp-true-connector-b"` |
| `s3.externalPresignedEndpoint` | `connector_a_config.s3_external_endpoint` | `"http://localhost:9000" / "http://172.17.0.1:9000"` |

## Common Commands

### View Configuration
```bash
cd terraform
terraform console
# Type: var.connector_a_config
# Type: var.connector_b_config
```

### Plan with Specific Values
```bash
terraform plan \
  -var 'connector_a_config.ssl_enabled=true' \
  -var 'connector_a_config.automatic_transfer=true'
```

### Apply with tfvars File
```bash
terraform apply -var-file="dev.tfvars"
terraform apply -var-file="prod.tfvars"
```

### View Generated Properties
```bash
cat .terraform/connector_a_application.properties
cat .terraform/connector_b_application.properties
```

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

**Error: "vars not defined"**
→ Ensure you're in the terraform directory and have run `terraform init`

**Error: "property file not found"**
→ Check paths in configmaps.tf are relative to terraform directory

**Generated properties look wrong**
→ Check terraform.tfvars values and verify template files have ${VARIABLE} syntax

**S3 bucket already exists**
→ Ensure unique bucket names in s3_bucket_name variables

**MongoDB connection failing**
→ Verify mongodb_host and mongodb_port are accessible from Kubernetes cluster

