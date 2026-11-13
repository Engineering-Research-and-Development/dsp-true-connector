#!/bin/bash
# Create (or converge) a user in a Keycloak realm and assign realm roles.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/keycloak-api.sh"

usage() {
    cat <<EOF
Usage: $0 <realm> <username> <email> <password> [realm-roles...] [options]
       $0 <realm> <username> <email> --no-password [realm-roles...] [options]

Roles are realm roles: ADMIN in a tenant realm, SUPER_ADMIN in connector-admin.
An existing user is updated in place (names, attributes, password) and gets the
roles added.

Options:
  --tenant-id <id>            tenant_id attribute (tenant realms): the tenant _id
                              in the connector, e.g. engineering-a, never the realm
  --org-type <type>           org_type attribute, e.g. operator or tech
  --accessible-tenants <csv>  accessible_tenants attribute (JSON array claim)
  --first-name <name>         Given name
  --last-name <name>          Family name
  --no-password               Create no credential (pair with --required-action)
  --required-action <name>    Required action on first login; repeatable
                              (e.g. UPDATE_PASSWORD)
  --grant-realm-admin         Also grant realm-management/realm-admin (realm's
                              own Keycloak admin console)

Never give SUPER_ADMIN to a user of a tenant realm: the connector rejects it.

Environment variables: see keycloak-api.sh.

Examples:
  $0 tenant-EngineeringA admin admin@mail.com password ADMIN \\
     --tenant-id engineering-a --org-type operator --first-name Espresso --last-name Pizzoni
  $0 connector-admin superadmin@mail.com superadmin@mail.com password SUPER_ADMIN
EOF
}

if [ "${1:-}" = -h ] || [ "${1:-}" = --help ]; then
    usage
    exit 0
fi
if [ "$#" -lt 4 ]; then
    usage
    exit 1
fi

REALM=$1
USERNAME=$2
EMAIL=$3
shift 3

need_value() {
    if [ -z "${2:-}" ] || [[ "$2" == --* ]]; then
        log_error "Value required after $1"
        exit 1
    fi
}

NO_PASSWORD=false
for ARG in "$@"; do
    [ "$ARG" = --no-password ] && NO_PASSWORD=true
done

PASSWORD=""
PASSWORD_SET=false
FIRST_NAME=""
LAST_NAME=""
TENANT_ID=""
ORG_TYPE=""
ACCESSIBLE_TENANTS=""
GRANT_REALM_ADMIN=false
ROLES=()
REQUIRED_ACTIONS=()
while [ "$#" -gt 0 ]; do
    case "$1" in
        --no-password)        shift ;;
        --grant-realm-admin)  GRANT_REALM_ADMIN=true; shift ;;
        --tenant-id)          need_value "$@"; TENANT_ID=$2; shift 2 ;;
        --org-type)           need_value "$@"; ORG_TYPE=$2; shift 2 ;;
        --accessible-tenants) need_value "$@"; ACCESSIBLE_TENANTS=$2; shift 2 ;;
        --first-name)         need_value "$@"; FIRST_NAME=$2; shift 2 ;;
        --last-name)          need_value "$@"; LAST_NAME=$2; shift 2 ;;
        --required-action)    need_value "$@"; REQUIRED_ACTIONS+=("$2"); shift 2 ;;
        --*)                  log_error "Unknown option: $1"; usage; exit 1 ;;
        *)
            if [ "$NO_PASSWORD" = false ] && [ "$PASSWORD_SET" = false ]; then
                PASSWORD=$1
                PASSWORD_SET=true
            else
                ROLES+=("$1")
            fi
            shift
            ;;
    esac
done

if [ "$NO_PASSWORD" = false ] && [ -z "$PASSWORD" ]; then
    log_error "Password required (or pass --no-password)"
    exit 1
fi

echo "=== Creating user $USERNAME in $REALM ==="
echo "Email:     $EMAIL"
echo "Roles:     ${ROLES[*]:-<none>}"
echo "tenant_id: ${TENANT_ID:-<none>}"
echo "org_type:  ${ORG_TYPE:-<none>}"
echo ""

get_admin_token > /dev/null

