# Docker Images Configuration
mongodb_image      = "mongo:7.0.12"
minio_image        = "minio/minio:RELEASE.2025-04-22T22-12-26Z"
connector_image    = "ghcr.io/engineering-research-and-development/dsp-true-connector:test"
connector_ui_image = "ghcr.io/engineering-research-and-development/dsp-true-connector-ui:test"

# Base URL Addresses - external access to the connectors
connector_a_base_url = "http://connector-a:8080/"
connector_b_base_url = "http://connector-b:8090/"

connector_a_tc_root_api_url = "/connector-a/api/v1"
# connector_a_tc_root_api_url = "http://be.endurance.lab.synelixis.com/api/v1"
connector_b_tc_root_api_url = "/connector-b/api/v1"

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

# Connector A Configuration
connector_a_config = {
  automatic_transfer      = true
  automatic_negotiation   = true
  mongodb_host            = "mongodb"
  mongodb_port            = 27017
  mongodb_database        = "true_connector_a"
  ssl_enabled             = false
  s3_endpoint             = "http://minio:9000"
  s3_access_key           = "minioadmin"
  s3_secret_key           = "minioadmin"
  s3_region               = "us-east-1"
  # put your local IP address here to access the S3 bucket from outside the docker network
  s3_external_endpoint    = "http://192.168.0.12:9000"
  # Shared HMAC-SHA256 secret for INTERNAL-mode JWT login (must be at least 32 bytes)
  jwt_secret              = "connector-jwt-dev-secret-change-in-prod-min-32-bytes"
}

# Connector B Configuration
connector_b_config = {
  automatic_transfer      = true
  automatic_negotiation   = true
  mongodb_host            = "mongodb"
  mongodb_port            = 27017
  mongodb_database        = "true_connector_b"
  ssl_enabled             = false
  s3_endpoint             = "http://minio:9000"
  s3_access_key           = "minioadmin"
  s3_secret_key           = "minioadmin"
  s3_region               = "us-east-1"
  # put your local IP address here to access the S3 bucket from outside the docker network
  s3_external_endpoint    = "http://192.168.0.12:9000"
  # Shared HMAC-SHA256 secret for INTERNAL-mode JWT login (must be at least 32 bytes)
  jwt_secret              = "connector-jwt-dev-secret-change-in-prod-min-32-bytes"
}

