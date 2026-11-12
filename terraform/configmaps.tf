locals {
  auth_provider = var.auth_provider
  # The UI does not accept DISABLED, so it remains in INTERNAL mode in that case.
  ui_auth_provider  = var.auth_provider == "KEYCLOAK" ? "KEYCLOAK" : "INTERNAL"
  keycloak_base_url = var.auth_provider == "KEYCLOAK" ? trimspace(var.keycloak_base_url) : "http://localhost:8180"
  # Validate the base URL's structure and then use cidrhost to reject malformed IP literals.
  keycloak_base_url_ipv4 = try(regex("(?i)^https?://([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)(:|/|$)", local.keycloak_base_url)[0], "")
  keycloak_base_url_ipv6 = try(regex("(?i)^https?://\\[([0-9A-Fa-f:.]+)\\]", local.keycloak_base_url)[0], "")
  keycloak_base_url_host = try(regex("(?i)^https?://([^/:]+)", local.keycloak_base_url)[0], "")
  keycloak_base_url_is_valid = can(regex(
    "(?i)^https?://(([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)*[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?|\\[[0-9A-Fa-f:.]+\\])(:0*([1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5]))?(/|(/([A-Za-z0-9._~!$&'()*+,;=:@-]|%[0-9A-Fa-f]{2})+)+/?)?$",
    local.keycloak_base_url
    )) && (
    length(local.keycloak_base_url_host) <= 253
    ) && (
    local.keycloak_base_url_ipv4 == "" ||
    can(cidrhost("${local.keycloak_base_url_ipv4}/32", 0))
    ) && (
    local.keycloak_base_url_ipv6 == "" ||
    can(cidrhost("${local.keycloak_base_url_ipv6}/128", 0))
  )
  keycloak_seed_dir = "${path.module}/../ci/docker/keycloak_resources"
  keycloak_seed_realms = {
    for filename in fileset(local.keycloak_seed_dir, "*-realm.json") :
    filename => jsondecode(file("${local.keycloak_seed_dir}/${filename}"))
  }
  keycloak_ui_origins = [
    trimspace(var.connector_a_ui_origin),
    trimspace(var.connector_b_ui_origin),
  ]
  keycloak_ui_redirect_uris = flatten([
    for origin in local.keycloak_ui_origins : [
      "${origin}/auth/callback",
      "${origin}/login",
    ]
  ])
  keycloak_import_data = {
    for filename, realm in local.keycloak_seed_realms :
    filename => jsonencode(merge(realm, {
      attributes = merge(try(realm.attributes, {}), {
        frontendUrl = local.keycloak_base_url
      })
      clients = [
        for client in realm.clients :
        client.clientId == "keycloak-api" ? merge(client, {
          redirectUris = local.keycloak_ui_redirect_uris
          webOrigins   = local.keycloak_ui_origins
          attributes = merge(try(client.attributes, {}), {
            "post.logout.redirect.uris" = "+"
          })
        }) : client
      ]
    }))
  }
  platform_realm = local.keycloak_seed_realms["platform-realm.json"]
  keycloak_seed_backend_client_secret = one([
    for client in local.platform_realm.clients :
    client.secret if client.clientId == "dsp-connector-backend"
  ])
  keycloak_backend_client_secret = var.auth_provider != "KEYCLOAK" ? "" : (
    var.keycloak_mode == "MANAGED" ? local.keycloak_seed_backend_client_secret : (
      var.keycloak_mode == "EXTERNAL" ? var.keycloak_backend_client_secret : ""
    )
  )
}

resource "kubernetes_secret" "keycloak_realm_import" {
  for_each = var.auth_provider == "KEYCLOAK" && var.keycloak_mode == "MANAGED" ? { enabled = true } : {}

  metadata {
    name = "keycloak-realm-import"
  }

  type = "Opaque"

  data = local.keycloak_import_data
}

