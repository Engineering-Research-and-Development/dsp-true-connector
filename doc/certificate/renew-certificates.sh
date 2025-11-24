#!/bin/bash
##################################################################
# Certificate Renewal Script for DSP True Connector
# Renews or generates server certificates (connector-a, connector-b, minio, ui-a, ui-b)
# without regenerating Root CA and Intermediate CA
##################################################################

set -e  # Exit on error

##################################################################
# CONFIGURATION - Edit these values as needed
##################################################################

# Intermediate CA Configuration (must exist)
INTERMEDIATE_ALIAS="dsp-intermediate-ca"
INTERMEDIATE_KEYSTORE="dsp-intermediate-ca.p12"
INTERMEDIATE_PASSWORD="password"

# Server Certificate Configuration
SERVER_VALIDITY=365
SERVER_PASSWORD="password"

# Subject Alternative Names (SAN) - Edit these lists as needed for each service
SAN_CONNECTOR_A="DNS:localhost,DNS:connector-a,IP:127.0.0.1"
SAN_CONNECTOR_B="DNS:localhost,DNS:connector-b,IP:127.0.0.1"
SAN_MINIO="DNS:localhost,DNS:minio,IP:127.0.0.1"
SAN_UI_A="DNS:localhost,DNS:ui-a,IP:127.0.0.1"
SAN_UI_B="DNS:localhost,DNS:ui-b,IP:127.0.0.1"

# Key Algorithm and Size
KEY_ALG="RSA"
KEY_SIZE=2048

##################################################################
# FUNCTIONS
##################################################################

show_menu() {
    echo ""
    echo "=================================================================="
    echo "DSP True Connector - Certificate Renewal Script"
    echo "=================================================================="
    echo ""
    echo "This script allows you to renew individual server certificates"
    echo "without regenerating the Root CA and Intermediate CA."
    echo ""
    echo "PREREQUISITES:"
    echo "  - dsp-intermediate-ca.p12 must exist"
    echo "  - intermediate-ca.crt must exist"
    echo "  - root-ca.crt must exist (for certificate chain)"
    echo ""
    echo "=================================================================="
    echo ""
    echo "Select certificate(s) to renew:"
    echo ""
    echo "  1. Renew connector-a certificate"
    echo "  2. Renew connector-b certificate"
    echo "  3. Renew minio certificate (PKCS12 + PEM)"
    echo "  4. Renew ui-a certificate (PEM + fullchain for nginx)"
    echo "  5. Renew ui-b certificate (PEM + fullchain for nginx)"
    echo "  6. Renew ALL server certificates"
    echo "  7. Exit"
    echo ""
    echo "=================================================================="
    echo ""
}

check_prerequisites() {
    echo ""
    echo "Checking prerequisites..."
    echo ""

    if [ ! -f "${INTERMEDIATE_KEYSTORE}" ]; then
        echo "ERROR: ${INTERMEDIATE_KEYSTORE} not found!"
        echo "Please run generate-certificates.sh first to create the CA hierarchy."
        exit 1
    fi

    if [ ! -f "intermediate-ca.crt" ]; then
        echo "WARNING: intermediate-ca.crt not found."
        echo "Exporting from keystore..."
        keytool -exportcert \
            -alias "${INTERMEDIATE_ALIAS}" \
            -keystore "${INTERMEDIATE_KEYSTORE}" \
            -storetype PKCS12 \
            -storepass "${INTERMEDIATE_PASSWORD}" \
            -file intermediate-ca.crt \
            -rfc

        if [ $? -ne 0 ]; then
            echo "ERROR: Failed to export Intermediate CA certificate"
            exit 1
        fi
    fi

    if [ ! -f "root-ca.crt" ]; then
        echo "WARNING: root-ca.crt not found."
        echo "Checking if Root CA is in Intermediate CA keystore..."
        keytool -exportcert \
            -alias dsp-root-ca \
            -keystore "${INTERMEDIATE_KEYSTORE}" \
            -storetype PKCS12 \
            -storepass "${INTERMEDIATE_PASSWORD}" \
            -file root-ca.crt \
            -rfc 2>/dev/null || {
                echo "WARNING: root-ca.crt not available. Certificate chain may be incomplete."
                echo "This is OK if you only need the server certificate."
            }
    fi

    echo "Prerequisites check complete."
    echo ""
}