ATTRIBUTES='{}'
[ -n "$TENANT_ID" ] && ATTRIBUTES=$(jq -c --arg v "$TENANT_ID" '.tenant_id = [$v]' <<< "$ATTRIBUTES")
[ -n "$ORG_TYPE" ] && ATTRIBUTES=$(jq -c --arg v "$ORG_TYPE" '.org_type = [$v]' <<< "$ATTRIBUTES")
if [ -n "$ACCESSIBLE_TENANTS" ]; then
    # Stored as one JSON-array string; the mapper emits it as a JSON claim.
    ATTRIBUTES=$(jq -c --arg csv "$ACCESSIBLE_TENANTS" \
        '.accessible_tenants = [($csv | split(",") | map(select(length > 0)) | tojson)]' <<< "$ATTRIBUTES")
fi
if [ "$ATTRIBUTES" != '{}' ]; then
    # Undeclared attributes are dropped unless the realm allows them.
    ensure_unmanaged_attributes_policy "$REALM"
fi

USER_JSON=$(jq -n --arg u "$USERNAME" --arg e "$EMAIL" --arg f "$FIRST_NAME" --arg l "$LAST_NAME" \
    --argjson attrs "$ATTRIBUTES" '{
        username: $u, email: $e, emailVerified: true, enabled: true, attributes: $attrs
    } + (if $f != "" then {firstName: $f} else {} end)
      + (if $l != "" then {lastName: $l} else {} end)')
if [ ${#REQUIRED_ACTIONS[@]} -gt 0 ]; then
    USER_JSON=$(jq -c --argjson a "$(printf '%s\n' "${REQUIRED_ACTIONS[@]}" | jq -R . | jq -s .)" \
        '.requiredActions = $a' <<< "$USER_JSON")
fi
PASSWORD_JSON=$(jq -n --arg pw "$PASSWORD" '{type: "password", value: $pw, temporary: false}')

USER_ID=$(get_user_id "$REALM" "$USERNAME")
if [ -n "$USER_ID" ]; then
    log_warn "User $USERNAME already exists; converging"
    # PUT replaces the attribute map: keep the attributes set elsewhere.
    CURRENT=$(kc_get "/$REALM/users/$USER_ID")
    USER_JSON=$(jq -c --argjson cur "$CURRENT" \
        '.attributes = (($cur.attributes // {}) + .attributes)' <<< "$USER_JSON")
    check_success "$(kc_send PUT "/$REALM/users/$USER_ID" "$USER_JSON")" "User $USERNAME update"
    if [ "$NO_PASSWORD" = false ]; then
        check_success "$(kc_send PUT "/$REALM/users/$USER_ID/reset-password" "$PASSWORD_JSON")" \
            "Password of $USERNAME"
    fi
else
    if [ "$NO_PASSWORD" = false ]; then
        USER_JSON=$(jq -c --argjson c "$PASSWORD_JSON" '.credentials = [$c]' <<< "$USER_JSON")
    fi
    check_success "$(kc_send POST "/$REALM/users" "$USER_JSON")" "User $USERNAME creation"
    USER_ID=$(get_user_id "$REALM" "$USERNAME")
    if [ -z "$USER_ID" ]; then
        log_error "User $USERNAME not found after creation"
        exit 1
    fi
fi

assign_user_realm_roles "$REALM" "$USER_ID" "${ROLES[@]}"

if [ "$GRANT_REALM_ADMIN" = true ]; then
    MGMT_UUID=$(get_client_uuid "$REALM" realm-management)
    REALM_ADMIN_ROLE=$(kc_get "/$REALM/clients/$MGMT_UUID/roles/realm-admin")
    check_success "$(kc_send POST "/$REALM/users/$USER_ID/role-mappings/clients/$MGMT_UUID" "[$REALM_ADMIN_ROLE]")" \
        "realm-admin for $USERNAME"
    echo "Admin console: $KEYCLOAK_PUBLIC_URL/admin/$REALM/console"
fi

echo ""
log_info "User $USERNAME ready in $REALM"
if [ "$NO_PASSWORD" = false ]; then
    echo ""
    echo "Test login:"
    echo "curl -X POST '$KEYCLOAK_PUBLIC_URL/realms/$REALM/protocol/openid-connect/token' \\"
    echo "  -d 'client_id=$KEYCLOAK_CLIENT_ID' -d 'grant_type=password' \\"
    echo "  --data-urlencode 'username=$USERNAME' --data-urlencode 'password=<password>'"
fi
