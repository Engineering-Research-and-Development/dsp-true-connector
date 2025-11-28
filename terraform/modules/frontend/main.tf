resource "kubernetes_deployment" "this" {
  metadata {
    name = var.name
    labels = {
      app = var.name
    }
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
          env {
            name  = "TC_ROOT_API_URL"
            value = var.tc_root_api_url
          }
          volume_mount {
            name       = "nginx-conf"
            mount_path = "/etc/nginx/nginx.conf"
            sub_path   = "nginx.conf"
          }
          volume_mount {
            name       = "ssl"
            mount_path = "/etc/nginx/ssl"
            read_only  = true
          }
        }
        volume {
          name = "nginx-conf"
          config_map {
            name = var.nginx_conf_config_map
          }
        }
        volume {
          name = "ssl"
          secret {
            secret_name = var.ssl_secret
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
      target_port = var.target_port
      node_port   = var.node_port
    }
    selector = {
      app = var.name
    }
  }
}
