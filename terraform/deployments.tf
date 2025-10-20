# Connector-A deployment via module
module "connector_a" {
  source                   = "./modules/connector"
  name                     = "connector-a"
  image                    = var.connector_image
  container_port           = 8080
  service_port             = 8080
  node_port                = 30080
  env_config_map           = kubernetes_config_map.connector_a_env.metadata[0].name
  config_map               = kubernetes_config_map.connector_a_config.metadata[0].name
  initial_data_config_map  = kubernetes_config_map.connector_a_initial_data.metadata[0].name
  certs_config_map         = kubernetes_config_map.connector_a_certs.metadata[0].name
  employee_data_config_map = null
}

module "connector_a_ui" {
  source                = "./modules/frontend"
  name                  = "connector-a-ui"
  image                 = var.connector_ui_image
  container_port        = 4200
  service_port          = 4200
  target_port           = 80
  node_port             = 30420
  tc_root_api_url       = var.connector_a_tc_root_api_url
  nginx_conf_config_map = kubernetes_config_map.ui_a_nginx_conf.metadata[0].name
  ssl_secret            = kubernetes_secret.ui_a_ssl.metadata[0].name
}

# Connector-B deployment via module
module "connector_b" {
  source                   = "./modules/connector"
  name                     = "connector-b"
  image                    = var.connector_image
  container_port           = 8090
  service_port             = 8090
  node_port                = 30090
  env_config_map           = kubernetes_config_map.connector_b_env.metadata[0].name
  config_map               = kubernetes_config_map.connector_b_config.metadata[0].name
  initial_data_config_map  = kubernetes_config_map.connector_b_initial_data.metadata[0].name
  certs_config_map         = kubernetes_config_map.connector_b_certs.metadata[0].name
  employee_data_config_map = kubernetes_config_map.employee_data.metadata[0].name
}

module "connector_b_ui" {
  source                = "./modules/frontend"
  name                  = "connector-b-ui"
  image                 = var.connector_ui_image
  container_port        = 4300
  service_port          = 4300
  target_port           = 80
  node_port             = 30430
  tc_root_api_url       = var.connector_b_tc_root_api_url
  nginx_conf_config_map = kubernetes_config_map.ui_b_nginx_conf.metadata[0].name
  ssl_secret            = kubernetes_secret.ui_b_ssl.metadata[0].name
}

# This file defines the Kubernetes services for MinIO.
resource "kubernetes_deployment" "minio" {
  metadata {
    name = "minio"
  }
  spec {
    replicas = 1
    selector {
      match_labels = {
        app = "minio"
      }
    }
    template {
      metadata {
        labels = {
          app = "minio"
        }
      }
      spec {
        container {
          name  = "minio"
          image = var.minio_image
          command = [
            "minio",
            "server",
            "/data",
            "--console-address",
            ":9001"
          ]
          port {
            name           = "api"
            container_port = 9000
          }
          port {
            name           = "console"
            container_port = 9001
          }
          env {
            name  = "MINIO_ROOT_USER"
            value = "minioadmin"
          }
          env {
            name  = "MINIO_ROOT_PASSWORD"
            value = "minioadmin"
          }
          env {
            name  = "MINIO_API_PORT_NUMBER"
            value = "9000"
          }
          env {
            name  = "MINIO_CONSOLE_PORT_NUMBER"
            value = "9001"
          }
          env {
            name  = "MINIO_SKIP_CLIENT"
            value = "yes"
          }
          volume_mount {
            name       = "minio-data"
            mount_path = "/data"
          }
        }
        volume {
          name = "minio-data"
          empty_dir {}
        }
      }
    }
  }
}

# mongoDB deployment
resource "kubernetes_deployment" "mongodb" {
  metadata {
    name = "mongodb"
  }
  spec {
    replicas = 1
    selector {
      match_labels = {
        app = "mongodb"
      }
    }
    template {
      metadata {
        labels = {
          app = "mongodb"
        }
      }
      spec {
        container {
          name  = "mongodb"
          image = var.mongodb_image
          port {
            container_port = 27017
          }
          # env {
          #   name  = "MONGO_INITDB_ROOT_USERNAME"
          #   value = var.mongo_username
          # }
          # env {
          #   name  = "MONGO_INITDB_ROOT_PASSWORD"
          #   value = var.mongo_password
          # }
          volume_mount {
            name       = "mongodb-data"
            mount_path = "/data/db"
          }
        }
        volume {
          name = "mongodb-data"
          empty_dir {}
        }
      }
    }
  }
}