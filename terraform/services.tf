# This file defines the Kubernetes services for RustFS.
resource "kubernetes_service" "s3storage" {
  metadata {
    name = "s3storage"
  }
  spec {
    type = "NodePort"
    port {
      name        = "api"
      port        = 9000
      target_port = 9000
      node_port   = 30081 # Match the port mapping in kind_cluster
    }
    port {
      name        = "console"
      port        = 9001
      target_port = 9001
      node_port   = 30082 # Match the port mapping in kind_cluster
    }
    selector = {
      app = "rustfs"
    }
  }
}

# This file defines the Kubernetes services for MongoDB.
resource "kubernetes_service" "mongodb" {
  metadata {
    name = "mongodb"
  }
  spec {
    type = "ClusterIP"
    port {
      port        = 27017
      target_port = 27017
    }
    selector = {
      app = "mongodb"
    }
  }
}

resource "kubernetes_service" "keycloak_db" {
  for_each = var.auth_provider == "KEYCLOAK" && var.keycloak_mode == "MANAGED" ? { enabled = true } : {}

  metadata {
    name = "keycloak-db"
  }

  spec {
    type = "ClusterIP"

    port {
      name        = "postgres"
      port        = 5432
      target_port = 5432
    }

    selector = {
      app = "keycloak-db"
    }
  }
}

resource "kubernetes_service" "keycloak" {
  for_each = var.auth_provider == "KEYCLOAK" && var.keycloak_mode == "MANAGED" ? { enabled = true } : {}

  metadata {
    name = "keycloak"
  }

  spec {
    type = "NodePort"

    port {
      name        = "http"
      port        = 8080
      target_port = 8080
      node_port   = var.keycloak_node_port
    }

    selector = {
      app = "keycloak"
    }
  }
}