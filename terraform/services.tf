# This file defines the Kubernetes services for MinIO.
resource "kubernetes_service" "minio" {
  metadata {
    name = "minio"
  }
  spec {
    type = "NodePort"
    port {
      name        = "api"
      port        = 9000
      target_port = 9000
      node_port   = 30081  # Match the port mapping in kind_cluster
    }
    port {
      name        = "console"
      port        = 9001
      target_port = 9001
      node_port   = 30082  # Match the port mapping in kind_cluster
    }
    selector = {
      app = "minio"
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