# Terraform Variables Configuration - Usage Examples

## Quick Reference: Environment-Specific Configurations

### Development Configuration (dev.tfvars)
```terraform
# Development: Enable automatic features with local endpoints
connector_a_config = {
  automatic_transfer      = true
  automatic_negotiation   = true
  mongodb_host            = "mongodb-dev"
  mongodb_port            = 27017
  mongodb_database        = "true_connector_a_dev"
  ssl_enabled             = false
  s3_endpoint             = "http://minio-dev:9000"
  s3_access_key           = "minioadmin"
  s3_secret_key           = "minioadmin"
  s3_region               = "us-east-1"
  s3_bucket_name          = "dsp-connector-a-dev"
  s3_external_endpoint    = "http://localhost:9000"
}

connector_b_config = {
  automatic_transfer      = true
  automatic_negotiation   = true
  mongodb_host            = "mongodb-dev"
  mongodb_port            = 27017
  mongodb_database        = "true_connector_b_dev"
  ssl_enabled             = false
  s3_endpoint             = "http://minio-dev:9000"
  s3_access_key           = "minioadmin"
  s3_secret_key           = "minioadmin"
  s3_region               = "us-east-1"
  s3_bucket_name          = "dsp-connector-b-dev"
  s3_external_endpoint    = "http://localhost:9000"
}
```

### Staging Configuration (staging.tfvars)
```terraform
# Staging: Disable automatic features, use staging endpoints with SSL
connector_a_config = {
  automatic_transfer      = false
  automatic_negotiation   = false
  mongodb_host            = "mongodb-staging"
  mongodb_port            = 27017
  mongodb_database        = "true_connector_a_staging"
  ssl_enabled             = true
  s3_endpoint             = "https://s3-staging.example.com"
  s3_access_key           = "staging-access-key"
  s3_secret_key           = "staging-secret-key"
  s3_region               = "us-west-2"
  s3_bucket_name          = "dsp-connector-a-staging"
  s3_external_endpoint    = "https://s3-staging.example.com"
}

connector_b_config = {
  automatic_transfer      = false
  automatic_negotiation   = false
  mongodb_host            = "mongodb-staging"
  mongodb_port            = 27017
  mongodb_database        = "true_connector_b_staging"
  ssl_enabled             = true
  s3_endpoint             = "https://s3-staging.example.com"
  s3_access_key           = "staging-access-key"
  s3_secret_key           = "staging-secret-key"
  s3_region               = "us-west-2"
  s3_bucket_name          = "dsp-connector-b-staging"
  s3_external_endpoint    = "https://s3-staging.example.com"
}
```

### Production Configuration (production.tfvars)
```terraform
# Production: Disable automatic features, use secure production endpoints with SSL
connector_a_config = {
  automatic_transfer      = false
  automatic_negotiation   = false
  mongodb_host            = "mongodb-prod"
  mongodb_port            = 27017
  mongodb_database        = "true_connector_a"
  ssl_enabled             = true
  s3_endpoint             = "https://s3-prod.example.com"
  s3_access_key           = var.prod_s3_access_key_a
  s3_secret_key           = var.prod_s3_secret_key_a
  s3_region               = "eu-central-1"
  s3_bucket_name          = "dsp-connector-a-prod"
  s3_external_endpoint    = "https://s3-prod.example.com"
}

connector_b_config = {
  automatic_transfer      = false
  automatic_negotiation   = false
  mongodb_host            = "mongodb-prod"
  mongodb_port            = 27017
  mongodb_database        = "true_connector_b"
  ssl_enabled             = true
  s3_endpoint             = "https://s3-prod.example.com"
  s3_access_key           = var.prod_s3_access_key_b
  s3_secret_key           = var.prod_s3_secret_key_b
  s3_region               = "eu-central-1"
  s3_bucket_name          = "dsp-connector-b-prod"
  s3_external_endpoint    = "https://s3-prod.example.com"
}
```

## How to Apply with Different Configurations

### Apply with Development Configuration
```bash
cd terraform
terraform apply -var-file="dev.tfvars"
```

### Apply with Staging Configuration
```bash
cd terraform
terraform apply -var-file="staging.tfvars"
```

### Apply with Production Configuration
```bash
cd terraform
terraform apply -var-file="production.tfvars"
```

### Override Specific Variables from Command Line
```bash
terraform apply \
  -var 'connector_a_config.ssl_enabled=true' \
  -var 'connector_a_config.mongodb_host=custom-mongodb' \
  -var 'connector_a_config.mongodb_port=27018'
```

### View Current Configuration
```bash
terraform console
# Then type:
var.connector_a_config
var.connector_b_config
```

## Integration with CI/CD Pipelines

### GitHub Actions Example
```yaml
name: Deploy Connector

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - uses: hashicorp/setup-terraform@v1
      
      - name: Select Environment
        id: env
        run: |
          if [[ "${{ github.ref }}" == "refs/heads/main" ]]; then
            echo "environment=production" >> $GITHUB_OUTPUT
          else
            echo "environment=staging" >> $GITHUB_OUTPUT
          fi
      
      - name: Terraform Init
        run: cd terraform && terraform init
      
      - name: Terraform Apply
        run: |
          cd terraform
          terraform apply -auto-approve -var-file="${{ steps.env.outputs.environment }}.tfvars"
```

## Troubleshooting

### Verify Variables are Loaded
```bash
terraform plan -var-file="dev.tfvars" -out=tfplan
terraform show tfplan | grep -A 20 "connector_a_config"
```

### Check Generated Properties Files
After applying, inspect the generated properties:
```bash
# Check generated connector A properties
cat .terraform/connector_a_application.properties

# Check generated connector B properties
cat .terraform/connector_b_application.properties
```

### Validate Configuration
```bash
terraform validate
terraform fmt -check
```

## Notes

- **Sensitive Values**: Consider storing S3 credentials in Terraform Cloud/Enterprise or using environment variables
- **MongoDB Credentials**: If authentication is required, update the `application.properties` files to use the appropriate Spring Data MongoDB properties
- **SSL Certificates**: Ensure certificate files are in place in `/cert/` directory when `ssl_enabled=true`
- **S3 Bucket Names**: Must be globally unique; use appropriate naming conventions for different environments

