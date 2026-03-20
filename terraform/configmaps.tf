resource "kubernetes_config_map" "connector_a_certs" {
  metadata {
    name = "dsp-connector-a-certs"
  }

  binary_data = {
    "connector-a.jks" = filebase64("./app-resources/cert/connector-a/connector-a.jks")
    "truststore.jks" = filebase64("./app-resources/cert/connector-a/truststore.jks")
  }
}

resource "kubernetes_config_map" "connector_b_certs" {
  metadata {
    name = "dsp-connector-b-certs"
  }

  binary_data = {
    "connector-b.jks" = filebase64("./app-resources/cert/connector-b/connector-b.jks")
    "truststore.jks" = filebase64("./app-resources/cert/connector-a/truststore.jks")
  }
}

resource "kubernetes_config_map" "connector_a_env" {
  metadata {
    name = "connector-a-env"
  }
  data = {
    CALLBACK_ADDRESS       = var.connector_a_callback_address
    DAPS_KEYSTORE_NAME     = var.daps_config["DAPS_KEYSTORE_NAME"]
    DAPS_KEYSTORE_PASSWORD = var.daps_config["DAPS_KEYSTORE_PASSWORD"]
    DAPS_KEYSTORE_ALIAS    = var.daps_config["DAPS_KEYSTORE_ALIAS"]
    KEYSTORE_NAME          = var.keystore_connector_a_config["KEYSTORE_NAME"]
    KEYSTORE_PASSWORD      = var.keystore_connector_a_config["KEYSTORE_PASSWORD"]
    KEYSTORE_ALIAS         = var.keystore_connector_a_config["KEYSTORE_ALIAS"]
    KEY_PASSWORD           = var.keystore_connector_a_config["KEY_PASSWORD"]
    TRUSTSTORE_NAME        = var.keystore_connector_a_config["TRUSTSTORE_NAME"]
    TRUSTSTORE_PASSWORD    = var.keystore_connector_a_config["TRUSTSTORE_PASSWORD"]
  }
}

