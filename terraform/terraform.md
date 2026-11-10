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
| `keycloak`       | NodePort  | 8080 (NodePort `30180`, host port `8180`) | Managed Keycloak; created only in `KEYCLOAK` / `MANAGED` mode |
| `keycloak-db`    | ClusterIP | 5432         | Managed Keycloak PostgreSQL; created only in `KEYCLOAK` / `MANAGED` mode |

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

## Authentication and Optional Keycloak

`auth_provider` controls the connectors' `application.auth.provider`. `keycloak_mode` independently controls whether
Terraform owns a Keycloak deployment; it does not select the connector's authentication provider. Only these
combinations are valid:

| `auth_provider` | `keycloak_mode` | Behavior |
|---|---|---|
| `INTERNAL` (default) | `NONE` (default) | Connector uses internal authentication; no Keycloak resources or Keycloak credentials are needed. |
| `DISABLED` | `NONE` | Connector authentication is disabled and endpoints are open. Demo/local use only; the UI remains in its supported `INTERNAL` mode. |
| `KEYCLOAK` | `MANAGED` | Terraform deploys Keycloak and PostgreSQL and imports the checked-in demo realms. |
| `KEYCLOAK` | `EXTERNAL` | Applications use an existing Keycloak; Terraform creates no Keycloak or PostgreSQL resources and does not configure the remote server. |

Terraform rejects other combinations: `INTERNAL` and `DISABLED` require `keycloak_mode=NONE`, while `KEYCLOAK` requires
`MANAGED` or `EXTERNAL`.

When using Keycloak, set `keycloak_base_url` to the absolute HTTP(S) base URL that is reachable from both the browser
and connector pods. It must match Keycloak's issuer: each realm's issuer is
`<keycloak_base_url>/realms/<realm>`. The same value configures Keycloak's managed `KC_HOSTNAME`, imported realm
`frontendUrl`, connector `application.keycloak.base-url`, and UI `KEYCLOAK_URL`. Do not use a browser-only `localhost`
address if connector pods cannot reach it. Managed mode maps the configurable host port (default `8180`) to the
configurable NodePort (default `30180`); the `keycloak` Service listens on port `8080`.

