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

variable "auth_provider" {
  description = "Authentication provider used directly by the connectors"
  type        = string
  default     = "INTERNAL"

  validation {
    condition     = contains(["INTERNAL", "KEYCLOAK", "DISABLED"], var.auth_provider)
    error_message = "auth_provider must be INTERNAL, KEYCLOAK, or DISABLED."
  }
}

variable "keycloak_mode" {
  description = "Keycloak provisioning mode; NONE, MANAGED, or EXTERNAL"
  type        = string
  default     = "NONE"

  validation {
    condition     = contains(["NONE", "MANAGED", "EXTERNAL"], var.keycloak_mode)
    error_message = "keycloak_mode must be NONE, MANAGED, or EXTERNAL."
  }
}

variable "keycloak_image" {
  description = "Keycloak image used by a managed Keycloak deployment"
  type        = string
  default     = "quay.io/keycloak/keycloak:26.0.0"
}

variable "keycloak_postgres_image" {
  description = "PostgreSQL image used by a managed Keycloak deployment"
  type        = string
  default     = "postgres:16"
}

variable "keycloak_host_port" {
  description = "Host port mapped to the managed Keycloak NodePort"
  type        = number
  default     = 8180
}

variable "keycloak_node_port" {
  description = "Kubernetes NodePort used by a managed Keycloak deployment"
  type        = number
  default     = 30180
}

variable "keycloak_db_name" {
  description = "Database name used by managed Keycloak PostgreSQL"
  type        = string
  default     = "keycloak"
}

variable "keycloak_base_url" {
  description = "Keycloak server base URL reachable from both the browser and connector pods when auth_provider is KEYCLOAK"
  type        = string
  default     = ""
}

# UI origins are validated here because Terraform appends callback/logout paths to them.
# Keep these as origins (scheme, host, optional port), not full URLs with paths or query strings.
variable "connector_a_ui_origin" {
  description = "Absolute HTTP(S) origin used by Connector A's browser UI"
  type        = string
  default     = "http://localhost:4200"

  validation {
    condition = can(regex(
      "(?i)^https?://(\\[[0-9A-Fa-f:.]+\\]|([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)*[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(:0*([1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5]))?$",
      trimspace(var.connector_a_ui_origin)
      )) && length(try(regex("(?i)^https?://([^/:]+)", trimspace(var.connector_a_ui_origin))[0], "")) <= 253 && (
      try(regex("(?i)^https?://([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)(:|$)", trimspace(var.connector_a_ui_origin))[0], "") == "" ||
      can(cidrhost(
        "${try(regex("(?i)^https?://([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)(:|$)", trimspace(var.connector_a_ui_origin))[0], "")}/32",
        0
      ))
      ) && (
      try(regex("(?i)^https?://\\[([0-9A-Fa-f:.]+)\\]", trimspace(var.connector_a_ui_origin))[0], "") == "" ||
      can(cidrhost(
        "${try(regex("(?i)^https?://\\[([0-9A-Fa-f:.]+)\\]", trimspace(var.connector_a_ui_origin))[0], "")}/128",
        0
      ))
    )
    error_message = "connector_a_ui_origin must be an absolute HTTP(S) origin without a path, query, fragment, or user information."
  }
}

variable "connector_b_ui_origin" {
  description = "Absolute HTTP(S) origin used by Connector B's browser UI"
  type        = string
  default     = "http://localhost:4300"

  validation {
    condition = can(regex(
      "(?i)^https?://(\\[[0-9A-Fa-f:.]+\\]|([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)*[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(:0*([1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5]))?$",
      trimspace(var.connector_b_ui_origin)
      )) && length(try(regex("(?i)^https?://([^/:]+)", trimspace(var.connector_b_ui_origin))[0], "")) <= 253 && (
      try(regex("(?i)^https?://([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)(:|$)", trimspace(var.connector_b_ui_origin))[0], "") == "" ||
      can(cidrhost(
        "${try(regex("(?i)^https?://([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)(:|$)", trimspace(var.connector_b_ui_origin))[0], "")}/32",
        0
      ))
      ) && (
      try(regex("(?i)^https?://\\[([0-9A-Fa-f:.]+)\\]", trimspace(var.connector_b_ui_origin))[0], "") == "" ||
      can(cidrhost(
        "${try(regex("(?i)^https?://\\[([0-9A-Fa-f:.]+)\\]", trimspace(var.connector_b_ui_origin))[0], "")}/128",
        0
      ))
    )
    error_message = "connector_b_ui_origin must be an absolute HTTP(S) origin without a path, query, fragment, or user information."
  }
}

variable "keycloak_admin_username" {
  description = "Bootstrap administrator username for a managed Keycloak deployment"
  type        = string
  sensitive   = true
  default     = ""

  validation {
    condition     = var.keycloak_mode != "MANAGED" || var.auth_provider != "KEYCLOAK" || length(trimspace(var.keycloak_admin_username)) > 0
    error_message = "Set keycloak_admin_username when auth_provider is KEYCLOAK and keycloak_mode is MANAGED."
  }
}

variable "keycloak_admin_password" {
  description = "Bootstrap administrator password for a managed Keycloak deployment"
  type        = string
  sensitive   = true
  default     = ""

  validation {
    condition     = var.keycloak_mode != "MANAGED" || var.auth_provider != "KEYCLOAK" || length(trimspace(var.keycloak_admin_password)) > 0
    error_message = "Set keycloak_admin_password when auth_provider is KEYCLOAK and keycloak_mode is MANAGED."
  }
}

variable "keycloak_db_username" {
  description = "PostgreSQL username for a managed Keycloak deployment"
  type        = string
  sensitive   = true
  default     = ""

  validation {
    condition     = var.keycloak_mode != "MANAGED" || var.auth_provider != "KEYCLOAK" || length(trimspace(var.keycloak_db_username)) > 0
    error_message = "Set keycloak_db_username when auth_provider is KEYCLOAK and keycloak_mode is MANAGED."
  }
}

variable "keycloak_db_password" {
  description = "PostgreSQL password for a managed Keycloak deployment"
  type        = string
  sensitive   = true
  default     = ""

  validation {
    condition     = var.keycloak_mode != "MANAGED" || var.auth_provider != "KEYCLOAK" || length(trimspace(var.keycloak_db_password)) > 0
    error_message = "Set keycloak_db_password when auth_provider is KEYCLOAK and keycloak_mode is MANAGED."
  }
}

variable "keycloak_backend_client_secret" {
  description = "Platform dsp-connector-backend client secret for external Keycloak"
  type        = string
  sensitive   = true
  default     = ""

  validation {
    condition     = var.keycloak_mode != "EXTERNAL" || var.auth_provider != "KEYCLOAK" || length(trimspace(var.keycloak_backend_client_secret)) > 0
    error_message = "Set keycloak_backend_client_secret when auth_provider is KEYCLOAK and keycloak_mode is EXTERNAL."
  }
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