resource "kubernetes_secret" "keycloak_credentials" {
  for_each = var.auth_provider == "KEYCLOAK" && var.keycloak_mode == "MANAGED" ? { enabled = true } : {}

  metadata {
    name = "keycloak-credentials"
  }

  type = "Opaque"

  data = {
    KEYCLOAK_ADMIN_USERNAME = var.keycloak_admin_username
    KEYCLOAK_ADMIN_PASSWORD = var.keycloak_admin_password
    KEYCLOAK_DB_USERNAME    = var.keycloak_db_username
    KEYCLOAK_DB_PASSWORD    = var.keycloak_db_password
  }

  lifecycle {
    precondition {
      condition = alltrue([
        length(trimspace(var.keycloak_admin_username)) > 0,
        length(trimspace(var.keycloak_admin_password)) > 0,
        length(trimspace(var.keycloak_db_username)) > 0,
        length(trimspace(var.keycloak_db_password)) > 0,
      ])
      error_message = "Set all Keycloak admin and PostgreSQL credentials when auth_provider is KEYCLOAK and keycloak_mode is MANAGED."
    }
  }
}

resource "kubernetes_secret" "connector_a_credentials" {
  metadata {
    name = "dsp-connector-a-credentials"
  }

  type = "Opaque"

  data = merge(
    {
      "S3_ACCESS_KEY"                   = var.connector_a_config.s3_access_key
      "S3_SECRET_KEY"                   = var.connector_a_config.s3_secret_key
      "KEYSTORE_PASSWORD"               = var.keystore_connector_a_config["KEYSTORE_PASSWORD"]
      "KEY_PASSWORD"                    = var.keystore_connector_a_config["KEY_PASSWORD"]
      "TRUSTSTORE_PASSWORD"             = var.keystore_connector_a_config["TRUSTSTORE_PASSWORD"]
      "APPLICATION_SECURITY_JWT_SECRET" = var.connector_a_config.jwt_secret
    },
    var.auth_provider == "KEYCLOAK" ? {
      APPLICATION_KEYCLOAK_BACKEND_CLIENT_SECRET = local.keycloak_backend_client_secret
    } : {}
  )

  lifecycle {
    precondition {
      condition     = var.auth_provider != "KEYCLOAK" || var.keycloak_mode != "EXTERNAL" || length(trimspace(var.keycloak_backend_client_secret)) > 0
      error_message = "Set keycloak_backend_client_secret when auth_provider is KEYCLOAK and keycloak_mode is EXTERNAL."
    }
  }
}

resource "kubernetes_secret" "connector_b_credentials" {
  metadata {
    name = "dsp-connector-b-credentials"
  }

  type = "Opaque"

  data = merge(
    {
      "S3_ACCESS_KEY"                   = var.connector_b_config.s3_access_key
      "S3_SECRET_KEY"                   = var.connector_b_config.s3_secret_key
      "KEYSTORE_PASSWORD"               = var.keystore_connector_b_config["KEYSTORE_PASSWORD"]
      "KEY_PASSWORD"                    = var.keystore_connector_b_config["KEY_PASSWORD"]
      "TRUSTSTORE_PASSWORD"             = var.keystore_connector_b_config["TRUSTSTORE_PASSWORD"]
      "APPLICATION_SECURITY_JWT_SECRET" = var.connector_b_config.jwt_secret
    },
    var.auth_provider == "KEYCLOAK" ? {
      APPLICATION_KEYCLOAK_BACKEND_CLIENT_SECRET = local.keycloak_backend_client_secret
    } : {}
  )

  lifecycle {
    precondition {
      condition     = var.auth_provider != "KEYCLOAK" || var.keycloak_mode != "EXTERNAL" || length(trimspace(var.keycloak_backend_client_secret)) > 0
      error_message = "Set keycloak_backend_client_secret when auth_provider is KEYCLOAK and keycloak_mode is EXTERNAL."
    }
  }
}

resource "kubernetes_config_map" "connector_a_certs" {
  metadata {
    name = "dsp-connector-a-certs"
  }

  binary_data = {
    "connector-a.jks" = filebase64("./app-resources/cert/connector-a/connector-a.jks")
    "truststore.jks"  = filebase64("./app-resources/cert/connector-a/truststore.jks")
  }
}

resource "kubernetes_config_map" "connector_b_certs" {
  metadata {
    name = "dsp-connector-b-certs"
  }

  binary_data = {
    "connector-b.jks" = filebase64("./app-resources/cert/connector-b/connector-b.jks")
    "truststore.jks"  = filebase64("./app-resources/cert/connector-a/truststore.jks")
  }
}

resource "kubernetes_config_map" "connector_a_env" {
  metadata {
    name = "connector-a-env"
  }
  data = {
    BASE_URL        = var.connector_a_base_url
    KEYSTORE_NAME   = var.keystore_connector_a_config["KEYSTORE_NAME"]
    KEYSTORE_ALIAS  = var.keystore_connector_a_config["KEYSTORE_ALIAS"]
    TRUSTSTORE_NAME = var.keystore_connector_a_config["TRUSTSTORE_NAME"]
  }
}

