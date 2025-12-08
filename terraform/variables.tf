variable "mongodb_image" {
  description = "MongoDB image to use"
  type        = string
  default     = "mongo:7.0.12"
}

variable "minio_image" {
  description = "MinIO image to use"
  type        = string
  default     = "minio/minio:RELEASE.2025-04-22T22-12-26Z"
}

variable "connector_image" {
  description = "DSP True Connector image to use"
  type        = string
  default     = "ghcr.io/engineering-research-and-development/dsp-true-connector:0.5.0"
}

variable "connector_ui_image" {
  description = "DSP True Connector UI image to use"
  type        = string
  default     = "ghcr.io/engineering-research-and-development/dsp-true-connector-ui:0.2.0"
}

variable "connector_a_callback_address" {
  description = "Connector A callback address"
  type        = string
  default     = "http://connector-a:8080/"
}

variable "keystore_connector_a_config" {
  description = "Keystore configuration for Connector A"
  type = map(string)
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
  type = map(string)
  default = {
    KEYSTORE_ALIAS      = "connector-b"
    KEY_PASSWORD        = "password"
    KEYSTORE_NAME       = "connector-b.jks"
    KEYSTORE_PASSWORD   = "password"
    TRUSTSTORE_NAME     = "truststore.jks"
    TRUSTSTORE_PASSWORD = "password"
  }
}

variable "daps_config" {
  description = "DAPS configuration"
  type = map(string)
  default = {
    DAPS_KEYSTORE_NAME     = ""
    DAPS_KEYSTORE_PASSWORD = ""
    DAPS_KEYSTORE_ALIAS    = ""
  }
}

variable "connector_b_callback_address" {
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