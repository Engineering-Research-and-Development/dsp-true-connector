# Terraform Deployment Guide — DSP True Connector

This document analyses the existing Terraform setup and describes how to deploy the connector to:
1. A **local** Kubernetes cluster (via Kind)
2. The **remote** Endurance Playground cluster (`kubernetes.services.synelixis.com`)

---

## Current Terraform Architecture — Analysis

### What the existing files already do

| File | Purpose |
|---|---|
| `providers.tf` | Declares `tehcyx/kind` + `hashicorp/kubernetes` providers. The Kubernetes provider **hard-wires** its `config_path` to the Kind cluster output. |
| `main.tf` | Creates a **Kind** (local) cluster named `dsp-cluster` with port-forward mappings for all services. |
| `deployments.tf` | Deploys `connector-a`, `connector-b` (via reusable module), `connector-a-ui`, `connector-b-ui`, `minio`, `mongodb`. |
| `services.tf` | Kubernetes `NodePort` services for MinIO + MongoDB; connector/UI services are created inside their modules. |
| `configmaps.tf` | Mounts all config files (`application.properties`, `initial_data.json`, JKS certs, nginx.conf, TLS secrets) as ConfigMaps/Secrets. |
| `variables.tf` | Parameterises images, ports, callback addresses, keystore config. |
| `modules/connector/` | Reusable Deployment + NodePort Service for any connector instance. |
| `modules/frontend/` | Reusable Deployment + NodePort Service for any UI instance. |

### Key observations / gaps

| # | Observation | Impact |
|---|---|---|
| 1 | `providers.tf` uses `kind_cluster.dsp-cluster.kubeconfig_path` — this **only works locally with Kind**. | Must be changed / overridden for remote deploy. |
| 2 | `main.tf` creates the Kind cluster — must be **skipped** for remote deploy. | |
| 3 | All resources deploy to the **default namespace**. The remote cluster uses `endurance-playground` namespace. | Namespace must be set for remote deploy. |
| 4 | `minio` and `mongodb` are deployed as in-cluster services. On the remote cluster **MinIO already exists** (`ztfl-minio`), and **MongoDB is not available**. | `minio` deployment should be skipped remotely; `mongodb` can remain or also be skipped (see below). |
| 5 | MinIO credentials in `application.properties` are hard-coded to `minioadmin/minioadmin`. On the remote cluster the real credentials come from the `ztfl-minio` secret. | Must update the config map or inject from the remote secret. |
| 6 | S3 endpoint in `application.properties` is `http://minio:9000`. On the remote cluster the service name is `ztfl-minio`. | Must update the endpoint. |
| 7 | NodePort service type works for Kind. Remote cluster may require `ClusterIP` + ingress, or the same NodePort if the cluster allows it. | Verify with cluster admin; NodePort range 30000-32767 is usually allowed. |
| 8 | No `image_pull_policy` is set. For local images (loaded into Kind) `Never` or `IfNotPresent` is needed. | See local setup below. |

---

## Part 1 — Local Deployment (Kind)

### Prerequisites

```powershell
# Install Docker Desktop (running), then:
choco install kind            # or: winget install Kubernetes.kind
choco install terraform
choco install kubernetes-cli  # kubectl
```

### How the current setup handles local ports

Kind maps host ports to NodePorts inside the cluster:

| Host port | NodePort | Service |
|---|---|---|
| 8080 | 30080 | connector-a |
| 8090 | 30090 | connector-b |
| 4200 | 30420 | connector-a-ui |
| 4300 | 30430 | connector-b-ui |
| 9000 | 30081 | minio API |
| 9001 | 30082 | minio Console |

### Using a local Docker image

If your connector image is built locally and not pushed to a registry, load it into Kind **before** running Terraform:

```powershell
# Build the image (from the connector module root)
docker build -t dsp-true-connector:local ./connector

# Load it into the Kind cluster
# NOTE: Kind cluster must already exist (created by 'terraform apply' or manually)
kind load docker-image dsp-true-connector:local --name dsp-cluster
```

Then override the image variable:

```powershell
terraform apply -var="connector_image=dsp-true-connector:local"
```

Or add to a `terraform.tfvars` file:

