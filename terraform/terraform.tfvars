# Docker Images Configuration
mongodb_image      = "mongo:7.0.12"
minio_image        = "minio/minio:RELEASE.2025-04-22T22-12-26Z"
connector_image    = "ghcr.io/engineering-research-and-development/dsp-true-connector:test"
connector_ui_image = "ghcr.io/engineering-research-and-development/dsp-true-connector-ui:0.6.1"

# Callback Addresses
connector_a_callback_address = "http://connector-a:8080/"
connector_b_callback_address = "http://connector-b:8090/"

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

# DAPS Configuration
# include /cert/ in filename when applying
daps_config = {
  DAPS_KEYSTORE_NAME     = ""
  DAPS_KEYSTORE_PASSWORD = ""
  DAPS_KEYSTORE_ALIAS    = ""
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
  s3_bucket_name          = "dsp-true-connector-a"
  # put your local IP address here to access the S3 bucket from outside the docker network
  s3_external_endpoint    = "http://172.24.224.1:9000"
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
  s3_bucket_name          = "dsp-true-connector-b"
  # put your local IP address here to access the S3 bucket from outside the docker network
  s3_external_endpoint    = "http://172.24.224.1:9000"
}

