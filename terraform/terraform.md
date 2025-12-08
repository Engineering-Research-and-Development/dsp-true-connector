# Terraform setup

This guide provides a step-by-step approach to setting up Terraform for managing infrastructure as code. Follow these
instructions to get started with Terraform.

## Prerequisites

Before you begin, ensure you have the following prerequisites:

- Install [Terraform](https://www.terraform.io/downloads.html) on your local machine.
- Install Kubernetes command-line tool [kubectl](https://kubernetes.io/docs/tasks/tools/).
- Install Kind (Kubernetes IN Docker) for local Kubernetes clusters. Follow the instructions at
  [Kind Quick Start](https://kind.sigs.k8s.io/docs/user/quick-start/).

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
| `minio`          | ClusterIP | 9000         | Internal service for MinIO object storage |
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