```hcl
connector_image = "dsp-true-connector:local"
```

You must also set `imagePullPolicy: Never` in the connector module when using local images.  
In `modules/connector/main.tf`, add inside the `container` block:

```hcl
image_pull_policy = "Never"
```

### Deploy to local Kind cluster

```powershell
cd terraform

# Step 1 — Initialise providers
terraform init

# Step 2 — Preview what will be created
terraform plan

# Step 3 — Create Kind cluster + all resources
terraform apply

# Wait for pods to be Running
kubectl get pods   # uses the Kind kubeconfig automatically via KUBECONFIG env or default
```

### Access services locally

```
Connector A:     http://localhost:8080
Connector B:     http://localhost:8090
Connector A UI:  https://localhost:4200
Connector B UI:  https://localhost:4300
MinIO API:       http://localhost:9000
MinIO Console:   http://localhost:9001  (user: minioadmin / minioadmin)
```

### Destroy local cluster

```powershell
terraform destroy
```

---

## Part 2 — Remote Deployment (Endurance Playground)

### Cluster details (from `endurance-playground-config`)

| Property | Value |
|---|---|
| API server | `https://kubernetes.services.synelixis.com:1337` |
| Namespace | `endurance-playground` |
| Auth | Bearer token (service account `endurance-playground-svcs-acct`) |
| Existing MinIO service | `ztfl-minio` (port 9000; credentials in `ztfl-minio` secret) |
| MongoDB | Deployed in-cluster as `mongodb` in `endurance-playground` namespace |
| Ingress controller | nginx (`ingress_class_name = "nginx"`) |

### Step 1 — Prepare a remote-specific Terraform workspace

The cleanest approach is to create a separate Terraform workspace or a separate root module that reuses the same modules but replaces Kind-specific pieces.

#### Create `terraform/remote/` directory structure

```
terraform/
  remote/
    providers.tf       ← points to endurance-playground-config
    main.tf            ← no Kind cluster; only kubernetes resources
    configmaps.tf      ← copy/symlink, with namespace added
    deployments.tf     ← copy/symlink, with namespace added
    services.tf        ← copy/symlink, with namespace added
    variables.tf       ← copy/symlink
    terraform.tfvars   ← remote-specific overrides
    app-resources/     ← symlink or copy from ../app-resources
```

Alternatively, use **Terraform workspaces** combined with `var` overrides and `count`/`for_each` conditionals to skip the Kind provider. The directory approach is simpler and shown below.

### Step 2 — `remote/providers.tf`

```hcl
terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25.0"
    }
  }
}

provider "kubernetes" {
  config_path    = "${path.root}/../../endurance-playground-config"
  config_context = "kubernetes-synelixis-endurance-playground"
}
```

> No `kind` provider needed — no local cluster is created.

### Step 3 — `remote/main.tf`

```hcl
# Remote deployment — no Kind cluster resource needed.
# All resources are defined in deployments.tf, configmaps.tf, services.tf
```

### Step 4 — Add namespace to all resources

Every `kubernetes_*` resource and module call must include `namespace = "endurance-playground"`.

In the connector module (`modules/connector/main.tf`) and frontend module, add a `namespace` variable and pass it through:

```hcl
# modules/connector/variables.tf — add:
variable "namespace" {
  type    = string
  default = "default"
}

# modules/connector/main.tf — in metadata blocks:
metadata {
  name      = var.name
  namespace = var.namespace
}
```

Then in `remote/deployments.tf`, pass `namespace = "endurance-playground"` to every module call.

### Step 5 — Update MinIO config for the remote cluster

On the remote cluster, MinIO is `ztfl-minio` with credentials stored in the `ztfl-minio` Kubernetes secret.

#### Option A — Hard-code credentials in terraform.tfvars (simplest)

After retrieving credentials from the cluster:

```powershell
# Retrieve credentials (PowerShell)
$kubeconfig = "..\..\endurance-playground-config"
$ns = "endurance-playground"

$user = kubectl get secret ztfl-minio -n $ns --kubeconfig $kubeconfig `
  -o jsonpath="{.data.rootUser}" | `
  [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_))

$pass = kubectl get secret ztfl-minio -n $ns --kubeconfig $kubeconfig `
  -o jsonpath="{.data.rootPassword}" | `
  [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_))

Write-Host "MinIO user: $user"
Write-Host "MinIO pass: $pass"
```