renew_server_cert() {
    local SERVER_NAME=$1
    local SERVER_DN=$2
    local SERVER_SAN=$3
    local SERVER_KEYSTORE="${SERVER_NAME}.p12"
    local SERVER_ALIAS="${SERVER_NAME}"

    echo ""
    echo "Renewing server certificate: ${SERVER_NAME}"
    echo ""

    # Backup old certificate if it exists
    if [ -f "${SERVER_KEYSTORE}" ]; then
        echo "Backing up old certificate..."
        local TIMESTAMP=$(date +%Y%m%d_%H%M%S)
        cp "${SERVER_KEYSTORE}" "${SERVER_KEYSTORE}.backup_${TIMESTAMP}"
        echo "Old certificate backed up to: ${SERVER_KEYSTORE}.backup_${TIMESTAMP}"
        echo ""
    fi

    # Delete old certificate files
    echo "Cleaning up old certificate files..."
    rm -f "${SERVER_KEYSTORE}"
    rm -f "${SERVER_NAME}.csr"
    rm -f "${SERVER_NAME}.crt"
    echo "Done."
    echo ""

    # Generate server key pair
    echo "Generating key pair for ${SERVER_NAME}..."
    keytool -genkeypair \
        -alias "${SERVER_ALIAS}" \
        -keyalg "${KEY_ALG}" \
        -keysize "${KEY_SIZE}" \
        -dname "${SERVER_DN}" \
        -validity "${SERVER_VALIDITY}" \
        -keystore "${SERVER_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -keypass "${SERVER_PASSWORD}" \
        -ext KeyUsage:critical=digitalSignature,keyEncipherment \
        -ext ExtendedKeyUsage=serverAuth,clientAuth \
        -ext "SAN=${SERVER_SAN}"

    echo "Done."
    echo ""

    # Generate CSR
    echo "Generating Certificate Signing Request for ${SERVER_NAME}..."
    keytool -certreq \
        -alias "${SERVER_ALIAS}" \
        -keystore "${SERVER_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file "${SERVER_NAME}.csr" \
        -ext KeyUsage:critical=digitalSignature,keyEncipherment \
        -ext ExtendedKeyUsage=serverAuth,clientAuth \
        -ext "SAN=${SERVER_SAN}"

    echo "Done."
    echo ""

    # Sign with Intermediate CA
    echo "Signing ${SERVER_NAME} certificate with Intermediate CA..."
    keytool -gencert \
        -alias "${INTERMEDIATE_ALIAS}" \
        -keystore "${INTERMEDIATE_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${INTERMEDIATE_PASSWORD}" \
        -infile "${SERVER_NAME}.csr" \
        -outfile "${SERVER_NAME}.crt" \
        -validity "${SERVER_VALIDITY}" \
        -ext KeyUsage:critical=digitalSignature,keyEncipherment \
        -ext ExtendedKeyUsage=serverAuth,clientAuth \
        -ext "SAN=${SERVER_SAN}" \
        -rfc

    echo "Done."
    echo ""

    # Import certificate chain
    echo "Importing certificate chain for ${SERVER_NAME}..."

    # First import Root CA (if available)
    echo "  - Importing Root CA..."
    keytool -importcert \
        -alias dsp-root-ca \
        -keystore "${SERVER_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file root-ca.crt \
        -noprompt 2>/dev/null || true

    # Then import Intermediate CA
    echo "  - Importing Intermediate CA..."
    keytool -importcert \
        -alias "${INTERMEDIATE_ALIAS}" \
        -keystore "${SERVER_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file intermediate-ca.crt \
        -noprompt

    # Finally import signed server certificate
    echo "  - Importing signed server certificate..."
    keytool -importcert \
        -alias "${SERVER_ALIAS}" \
        -keystore "${SERVER_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file "${SERVER_NAME}.crt" \
        -noprompt

    echo "Done."
    echo ""

    # Cleanup temporary files
    echo "Cleaning up temporary files..."
    rm -f "${SERVER_NAME}.csr"
    rm -f "${SERVER_NAME}.crt"
    echo "Done."
    echo ""

    echo "Server certificate ${SERVER_NAME} renewed successfully!"
    echo "  - Keystore: ${SERVER_KEYSTORE}"
    echo "  - Alias: ${SERVER_ALIAS}"
    echo "  - SAN: ${SERVER_SAN}"
    echo "  - Valid for: ${SERVER_VALIDITY} days"
    echo ""

    # Verify the new certificate
    echo "Verifying certificate..."
    keytool -list -v -keystore "${SERVER_KEYSTORE}" -storepass "${SERVER_PASSWORD}" -storetype PKCS12 | grep -E "Valid|Alias|Owner|DNS" || true
    echo ""
}

