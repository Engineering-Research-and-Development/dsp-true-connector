#!/bin/bash
# Create (or converge) a TRUE Connector tenant realm in Keycloak, shaped like the
# seed realms ci/docker/keycloak_resources/tenant-*-realm.json.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/keycloak-api.sh"

usage() {
    cat <<EOF
Usage: $0 <tenant> <display-name> [tenant-name]

Creates realm ${TENANT_REALM_PREFIX}<tenant> with:
  - the realm role ADMIN (TRUE Connector administrator)
  - the public client $KEYCLOAK_CLIENT_ID (PKCE S256, redirect $CONNECTOR_UI_URL/*),
    whose tokens carry only ADMIN
  - the realm-default scope $CONNECTOR_AUDIENCE_SCOPE: aud=$CONNECTOR_AUDIENCE,
    accessible_tenants and home_realm (from user attributes, when set)
  - the realm-default scope org: org=<tenant>, tenant_name=<tenant-name> and
    tenant_id from the user's tenant_id attribute
  - unmanaged user attributes editable by admins (tenant_id, org_type, ...)
  - admin and account consoles working on $KEYCLOAK_PUBLIC_URL

  <tenant>        realm suffix and org claim, e.g. EngineeringA
  <display-name>  realm display name, e.g. "Engineering A tenant"
  [tenant-name]   tenant_name claim: the tenant 'name' in the connector's tenant
                  record, shown in the UI (defaults to <tenant>)

Environment variables: see keycloak-api.sh.

Example: $0 acme "Acme tenant" Acme
EOF
}

if [ "${1:-}" = -h ] || [ "${1:-}" = --help ]; then
    usage
    exit 0
fi
if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
    usage
    exit 1
fi

TENANT=$1
DISPLAY_NAME=$2
TENANT_NAME=${3:-$TENANT}
REALM="${TENANT_REALM_PREFIX}${TENANT}"

echo "=== Creating tenant realm $REALM ==="
echo "Display name: $DISPLAY_NAME"
echo "org claim:    $TENANT"
echo "tenant_name:  $TENANT_NAME"
echo ""

get_admin_token > /dev/null

ensure_realm "$REALM" "$DISPLAY_NAME"
ensure_realm_role "$REALM" ADMIN "TRUE Connector administrator"

# Realm-default scopes first, so the client created next picks them up.
ensure_connector_audience_scope "$REALM" tenant > /dev/null
ensure_org_scope "$REALM" "$TENANT" "$TENANT_NAME" > /dev/null

ensure_ui_client "$REALM" ADMIN
# Realm defaults reach only clients created afterwards. Attach explicitly:
# connector-audience to the UI client, org to every client (built-ins included).
CLIENT_UUID=$(get_client_uuid "$REALM" "$KEYCLOAK_CLIENT_ID")
attach_client_default_scope "$REALM" "$CLIENT_UUID" "$(get_client_scope_id "$REALM" "$CONNECTOR_AUDIENCE_SCOPE")"
ORG_SCOPE_ID=$(get_client_scope_id "$REALM" org)
for UUID in $(kc_get "/$REALM/clients" | jq -r '.[].id'); do
    attach_client_default_scope "$REALM" "$UUID" "$ORG_SCOPE_ID"
done

ensure_unmanaged_attributes_policy "$REALM"
configure_console_clients "$REALM"

echo ""
log_info "Tenant realm $REALM ready"
echo ""
echo "Next steps:"
echo "1. Create users (tenant_id = the tenant _id in the connector, not the realm name):"
echo "   $SCRIPT_DIR/create-user.sh $REALM <username> <email> <password> ADMIN --tenant-id <tenant-id>"
echo "2. Make sure the connector has an enabled tenant whose 'realm' is $REALM and"
echo "   whose 'name' is $TENANT_NAME; otherwise tokens from this realm get 401."
