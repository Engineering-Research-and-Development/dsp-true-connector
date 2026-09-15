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
  credentials_secret       = kubernetes_secret.connector_a_credentials.metadata[0].name
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
  credentials_secret       = kubernetes_secret.connector_b_credentials.metadata[0].name
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

# This file defines the Kubernetes deployment for RustFS.
resource "kubernetes_deployment" "rustfs" {
  metadata {
    name = "rustfs"
  }
  spec {
    replicas = 1
    selector {
      match_labels = {
        app = "rustfs"
      }
    }
    template {
      metadata {
        labels = {
          app = "rustfs"
        }
      }
      spec {
        container {
          name              = "rustfs"
          image             = var.rustfs_image
          image_pull_policy = "IfNotPresent"
          port {
            name           = "api"
            container_port = 9000
          }
          port {
            name           = "console"
            container_port = 9001
          }
          env {
            name  = "RUSTFS_ACCESS_KEY"
            value = "rustfsadmin"
          }
          env {
            name  = "RUSTFS_SECRET_KEY"
            value = "rustfsadmin"
          }
          env {
            name  = "RUSTFS_ADDRESS"
            value = ":9000"
          }
          volume_mount {
            name       = "rustfs-data"
            mount_path = "/data"
          }
        }
        volume {
          name = "rustfs-data"
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
          name              = "mongodb"
          image             = var.mongodb_image
          image_pull_policy = "IfNotPresent"
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