renew_minio() {
    check_prerequisites

    echo ""
    echo "=================================================================="
    echo "Renewing MinIO Certificate (PKCS12 + PEM format)"
    echo "=================================================================="
    echo ""

    local MINIO_NAME="minio"
    local MINIO_DN="CN=minio, OU=Storage, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS"
    local MINIO_KEYSTORE="minio-temp.p12"
    local MINIO_ALIAS="minio"

    # Delete old minio certificates
    echo "Cleaning up old MinIO certificate files..."
    rm -f "${MINIO_KEYSTORE}"
    rm -f minio.csr
    rm -f minio-signed.crt
    rm -f private.key
    rm -f public.crt
    echo "Done."
    echo ""

    # Generate MinIO key pair
    echo "Generating key pair for MinIO..."
    keytool -genkeypair \
        -alias "${MINIO_ALIAS}" \
        -keyalg "${KEY_ALG}" \
        -keysize "${KEY_SIZE}" \
        -dname "${MINIO_DN}" \
        -validity "${SERVER_VALIDITY}" \
        -keystore "${MINIO_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -keypass "${SERVER_PASSWORD}" \
        -ext KeyUsage:critical=digitalSignature,keyEncipherment \
        -ext ExtendedKeyUsage=serverAuth,clientAuth \
        -ext "SAN=${SAN_MINIO}"

    echo "Done."
    echo ""

    # Generate CSR
    echo "Generating Certificate Signing Request for MinIO..."
    keytool -certreq \
        -alias "${MINIO_ALIAS}" \
        -keystore "${MINIO_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file minio.csr \
        -ext KeyUsage:critical=digitalSignature,keyEncipherment \
        -ext ExtendedKeyUsage=serverAuth,clientAuth \
        -ext "SAN=${SAN_MINIO}"

    echo "Done."
    echo ""

    # Sign with Intermediate CA
    echo "Signing MinIO certificate with Intermediate CA..."
    keytool -gencert \
        -alias "${INTERMEDIATE_ALIAS}" \
        -keystore "${INTERMEDIATE_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${INTERMEDIATE_PASSWORD}" \
        -infile minio.csr \
        -outfile minio-signed.crt \
        -validity "${SERVER_VALIDITY}" \
        -ext KeyUsage:critical=digitalSignature,keyEncipherment \
        -ext ExtendedKeyUsage=serverAuth,clientAuth \
        -ext "SAN=${SAN_MINIO}" \
        -rfc

    echo "Done."
    echo ""

    # Import certificate chain
    echo "Importing certificate chain for MinIO..."
    echo "  - Importing Root CA..."
    keytool -importcert \
        -alias dsp-root-ca \
        -keystore "${MINIO_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file root-ca.crt \
        -noprompt 2>/dev/null || true

    echo "  - Importing Intermediate CA..."
    keytool -importcert \
        -alias "${INTERMEDIATE_ALIAS}" \
        -keystore "${MINIO_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file intermediate-ca.crt \
        -noprompt

    echo "  - Importing signed MinIO certificate..."
    keytool -importcert \
        -alias "${MINIO_ALIAS}" \
        -keystore "${MINIO_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file minio-signed.crt \
        -noprompt

    echo "Done."
    echo ""

    # Export to PEM format
    echo "Exporting MinIO private key to PEM format (private.key)..."
    if command -v openssl &> /dev/null; then
        openssl pkcs12 -in "${MINIO_KEYSTORE}" -nocerts -nodes -passin pass:"${SERVER_PASSWORD}" -out private.key
        echo "Done."
        echo ""

        echo "Exporting MinIO certificate to PEM format (public.crt)..."
        openssl pkcs12 -in "${MINIO_KEYSTORE}" -clcerts -nokeys -passin pass:"${SERVER_PASSWORD}" -out public.crt
        echo "Done."
    else
        echo "WARNING: OpenSSL not found. Cannot convert to PEM format automatically."
        echo "Please convert manually using:"
        echo "  openssl pkcs12 -in minio-temp.p12 -nocerts -nodes -passin pass:${SERVER_PASSWORD} -out private.key"
        echo "  openssl pkcs12 -in minio-temp.p12 -clcerts -nokeys -passin pass:${SERVER_PASSWORD} -out public.crt"
        echo ""
        echo "Alternatively, install OpenSSL and run this script again."
    fi
    echo ""

    # Cleanup temporary files
    echo "Cleaning up temporary files..."
    rm -f minio.csr
    rm -f minio-signed.crt
    echo "Done."
    echo ""

    echo "MinIO certificate renewed successfully!"
    echo "  - minio-temp.p12 (PKCS12 format)"
    echo "  - private.key (PEM format, for MinIO)"
    echo "  - public.crt (PEM format, for MinIO)"
    echo "  - SANs: ${SAN_MINIO}"
    echo ""
}

