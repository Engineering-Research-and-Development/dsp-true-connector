#!/bin/bash
# Recreate the TRUE Connector seed realms (ci/docker/keycloak_resources) in a
# running Keycloak through the Admin REST API:
#
#   platform             client dsp-connector-backend (client credentials, CONNECTOR)
#   connector-admin      superadmin@mail.com / password (SUPER_ADMIN)
#   tenant-EngineeringA  admin / password (ADMIN, tenant_id engineering-a)
#   tenant-EngineeringB  adminB / password (ADMIN, tenant_id engineering-b)
#   tenant-acme          coyote / coyote, roadrunner / roadrunner (ADMIN, tenant_id acme)
#
# Safe to re-run: existing realms, clients, scopes and users are converged.
# The passwords and the client secret are for local use only.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    cat <<EOF
Usage: $0 [--url <keycloak-url>] [--public-url <url>] [--admin <user>] [--password <pass>]

  --url         Keycloak Admin REST base URL      (default: http://localhost:8180)
  --public-url  Browser-facing Keycloak URL, used as realm frontendUrl and for
                the console origins               (default: --url)
  --admin       Keycloak admin user               (default: admin)
  --password    Keycloak admin password           (default: admin)

Environment variables (see keycloak-api.sh) work too; the options win.
DSP_BACKEND_CLIENT_SECRET sets the dsp-connector-backend secret
(default: dsp-connector-backend-secret).
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --url)        export KEYCLOAK_URL=$2; shift 2 ;;
        --public-url) export KEYCLOAK_PUBLIC_URL=$2; shift 2 ;;
        --admin)      export KEYCLOAK_ADMIN=$2; shift 2 ;;
        --password)   export KEYCLOAK_ADMIN_PASSWORD=$2; shift 2 ;;
        --help|-h)    usage; exit 0 ;;
        *)            echo "Unknown option: $1" >&2; usage; exit 1 ;;
    esac
done

source "$SCRIPT_DIR/keycloak-api.sh"
# The child scripts re-source keycloak-api.sh: hand them the resolved settings.
export KEYCLOAK_URL KEYCLOAK_PUBLIC_URL KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD KEYCLOAK_ADMIN_REALM

DSP_BACKEND_CLIENT_ID="dsp-connector-backend"
DSP_BACKEND_CLIENT_SECRET="${DSP_BACKEND_CLIENT_SECRET:-dsp-connector-backend-secret}"

echo "=== TRUE Connector Keycloak seed ==="
echo "Keycloak URL: $KEYCLOAK_URL"
echo "Public URL:   $KEYCLOAK_PUBLIC_URL"
echo ""

get_admin_token > /dev/null

# --- platform: connector-to-connector (machine-to-machine) -------------------
echo "=== Realm platform ==="
REALM=platform
ensure_realm "$REALM" "Platform (machine-to-machine)" \
    '{"loginWithEmailAllowed": false, "resetPasswordAllowed": false}'
ensure_realm_role "$REALM" CONNECTOR "Connector-to-connector (DSP protocol) access"

ensure_client "$REALM" "$(jq -n --arg id "$DSP_BACKEND_CLIENT_ID" --arg secret "$DSP_BACKEND_CLIENT_SECRET" '{
    clientId: $id,
    name: $id,
    enabled: true,
    protocol: "openid-connect",
    publicClient: false,
    clientAuthenticatorType: "client-secret",
    secret: $secret,
    standardFlowEnabled: false,
    implicitFlowEnabled: false,
    directAccessGrantsEnabled: false,
    serviceAccountsEnabled: true,
    frontchannelLogout: false,
    redirectUris: [],
    webOrigins: [],
    fullScopeAllowed: false
}')"
CLIENT_UUID=$(get_client_uuid "$REALM" "$DSP_BACKEND_CLIENT_ID")
add_realm_role_scope_mappings "$REALM" "$CLIENT_UUID" CONNECTOR
SCOPE_ID=$(ensure_connector_audience_scope "$REALM" plain)
attach_client_default_scope "$REALM" "$CLIENT_UUID" "$SCOPE_ID"
SA_USER_ID=$(kc_get "/$REALM/clients/$CLIENT_UUID/service-account-user" | jq -r '.id')
assign_user_realm_roles "$REALM" "$SA_USER_ID" CONNECTOR
echo ""

# --- connector-admin: super-admins -------------------------------------------
echo "=== Realm connector-admin ==="
REALM=connector-admin
ensure_realm "$REALM" "Connector administration"
ensure_realm_role "$REALM" SUPER_ADMIN "Connector super-admin: tenant and user management"
ensure_ui_client "$REALM" SUPER_ADMIN
CLIENT_UUID=$(get_client_uuid "$REALM" "$KEYCLOAK_CLIENT_ID")
SCOPE_ID=$(ensure_connector_audience_scope "$REALM" plain)
attach_client_default_scope "$REALM" "$CLIENT_UUID" "$SCOPE_ID"
configure_console_clients "$REALM"
echo ""

"$SCRIPT_DIR/create-user.sh" "$REALM" superadmin@mail.com superadmin@mail.com password SUPER_ADMIN \
    --first-name Capo --last-name Capi
echo ""

# --- tenant realms -----------------------------------------------------------
"$SCRIPT_DIR/create-tenant-realm.sh" EngineeringA "Engineering A tenant" EngineeringA
"$SCRIPT_DIR/create-user.sh" tenant-EngineeringA admin admin@mail.com password ADMIN \
    --tenant-id engineering-a --org-type operator --first-name Espresso --last-name Pizzoni
echo ""

"$SCRIPT_DIR/create-tenant-realm.sh" EngineeringB "Engineering B tenant" EngineeringB
"$SCRIPT_DIR/create-user.sh" tenant-EngineeringB adminB adminB@mail.com password ADMIN \
    --tenant-id engineering-b --org-type operator --first-name Cappuccino --last-name Brioschi
echo ""

"$SCRIPT_DIR/create-tenant-realm.sh" acme "Acme tenant" Acme
"$SCRIPT_DIR/create-user.sh" tenant-acme coyote coyote@mail.com coyote ADMIN \
    --tenant-id acme --org-type tech --first-name "Wile E." --last-name Coyote
"$SCRIPT_DIR/create-user.sh" tenant-acme roadrunner roadrunner@mail.com roadrunner ADMIN \
    --tenant-id acme --org-type tech --first-name Acceleratti --last-name Incredibilus

echo ""
echo "=== Seed complete ==="
echo ""
echo "Machine-to-machine token:"
echo "curl -X POST '$KEYCLOAK_PUBLIC_URL/realms/platform/protocol/openid-connect/token' \\"
echo "  -d grant_type=client_credentials -d client_id=$DSP_BACKEND_CLIENT_ID -d client_secret=<secret>"
echo ""
echo "User token (e.g. tenant admin):"
echo "curl -X POST '$KEYCLOAK_PUBLIC_URL/realms/tenant-EngineeringA/protocol/openid-connect/token' \\"
echo "  -d grant_type=password -d client_id=$KEYCLOAK_CLIENT_ID -d username=admin -d password=password"
