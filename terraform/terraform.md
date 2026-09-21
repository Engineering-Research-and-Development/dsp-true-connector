# Terraform setup

This guide provides a step-by-step approach to setting up Terraform for managing infrastructure as code. Follow these
instructions to get started with Terraform.

## Prerequisites

Before you begin, ensure you have the following prerequisites:

- Install [Terraform](https://www.terraform.io/downloads.html) on your local machine.
- Install Kubernetes command-line tool [kubectl](https://kubernetes.io/docs/tasks/tools/).
- Install Kind (Kubernetes IN Docker) for local Kubernetes clusters. Follow the instructions at
  [Kind Quick Start](https://kind.sigs.k8s.io/docs/user/quick-start/).

### Installing Kind on Linux / WSL Ubuntu

`kind` is not available via `apt`. Install the static binary directly:

```sh
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.23.0/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind

# Verify installation
kind version
kind get clusters   # should list "dsp-cluster" once terraform apply has run
```

For other architectures/OSes, see the [Kind releases page](https://github.com/kubernetes-sigs/kind/releases).

## Kubernetes Resources Overview

### Provider

The Kubernetes provider in Terraform allows you to manage Kubernetes resources using Terraform configuration files. It
interacts with the Kubernetes API to create, update, and delete resources.

### Services

A Service in Kubernetes is an abstraction that defines a logical set of Pods and a policy by which to access them.
Services enable network access to a set of Pods, load balancing, and service discovery.

Services configured in current configuration are:

| Service Name     | Type      | Exposed port | Description                               |
|------------------|-----------|--------------|-------------------------------------------|
| `connector-a`    | ClusterIP | 8080         | Internal service for Connector A          |
| `connector-a-ui` | NodePort  | 4200         | External access to Connector A UI         |
| `connector-b`    | ClusterIP | 8090         | Internal service for Connector B          |
| `connector-b-ui` | NodePort  | 4300         | External access to Connector B UI         |
| `rustfs`         | ClusterIP | 9000         | Internal service for RustFS object storage |
| `mongodb`        | ClusterIP | 27017        | Internal service for MongoDB database     |

### Deployments

A Deployment manages a set of identical Pods, ensuring the desired number are running and up-to-date. Deployments
provide declarative updates,
rolling updates, rollbacks, and scaling. They are the recommended way to manage stateless applications.

### ConfigMaps

A ConfigMap is used to store non-confidential configuration data in key-value pairs. ConfigMaps decouple configuration
from application code,
allowing you to change configuration without rebuilding images. They can be mounted as files or exposed as environment
variables in Pods.

Here you can change configuration settings for your applications without modifying the application code.
For example:

- keystore and truststore files
- environment variables for connectors (keystore password, callback address, etc.)
- initial_data for connectors
- connector property files
- frontend configuration files (nginx.conf and ssl)

## Usage

### Starting Terraform

- Initialize Terraform:
  ```sh
  terraform init
  ```
- Plan the deployment:
  ```sh
  terraform plan
  ```
- Apply the configuration (create resources):
  ```sh
  terraform apply
  ```

### Stopping Terraform

- Destroy all resources created by Terraform:
  ```sh
  terraform destroy
  ```

### Using a Locally Built Docker Image (not pushed to a registry)

Kind runs Kubernetes nodes as separate containers with their **own internal image store**, isolated from your host's
Docker daemon. `docker images` on your host does **not** mean the Kind node can see or pull that image — you must load
it explicitly.

1. Build your image locally:
   ```sh
   docker build -t ghcr.io/engineering-research-and-development/dsp-true-connector:local ./connector
   ```
2. Load it into the running Kind cluster (cluster must already exist, e.g. after a previous `terraform apply`):
   ```sh
   kind load docker-image ghcr.io/engineering-research-and-development/dsp-true-connector:local --name dsp-cluster
   ```
3. Point Terraform at the local tag, either via `-var` or in `terraform.tfvars`:
   ```sh
   terraform apply -var="connector_image=ghcr.io/engineering-research-and-development/dsp-true-connector:local"
   ```
4. Ensure the deployment does not try to pull from the registry. In `modules/connector/main.tf`, set:
   ```hcl
   image_pull_policy = "Never"
   ```

**Important — tag collisions:** if you reuse a tag that also exists in the remote registry (e.g. `test`), `IfNotPresent`
may silently keep whichever image the node already cached — local or remote — with no clear indication of which one was
used. Prefer:
- A unique local-only tag per build (e.g. `local`, `test1`, `test2`, …), and
- `image_pull_policy = "Never"`, so a missing `kind load` step fails loudly (`ErrImageNeverPull`) instead of silently
  pulling the wrong image from the registry.

You must repeat `kind load docker-image` every time you rebuild the image — Kind nodes do not share your host's Docker
build cache/layers.

### Inspecting Kubernetes State

- List all pods in all namespaces:
  ```sh
  kubectl get pods --all-namespaces
  ```
- Get detailed info about a specific pod:
  ```sh
  kubectl describe pod <pod-name> -n dsp-cluster
  ```
- View logs for a pod:
  ```sh
  kubectl logs <pod-name> -n dsp-cluster
  ```
- List all services:
  ```sh
  kubectl get svc --all-namespaces
  ```
- List all deployments:
  ```sh
  kubectl get deployments --all-namespaces
  ```
