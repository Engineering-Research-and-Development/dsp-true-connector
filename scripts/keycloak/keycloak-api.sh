#!/bin/bash
# Shared functions for provisioning the TRUE Connector Keycloak realms through
# the Keycloak Admin REST API. Sourced by the other scripts in this directory.
#
# The functions reproduce the seed realms in ci/docker/keycloak_resources:
#   platform         machine-to-machine client dsp-connector-backend (CONNECTOR)
#   connector-admin  super-admins (SUPER_ADMIN), public client keycloak-api
#   tenant-<name>    tenant admins (ADMIN), public client keycloak-api,
#                    connector-audience and org scopes as realm defaults
#
# Every function is ensure-style (create when missing, converge otherwise), so
# re-running a script against an existing realm is safe.
#
# Configuration (environment variables, all optional):
#   KEYCLOAK_URL             Admin REST base URL        (http://localhost:8180)
#   KEYCLOAK_PUBLIC_URL      Browser-facing Keycloak URL: realm frontendUrl,
#                            admin/account console origins (KEYCLOAK_URL)
#   KEYCLOAK_ADMIN           Admin user                 (admin)
#   KEYCLOAK_ADMIN_PASSWORD  Admin password             (admin)
#   KEYCLOAK_ADMIN_REALM     Realm of the admin user    (master)
#   KEYCLOAK_CLIENT_ID       Public UI client id        (keycloak-api)
#   CONNECTOR_UI_URL         Connector UI origin        (http://localhost:4200)
#   CONNECTOR_AUDIENCE       aud claim the connector validates (dsp-true-connector)
#   CONNECTOR_AUDIENCE_SCOPE Client scope emitting it   (connector-audience)
#   TENANT_REALM_PREFIX      Tenant realm prefix        (tenant-)

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_URL="${KEYCLOAK_URL%/}"
KEYCLOAK_PUBLIC_URL="${KEYCLOAK_PUBLIC_URL:-$KEYCLOAK_URL}"
KEYCLOAK_PUBLIC_URL="${KEYCLOAK_PUBLIC_URL%/}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
KEYCLOAK_ADMIN_REALM="${KEYCLOAK_ADMIN_REALM:-master}"
KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-keycloak-api}"
CONNECTOR_UI_URL="${CONNECTOR_UI_URL:-http://localhost:4200}"
CONNECTOR_UI_URL="${CONNECTOR_UI_URL%/}"
CONNECTOR_AUDIENCE="${CONNECTOR_AUDIENCE:-dsp-true-connector}"
CONNECTOR_AUDIENCE_SCOPE="${CONNECTOR_AUDIENCE_SCOPE:-connector-audience}"
TENANT_REALM_PREFIX="${TENANT_REALM_PREFIX:-tenant-}"

for _kc_tool in curl jq; do
    command -v "$_kc_tool" > /dev/null || { echo "$_kc_tool is required" >&2; exit 1; }
done
unset _kc_tool

# --- Logging -----------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}✓${NC} $1"; }
log_warn()  { echo -e "${YELLOW}⚠${NC} $1"; }
log_error() { echo -e "${RED}✗${NC} $1" >&2; }

# Succeed on 2xx, accept 409 (already exists) with a warning, fail otherwise.
# Usage: check_success <http_status> <operation>
check_success() {
    case "$1" in
        200|201|204) log_info "$2 succeeded" ;;
        409)         log_warn "$2: already exists" ;;
        *)           log_error "$2 failed (HTTP $1)"; return 1 ;;
    esac
}