renew_ui() {
    local UI_NAME=$1
    local SAN_VAR=$2

    check_prerequisites

    echo ""
    echo "=================================================================="
    echo "Renewing ${UI_NAME} Certificate (PEM + fullchain for nginx)"
    echo "=================================================================="
    echo ""

    local UI_DN="CN=${UI_NAME}, OU=UI, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS"
    local UI_KEYSTORE="${UI_NAME}-temp.p12"
    local UI_ALIAS="${UI_NAME}"

    # Delete old ui certificates
    echo "Cleaning up old ${UI_NAME} certificate files..."
    rm -f "${UI_KEYSTORE}"
    rm -f "${UI_NAME}.csr"
    rm -f "${UI_NAME}-signed.crt"
    rm -f "${UI_NAME}-cert.key"
    rm -f "${UI_NAME}-cert.crt"
    rm -f "${UI_NAME}-fullchain.crt"
    echo "Done."
    echo ""

    # Generate UI key pair
    echo "Generating key pair for ${UI_NAME}..."
    keytool -genkeypair \
        -alias "${UI_ALIAS}" \
        -keyalg "${KEY_ALG}" \
        -keysize "${KEY_SIZE}" \
        -dname "${UI_DN}" \
        -validity "${SERVER_VALIDITY}" \
        -keystore "${UI_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -keypass "${SERVER_PASSWORD}" \
        -ext KeyUsage:critical=digitalSignature,keyEncipherment \
        -ext ExtendedKeyUsage=serverAuth,clientAuth \
        -ext "SAN=${SAN_VAR}"

    echo "Done."
    echo ""

    # Generate CSR
    echo "Generating Certificate Signing Request for ${UI_NAME}..."
    keytool -certreq \
        -alias "${UI_ALIAS}" \
        -keystore "${UI_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file "${UI_NAME}.csr" \
        -ext KeyUsage:critical=digitalSignature,keyEncipherment \
        -ext ExtendedKeyUsage=serverAuth,clientAuth \
        -ext "SAN=${SAN_VAR}"

    echo "Done."
    echo ""

    # Sign with Intermediate CA
    echo "Signing ${UI_NAME} certificate with Intermediate CA..."
    keytool -gencert \
        -alias "${INTERMEDIATE_ALIAS}" \
        -keystore "${INTERMEDIATE_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${INTERMEDIATE_PASSWORD}" \
        -infile "${UI_NAME}.csr" \
        -outfile "${UI_NAME}-signed.crt" \
        -validity "${SERVER_VALIDITY}" \
        -ext KeyUsage:critical=digitalSignature,keyEncipherment \
        -ext ExtendedKeyUsage=serverAuth,clientAuth \
        -ext "SAN=${SAN_VAR}" \
        -rfc

    echo "Done."
    echo ""

    # Import certificate chain
    echo "Importing certificate chain for ${UI_NAME}..."
    echo "  - Importing Root CA..."
    keytool -importcert \
        -alias dsp-root-ca \
        -keystore "${UI_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file root-ca.crt \
        -noprompt 2>/dev/null || true

    echo "  - Importing Intermediate CA..."
    keytool -importcert \
        -alias "${INTERMEDIATE_ALIAS}" \
        -keystore "${UI_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file intermediate-ca.crt \
        -noprompt

    echo "  - Importing signed ${UI_NAME} certificate..."
    keytool -importcert \
        -alias "${UI_ALIAS}" \
        -keystore "${UI_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file "${UI_NAME}-signed.crt" \
        -noprompt

    echo "Done."
    echo ""

    # Export to PEM format
    echo "Exporting ${UI_NAME} private key to PEM format (${UI_NAME}-cert.key)..."
    if command -v openssl &> /dev/null; then
        openssl pkcs12 -in "${UI_KEYSTORE}" -nocerts -nodes -passin pass:"${SERVER_PASSWORD}" -out "${UI_NAME}-cert.key"
        echo "Done."
        echo ""

        echo "Exporting ${UI_NAME} certificate to PEM format (${UI_NAME}-cert.crt)..."
        openssl pkcs12 -in "${UI_KEYSTORE}" -clcerts -nokeys -passin pass:"${SERVER_PASSWORD}" -out "${UI_NAME}-cert.crt"
        echo "Done."
        echo ""

        echo "Creating fullchain certificate for ${UI_NAME} (server cert + intermediate CA)..."
        cat "${UI_NAME}-cert.crt" intermediate-ca.crt > "${UI_NAME}-fullchain.crt"
        echo "Done."
    else
        echo "WARNING: OpenSSL not found. Cannot convert to PEM format automatically."
        echo "Please convert manually using:"
        echo "  openssl pkcs12 -in ${UI_NAME}-temp.p12 -nocerts -nodes -passin pass:${SERVER_PASSWORD} -out ${UI_NAME}-cert.key"
        echo "  openssl pkcs12 -in ${UI_NAME}-temp.p12 -clcerts -nokeys -passin pass:${SERVER_PASSWORD} -out ${UI_NAME}-cert.crt"
        echo ""
        echo "Alternatively, install OpenSSL and run this script again."
    fi
    echo ""

    # Cleanup temporary files
    echo "Cleaning up temporary files..."
    rm -f "${UI_NAME}.csr"
    rm -f "${UI_NAME}-signed.crt"
    echo "Done."
    echo ""

    echo "${UI_NAME} certificate renewed successfully!"
    echo "  - ${UI_NAME}-temp.p12 (PKCS12 format)"
    echo "  - ${UI_NAME}-cert.key (PEM format, private key for nginx)"
    echo "  - ${UI_NAME}-cert.crt (PEM format, server certificate)"
    echo "  - ${UI_NAME}-fullchain.crt (PEM format, fullchain for nginx - USE THIS)"
    echo "  - SANs: ${SAN_VAR}"
    echo ""
}