resource "kubernetes_config_map" "connector_b_env" {
  metadata {
    name = "connector-b-env"
  }
  data = {
    BASE_URL        = var.connector_b_base_url
    KEYSTORE_NAME   = var.keystore_connector_b_config["KEYSTORE_NAME"]
    KEYSTORE_ALIAS  = var.keystore_connector_b_config["KEYSTORE_ALIAS"]
    TRUSTSTORE_NAME = var.keystore_connector_b_config["TRUSTSTORE_NAME"]
  }
}

resource "kubernetes_config_map" "connector_a_initial_data" {
  metadata {
    name = "dsp-connector-a-initial-data"
  }

  data = {
    "initial_data.json" = file("./app-resources/connector_a_resources/initial_data.json")
  }
}

resource "kubernetes_config_map" "connector_b_initial_data" {
  metadata {
    name = "dsp-connector-b-initial-data"
  }

  data = {
    "initial_data.json" = file("./app-resources/connector_b_resources/initial_data.json")
  }
}

resource "kubernetes_config_map" "employee_data" {
  metadata {
    name = "employee-data"
  }

  data = {
    "ENG-employee.json" = file("./app-resources/connector_b_resources/ENG-employee.json")
  }
}

resource "local_file" "connector_a_properties" {
  content = templatefile("${path.module}/app-resources/connector_a_resources/application.properties", {
    BASE_URL                          = var.connector_a_base_url
    APPLICATION_AUTH_PROVIDER         = local.auth_provider
    KEYCLOAK_BASE_URL                 = local.keycloak_base_url
    KEYSTORE_ALIAS                    = var.keystore_connector_a_config["KEYSTORE_ALIAS"]
    KEY_PASSWORD                      = "$${KEY_PASSWORD}"
    KEYSTORE_NAME                     = var.keystore_connector_a_config["KEYSTORE_NAME"]
    KEYSTORE_PASSWORD                 = "$${KEYSTORE_PASSWORD}"
    TRUSTSTORE_NAME                   = var.keystore_connector_a_config["TRUSTSTORE_NAME"]
    TRUSTSTORE_PASSWORD               = "$${TRUSTSTORE_PASSWORD}"
    APPLICATION_AUTOMATIC_TRANSFER    = var.connector_a_config.automatic_transfer
    APPLICATION_AUTOMATIC_NEGOTIATION = var.connector_a_config.automatic_negotiation
    MONGODB_HOST                      = var.connector_a_config.mongodb_host
    MONGODB_PORT                      = var.connector_a_config.mongodb_port
    MONGODB_DATABASE                  = var.connector_a_config.mongodb_database
    SERVER_SSL_ENABLED                = var.connector_a_config.ssl_enabled
    S3_ENDPOINT                       = var.connector_a_config.s3_endpoint
    S3_ACCESS_KEY                     = "$${S3_ACCESS_KEY}"
    S3_SECRET_KEY                     = "$${S3_SECRET_KEY}"
    S3_REGION                         = var.connector_a_config.s3_region
    S3_EXTERNAL_PRESIGNED_ENDPOINT    = var.connector_a_config.s3_external_endpoint
    S3_BUCKET_NAME                    = var.connector_a_config.s3_bucket_name
  })
  filename = "${path.module}/.terraform/connector_a_application.properties"

  lifecycle {
    precondition {
      condition = (
        var.auth_provider == "KEYCLOAK" &&
        contains(["MANAGED", "EXTERNAL"], var.keycloak_mode)
        ) || (
        var.auth_provider != "KEYCLOAK" &&
        var.keycloak_mode == "NONE"
      )
      error_message = "Use keycloak_mode MANAGED or EXTERNAL only with auth_provider KEYCLOAK; otherwise keycloak_mode must be NONE."
    }

    precondition {
      condition     = var.auth_provider != "KEYCLOAK" || local.keycloak_base_url_is_valid
      error_message = "Set keycloak_base_url to an absolute HTTP(S) server URL when auth_provider is KEYCLOAK."
    }
  }
}

resource "kubernetes_config_map" "connector_a_config" {
  metadata {
    name = "dsp-connector-a-config"
  }

  data = {
    "application.properties" = local_file.connector_a_properties.content
  }

  depends_on = [local_file.connector_a_properties]
}

