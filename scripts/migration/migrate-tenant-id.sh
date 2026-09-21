#!/usr/bin/env bash
# =============================================================================
# migrate-tenant-id.sh
#
# Shell wrapper around add-tenant-id.js.
# Assigns a tenantId to every document that currently has no tenantId in the
# TRUE Connector MongoDB collections.
#
# Requirements: mongosh must be on $PATH.
#
# Installing mongosh
# ------------------
# Official download page: https://www.mongodb.com/try/download/shell
#
# macOS (Homebrew):
#   brew install mongosh
#
# Ubuntu / Debian:
#   wget -qO- https://www.mongodb.org/static/pgp/server-7.0.asc | sudo tee /etc/apt/trusted.gpg.d/mongodb.asc
#   echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu $(lsb_release -cs)/mongodb-org/7.0 multiverse" \
#     | sudo tee /etc/apt/sources.list.d/mongodb-org-7.0.list
#   sudo apt-get update && sudo apt-get install -y mongodb-mongosh
#
# RHEL / CentOS / Fedora:
#   Create /etc/yum.repos.d/mongodb-org-7.0.repo with:
#     [mongodb-org-7.0]
#     name=MongoDB Repository
#     baseurl=https://repo.mongodb.org/yum/redhat/$releasever/mongodb-org/7.0/x86_64/
#     gpgcheck=1
#     enabled=1
#     gpgkey=https://www.mongodb.org/static/pgp/server-7.0.asc
#   sudo yum install -y mongodb-mongosh
#
# Windows (winget):
#   winget install MongoDB.Shell
#
# Windows (manual):
#   Download the .msi installer from https://www.mongodb.com/try/download/shell
#   and add the install directory to your PATH.
#
# Docker (no install needed):
#   docker run --rm -it mongo:7 mongosh <uri>/<database> \
#     --eval "var TENANT_ID='engineering'; var DRY_RUN=true;" \
#     --file /migration/add-tenant-id.js
#
# Usage:
#   ./migrate-tenant-id.sh [OPTIONS]
#
# Options:
#   -t | --tenant     <id>       Tenant ID to assign (required)
#   -d | --database   <name>     MongoDB database name (default: true_connector)
#   -H | --host       <host>     MongoDB host          (default: localhost)
#   -p | --port       <port>     MongoDB port          (default: 27017)
#   -u | --username   <user>     MongoDB username       (optional)
#   -w | --password   <pass>     MongoDB password       (optional
#        --auth-db    <db>       Auth database          (default: admin)
#        --dry-run               Print what would change, make no writes
#   -h | --help                  Show this help
#
# Examples:
#   # Dry run against the provider database:
#   ./migrate-tenant-id.sh --tenant engineering --database true_connector_provider --dry-run
#
#   # Live migration (provider):
#   ./migrate-tenant-id.sh --tenant engineering --database true_connector_provider
#
#   # With authentication:
#   ./migrate-tenant-id.sh --tenant engineering --database true_connector_provider \
#       --username tc --password secret
#
#   # Remote host:
#   ./migrate-tenant-id.sh --tenant engineering --database true_connector_provider \
#       --host db.example.com --port 27017
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JS_SCRIPT="${SCRIPT_DIR}/add-tenant-id.js"

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------
TENANT_ID=""
DB_NAME="true_connector"
MONGO_HOST="localhost"
MONGO_PORT="27017"
MONGO_USER=""
MONGO_PASS=""
AUTH_DB="admin"
DRY_RUN="true"

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
usage() {
  # Print only the header block (lines between the opening =====... and the first blank comment line after options)
  sed -n '/^# ===*/,/^# ===*$/p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -t|--tenant)    TENANT_ID="$2";   shift 2 ;;
    -d|--database)  DB_NAME="$2";     shift 2 ;;
    -H|--host)      MONGO_HOST="$2";  shift 2 ;;
    -p|--port)      MONGO_PORT="$2";  shift 2 ;;
    -u|--username)  MONGO_USER="$2";  shift 2 ;;
    -w|--password)  MONGO_PASS="$2";  shift 2 ;;
    --auth-db)      AUTH_DB="$2";     shift 2 ;;
    --dry-run)      DRY_RUN="true";   shift   ;;
    --live)         DRY_RUN="false";  shift   ;;
    -h|--help)      usage ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
done

# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------
if [[ -z "${TENANT_ID}" ]]; then
  echo "ERROR: --tenant is required."
  echo "Run with --help for usage."
  exit 1
fi

if ! command -v mongosh &>/dev/null; then
  echo "ERROR: mongosh is not installed or not on PATH."
  echo "Run './migrate-tenant-id.sh --help' for installation instructions."
  exit 1
fi

if [[ ! -f "${JS_SCRIPT}" ]]; then
  echo "ERROR: Migration script not found: ${JS_SCRIPT}"
  exit 1
fi

# ---------------------------------------------------------------------------
# Build mongosh connection URI
# ---------------------------------------------------------------------------
if [[ -n "${MONGO_USER}" && -n "${MONGO_PASS}" ]]; then
  ENCODED_USER=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "${MONGO_USER}")
  ENCODED_PASS=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "${MONGO_PASS}")
  MONGO_URI="mongodb://${ENCODED_USER}:${ENCODED_PASS}@${MONGO_HOST}:${MONGO_PORT}/${DB_NAME}?authSource=${AUTH_DB}"
else
  MONGO_URI="mongodb://${MONGO_HOST}:${MONGO_PORT}/${DB_NAME}"
fi

# ---------------------------------------------------------------------------
# Confirmation prompt for live runs
# ---------------------------------------------------------------------------
if [[ "${DRY_RUN}" == "false" ]]; then
  echo ""
  echo "  WARNING: You are about to run a LIVE migration."
  echo "  All documents without a tenantId in database '${DB_NAME}'"
  echo "  on ${MONGO_HOST}:${MONGO_PORT} will be updated with tenantId='${TENANT_ID}'."
  echo ""
  read -r -p "  Type 'yes' to proceed: " CONFIRM
  if [[ "${CONFIRM}" != "yes" ]]; then
    echo "Aborted."
    exit 0
  fi
fi

# ---------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------
echo ""
echo "Running migration..."
echo "  --eval \"var TENANT_ID='${TENANT_ID}'; var DRY_RUN=${DRY_RUN};\""
echo ""

mongosh "${MONGO_URI}" \
  --eval "var TENANT_ID='${TENANT_ID}'; var DRY_RUN=${DRY_RUN};" \
  --nodb=false \
  --quiet \
  "${JS_SCRIPT}"
