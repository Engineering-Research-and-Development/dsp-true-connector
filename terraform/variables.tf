variable "mongodb_image" {
  description = "MongoDB image to use"
  type        = string
  default     = "mongo:7.0.12"
}

variable "rustfs_image" {
  description = "RustFS image to use"
  type        = string
  default     = "rustfs/rustfs:1.0.0-rc.6"
}

variable "connector_image" {
  description = "DSP True Connector image to use"
  type        = string
  default     = "ghcr.io/engineering-research-and-development/dsp-true-connector:latest"
}

variable "connector_ui_image" {
  description = "DSP True Connector UI image to use"
  type        = string
  default     = "ghcr.io/engineering-research-and-development/dsp-true-connector-ui:latest"
}

variable "connector_a_base_url" {
  description = "Connector A callback address"
  type        = string
  default     = "http://connector-a:8080/"
}

variable "keystore_connector_a_config" {
  description = "Keystore configuration for Connector A"
  type        = map(string)
  default = {
    KEYSTORE_ALIAS      = "connector-a"
    KEY_PASSWORD        = "password"
    KEYSTORE_NAME       = "connector-a.jks"
    KEYSTORE_PASSWORD   = "password"
    TRUSTSTORE_NAME     = "truststore.jks"
    TRUSTSTORE_PASSWORD = "password"
  }
}

variable "keystore_connector_b_config" {
  description = "Keystore configuration for Connector B"
  type        = map(string)
  default = {
    KEYSTORE_ALIAS      = "connector-b"
    KEY_PASSWORD        = "password"
    KEYSTORE_NAME       = "connector-b.jks"
    KEYSTORE_PASSWORD   = "password"
    TRUSTSTORE_NAME     = "truststore.jks"
    TRUSTSTORE_PASSWORD = "password"
  }
}

variable "connector_b_base_url" {
  description = "Connector B callback address"
  type        = string
  default     = "http://connector-b:8090/"
}

variable "connector_a_tc_root_api_url" {
  description = "Connector A TC Root API URL"
  type        = string
  default     = "/connector-a/api/v1"
}

variable "connector_b_tc_root_api_url" {
  description = "Connector B TC Root API URL"
  type        = string
  default     = "/connector-b/api/v1"
}

variable "mongo_username" {
  description = "MongoDB username"
  type        = string
  default     = "admin"
}

variable "mongo_password" {
  description = "MongoDB password"
  type        = string
  default     = "admin"
}

# Connector A Application Configuration
variable "connector_a_config" {
  description = "Connector A application configuration properties"
  type = object({
    automatic_transfer    = bool
    automatic_negotiation = bool
    mongodb_host          = string
    mongodb_port          = number
    mongodb_database      = string
    ssl_enabled           = bool
    s3_endpoint           = string
    s3_access_key         = string
    s3_secret_key         = string
    s3_region             = string
    s3_external_endpoint  = string
    s3_bucket_name        = string
    jwt_secret            = string
  })
  default = {
    automatic_transfer    = false
    automatic_negotiation = false
    mongodb_host          = "mongodb"
    mongodb_port          = 27017
    mongodb_database      = "true_connector_a"
    ssl_enabled           = false
    s3_endpoint           = "http://s3storage:9000"
    s3_access_key         = "rustfsadmin"
    s3_secret_key         = "rustfsadmin"
    s3_region             = "us-east-1"
    s3_external_endpoint  = "http://localhost:9000"
    s3_bucket_name        = "dsp-true-connector-a-default-bucket"
    jwt_secret            = "connector-jwt-dev-secret-change-in-prod-min-32-bytes"
  }
}

# Connector B Application Configuration
variable "connector_b_config" {
  description = "Connector B application configuration properties"
  type = object({
    automatic_transfer    = bool
    automatic_negotiation = bool
    mongodb_host          = string
    mongodb_port          = number
    mongodb_database      = string
    ssl_enabled           = bool
    s3_endpoint           = string
    s3_access_key         = string
    s3_secret_key         = string
    s3_region             = string
    s3_external_endpoint  = string
    s3_bucket_name        = string
    jwt_secret            = string
  })
  default = {
    automatic_transfer    = false
    automatic_negotiation = false
    mongodb_host          = "mongodb"
    mongodb_port          = 27017
    mongodb_database      = "true_connector_b"
    ssl_enabled           = false
    s3_endpoint           = "http://s3storage:9000"
    s3_access_key         = "rustfsadmin"
    s3_secret_key         = "rustfsadmin"
    s3_region             = "us-east-1"
    s3_external_endpoint  = "http://172.17.0.1:9000"
    s3_bucket_name        = "dsp-true-connector-b-default-bucket"
    jwt_secret            = "connector-jwt-dev-secret-change-in-prod-min-32-bytes"
  }
}