resource "local_file" "connector_b_properties" {
  content = templatefile("${path.module}/app-resources/connector_b_resources/application.properties", {
    BASE_URL                          = var.connector_b_base_url
    APPLICATION_AUTH_PROVIDER         = local.auth_provider
    KEYCLOAK_BASE_URL                 = local.keycloak_base_url
    KEYSTORE_ALIAS                    = var.keystore_connector_b_config["KEYSTORE_ALIAS"]
    KEY_PASSWORD                      = "$${KEY_PASSWORD}"
    KEYSTORE_NAME                     = var.keystore_connector_b_config["KEYSTORE_NAME"]
    KEYSTORE_PASSWORD                 = "$${KEYSTORE_PASSWORD}"
    TRUSTSTORE_NAME                   = var.keystore_connector_b_config["TRUSTSTORE_NAME"]
    TRUSTSTORE_PASSWORD               = "$${TRUSTSTORE_PASSWORD}"
    APPLICATION_AUTOMATIC_TRANSFER    = var.connector_b_config.automatic_transfer
    APPLICATION_AUTOMATIC_NEGOTIATION = var.connector_b_config.automatic_negotiation
    MONGODB_HOST                      = var.connector_b_config.mongodb_host
    MONGODB_PORT                      = var.connector_b_config.mongodb_port
    MONGODB_DATABASE                  = var.connector_b_config.mongodb_database
    SERVER_SSL_ENABLED                = var.connector_b_config.ssl_enabled
    S3_ENDPOINT                       = var.connector_b_config.s3_endpoint
    S3_ACCESS_KEY                     = "$${S3_ACCESS_KEY}"
    S3_SECRET_KEY                     = "$${S3_SECRET_KEY}"
    S3_REGION                         = var.connector_b_config.s3_region
    S3_EXTERNAL_PRESIGNED_ENDPOINT    = var.connector_b_config.s3_external_endpoint
    S3_BUCKET_NAME                    = var.connector_b_config.s3_bucket_name
  })
  filename = "${path.module}/.terraform/connector_b_application.properties"

  lifecycle {
    precondition {
      condition = (
        var.auth_provider == "KEYCLOAK" &&
        contains(["MANAGED", "EXTERNAL"], var.keycloak_mode)
        ) || (
        var.auth_provider != "KEYCLOAK" &&
        var.keycloak_mode == "NONE"
      )
      error_message = "Use keycloak_mode MANAGED or EXTERNAL only with auth_provider KEYCLOAK; otherwise keycloak_mode must be NONE."
    }

    precondition {
      condition     = var.auth_provider != "KEYCLOAK" || local.keycloak_base_url_is_valid
      error_message = "Set keycloak_base_url to an absolute HTTP(S) server URL when auth_provider is KEYCLOAK."
    }
  }
}

resource "kubernetes_config_map" "connector_b_config" {
  metadata {
    name = "dsp-connector-b-config"
  }

  data = {
    "application.properties" = local_file.connector_b_properties.content
  }

  depends_on = [local_file.connector_b_properties]
}

resource "kubernetes_config_map" "ui_a_nginx_conf" {
  metadata {
    name = "ui-a-nginx-conf"
  }
  data = {
    "nginx.conf" = file("./app-resources/ui_a_resources/nginx.conf")
  }
}

resource "kubernetes_secret" "ui_a_ssl" {
  metadata {
    name = "ui-a-ssl"
  }
  type = "Opaque"
  data = {
    "ui-a-cert.key" = file("${path.module}/app-resources/ui_a_resources/ssl/ui-a-cert.key")
    "ui-a-cert.crt" = file("${path.module}/app-resources/ui_a_resources/ssl/ui-a-cert.crt")
  }
}

resource "kubernetes_config_map" "ui_b_nginx_conf" {
  metadata {
    name = "ui-b-nginx-conf"
  }
  data = {
    "nginx.conf" = file("./app-resources/ui_b_resources/nginx.conf")
  }
}

resource "kubernetes_secret" "ui_b_ssl" {
  metadata {
    name = "ui-b-ssl"
  }
  type = "Opaque"
  data = {
    "ui-b-cert.key" = file("${path.module}/app-resources/ui_b_resources/ssl/ui-b-cert.key")
    "ui-b-cert.crt" = file("${path.module}/app-resources/ui_b_resources/ssl/ui-b-cert.crt")
  }
}