resource "kubernetes_config_map" "connector_b_env" {
  metadata {
    name = "connector-b-env"
  }
  data = {
    CALLBACK_ADDRESS       = var.connector_b_callback_address
    DAPS_KEYSTORE_NAME     = var.daps_config["DAPS_KEYSTORE_NAME"]
    DAPS_KEYSTORE_PASSWORD = var.daps_config["DAPS_KEYSTORE_PASSWORD"]
    DAPS_KEYSTORE_ALIAS    = var.daps_config["DAPS_KEYSTORE_ALIAS"]
    KEYSTORE_NAME          = var.keystore_connector_b_config["KEYSTORE_NAME"]
    KEYSTORE_PASSWORD      = var.keystore_connector_b_config["KEYSTORE_PASSWORD"]
    KEYSTORE_ALIAS         = var.keystore_connector_b_config["KEYSTORE_ALIAS"]
    KEY_PASSWORD           = var.keystore_connector_b_config["KEY_PASSWORD"]
    TRUSTSTORE_NAME        = var.keystore_connector_b_config["TRUSTSTORE_NAME"]
    TRUSTSTORE_PASSWORD    = var.keystore_connector_b_config["TRUSTSTORE_PASSWORD"]
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
  content = base64decode(base64encode(templatefile("${path.module}/app-resources/connector_a_resources/application.properties", {
    CALLBACK_ADDRESS                    = var.connector_a_callback_address
    KEYSTORE_ALIAS                      = var.keystore_connector_a_config["KEYSTORE_ALIAS"]
    KEY_PASSWORD                        = var.keystore_connector_a_config["KEY_PASSWORD"]
    KEYSTORE_NAME                       = var.keystore_connector_a_config["KEYSTORE_NAME"]
    KEYSTORE_PASSWORD                   = var.keystore_connector_a_config["KEYSTORE_PASSWORD"]
    TRUSTSTORE_NAME                     = var.keystore_connector_a_config["TRUSTSTORE_NAME"]
    TRUSTSTORE_PASSWORD                 = var.keystore_connector_a_config["TRUSTSTORE_PASSWORD"]
    DAPS_KEYSTORE_NAME                  = var.daps_config["DAPS_KEYSTORE_NAME"]
    DAPS_KEYSTORE_PASSWORD              = var.daps_config["DAPS_KEYSTORE_PASSWORD"]
    DAPS_KEYSTORE_ALIAS                 = var.daps_config["DAPS_KEYSTORE_ALIAS"]
    APPLICATION_AUTOMATIC_TRANSFER      = var.connector_a_config.automatic_transfer
    APPLICATION_AUTOMATIC_NEGOTIATION   = var.connector_a_config.automatic_negotiation
    MONGODB_HOST                        = var.connector_a_config.mongodb_host
    MONGODB_PORT                        = var.connector_a_config.mongodb_port
    MONGODB_DATABASE                    = var.connector_a_config.mongodb_database
    SERVER_SSL_ENABLED                  = var.connector_a_config.ssl_enabled
    S3_ENDPOINT                         = var.connector_a_config.s3_endpoint
    S3_ACCESS_KEY                       = var.connector_a_config.s3_access_key
    S3_SECRET_KEY                       = var.connector_a_config.s3_secret_key
    S3_REGION                           = var.connector_a_config.s3_region
    S3_BUCKET_NAME                      = var.connector_a_config.s3_bucket_name
    S3_EXTERNAL_PRESIGNED_ENDPOINT      = var.connector_a_config.s3_external_endpoint
  })))
  filename = "${path.module}/.terraform/connector_a_application.properties"
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
  content = base64decode(base64encode(templatefile("${path.module}/app-resources/connector_b_resources/application.properties", {
    CALLBACK_ADDRESS                    = var.connector_b_callback_address
    KEYSTORE_ALIAS                      = var.keystore_connector_b_config["KEYSTORE_ALIAS"]
    KEY_PASSWORD                        = var.keystore_connector_b_config["KEY_PASSWORD"]
    KEYSTORE_NAME                       = var.keystore_connector_b_config["KEYSTORE_NAME"]
    KEYSTORE_PASSWORD                   = var.keystore_connector_b_config["KEYSTORE_PASSWORD"]
    TRUSTSTORE_NAME                     = var.keystore_connector_b_config["TRUSTSTORE_NAME"]
    TRUSTSTORE_PASSWORD                 = var.keystore_connector_b_config["TRUSTSTORE_PASSWORD"]
    DAPS_KEYSTORE_NAME                  = var.daps_config["DAPS_KEYSTORE_NAME"]
    DAPS_KEYSTORE_PASSWORD              = var.daps_config["DAPS_KEYSTORE_PASSWORD"]
    DAPS_KEYSTORE_ALIAS                 = var.daps_config["DAPS_KEYSTORE_ALIAS"]
    APPLICATION_AUTOMATIC_TRANSFER      = var.connector_b_config.automatic_transfer
    APPLICATION_AUTOMATIC_NEGOTIATION   = var.connector_b_config.automatic_negotiation
    MONGODB_HOST                        = var.connector_b_config.mongodb_host
    MONGODB_PORT                        = var.connector_b_config.mongodb_port
    MONGODB_DATABASE                    = var.connector_b_config.mongodb_database
    SERVER_SSL_ENABLED                  = var.connector_b_config.ssl_enabled
    S3_ENDPOINT                         = var.connector_b_config.s3_endpoint
    S3_ACCESS_KEY                       = var.connector_b_config.s3_access_key
    S3_SECRET_KEY                       = var.connector_b_config.s3_secret_key
    S3_REGION                           = var.connector_b_config.s3_region
    S3_BUCKET_NAME                      = var.connector_b_config.s3_bucket_name
    S3_EXTERNAL_PRESIGNED_ENDPOINT      = var.connector_b_config.s3_external_endpoint
  })))
  filename = "${path.module}/.terraform/connector_b_application.properties"
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