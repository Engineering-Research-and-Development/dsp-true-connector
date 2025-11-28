// Module for connector deployment and service
resource "kubernetes_deployment" "this" {
  metadata {
    name = var.name
  }
  spec {
    replicas = 1
    selector {
      match_labels = {
        app = var.name
      }
    }
    template {
      metadata {
        labels = {
          app = var.name
        }
      }
      spec {
        container {
          name  = var.name
          image = var.image
          port {
            container_port = var.container_port
          }
          env_from {
            config_map_ref {
              name = var.env_config_map
            }
          }
          volume_mount {
            name       = "config"
            mount_path = "/config/application.properties"
            sub_path   = "application.properties"
          }
          volume_mount {
            name       = "initial-data"
            mount_path = "/config/initial_data.json"
            sub_path   = "initial_data.json"
          }
          # Optional extra volume mount for connector-b
          dynamic "volume_mount" {
            for_each = var.employee_data_config_map != null ? [1] : []
            content {
              mount_path = "/config/ENG-employee.json"
              sub_path   = "ENG-employee.json"
              name       = "employee-data"
            }
          }
          volume_mount {
            name       = "certs"
            mount_path = "/cert"
          }
        }
        volume {
          name = "config"
          config_map {
            name = var.config_map
          }
        }
        # Optional employee-data volume for connector-b
        dynamic "volume" {
          for_each = var.employee_data_config_map != null ? [1] : []
          content {
            name = "employee-data"
            config_map {
              name = var.employee_data_config_map
            }
          }
        }
        volume {
          name = "initial-data"
          config_map {
            name = var.initial_data_config_map
          }
        }
        volume {
          name = "certs"
          config_map {
            name = var.certs_config_map
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "this" {
  metadata {
    name = var.name
  }
  spec {
    type = "NodePort"
    port {
      name        = "http"
      port        = var.service_port
      target_port = var.container_port
      node_port   = var.node_port
    }
    selector = {
      app = var.name
    }
  }
}
