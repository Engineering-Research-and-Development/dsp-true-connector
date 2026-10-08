# Integrating with ENDURANCE Keycloak (SSO) — Onboarding Guide

> Reference answer for any tool/service (e.g. CIRS-Agent) that needs to
> authenticate against the ENDURANCE identity provider. Reusable for future
> integrators.

## First, a clarification about the repository

The `keycloak-tools` repository you reviewed contains **example/default
configurations and management scripts** — it is *not* the deployment definition of
the live ENDURANCE Keycloak, and it does not contain realm/client state or
credentials. That is correct and by design: the live instance is operated by the
ENDURANCE DevOps team, and **clients/accounts are provisioned by us on request**,
not self-served from that repo. The sections below tell you everything needed to
integrate.

## 1. Keycloak instance

| | Value |
|---|---|
| **Base URL / Issuer host** | `https://auth.endurance.lab.synelixis.com` |
| **Issuer (per realm)** | `https://auth.endurance.lab.synelixis.com/realms/<realm>` |
| **OIDC discovery** | `https://auth.endurance.lab.synelixis.com/realms/<realm>/.well-known/openid-configuration` |
| **Protocol** | OpenID Connect (OAuth 2.0) |

Always integrate against the **discovery document** (don't hard-code token/JWKS
endpoints) — it returns the authorization, token, userinfo, and JWKS URLs.

> **Network note:** this hostname is reachable on the **public internet and will
> stay that way** — no reachability or configuration change is planned. Any future
> network-level restriction would come via the centralized Synelixis IT-managed
> VPN and would be coordinated with integrators well in advance.

## 2. Realms

| Realm | Purpose |
|-------|---------|
| `endurance-platform` | Platform/service realm — platform-wide services and their clients |
| `endurance-<tenant>` (×23) | One realm per partner/tenant (e.g. `endurance-eydap`), with standardized roles `reader` / `writer` / `executor` / `admin` |

**Which realm CIRS-Agent should use depends on its scope:**
- A **platform-wide** service that operates across the system → a client in
  **`endurance-platform`**.
- A service that acts **on behalf of a specific tenant** → a client in that
  tenant's `endurance-<tenant>` realm (or wired into several).

If unsure, default recommendation for an automation/agent is a **confidential
service client in `endurance-platform`** using the **client-credentials** grant.

## 3. Clients & accounts — how integrations are set up

- **We create the client for you.** Integrations receive a **dedicated Keycloak
  client**, not a Keycloak admin account. Admin/console access is not granted to
  integrating tools.
- For machine-to-machine (no human login), we create a **confidential client with
  a service account** (OAuth2 `client_credentials` grant). You receive a
  `client_id` + `client_secret`.
- For flows where a human logs in through CIRS-Agent, we create a client with the
  appropriate flow (auth-code) and redirect URIs you provide.
- Roles/permissions are attached to the client/service-account per your stated
  needs (e.g. tenant `reader`/`writer`/`executor`/`admin`, or platform roles).

## 4. What we need from you to provision the client

Reply with:

1. **Deployment location** — does CIRS-Agent run **inside** the ENDURANCE
   Kubernetes cluster, or **externally**? (Helps us plan integration and any
   privileged-access needs — see §6.)
2. **Scope** — platform-wide, or scoped to specific tenant(s)? If tenant-scoped,
   which tenant code(s)?
3. **Integration type** — machine-to-machine (service account, no user login), or
   does a human authenticate through it?
4. **If human login:** the **redirect URI(s)** and **web origin(s)**.
5. **Permissions needed** — what is CIRS-Agent allowed to do (read/write/execute,
   which APIs/tenants)?
6. **Preferred client id** (we suggest `cirs-agent`) and a technical contact.

We will then create the client and return the `client_id` (and, for confidential
clients, the secret via a secure channel — see §7).

## 5. Token / integration contract

Once provisioned:

- **Machine-to-machine token:**
  ```
  POST https://auth.endurance.lab.synelixis.com/realms/<realm>/protocol/openid-connect/token
       grant_type=client_credentials
       client_id=<your client id>
       client_secret=<your secret>
  ```
- **Validate access tokens** against the realm's JWKS (from discovery). Check:
  - `iss` = `https://auth.endurance.lab.synelixis.com/realms/<realm>`
  - `aud` contains the audience you were assigned
  - `realm_access.roles` (and/or client roles) for authorization
- Tokens are standard OIDC JWTs; roles drive authorization on the ENDURANCE side.

## 6. Network access

| If CIRS-Agent runs… | How it reaches Keycloak |
|---------------------|--------------------------|
| **Inside** the cluster | Use the issuer URL `https://auth.endurance.lab.synelixis.com/...` (resolves internally) |
| **Outside** the cluster | Public internet — works today and stays that way |

The OIDC endpoints are and remain publicly reachable over HTTPS. Separately, if
your tool needs **privileged access to the ENDURANCE environment** beyond the
public HTTPS services, IT-managed VPN credentials exist for that — arranged
through the DevOps team.

## 7. Secret handling

- Client secrets are **never** sent over email/chat/issue trackers. We deliver them
  via an agreed secure channel (e.g. a secrets manager entry, encrypted message, or
  in-cluster Kubernetes Secret if CIRS-Agent is in-cluster).
- Rotate on request; tell us if a secret is exposed and we re-issue.

---

## Quick answers to the questions asked

| Question | Answer |
|----------|--------|
| **Keycloak URL?** | `https://auth.endurance.lab.synelixis.com` (issuer: `…/realms/<realm>`) |
| **Which realm?** | `endurance-platform` for platform-wide use, or a specific `endurance-<tenant>` realm if tenant-scoped — confirm your scope (§2/§4) |
| **Client/admin already created?** | No dedicated CIRS-Agent client exists yet. Integrations get a **client**, not an admin account |
| **Provide Client ID / Admin ID?** | We'll assign one (suggested `cirs-agent`) once you send the §4 details; secret via secure channel |
| **Can you create the client / get permissions?** | **We provision it** — you don't need Keycloak admin. Send the §4 intake and we'll create it with the right roles |
