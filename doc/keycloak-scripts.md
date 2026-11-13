# Keycloak provisioning scripts

The scripts in [`scripts/keycloak/`](../scripts/keycloak/) create the connector's Keycloak realms, clients, scopes and users through the Keycloak Admin REST API. They build the same realms as the seed files in [`ci/docker/keycloak_resources/`](../ci/docker/keycloak_resources/), and they can add further tenant realms and users of the same shape.

For how tenants and users map to these realms, see [keycloak-tenant-mapping.md](keycloak-tenant-mapping.md). For the validation rules, see [security.md](security.md#seed-realms-and-tenant-identity) and [multi-tenant-user-guide.md](multi-tenant-user-guide.md).

## When to use them

| Situation | What to use |
|---|---|
| Local stack started with `docker compose --profile keycloak` | Nothing. Keycloak imports the seed files on its first start (`--import-realm`). |
| A Keycloak that is already running and was not started with the seed import (shared dev server, test environment, a Keycloak whose database already exists) | `bootstrap-keycloak.sh` |
| A seed realm drifted or was partly deleted | `bootstrap-keycloak.sh`. It converges existing realms, so it is safe to re-run. |
| A new tenant | `create-tenant-realm.sh`, then `create-user.sh` |
| A new user in an existing realm | `create-user.sh` |

`--import-realm` imports a realm only if it does not exist yet. Changes to the seed files therefore never reach a Keycloak that already has the realm, but re-running `bootstrap-keycloak.sh` updates it.

## Prerequisites

- `bash` 4.4 or later, `curl`, `jq`
- a running Keycloak (tested with 26.0.0, the version in `connector/docker-compose.yml`)
- a Keycloak admin account that can use the `admin-cli` password grant. The local stack uses `admin` / `admin` in the `master` realm.

## The scripts

| Script | Purpose |
|---|---|
| `keycloak-api.sh` | Shared library, used by the other scripts; do not run it directly. Holds the configuration and the ensure-style functions (create when missing, update otherwise). |
| `bootstrap-keycloak.sh` | Creates all five seed realms with their users. |
| `create-tenant-realm.sh` | Creates one tenant realm. |
| `create-user.sh` | Creates or updates one user and assigns realm roles. |

Every script prints its usage with `--help`.

## Configuration

All settings have defaults that match the local stack. You can override them with environment variables. `bootstrap-keycloak.sh` also accepts the most common ones as options, and the options take precedence.

| Variable | Option (bootstrap only) | Default | Meaning |
|---|---|---|---|
| `KEYCLOAK_URL` | `--url` | `http://localhost:8180` | Address the scripts use to call the Admin REST API |
| `KEYCLOAK_PUBLIC_URL` | `--public-url` | `KEYCLOAK_URL` | Address browsers and the connector use. It becomes the realm `frontendUrl` (and so the token `iss`) and the admin/account console origin. |
| `KEYCLOAK_ADMIN` | `--admin` | `admin` | Admin user |
| `KEYCLOAK_ADMIN_PASSWORD` | `--password` | `admin` | Admin password |
| `KEYCLOAK_ADMIN_REALM` | | `master` | Realm of the admin user |
| `KEYCLOAK_CLIENT_ID` | | `keycloak-api` | Public client of the connector UI |
| `CONNECTOR_UI_URL` | | `http://localhost:4200` | UI origin: redirect URI and web origin of `keycloak-api` |
| `CONNECTOR_AUDIENCE` | | `dsp-true-connector` | `aud` the connector requires |
| `CONNECTOR_AUDIENCE_SCOPE` | | `connector-audience` | Client scope that emits that `aud` |
| `TENANT_REALM_PREFIX` | | `tenant-` | Prefix of tenant realm names |
| `DSP_BACKEND_CLIENT_SECRET` | | `dsp-connector-backend-secret` | Secret of `dsp-connector-backend` (bootstrap only) |

`KEYCLOAK_PUBLIC_URL` matters whenever the scripts reach Keycloak at a different address from the connector. The connector accepts a token only if its `iss` matches the issuer it was configured with. If the realm `frontendUrl` is wrong, every token gets `401`. See [security.md](security.md) for issuer troubleshooting.

## `bootstrap-keycloak.sh`: seed all realms

```bash
# Local stack (Keycloak on localhost:8180, admin/admin)
scripts/keycloak/bootstrap-keycloak.sh

# Another Keycloak: called internally on one address, used by browsers on another
scripts/keycloak/bootstrap-keycloak.sh \
  --url http://keycloak.internal:8080 \
  --public-url https://auth.example.com \
  --admin admin --password '<admin-password>'
```

It creates:

| Realm | Contents | Login |
|---|---|---|
| `platform` | Role `CONNECTOR`; confidential client `dsp-connector-backend` (client credentials grant), whose service account has `CONNECTOR` | `client_id=dsp-connector-backend`, `client_secret=dsp-connector-backend-secret` |
| `connector-admin` | Role `SUPER_ADMIN`; public client `keycloak-api` | `superadmin@mail.com` / `password` |
| `tenant-EngineeringA` | Tenant realm (see below), `tenant_name` `EngineeringA` | `admin` / `password`, `tenant_id` `engineering-a` |
| `tenant-EngineeringB` | Tenant realm, `tenant_name` `EngineeringB` | `adminB` / `password`, `tenant_id` `engineering-b` |
| `tenant-acme` | Tenant realm, `tenant_name` `Acme` (demo only) | `coyote` / `coyote`, `roadrunner` / `roadrunner`, `tenant_id` `acme` |

The passwords and the client secret are the ones from the seed files, so use this only for local and test setups. On any other Keycloak, set `DSP_BACKEND_CLIENT_SECRET`, and change the user passwords afterwards (or create users with `create-user.sh` instead).

Each token carries `aud=dsp-true-connector` and only its realm's connector role, because the clients use `fullScopeAllowed=false` plus explicit scope mappings.

## `create-tenant-realm.sh`: add a tenant realm

```bash
scripts/keycloak/create-tenant-realm.sh <tenant> <display-name> [tenant-name]

# Example: realm tenant-Logistics, org=Logistics, tenant_name=Logistics
scripts/keycloak/create-tenant-realm.sh Logistics "Logistics tenant"

# Example: realm tenant-acme, org=acme, tenant_name=Acme
scripts/keycloak/create-tenant-realm.sh acme "Acme tenant" Acme
```

| Argument | Becomes |
|---|---|
| `<tenant>` | Realm name `tenant-<tenant>` and the value of the `org` claim |
| `<display-name>` | Realm display name, shown on the login page |
| `[tenant-name]` | Value of the `tenant_name` claim, which the UI shows. Must equal the tenant `name` in the connector. Defaults to `<tenant>`. |

The realm it creates contains:

- realm role `ADMIN`
- public client `keycloak-api` (standard flow with PKCE S256 and direct access grants, redirect `CONNECTOR_UI_URL/*`), whose tokens carry only `ADMIN`
- realm-default scope `connector-audience`: `aud=dsp-true-connector`, plus `accessible_tenants` (JSON) and `home_realm` when the user has those attributes
- realm-default scope `org`, attached to every client: `org` and `tenant_name` (fixed for the realm), and `tenant_id` (from the user attribute)
- user-profile policy `ADMIN_EDIT`, so admins can set `tenant_id` and `org_type` on users. Without it, Keycloak silently drops them.
- admin console (`<public-url>/admin/<realm>/console`) and account console working on the public Keycloak origin

### A Keycloak realm alone is not enough

The connector trusts a tenant realm only while an **enabled tenant in MongoDB** names it in its `realm` field. A new realm also needs:

1. a tenant record whose `realm` is `tenant-<tenant>` and whose `name` equals `[tenant-name]`, created through the tenant API as super-admin or through the seed data
2. users whose `tenant_id` equals that tenant's `_id`

Without the tenant record, tokens from the realm get `401`. Without the right `tenant_id`, the tenant binding also returns `401`.

## `create-user.sh`: add or update a user

```bash
scripts/keycloak/create-user.sh <realm> <username> <email> <password> [realm-roles...] [options]
scripts/keycloak/create-user.sh <realm> <username> <email> --no-password [realm-roles...] [options]
```

| Option | Meaning |
|---|---|
| `--tenant-id <id>` | `tenant_id` attribute. Required for tenant users; it is the tenant `_id` (e.g. `engineering-a`), never the realm name. |
| `--org-type <type>` | `org_type` attribute (e.g. `operator`, `tech`) |
| `--accessible-tenants <csv>` | `accessible_tenants` attribute, emitted as a JSON array claim |
| `--first-name`, `--last-name` | Given and family name |
| `--no-password` | Create no credential; pair with `--required-action` |
| `--required-action <name>` | Required action at first login, e.g. `UPDATE_PASSWORD`; repeatable |
| `--grant-realm-admin` | Also grant `realm-management/realm-admin`, giving access to the realm's own Keycloak admin console |

Examples:

```bash
# Tenant admin
scripts/keycloak/create-user.sh tenant-Logistics alice alice@example.com 'S3cret!' ADMIN \
  --tenant-id logistics --org-type operator --first-name Alice --last-name Example

# Tenant admin who sets their own password at first login
scripts/keycloak/create-user.sh tenant-Logistics bob bob@example.com --no-password ADMIN \
  --tenant-id logistics --required-action UPDATE_PASSWORD

# Super-admin (connector-admin realm, no tenant_id)
scripts/keycloak/create-user.sh connector-admin ops@example.com ops@example.com 'S3cret!' SUPER_ADMIN
```

If the user already exists, the script updates the user's name, email and given attributes (other attributes are kept), resets the password, and adds the roles. It never removes roles.

Rules the connector enforces (see [security.md](security.md#user-management)):

- Put super-admins only in `connector-admin`, with `SUPER_ADMIN` and no `tenant_id`.
- Never give `SUPER_ADMIN` to a user of a tenant realm. Such a token gets `401` and a `SECURITY_REALM_ROLE_VIOLATION` audit event.
- Keycloak stores usernames in lowercase (`adminB` becomes `adminb`), but login works with either form.

## Checking the result

```bash
# M2M token
curl -s -X POST http://localhost:8180/realms/platform/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=dsp-connector-backend \
  -d client_secret=dsp-connector-backend-secret | jq -r .access_token

# User token, decoded
curl -s -X POST http://localhost:8180/realms/tenant-EngineeringA/protocol/openid-connect/token \
  -d grant_type=password -d client_id=keycloak-api -d username=admin -d password=password \
  | jq -r .access_token | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null \
  | jq '{iss, aud, roles: .realm_access.roles, org, tenant_id, tenant_name}'
```

A tenant token should show `aud` `dsp-true-connector`, `roles` `["ADMIN"]`, and the tenant's `org`, `tenant_id` and `tenant_name`.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Failed to get an admin token` | Wrong `--url`, admin credentials or admin realm, or Keycloak is not up yet. |
| `... failed (HTTP 403)` | The admin account lacks admin rights in the master realm. |
| The connector answers `401` for every token from a realm | The token `iss` does not match the connector's issuer: re-run with the right `--public-url`. Or no enabled tenant names the realm. |
| `401` for one user only | The user's `tenant_id` is missing or is not the tenant's `_id`. Re-run `create-user.sh` with `--tenant-id`. |
| A token has no `tenant_id` claim even though the attribute was set | The realm has no `ADMIN_EDIT` policy (it was not created by these scripts). `create-user.sh` sets the policy whenever it writes attributes, so re-run it. |
| The account console spins after login | The account-console client is missing the Keycloak origin. Re-run `create-tenant-realm.sh` with the right `KEYCLOAK_PUBLIC_URL`. |

## Keeping scripts and seed files in sync

The seed files in `ci/docker/keycloak_resources/` and these scripts describe the same realms twice. When you change one, change the other in the same commit. To check that they still agree, run `bootstrap-keycloak.sh` against a throwaway Keycloak on another port and compare a `partial-export` of each realm with its seed file, ignoring ids, secrets and keys. [KEYCLOAK_SEED_SCRIPTS_FINDINGS.md](../KEYCLOAK_SEED_SCRIPTS_FINDINGS.md) describes how this was done and which differences are expected.
