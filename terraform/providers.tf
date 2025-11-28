terraform {
  required_providers {
    kind = {
      source  = "tehcyx/kind"
      version = "~> 0.2.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25.0"
    }
  }
}

provider "kind" {}

provider "kubernetes" {
  config_path = kind_cluster.dsp-cluster.kubeconfig_path
}