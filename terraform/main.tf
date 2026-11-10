resource "kind_cluster" "dsp-cluster" {
  name           = "dsp-cluster"
  wait_for_ready = true
  kind_config {
    kind        = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    node {
      role = "control-plane"
      extra_mounts {
        host_path      = "${path.root}/../app-resources"
        container_path = "/app-resources"
      }
      extra_port_mappings {
        container_port = 30080
        host_port      = 8080
        protocol       = "TCP"
      }
      extra_port_mappings {
        container_port = 30090
        host_port      = 8090
        protocol       = "TCP"
      }
      extra_port_mappings {
        container_port = 30420
        host_port      = 4200
        protocol       = "TCP"
      }
      extra_port_mappings {
        container_port = 30430
        host_port      = 4300
        protocol       = "TCP"
      }
      extra_port_mappings {
        container_port = 30081
        host_port      = 9000
        protocol       = "TCP"
      }
      extra_port_mappings {
        container_port = 30082
        host_port      = 9001
        protocol       = "TCP"
      }
      dynamic "extra_port_mappings" {
        for_each = var.auth_provider == "KEYCLOAK" && var.keycloak_mode == "MANAGED" ? [true] : []

        content {
          container_port = var.keycloak_node_port
          host_port      = var.keycloak_host_port
          protocol       = "TCP"
        }
      }
    }
  }
}