Then put the values into `remote/terraform.tfvars`:

```hcl
minio_access_key    = "<retrieved-user>"
minio_secret_key    = "<retrieved-password>"
minio_endpoint      = "http://ztfl-minio:9000"
```

Add the corresponding variables to `variables.tf`:

```hcl
variable "minio_endpoint"   { type = string; default = "http://minio:9000" }
variable "minio_access_key" { type = string; default = "minioadmin" }
variable "minio_secret_key" { type = string; default = "minioadmin" }
```

#### Option B — Reference the existing Kubernetes secret directly (Terraform data source)

```hcl
# remote/main.tf
data "kubernetes_secret" "ztfl_minio" {
  metadata {
    name      = "ztfl-minio"
    namespace = "endurance-playground"
  }
}

locals {
  minio_user     = data.kubernetes_secret.ztfl_minio.data["rootUser"]
  minio_password = data.kubernetes_secret.ztfl_minio.data["rootPassword"]
}
```

Then inject `local.minio_user` / `local.minio_password` into the connector ConfigMaps instead of the hard-coded `minioadmin` values.

### Step 6 — Update `application.properties` for remote MinIO

In `app-resources/connector_a_resources/application.properties` (and `_b_`), change:

```properties
# FROM (local):
s3.endpoint=http://minio:9000
s3.accessKey=minioadmin
s3.secretKey=minioadmin

# TO (remote cluster):
s3.endpoint=http://ztfl-minio:9000
s3.accessKey=${MINIO_ACCESS_KEY}
s3.secretKey=${MINIO_SECRET_KEY}
```

And add `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` to the connector env ConfigMaps in `configmaps.tf`.

### Step 7 — MongoDB on the remote cluster

MongoDB is **deployed in-cluster** in the `endurance-playground` namespace as part of this Terraform configuration (`kubernetes_deployment.mongodb` + `kubernetes_service.mongodb`). No external database is required.

### Step 8 — Ingress and service names for remote cluster

The remote cluster uses an nginx Ingress controller. All services are type `ClusterIP` and are exposed via the following Ingress rules (defined in `ingress.tf`):

| Hostname | Kubernetes Service | Port |
|---|---|---|
| `fe.endurance.lab.synelixis.com` | `tc-fe-service` | 80 |
| `be.endurance.lab.synelixis.com` | `tc-be-service` | 80 |
| `storage.endurance.lab.synelixis.com` | `tc-storage-service` | 80 |

The connector and UI modules accept a `service_name` override so they create services with exactly those names. The MinIO service in `services.tf` is also named `tc-storage-service`.

#### Temporary local access via kubectl port-forward (bypasses Ingress):

```powershell
$kubeconfig = "endurance-playground-config"
$ns = "endurance-playground"

# Connector backend
kubectl port-forward svc/tc-be-service 8080:80 -n $ns --kubeconfig $kubeconfig

# Connector UI
kubectl port-forward svc/tc-fe-service 4200:80 -n $ns --kubeconfig $kubeconfig

# MinIO (using in-cluster tc-storage-service)
kubectl port-forward svc/tc-storage-service 9000:80 -n $ns --kubeconfig $kubeconfig
```

### Step 9 — Deploy to remote cluster

```powershell
cd terraform/remote

terraform init

# Preview
terraform plan -var-file="terraform.tfvars"

# Apply
terraform apply -var-file="terraform.tfvars"
```

### Step 10 — Verify

```powershell
$kubeconfig = "..\..\endurance-playground-config"
$ns = "endurance-playground"

# Check pods
kubectl get pods -n $ns --kubeconfig $kubeconfig

# Check services
kubectl get svc -n $ns --kubeconfig $kubeconfig

# Logs for connector-a
kubectl logs deployment/connector-a -n $ns --kubeconfig $kubeconfig
```

---

## Part 3 — Ingress Explained

### What is an Ingress and why is it here?

In Kubernetes, a **Service** of type `ClusterIP` is only reachable _inside_ the cluster — it has no externally routable address. An **Ingress** is a cluster-level HTTP(S) router that sits in front of those services and maps public hostnames + paths to them.