The UI performs the browser authorization-code flow with PKCE S256; the connector validates the resulting JWT. Both
UIs receive only the public runtime settings `AUTH_PROVIDER`, `KEYCLOAK_URL`, `KEYCLOAK_CLIENT_ID=keycloak-api`, and
`KEYCLOAK_ADMIN_REALM=connector-admin`. No client secret is passed to the UI. For Keycloak login, use an image built
from the verified
[UI `feature/keycloak_auth_code_login` branch](https://github.com/Engineering-Research-and-Development/dsp-true-connector-ui/tree/feature/keycloak_auth_code_login)
at commit `ce736d0a788d592850eec1f6674c447ee2d1541c` or a later compatible revision. The configured UI origins
(`connector_a_ui_origin` and `connector_b_ui_origin`, defaulting to `http://localhost:4200` and
`http://localhost:4300`) must be allowed by the public `keycloak-api` client: exact `<origin>/auth/callback`
redirects, `<origin>/login` post-logout destinations, and matching web origins.

### Managed mode

`KEYCLOAK` / `MANAGED` requires `keycloak_base_url` and non-empty
`keycloak_admin_username`, `keycloak_admin_password`, `keycloak_db_username`, and `keycloak_db_password` inputs.
Supply credentials through a protected environment or secret manager, such as Terraform's `TF_VAR_...` inputs; do not
put real credentials in tracked `terraform.tfvars`. Terraform stores them in the `keycloak-credentials` Kubernetes
Secret. It also creates the `keycloak-realm-import` Secret from the five seed exports (`platform`,
`connector-admin`, `tenant-EngineeringA`, `tenant-EngineeringB`, and `tenant-acme`) and configures the `keycloak` and
`keycloak-db` Deployment/Service pairs.

Realm import is initial-seed-only: Keycloak imports a realm when it does not already exist; editing the source files
does not reconcile an existing realm. The seeds include demo users and client credentials and are not production
identity data. PostgreSQL uses ephemeral `empty_dir` storage, so recreating its pod loses Keycloak state. Switching
from `MANAGED` to `NONE` or `EXTERNAL` removes the Terraform-managed Keycloak/PostgreSQL resources and their current
ephemeral data.

### External mode

`KEYCLOAK` / `EXTERNAL` requires `keycloak_base_url` and the sensitive
`keycloak_backend_client_secret` for the pre-provisioned `platform/dsp-connector-backend` confidential client.
Terraform passes that secret to both connector credential Secrets, but does not create, import, change, or validate
anything on the external Keycloak server. No Keycloak administrator or PostgreSQL credentials are needed.

Provision the external provider according to [`doc/security.md`](../doc/security.md#keycloak-authentication-mode-keycloak):
the `platform` realm's `dsp-connector-backend` client must provide the `CONNECTOR` role and audience
`dsp-true-connector`; `connector-admin` must provide the public `keycloak-api` client and `SUPER_ADMIN` users; and each
tenant realm named in connector tenant records must provide that public client, `ADMIN` users, and the matching
`tenant_id`. The `keycloak-api` clients need Standard Flow and PKCE S256, the exact callback/logout URLs and web
origins for both configured UI origins. Discovery's `issuer` must exactly equal
`<keycloak_base_url>/realms/<realm>`. Use HTTPS with a certificate trusted by both browsers and connector pods; provide
private-CA trust separately if required. The base URL, discovery, and JWKS endpoints must be reachable from both
browsers and connector pods.

### Protecting credentials and Terraform state

Kubernetes Secrets are base64-encoded and are not necessarily encrypted at rest. Terraform state and saved plan files
can contain secret values, including imported demo realm credentials. Restrict access to state and plan artifacts,
use an appropriately protected remote state backend for shared deployments, and provide production credentials through
a managed secret store. Never treat the checked-in realm users or client secrets as production credentials.

## Usage

### Automated configuration checks

The [Terraform checks workflow](../.github/workflows/terraform-checks.yml) runs on pull requests targeting
`develop`, pushes to `develop`, and manual dispatch. It uses Terraform 1.14.8 to check formatting, validate
the root configuration and its referenced modules, and run the mocked configuration tests. It runs without
path filters so it can be used as a required pull request check.

Run the same checks locally from the repository root:

```sh
terraform -chdir=terraform fmt -check -recursive
terraform -chdir=terraform init -backend=false -input=false -no-color
terraform -chdir=terraform validate -no-color
terraform -chdir=terraform test -no-color
```

Initialization downloads providers and creates local initialization files, but does not deploy infrastructure.
These checks do not require Docker, Kubernetes access, deployment credentials, or HCP Terraform. Test scenarios
evaluate plans with mocked providers; they do not apply or destroy real infrastructure, or verify application
readiness or connectivity. Disposable Kind deployment tests can be added separately.

### Mocked configuration tests

The native Terraform suite in [`tests/auth_modes.tftest.hcl`](tests/auth_modes.tftest.hcl) covers:

| Scenario | Expected result |
|---|---|
| `INTERNAL` / `NONE` | No Keycloak or PostgreSQL deployments. |
| `DISABLED` / `NONE` | No Keycloak or PostgreSQL deployments; the UI remains in its supported `INTERNAL` mode. |
| `KEYCLOAK` / `MANAGED` | Keycloak, PostgreSQL, and realm-import resources are planned. |
| `KEYCLOAK` / `EXTERNAL` | No managed Keycloak, PostgreSQL, or realm-import resources. |
| `INTERNAL` / `MANAGED` | Both connectors' configuration preconditions reject the combination. |
| UI origin containing a path | Input validation rejects the origin. |

Use Terraform 1.14.8 to match CI. Provider mocking requires at least Terraform 1.7, but the configuration's other
language features may require a newer version. After initialization, run all tests with
`terraform -chdir=terraform test -no-color`, or select just this file:

```sh
terraform -chdir=terraform test -filter=tests/auth_modes.tftest.hcl -no-color
```

Each `run` block uses `command = plan` with mocked `kind`, `kubernetes`, and `local` providers. No cluster is
created, Kubernetes is not contacted, and the `local_file` resources do not write generated application files.
Templates, certificates, and realm JSON files are still read from the repository. The tests use separate test
state, not the state of an existing deployment.

Test variables override `.tfvars` and environment inputs for authentication mode, credentials, and UI origins;
other unspecified inputs still follow normal variable precedence. Credentials supplied by the scenarios are
dummy values and the example URLs need not resolve. No real deployment credentials should be supplied for these tests.

Terraform reports each scenario as passed or failed and exits nonzero for unexpected errors or failed assertions.
The negative cases use `expect_failures`: they pass only when the specified validation or precondition rejects
the input. The workflow runs the same suite after configuration validation. Passing these tests verifies the
asserted configuration logic, not image availability, networking, actual Keycloak behavior, or application startup.

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