# --- Admin REST helpers ------------------------------------------------------
# Master-realm admin tokens live about 60 seconds, so every call gets a fresh one.
get_admin_token() {
    local token
    token=$(curl -s -X POST "$KEYCLOAK_URL/realms/$KEYCLOAK_ADMIN_REALM/protocol/openid-connect/token" \
        -d "client_id=admin-cli" \
        --data-urlencode "username=$KEYCLOAK_ADMIN" \
        --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" \
        -d "grant_type=password" | jq -r '.access_token // empty')
    if [ -z "$token" ]; then
        log_error "Failed to get an admin token from $KEYCLOAK_URL (realm '$KEYCLOAK_ADMIN_REALM'). Check the URL and credentials."
        return 1
    fi
    echo "$token"
}

# GET an admin resource and print its body; fails on a non-2xx answer.
# Usage: kc_get <path below /admin/realms>
kc_get() {
    local token
    token=$(get_admin_token) || return 1
    curl -fsS "$KEYCLOAK_URL/admin/realms$1" -H "Authorization: Bearer $token"
}

# Send a request and print the HTTP status only.
# Usage: kc_send <METHOD> <path below /admin/realms> [json-body]
kc_send() {
    local token
    token=$(get_admin_token) || return 1
    if [ -n "${3:-}" ]; then
        curl -s -o /dev/null -w "%{http_code}" -X "$1" "$KEYCLOAK_URL/admin/realms$2" \
            -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "$3"
    else
        curl -s -o /dev/null -w "%{http_code}" -X "$1" "$KEYCLOAK_URL/admin/realms$2" \
            -H "Authorization: Bearer $token"
    fi
}

# Print the browser origin (scheme://host[:port]) of a URL.
url_origin() {
    if [[ "$1" =~ ^https?://[^/]+ ]]; then
        printf '%s' "${BASH_REMATCH[0]}"
    else
        log_error "Not an HTTP(S) URL: $1"
        return 1
    fi
}

# --- Realms ------------------------------------------------------------------
realm_exists() {
    local token status
    token=$(get_admin_token) || return 1
    status=$(curl -s -o /dev/null -w "%{http_code}" "$KEYCLOAK_URL/admin/realms/$1" \
        -H "Authorization: Bearer $token")
    [ "$status" = 200 ]
}

# Create the realm when missing, then apply the settings every seed realm shares
# plus the given overrides (a JSON object merged over them).
# Usage: ensure_realm <realm> <display-name> [overrides-json]
ensure_realm() {
    local realm=$1 display_name=$2 overrides=${3:-'{}'} body
    body=$(jq -n --arg realm "$realm" --arg display "$display_name" \
        --arg frontend "$KEYCLOAK_PUBLIC_URL" --argjson overrides "$overrides" '{
            realm: $realm,
            displayName: $display,
            enabled: true,
            sslRequired: "external",
            registrationAllowed: false,
            loginWithEmailAllowed: true,
            duplicateEmailsAllowed: false,
            resetPasswordAllowed: true,
            editUsernameAllowed: false,
            bruteForceProtected: true,
            accessTokenLifespan: 300,
            ssoSessionIdleTimeout: 1800,
            ssoSessionMaxLifespan: 36000,
            eventsEnabled: true,
            eventsListeners: ["jboss-logging"],
            adminEventsEnabled: true,
            adminEventsDetailsEnabled: true,
            attributes: {adminEventsExpiration: "604800", frontendUrl: $frontend}
        } * $overrides')

    if realm_exists "$realm"; then
        log_warn "Realm $realm already exists; converging its settings"
        check_success "$(kc_send PUT "/$realm" "$body")" "Realm $realm update"
    else
        check_success "$(kc_send POST "" "$body")" "Realm $realm creation"
    fi
}

# --- Roles -------------------------------------------------------------------
# Usage: ensure_realm_role <realm> <role> [description]
ensure_realm_role() {
    local realm=$1 role=$2 description=${3:-"$2 role"} body
    body=$(jq -n --arg name "$role" --arg desc "$description" '{name: $name, description: $desc}')
    if kc_get "/$realm/roles/$role" > /dev/null 2>&1; then
        check_success "$(kc_send PUT "/$realm/roles/$role" "$body")" "Realm role $role in $realm"
    else
        check_success "$(kc_send POST "/$realm/roles" "$body")" "Realm role $role in $realm"
    fi
}

# Give a client realm-role scope mappings. The seed clients have
# fullScopeAllowed=false, so a role without a scope mapping never reaches a token.
# Usage: add_realm_role_scope_mappings <realm> <client-uuid> <role>...
add_realm_role_scope_mappings() {
    local realm=$1 client_uuid=$2 role roles='[]'
    shift 2
    for role in "$@"; do
        roles=$(jq -c --argjson r "$(kc_get "/$realm/roles/$role")" '. + [$r]' <<< "$roles") || {
            log_error "Realm role $role not found in $realm"
            return 1
        }
    done
    check_success "$(kc_send POST "/$realm/clients/$client_uuid/scope-mappings/realm" "$roles")" \
        "Scope mappings [$*] in $realm"
}

# --- Clients -----------------------------------------------------------------
# Print a client's internal id, or nothing when the client does not exist.
# Usage: get_client_uuid <realm> <clientId>
get_client_uuid() {
    kc_get "/$1/clients?clientId=$2" | jq -r --arg c "$2" 'first(.[] | select(.clientId == $c) | .id) // empty'
}

# Create a client or update it in place from the given representation.
# Usage: ensure_client <realm> <client-json>
ensure_client() {
    local realm=$1 body=$2 client_id uuid
    client_id=$(jq -r '.clientId' <<< "$body")
    uuid=$(get_client_uuid "$realm" "$client_id") || return 1
    if [ -n "$uuid" ]; then
        check_success "$(kc_send PUT "/$realm/clients/$uuid" "$body")" "Client $client_id update in $realm"
    else
        check_success "$(kc_send POST "/$realm/clients" "$body")" "Client $client_id creation in $realm"
    fi
}

# The public client the connector UI logs in with (standard flow with PKCE S256;
# direct access grants stay on for scripted logins and tests). Its token carries
# only the given realm roles (fullScopeAllowed=false + scope mappings).
# Usage: ensure_ui_client <realm> <role>...
ensure_ui_client() {
    local realm=$1 uuid
    shift
    ensure_client "$realm" "$(jq -n --arg id "$KEYCLOAK_CLIENT_ID" --arg ui "$CONNECTOR_UI_URL" '{
        clientId: $id,
        name: $id,
        enabled: true,
        protocol: "openid-connect",
        publicClient: true,
        standardFlowEnabled: true,
        implicitFlowEnabled: false,
        directAccessGrantsEnabled: true,
        serviceAccountsEnabled: false,
        frontchannelLogout: false,
        redirectUris: [$ui + "/*"],
        webOrigins: [$ui],
        fullScopeAllowed: false,
        attributes: {"pkce.code.challenge.method": "S256"}
    }')" || return 1
    uuid=$(get_client_uuid "$realm" "$KEYCLOAK_CLIENT_ID") || return 1
    add_realm_role_scope_mappings "$realm" "$uuid" "$@"
}

# Let a realm's own admin console and account console work on the public
# Keycloak origin: the admin console needs the realm's console redirect URI,
# the account console needs the origin for its login-status iframe check.
# Usage: configure_console_clients <realm>
configure_console_clients() {
    local realm=$1 origin client uuid body
    origin=$(url_origin "$KEYCLOAK_PUBLIC_URL") || return 1

    client=$(kc_get "/$realm/clients?clientId=security-admin-console" | jq -c '.[0] // empty') || return 1
    if [ -z "$client" ]; then
        log_error "$realm: security-admin-console client missing"
        return 1
    fi
    uuid=$(jq -r '.id' <<< "$client")
    body=$(jq -c --arg realm "$realm" --arg public "$KEYCLOAK_PUBLIC_URL" '
        .redirectUris = ["\($public)/admin/\($realm)/console/*", "https://*/admin/\($realm)/console/*"]
        | .webOrigins = ["+"]
        | .frontchannelLogout = true' <<< "$client")
    check_success "$(kc_send PUT "/$realm/clients/$uuid" "$body")" "Admin console client in $realm"

    client=$(kc_get "/$realm/clients?clientId=account-console" | jq -c '.[0] // empty') || return 1
    if [ -z "$client" ]; then
        log_error "$realm: account-console client missing"
        return 1
    fi
    uuid=$(jq -r '.id' <<< "$client")
    body=$(jq -c --arg origin "$origin" '.webOrigins = ((.webOrigins // []) + [$origin] | unique)' <<< "$client")
    check_success "$(kc_send PUT "/$realm/clients/$uuid" "$body")" "Account console origin in $realm"
}

# --- Client scopes -----------------------------------------------------------
# Print a client scope's id, or nothing when it does not exist.
# Usage: get_client_scope_id <realm> <scope-name>
get_client_scope_id() {
    kc_get "/$1/client-scopes" | jq -r --arg n "$2" 'first(.[] | select(.name == $n) | .id) // empty'
}

# Create an OIDC client scope that is put into tokens but not shown on consent
# screens, and print its id.
# Usage: ensure_client_scope <realm> <name> <description>
ensure_client_scope() {
    local realm=$1 name=$2 description=$3 id body status
    body=$(jq -n --arg n "$name" --arg d "$description" '{
        name: $n, description: $d, protocol: "openid-connect",
        attributes: {"include.in.token.scope": "true", "display.on.consent.screen": "false"}
    }')
    id=$(get_client_scope_id "$realm" "$name") || return 1
    if [ -n "$id" ]; then
        status=$(kc_send PUT "/$realm/client-scopes/$id" "$(jq --arg id "$id" '.id = $id' <<< "$body")")
    else
        status=$(kc_send POST "/$realm/client-scopes" "$body")
    fi
    check_success "$status" "Client scope $name in $realm" >&2 || return 1
    get_client_scope_id "$realm" "$name"
}

# Create or replace a protocol mapper on a client scope.
# Usage: replace_scope_mapper <realm> <scope-id> <name> <protocolMapper> <config-json>
replace_scope_mapper() {
    local realm=$1 scope_id=$2 name=$3 mapper=$4 config=$5 existing
    existing=$(kc_get "/$realm/client-scopes/$scope_id/protocol-mappers/models" \
        | jq -r --arg n "$name" 'first(.[] | select(.name == $n) | .id) // empty') || return 1
    if [ -n "$existing" ]; then
        kc_send DELETE "/$realm/client-scopes/$scope_id/protocol-mappers/models/$existing" > /dev/null
    fi
    check_success "$(kc_send POST "/$realm/client-scopes/$scope_id/protocol-mappers/models" \
        "$(jq -n --arg n "$name" --arg m "$mapper" --argjson c "$config" \
            '{name: $n, protocol: "openid-connect", protocolMapper: $m, consentRequired: false, config: $c}')")" \
        "Mapper $name in $realm"
}

# Make a scope a realm default, so every client created later gets it.
# Usage: set_realm_default_scope <realm> <scope-id>
set_realm_default_scope() {
    kc_send DELETE "/$1/default-optional-client-scopes/$2" > /dev/null
    check_success "$(kc_send PUT "/$1/default-default-client-scopes/$2")" "Realm default scope in $1"
}

# Attach a scope as a default scope of one client.
# Usage: attach_client_default_scope <realm> <client-uuid> <scope-id>
attach_client_default_scope() {
    check_success "$(kc_send PUT "/$1/clients/$2/default-client-scopes/$3")" "Client default scope in $1"
}

# The connector-audience scope: emits aud=$CONNECTOR_AUDIENCE, which the
# connector requires. Tenant realms also emit accessible_tenants and home_realm
# from user attributes (present only when set) and make the scope a realm
# default. Prints the scope id.
# Usage: ensure_connector_audience_scope <realm> <tenant|plain>
ensure_connector_audience_scope() {
    local realm=$1 kind=$2 description id
    if [ "$kind" = tenant ]; then
        description="Emits aud and accessible_tenants for the TRUE Connector"
    else
        description="Emits aud for the connector API"
    fi
    id=$(ensure_client_scope "$realm" "$CONNECTOR_AUDIENCE_SCOPE" "$description") || return 1
    [ -n "$id" ] || { log_error "Client scope $CONNECTOR_AUDIENCE_SCOPE not found in $realm"; return 1; }

    replace_scope_mapper "$realm" "$id" connector-audience-mapper oidc-audience-mapper \
        "$(jq -n --arg aud "$CONNECTOR_AUDIENCE" \
            '{"included.custom.audience": $aud, "access.token.claim": "true", "id.token.claim": "false"}')" >&2 || return 1
    if [ "$kind" = tenant ]; then
        replace_scope_mapper "$realm" "$id" accessible-tenants-mapper oidc-usermodel-attribute-mapper \
            '{"user.attribute":"accessible_tenants","claim.name":"accessible_tenants","jsonType.label":"JSON","id.token.claim":"true","access.token.claim":"true","userinfo.token.claim":"true"}' >&2 || return 1
        replace_scope_mapper "$realm" "$id" home-realm-mapper oidc-usermodel-attribute-mapper \
            '{"user.attribute":"home_realm","claim.name":"home_realm","jsonType.label":"String","id.token.claim":"false","access.token.claim":"true","userinfo.token.claim":"false"}' >&2 || return 1
        set_realm_default_scope "$realm" "$id" >&2 || return 1
    fi
    echo "$id"
}

# The org scope of a tenant realm, a realm default: org and tenant_name are
# constants of the realm, tenant_id comes from the user's tenant_id attribute.
# Prints the scope id.
# Usage: ensure_org_scope <realm> <org> <tenant-name>
ensure_org_scope() {
    local realm=$1 org=$2 tenant_name=$3 id
    id=$(ensure_client_scope "$realm" org "Organization and tenant claims for direct and brokered logins") || return 1
    [ -n "$id" ] || { log_error "Client scope org not found in $realm"; return 1; }

    replace_scope_mapper "$realm" "$id" org-mapper oidc-hardcoded-claim-mapper \
        "$(jq -n --arg v "$org" '{"claim.name":"org","claim.value":$v,"jsonType.label":"String","id.token.claim":"true","access.token.claim":"true","userinfo.token.claim":"true"}')" >&2 || return 1
    replace_scope_mapper "$realm" "$id" tenant-id-mapper oidc-usermodel-attribute-mapper \
        '{"user.attribute":"tenant_id","claim.name":"tenant_id","jsonType.label":"String","multivalued":"false","id.token.claim":"true","access.token.claim":"true","userinfo.token.claim":"true"}' >&2 || return 1
    replace_scope_mapper "$realm" "$id" tenant-name-mapper oidc-hardcoded-claim-mapper \
        "$(jq -n --arg v "$tenant_name" '{"claim.name":"tenant_name","claim.value":$v,"jsonType.label":"String","id.token.claim":"true","access.token.claim":"true","userinfo.token.claim":"true"}')" >&2 || return 1
    set_realm_default_scope "$realm" "$id" >&2 || return 1
    echo "$id"
}

# Keycloak's declarative user profile drops attributes it does not declare
# (tenant_id, org_type, accessible_tenants, home_realm) from regular users
# unless this policy allows them. ADMIN_EDIT: admins read and write them,
# users never see them.
# Usage: ensure_unmanaged_attributes_policy <realm>
ensure_unmanaged_attributes_policy() {
    local realm=$1 profile
    profile=$(kc_get "/$realm/users/profile") || return 1
    if [ "$(jq -r '.unmanagedAttributePolicy // ""' <<< "$profile")" = ADMIN_EDIT ]; then
        log_info "Unmanaged-attribute policy already ADMIN_EDIT in $realm"
        return 0
    fi
    check_success "$(kc_send PUT "/$realm/users/profile" \
        "$(jq -c '.unmanagedAttributePolicy = "ADMIN_EDIT"' <<< "$profile")")" \
        "Unmanaged-attribute policy (ADMIN_EDIT) in $realm"
}

# --- Users -------------------------------------------------------------------
# Print a user's internal id, or nothing when the user does not exist.
# Usage: get_user_id <realm> <username>
get_user_id() {
    kc_get "/$1/users?username=$(jq -rn --arg u "$2" '$u|@uri')&exact=true" | jq -r '.[0].id // empty'
}

# Assign realm roles to a user.
# Usage: assign_user_realm_roles <realm> <user-id> <role>...
assign_user_realm_roles() {
    local realm=$1 user_id=$2 role roles='[]'
    shift 2
    [ "$#" -gt 0 ] || return 0
    for role in "$@"; do
        roles=$(jq -c --argjson r "$(kc_get "/$realm/roles/$role")" '. + [$r]' <<< "$roles") || {
            log_error "Realm role $role not found in $realm"
            return 1
        }
    done
    check_success "$(kc_send POST "/$realm/users/$user_id/role-mappings/realm" "$roles")" \
        "Realm roles [$*] for user $user_id in $realm"
}