The remote endurance cluster has an **nginx Ingress controller** already running. When you deploy an `IngressClass = nginx` resource, the controller automatically programs itself to forward incoming HTTP requests to the correct backend service.

```
Internet / VPN
      │
      ▼
nginx Ingress controller  (one shared instance in the cluster)
      │
      ├─ fe.endurance.lab.synelixis.com  ──►  tc-fe-service:80
      ├─ be.endurance.lab.synelixis.com  ──►  tc-be-service:80
      └─ storage.endurance.lab.synelixis.com ──► tc-storage-service:80
```

Without the Ingress, the connector UI and API would be entirely unreachable from outside the cluster (or require manual `kubectl port-forward` every time).

---

### The three Ingress rules (`ingress.tf`)

| Terraform resource | Hostname | Backend service | Backend port | What it exposes |
|---|---|---|---|---|
| `kubernetes_ingress_v1.tc_frontend` | `fe.endurance.lab.synelixis.com` | `tc-fe-service` | 80 | Connector UI (nginx serving the Angular app) |
| `kubernetes_ingress_v1.tc_backend` | `be.endurance.lab.synelixis.com` | `tc-be-service` | 80 | Connector REST API (Spring Boot) |
| `kubernetes_ingress_v1.tc_storage` | `storage.endurance.lab.synelixis.com` | `tc-storage-service` | 80 | MinIO S3 API |

The `tc-storage-ingress` has additional annotations to support large file uploads:
- `proxy-body-size: 0` — disables the default 1 MB body limit
- `proxy-read-timeout: 600` / `proxy-send-timeout: 600` — allows slow uploads to complete

---

### How service names connect everything

The Ingress references service names, not pod names or IPs. Each service selects pods by label. The full chain is:

```
Ingress rule  →  Kubernetes Service (ClusterIP)  →  Pod (via label selector)
```

Specifically:

```
tc-fe-service    →  pods with label app=connector-a-ui  (or connector-b-ui)
tc-be-service    →  pods with label app=connector-a     (or connector-b)
tc-storage-service → pods with label app=minio
```

The `service_name` override in `deployments.tf` is what assigns these exact names:

```hcl
module "connector_a" {
  service_name = "tc-be-service"   # ← this is what the Ingress routes to
  ...
}

module "connector_a_ui" {
  service_name = "tc-fe-service"   # ← this is what the Ingress routes to
  ...
}
```

---

### URL behaviour — internal vs. external

#### External (from a browser or API client outside the cluster)

Traffic enters via the Ingress controller on port 80. The hostname in the request determines which backend receives it.

| Target | URL |
|---|---|
| Connector UI | `http://fe.endurance.lab.synelixis.com` |
| Connector REST API | `http://be.endurance.lab.synelixis.com` |
| MinIO S3 API | `http://storage.endurance.lab.synelixis.com` |

Example — opening the UI in a browser:

```
Browser  →  http://fe.endurance.lab.synelixis.com
         →  nginx Ingress (Host: fe.endurance.lab.synelixis.com)
         →  tc-fe-service:80
         →  connector-a-ui pod:80 (nginx serving the Angular SPA)
         →  Angular app loads; its API calls go to be.endurance.lab.synelixis.com
```

The Angular app must be told where the backend API is. This is the `tc_root_api_url` variable. For the remote cluster it should be an absolute URL:

```hcl
# terraform.tfvars
connector_a_tc_root_api_url = "http://be.endurance.lab.synelixis.com/api/v1"
```

#### Internal (pod-to-pod, inside the cluster)

Pods inside the cluster never go through the Ingress. They talk directly to each other using the Kubernetes DNS name of the **ClusterIP service**:

```
<service-name>.<namespace>.svc.cluster.local
```

| From pod | To service | DNS name used |
|---|---|---|
| connector-a | mongodb | `mongodb.endurance-playground.svc.cluster.local` |
| connector-a | minio (ztfl-minio) | `ztfl-minio.endurance-playground.svc.cluster.local` |
| connector-a-ui (nginx proxy) | connector-a | `tc-be-service.endurance-playground.svc.cluster.local` |