renew_all() {
    check_prerequisites

    echo ""
    echo "=================================================================="
    echo "Renewing ALL server certificates"
    echo "=================================================================="
    echo ""

    renew_server_cert "connector-a" "CN=connector-a, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "${SAN_CONNECTOR_A}"
    renew_server_cert "connector-b" "CN=connector-b, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "${SAN_CONNECTOR_B}"
    renew_minio
    renew_ui "ui-a" "${SAN_UI_A}"
    renew_ui "ui-b" "${SAN_UI_B}"

    echo ""
    echo "=================================================================="
    echo "ALL certificates renewed successfully!"
    echo "=================================================================="
    echo ""
}

##################################################################
# MAIN LOOP
##################################################################

while true; do
    show_menu
    read -p "Enter your choice (1-7): " choice

    case $choice in
        1)
            check_prerequisites
            echo ""
            echo "=================================================================="
            echo "Renewing connector-a certificate"
            echo "=================================================================="
            echo ""
            renew_server_cert "connector-a" "CN=connector-a, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "${SAN_CONNECTOR_A}"
            read -p "Press Enter to continue..."
            ;;
        2)
            check_prerequisites
            echo ""
            echo "=================================================================="
            echo "Renewing connector-b certificate"
            echo "=================================================================="
            echo ""
            renew_server_cert "connector-b" "CN=connector-b, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "${SAN_CONNECTOR_B}"
            read -p "Press Enter to continue..."
            ;;
        3)
            renew_minio
            read -p "Press Enter to continue..."
            ;;
        4)
            renew_ui "ui-a" "${SAN_UI_A}"
            read -p "Press Enter to continue..."
            ;;
        5)
            renew_ui "ui-b" "${SAN_UI_B}"
            read -p "Press Enter to continue..."
            ;;
        6)
            renew_all
            read -p "Press Enter to continue..."
            ;;
        7)
            echo ""
            echo "Exiting..."
            exit 0
            ;;
        *)
            echo "Invalid choice. Please try again."
            read -p "Press Enter to continue..."
            ;;
    esac
done