> The short form (e.g. just `mongodb`) also works when both pods are in the same namespace, which is how `application.properties` refers to it:
> ```properties
> spring.data.mongodb.host=mongodb
> s3.endpoint=http://ztfl-minio:9000
> ```

#### Summary table

| Caller | Reaches connector UI via… |
|---|---|
| Browser / external client | `http://fe.endurance.lab.synelixis.com` (through Ingress) |
| Another pod in same namespace | `http://tc-fe-service` or `http://tc-fe-service.endurance-playground.svc.cluster.local` |
| `kubectl port-forward` (developer) | `http://localhost:4200` → `svc/tc-fe-service:80` |

---

### `enable_connector_a` / `enable_connector_b` and the Ingress

Because both connector-a and connector-b would create services with the **same names** (`tc-fe-service`, `tc-be-service`), only one connector pair can be active at a time. The `count` on the Ingress resources matches this:

```hcl
count = var.enable_connector_a ? 1 : (var.enable_connector_b ? 1 : 0)
```

If neither is enabled the Ingress resources are not created. If both were enabled (not recommended here), the second connector's service would collide with the first's name.

---

## Part 4 — Changes Required to Existing Terraform Files (Summary)

### Changes to make the local setup fully work

| File | Change needed |
|---|---|
| `modules/connector/main.tf` | Add `image_pull_policy = "Never"` (for local images) or `"IfNotPresent"` |
| `modules/connector/variables.tf` | Add `namespace` variable (default `"default"`) |
| `modules/frontend/variables.tf` | Add `namespace` variable (default `"default"`) |

### Changes needed for remote deployment

| File | Change |
|---|---|
| `providers.tf` (remote copy) | Remove `kind` provider; point `kubernetes` to `endurance-playground-config` |
| `main.tf` (remote copy) | Remove `kind_cluster` resource; add `data` source for `ztfl-minio` secret |
| All metadata blocks | Add `namespace = var.namespace` |
| `application.properties` (both) | Parameterise `s3.endpoint`, `s3.accessKey`, `s3.secretKey` via env var placeholders |
| `configmaps.tf` | Store S3 credentials and config in `kubernetes_secret` resources; add `count` per connector |
| `deployments.tf` | Set `service_name` + `service_type = "ClusterIP"` on module calls; add `count` for optional connectors |
| `services.tf` | Rename MinIO service to `tc-storage-service`; switch all services to `ClusterIP` |
| `ingress.tf` _(new)_ | Three `kubernetes_ingress_v1` resources mapping hostnames to `tc-fe-service`, `tc-be-service`, `tc-storage-service` |
| `modules/connector/variables.tf` | Add `namespace`, `service_name`, `service_type`, optional `node_port` variables |
| `modules/frontend/variables.tf` | Add `namespace`, `service_name`, `service_type`, optional `node_port` variables |
| `variables.tf` | Add `namespace`, `minio_endpoint`, `s3_region`, `s3_bucket_name_a/b`, `enable_connector_a/b` variables |

---

## Quick Reference — Commands

### Local (Kind)

```powershell
cd terraform
terraform init
terraform apply                          # creates Kind cluster + all resources
kubectl get pods                         # verify (Kind auto-configures kubeconfig)
terraform destroy                        # tear down everything
```

### Remote (Endurance Playground)

```powershell
cd terraform/remote-endurance
terraform init
terraform plan  -var-file="terraform.tfvars"
terraform apply -var-file="terraform.tfvars"

# Port-forward to access locally (bypasses Ingress)
kubectl port-forward svc/tc-be-service 8080:80 `
  -n endurance-playground `
  --kubeconfig endurance-playground-config

kubectl port-forward svc/tc-fe-service 4200:80 `
  -n endurance-playground `
  --kubeconfig endurance-playground-config
```

### Useful kubectl commands (remote)

```powershell
# Set shorthand
$kube = "--kubeconfig ..\..\endurance-playground-config -n endurance-playground"

kubectl get pods    $kube
kubectl get svc     $kube
kubectl get cm      $kube   # configmaps
kubectl get secrets $kube

kubectl logs deployment/connector-a $kube -f
kubectl describe pod <pod-name>      $kube
```

