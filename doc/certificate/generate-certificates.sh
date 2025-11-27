#!/bin/bash
##################################################################
# Certificate Generation Script for DSP True Connector
# Creates a complete PKI hierarchy:
#   1. Root CA (self-signed)
#   2. Intermediate CA (signed by Root CA)
#   3. Server certificates (signed by Intermediate CA)
##################################################################

set -e  # Exit on error

##################################################################
# CONFIGURATION - Edit these values as needed
##################################################################

# Root CA Configuration
ROOT_ALIAS="dsp-root-ca"
ROOT_DNAME="CN=DSP Root CA, OU=Security, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS"
ROOT_VALIDITY=3650
ROOT_KEYSTORE="dsp-root-ca.p12"
ROOT_PASSWORD="password"

# Intermediate CA Configuration
INTERMEDIATE_ALIAS="dsp-intermediate-ca"
INTERMEDIATE_DNAME="CN=DSP Intermediate CA, OU=Security, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS"
INTERMEDIATE_VALIDITY=1825
INTERMEDIATE_KEYSTORE="dsp-intermediate-ca.p12"
INTERMEDIATE_PASSWORD="password"

# Server Certificate Configuration
SERVER_VALIDITY=365
SERVER_PASSWORD="password"

# Subject Alternative Names (SAN) - Edit these lists as needed for each service
# Each server should only have the SANs it actually needs for security best practices
SAN_CONNECTOR_A="DNS:localhost,DNS:connector-a,IP:127.0.0.1"
SAN_CONNECTOR_B="DNS:localhost,DNS:connector-b,IP:127.0.0.1"
SAN_MINIO="DNS:localhost,DNS:minio,IP:127.0.0.1"
SAN_UI_A="DNS:localhost,DNS:ui-a,IP:127.0.0.1"
SAN_UI_B="DNS:localhost,DNS:ui-b,IP:127.0.0.1"

# Legacy: All SANs combined (for backward compatibility or development)
# SAN_LIST="DNS:localhost,DNS:connector-a,DNS:connector-b,DNS:minio,DNS:mongodb-a,DNS:mongodb-b,DNS:ui-a,DNS:ui-b,IP:127.0.0.1"

# Truststore Configuration
TRUSTSTORE="dsp-truststore.p12"
TRUSTSTORE_PASSWORD="password"

# Key Algorithm and Size
KEY_ALG="RSA"
KEY_SIZE=2048

##################################################################
# START CERTIFICATE GENERATION
##################################################################

echo ""
echo "=================================================================="
echo "DSP True Connector - Certificate Generation Script"
echo "=================================================================="
echo ""
echo "This script will generate:"
echo "  1. Root CA (self-signed)"
echo "  2. Intermediate CA (signed by Root CA)"
echo "  3. Server certificates for connector-a and connector-b"
echo "  4. MinIO certificate (PEM format)"
echo "  5. UI-A and UI-B certificates (PEM format with fullchain)"
echo "  6. Truststore with Intermediate CA certificate"
echo ""
echo "Configuration:"
echo "  - connector-a SANs: ${SAN_CONNECTOR_A}"
echo "  - connector-b SANs: ${SAN_CONNECTOR_B}"
echo "  - MinIO SANs: ${SAN_MINIO}"
echo "  - UI-A SANs: ${SAN_UI_A}"
echo "  - UI-B SANs: ${SAN_UI_B}"
echo "  - Key Algorithm: ${KEY_ALG} ${KEY_SIZE} bits"
echo "  - Root CA Validity: ${ROOT_VALIDITY} days"
echo "  - Intermediate CA Validity: ${INTERMEDIATE_VALIDITY} days"
echo "  - Server Cert Validity: ${SERVER_VALIDITY} days"
echo ""
echo "=================================================================="
echo ""

read -p "Press Enter to continue..."

# Clean up old files
echo "Cleaning up old certificate files..."
rm -f "${ROOT_KEYSTORE}"
rm -f "${INTERMEDIATE_KEYSTORE}"
rm -f connector-a.p12
rm -f connector-b.p12
rm -f minio-temp.p12
rm -f ui-a-temp.p12
rm -f ui-b-temp.p12
rm -f private.key
rm -f public.crt
rm -f ui-a-cert.key
rm -f ui-a-cert.crt
rm -f ui-a-fullchain.crt
rm -f ui-b-cert.key
rm -f ui-b-cert.crt
rm -f ui-b-fullchain.crt
rm -f "${TRUSTSTORE}"
rm -f *.csr
rm -f *.crt
rm -f *.cer
echo "Done."
echo ""

##################################################################
# STEP 1: Generate Root CA (Self-Signed)
##################################################################

echo "=================================================================="
echo "STEP 1: Generating Root CA (Self-Signed)"
echo "=================================================================="
echo ""

keytool -genkeypair \
    -alias "${ROOT_ALIAS}" \
    -keyalg "${KEY_ALG}" \
    -keysize "${KEY_SIZE}" \
    -dname "${ROOT_DNAME}" \
    -validity "${ROOT_VALIDITY}" \
    -keystore "${ROOT_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${ROOT_PASSWORD}" \
    -keypass "${ROOT_PASSWORD}" \
    -ext BasicConstraints:critical=ca:true \
    -ext KeyUsage:critical=keyCertSign,cRLSign

echo ""
echo "Root CA generated successfully!"
echo "  - Keystore: ${ROOT_KEYSTORE}"
echo "  - Alias: ${ROOT_ALIAS}"
echo ""

# Export Root CA certificate
echo "Exporting Root CA certificate..."
keytool -exportcert \
    -alias "${ROOT_ALIAS}" \
    -keystore "${ROOT_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${ROOT_PASSWORD}" \
    -file root-ca.crt \
    -rfc

echo "Done."
echo ""

##################################################################
# STEP 2: Generate Intermediate CA
##################################################################

echo "=================================================================="
echo "STEP 2: Generating Intermediate CA"
echo "=================================================================="
echo ""

# Generate Intermediate CA key pair
echo "Generating Intermediate CA key pair..."
keytool -genkeypair \
    -alias "${INTERMEDIATE_ALIAS}" \
    -keyalg "${KEY_ALG}" \
    -keysize "${KEY_SIZE}" \
    -dname "${INTERMEDIATE_DNAME}" \
    -validity "${INTERMEDIATE_VALIDITY}" \
    -keystore "${INTERMEDIATE_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${INTERMEDIATE_PASSWORD}" \
    -keypass "${INTERMEDIATE_PASSWORD}" \
    -ext BasicConstraints:critical=ca:true,pathlen:0 \
    -ext KeyUsage:critical=keyCertSign,cRLSign

echo "Done."
echo ""

# Generate CSR for Intermediate CA
echo "Generating Certificate Signing Request for Intermediate CA..."
keytool -certreq \
    -alias "${INTERMEDIATE_ALIAS}" \
    -keystore "${INTERMEDIATE_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${INTERMEDIATE_PASSWORD}" \
    -file intermediate-ca.csr

echo "Done."
echo ""

# Sign Intermediate CA certificate with Root CA
echo "Signing Intermediate CA certificate with Root CA..."
keytool -gencert \
    -alias "${ROOT_ALIAS}" \
    -keystore "${ROOT_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${ROOT_PASSWORD}" \
    -infile intermediate-ca.csr \
    -outfile intermediate-ca.crt \
    -validity "${INTERMEDIATE_VALIDITY}" \
    -ext BasicConstraints:critical=ca:true,pathlen:0 \
    -ext KeyUsage:critical=keyCertSign,cRLSign \
    -rfc

echo "Done."
echo ""

# Import Root CA certificate into Intermediate CA keystore
echo "Importing Root CA into Intermediate CA keystore..."
keytool -importcert \
    -alias "${ROOT_ALIAS}" \
    -keystore "${INTERMEDIATE_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${INTERMEDIATE_PASSWORD}" \
    -file root-ca.crt \
    -noprompt

echo "Done."
echo ""

# Import signed Intermediate CA certificate
echo "Importing signed Intermediate CA certificate..."
keytool -importcert \
    -alias "${INTERMEDIATE_ALIAS}" \
    -keystore "${INTERMEDIATE_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${INTERMEDIATE_PASSWORD}" \
    -file intermediate-ca.crt \
    -noprompt

echo "Done."
echo ""

echo "Intermediate CA generated successfully!"
echo "  - Keystore: ${INTERMEDIATE_KEYSTORE}"
echo "  - Alias: ${INTERMEDIATE_ALIAS}"
echo ""

##################################################################
# STEP 3: Generate Server Certificates
##################################################################

echo "=================================================================="
echo "STEP 3: Generating Server Certificates"
echo "=================================================================="
echo ""

# Function to generate server certificate
generate_server_cert() {
    local SERVER_NAME=$1
    local SERVER_DN=$2
    local SERVER_SAN=$3
    local SERVER_KEYSTORE="${SERVER_NAME}.p12"
    local SERVER_ALIAS="${SERVER_NAME}"

    echo ""
    echo "------------------------------------------------------------------"
    echo "Generating server certificate: ${SERVER_NAME}"
    echo "------------------------------------------------------------------"
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

    # Import certificate chain (Root CA, Intermediate CA, Server Cert)
    echo "Importing certificate chain for ${SERVER_NAME}..."

    # First import Root CA
    echo "  - Importing Root CA..."
    keytool -importcert \
        -alias "${ROOT_ALIAS}" \
        -keystore "${SERVER_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file root-ca.crt \
        -noprompt

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
    echo "Server certificate ${SERVER_NAME} generated successfully!"
    echo "  - Keystore: ${SERVER_KEYSTORE}"
    echo "  - Alias: ${SERVER_ALIAS}"
    echo "  - SAN: ${SERVER_SAN}"
    echo ""
}

# Generate server certificates
generate_server_cert "connector-a" "CN=connector-a, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "${SAN_CONNECTOR_A}"
generate_server_cert "connector-b" "CN=connector-b, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "${SAN_CONNECTOR_B}"

echo ""
echo "All server certificates generated successfully!"
echo ""

##################################################################
# STEP 4: Generate MinIO Certificate (PEM format)
##################################################################

echo "=================================================================="
echo "STEP 4: Generating MinIO Certificate (PEM format)"
echo "=================================================================="
echo ""

MINIO_NAME="minio"
MINIO_DN="CN=minio, OU=Storage, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS"
MINIO_KEYSTORE="minio-temp.p12"
MINIO_ALIAS="minio"

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

echo "Importing certificate chain for MinIO..."
echo "  - Importing Root CA..."
keytool -importcert \
    -alias "${ROOT_ALIAS}" \
    -keystore "${MINIO_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${SERVER_PASSWORD}" \
    -file root-ca.crt \
    -noprompt

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

echo "Exporting MinIO private key to PEM format (private.key)..."
if command -v openssl &> /dev/null; then
    openssl pkcs12 -in "${MINIO_KEYSTORE}" -nocerts -nodes -passin pass:"${SERVER_PASSWORD}" -out private.key
    echo "Done."
else
    echo "WARNING: OpenSSL not found. Cannot convert to PEM format automatically."
    echo "Please convert manually using:"
    echo "  openssl pkcs12 -in minio-temp.p12 -nocerts -nodes -passin pass:${SERVER_PASSWORD} -out private.key"
    echo ""
    echo "Creating placeholder private.key file..."
    cat > private.key << EOF
# MinIO Private Key
# Convert from minio-temp.p12 using OpenSSL
# Command: openssl pkcs12 -in minio-temp.p12 -nocerts -nodes -passin pass:${SERVER_PASSWORD} -out private.key
EOF
fi
echo ""

echo "Exporting MinIO certificate to PEM format (public.crt)..."
if command -v openssl &> /dev/null; then
    openssl pkcs12 -in "${MINIO_KEYSTORE}" -clcerts -nokeys -passin pass:"${SERVER_PASSWORD}" -out public.crt
    echo "Done."
else
    echo "WARNING: OpenSSL not found. Using keytool export..."
    keytool -exportcert \
        -alias "${MINIO_ALIAS}" \
        -keystore "${MINIO_KEYSTORE}" \
        -storetype PKCS12 \
        -storepass "${SERVER_PASSWORD}" \
        -file public.crt \
        -rfc
    echo "Done."
fi
echo ""

echo "MinIO certificate files generated:"
echo "  - private.key (Private key in PEM format)"
echo "  - public.crt (Certificate in PEM format, signed by Intermediate CA)"
echo "  - SAN: ${SAN_MINIO}"
echo "  - minio-temp.p12 (Temporary PKCS12 keystore, can be deleted)"
echo ""

##################################################################
# STEP 4b: Generate UI-A Certificate (PEM format for nginx)
##################################################################

echo "=================================================================="
echo "STEP 4b: Generating UI-A Certificate (PEM format for nginx)"
echo "=================================================================="
echo ""

UI_A_NAME="ui-a"
UI_A_DN="CN=ui-a, OU=UI, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS"
UI_A_KEYSTORE="ui-a-temp.p12"
UI_A_ALIAS="ui-a"

echo "Generating key pair for UI-A..."
keytool -genkeypair \
    -alias "${UI_A_ALIAS}" \
    -keyalg "${KEY_ALG}" \
    -keysize "${KEY_SIZE}" \
    -dname "${UI_A_DN}" \
    -validity "${SERVER_VALIDITY}" \
    -keystore "${UI_A_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${SERVER_PASSWORD}" \
    -keypass "${SERVER_PASSWORD}" \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=${SAN_UI_A}"

echo "Done."
echo ""

echo "Generating Certificate Signing Request for UI-A..."
keytool -certreq \
    -alias "${UI_A_ALIAS}" \
    -keystore "${UI_A_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${SERVER_PASSWORD}" \
    -file ui-a.csr \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=${SAN_UI_A}"

echo "Done."
echo ""

echo "Signing UI-A certificate with Intermediate CA..."
keytool -gencert \
    -alias "${INTERMEDIATE_ALIAS}" \
    -keystore "${INTERMEDIATE_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${INTERMEDIATE_PASSWORD}" \
    -infile ui-a.csr \
    -outfile ui-a-signed.crt \
    -validity "${SERVER_VALIDITY}" \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=${SAN_UI_A}" \
    -rfc

echo "Done."
echo ""

echo "Importing certificate chain for UI-A..."
echo "  - Importing Root CA..."
keytool -importcert \
    -alias "${ROOT_ALIAS}" \
    -keystore "${UI_A_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${SERVER_PASSWORD}" \
    -file root-ca.crt \
    -noprompt

echo "  - Importing Intermediate CA..."
keytool -importcert \
    -alias "${INTERMEDIATE_ALIAS}" \
    -keystore "${UI_A_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${SERVER_PASSWORD}" \
    -file intermediate-ca.crt \
    -noprompt

echo "  - Importing signed UI-A certificate..."
keytool -importcert \
    -alias "${UI_A_ALIAS}" \
    -keystore "${UI_A_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${SERVER_PASSWORD}" \
    -file ui-a-signed.crt \
    -noprompt

echo "Done."
echo ""

echo "Exporting UI-A private key to PEM format (ui-a-cert.key)..."
if command -v openssl &> /dev/null; then
    openssl pkcs12 -in "${UI_A_KEYSTORE}" -nocerts -nodes -passin pass:"${SERVER_PASSWORD}" -out ui-a-cert.key
    echo "Done."
    echo ""

    echo "Exporting UI-A certificate to PEM format (ui-a-cert.crt)..."
    openssl pkcs12 -in "${UI_A_KEYSTORE}" -clcerts -nokeys -passin pass:"${SERVER_PASSWORD}" -out ui-a-cert.crt
    echo "Done."
    echo ""

    echo "Creating fullchain certificate for UI-A (server cert + intermediate CA)..."
    cat ui-a-cert.crt intermediate-ca.crt > ui-a-fullchain.crt
    echo "Done."
else
    echo "WARNING: OpenSSL not found. Cannot convert to PEM format automatically."
    echo "Please install OpenSSL and run this script again."
fi
echo ""

echo "UI-A certificate files generated:"
echo "  - ui-a-cert.key (Private key in PEM format)"
echo "  - ui-a-cert.crt (Certificate in PEM format, signed by Intermediate CA)"
echo "  - ui-a-fullchain.crt (Full certificate chain: server cert + intermediate CA)"
echo "  - SAN: ${SAN_UI_A}"
echo "  - ui-a-temp.p12 (Temporary PKCS12 keystore, can be deleted)"
echo ""

##################################################################
# STEP 4c: Generate UI-B Certificate (PEM format for nginx)
##################################################################

echo "=================================================================="
echo "STEP 4c: Generating UI-B Certificate (PEM format for nginx)"
echo "=================================================================="
echo ""

UI_B_NAME="ui-b"
UI_B_DN="CN=ui-b, OU=UI, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS"
UI_B_KEYSTORE="ui-b-temp.p12"
UI_B_ALIAS="ui-b"

echo "Generating key pair for UI-B..."
keytool -genkeypair \
    -alias "${UI_B_ALIAS}" \
    -keyalg "${KEY_ALG}" \
    -keysize "${KEY_SIZE}" \
    -dname "${UI_B_DN}" \
    -validity "${SERVER_VALIDITY}" \
    -keystore "${UI_B_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${SERVER_PASSWORD}" \
    -keypass "${SERVER_PASSWORD}" \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=${SAN_UI_B}"

echo "Done."
echo ""

echo "Generating Certificate Signing Request for UI-B..."
keytool -certreq \
    -alias "${UI_B_ALIAS}" \
    -keystore "${UI_B_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${SERVER_PASSWORD}" \
    -file ui-b.csr \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=${SAN_UI_B}"

echo "Done."
echo ""

echo "Signing UI-B certificate with Intermediate CA..."
keytool -gencert \
    -alias "${INTERMEDIATE_ALIAS}" \
    -keystore "${INTERMEDIATE_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${INTERMEDIATE_PASSWORD}" \
    -infile ui-b.csr \
    -outfile ui-b-signed.crt \
    -validity "${SERVER_VALIDITY}" \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=${SAN_UI_B}" \
    -rfc

echo "Done."
echo ""

echo "Importing certificate chain for UI-B..."
echo "  - Importing Root CA..."
keytool -importcert \
    -alias "${ROOT_ALIAS}" \
    -keystore "${UI_B_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${SERVER_PASSWORD}" \
    -file root-ca.crt \
    -noprompt

echo "  - Importing Intermediate CA..."
keytool -importcert \
    -alias "${INTERMEDIATE_ALIAS}" \
    -keystore "${UI_B_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${SERVER_PASSWORD}" \
    -file intermediate-ca.crt \
    -noprompt

echo "  - Importing signed UI-B certificate..."
keytool -importcert \
    -alias "${UI_B_ALIAS}" \
    -keystore "${UI_B_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${SERVER_PASSWORD}" \
    -file ui-b-signed.crt \
    -noprompt

echo "Done."
echo ""

echo "Exporting UI-B private key to PEM format (ui-b-cert.key)..."
if command -v openssl &> /dev/null; then
    openssl pkcs12 -in "${UI_B_KEYSTORE}" -nocerts -nodes -passin pass:"${SERVER_PASSWORD}" -out ui-b-cert.key
    echo "Done."
    echo ""

    echo "Exporting UI-B certificate to PEM format (ui-b-cert.crt)..."
    openssl pkcs12 -in "${UI_B_KEYSTORE}" -clcerts -nokeys -passin pass:"${SERVER_PASSWORD}" -out ui-b-cert.crt
    echo "Done."
    echo ""

    echo "Creating fullchain certificate for UI-B (server cert + intermediate CA)..."
    cat ui-b-cert.crt intermediate-ca.crt > ui-b-fullchain.crt
    echo "Done."
else
    echo "WARNING: OpenSSL not found. Cannot convert to PEM format automatically."
    echo "Please install OpenSSL and run this script again."
fi
echo ""

echo "UI-B certificate files generated:"
echo "  - ui-b-cert.key (Private key in PEM format)"
echo "  - ui-b-cert.crt (Certificate in PEM format, signed by Intermediate CA)"
echo "  - ui-b-fullchain.crt (Full certificate chain: server cert + intermediate CA)"
echo "  - SAN: ${SAN_UI_B}"
echo "  - ui-b-temp.p12 (Temporary PKCS12 keystore, can be deleted)"
echo ""

##################################################################
# STEP 5: Create Truststore with Intermediate CA
##################################################################

echo "=================================================================="
echo "STEP 5: Creating Truststore"
echo "=================================================================="
echo ""

echo "Creating truststore with Intermediate CA certificate..."
keytool -importcert \
    -trustcacerts \
    -alias "${INTERMEDIATE_ALIAS}" \
    -file intermediate-ca.crt \
    -keystore "${TRUSTSTORE}" \
    -storetype PKCS12 \
    -storepass "${TRUSTSTORE_PASSWORD}" \
    -noprompt

echo ""
echo "Importing Root CA certificate into truststore (optional, for complete chain)..."
keytool -importcert \
    -trustcacerts \
    -alias "${ROOT_ALIAS}" \
    -file root-ca.crt \
    -keystore "${TRUSTSTORE}" \
    -storetype PKCS12 \
    -storepass "${TRUSTSTORE_PASSWORD}" \
    -noprompt

echo "Done."
echo ""

##################################################################
# STEP 6: Verification
##################################################################

echo "=================================================================="
echo "STEP 6: Verifying Generated Certificates"
echo "=================================================================="
echo ""

echo "Root CA Keystore:"
keytool -list -v -keystore "${ROOT_KEYSTORE}" -storepass "${ROOT_PASSWORD}" -storetype PKCS12 | grep -E "Alias|Owner|Issuer|Valid"
echo ""

echo "Intermediate CA Keystore:"
keytool -list -v -keystore "${INTERMEDIATE_KEYSTORE}" -storepass "${INTERMEDIATE_PASSWORD}" -storetype PKCS12 | grep -E "Alias|Owner|Issuer|Valid"
echo ""

echo "Connector-A Keystore:"
keytool -list -v -keystore connector-a.p12 -storepass "${SERVER_PASSWORD}" -storetype PKCS12 | grep -E "Alias|Owner|Issuer|Valid|DNS"
echo ""

echo "Connector-B Keystore:"
keytool -list -v -keystore connector-b.p12 -storepass "${SERVER_PASSWORD}" -storetype PKCS12 | grep -E "Alias|Owner|Issuer|Valid|DNS"
echo ""

echo "MinIO Certificate Files:"
echo "  - private.key: Private key in PEM format"
echo "  - public.crt: Certificate in PEM format"
if [ -f private.key ]; then
    echo "  private.key exists: YES"
    grep "BEGIN" private.key || true
else
    echo "  private.key exists: NO"
fi
if [ -f public.crt ]; then
    echo "  public.crt exists: YES"
    grep "BEGIN CERTIFICATE" public.crt || true
else
    echo "  public.crt exists: NO"
fi
echo ""

echo "Truststore:"
keytool -list -v -keystore "${TRUSTSTORE}" -storepass "${TRUSTSTORE_PASSWORD}" -storetype PKCS12 | grep -E "Alias|Owner|Issuer|Valid"
echo ""

##################################################################
# CLEANUP
##################################################################

echo "=================================================================="
echo "Cleaning up temporary files..."
echo "=================================================================="
echo ""

rm -f *.csr
rm -f root-ca.crt
rm -f intermediate-ca.crt
rm -f minio-signed.crt
rm -f ui-a-signed.crt
rm -f ui-b-signed.crt
# Keep public.crt for MinIO
# Keep private.key for MinIO
# Keep ui-a-cert.crt and ui-a-cert.key for UI-A
# Keep ui-b-cert.crt and ui-b-cert.key for UI-B
rm -f connector-a.crt
rm -f connector-b.crt
rm -f *.cer

echo "Done."
echo ""

##################################################################
# SUMMARY
##################################################################

echo "=================================================================="
echo "CERTIFICATE GENERATION COMPLETE!"
echo "=================================================================="
echo ""
echo "Generated files:"
echo "  1. ${ROOT_KEYSTORE} - Root CA (keep secure, used for signing Intermediate CA)"
echo "  2. ${INTERMEDIATE_KEYSTORE} - Intermediate CA (keep secure, used for signing server certs)"
echo "  3. connector-a.p12 - Server certificate for connector-a"
echo "  4. connector-b.p12 - Server certificate for connector-b"
echo "  5. private.key - MinIO private key in PEM format (for MinIO certs/private.key)"
echo "  6. public.crt - MinIO certificate in PEM format (for MinIO certs/public.crt)"
echo "  7. minio-temp.p12 - MinIO certificate in PKCS12 format (optional, can be deleted)"
echo "  8. ui-a-cert.key - UI-A private key in PEM format (for nginx)"
echo "  9. ui-a-cert.crt - UI-A certificate in PEM format (for nginx, signed by Intermediate CA)"
echo "  10. ui-a-fullchain.crt - UI-A fullchain certificate (server cert + intermediate CA)"
echo "  11. ui-a-temp.p12 - UI-A certificate in PKCS12 format (optional, can be deleted)"
echo "  12. ui-b-cert.key - UI-B private key in PEM format (for nginx)"
echo "  13. ui-b-cert.crt - UI-B certificate in PEM format (for nginx, signed by Intermediate CA)"
echo "  14. ui-b-fullchain.crt - UI-B fullchain certificate (server cert + intermediate CA)"
echo "  15. ui-b-temp.p12 - UI-B certificate in PKCS12 format (optional, can be deleted)"
echo "  16. ${TRUSTSTORE} - Truststore with Intermediate CA (use for TLS validation)"
echo ""
echo "Certificate Chain:"
echo "  Root CA --signs--> Intermediate CA --signs--> Server Certificates (including MinIO and UI)"
echo ""
echo "For TLS handshake:"
echo "  - Servers present: connector-a.p12, connector-b.p12, or MinIO/UI PEM files"
echo "  - Clients trust: ${TRUSTSTORE} (contains Intermediate CA)"
echo ""
echo "For MinIO Docker setup:"
echo "  Copy to MinIO certs directory:"
echo "    - private.key --> /root/.minio/certs/private.key"
echo "    - public.crt --> /root/.minio/certs/public.crt"
echo "  Or mount as Docker volume:"
echo "    - ./private.key:/root/.minio/certs/private.key:ro"
echo "    - ./public.crt:/root/.minio/certs/public.crt:ro"
echo ""
echo "For nginx (UI-A and UI-B) Docker setup:"
echo "  Copy to nginx ssl directory or mount as Docker volume:"
echo "  UI-A:"
echo "    - ./ui-a-fullchain.crt:/etc/nginx/ssl/ui-a-fullchain.crt:ro"
echo "    - ./ui-a-cert.key:/etc/nginx/ssl/ui-a-cert.key:ro"
echo "  UI-B:"
echo "    - ./ui-b-fullchain.crt:/etc/nginx/ssl/ui-b-fullchain.crt:ro"
echo "    - ./ui-b-cert.key:/etc/nginx/ssl/ui-b-cert.key:ro"
echo "  Configure in nginx.conf:"
echo "    ssl_certificate /etc/nginx/ssl/ui-a-fullchain.crt;"
echo "    ssl_certificate_key /etc/nginx/ssl/ui-a-cert.key;"
echo "  Note: The fullchain certificate includes both the server cert and intermediate CA"
echo ""
echo "Update your application.properties:"
echo "  spring.ssl.bundle.jks.connector.keystore.location=classpath:connector-a.p12 (or connector-b.p12)"
echo "  spring.ssl.bundle.jks.connector.keystore.password=${SERVER_PASSWORD}"
echo "  spring.ssl.bundle.jks.connector.truststore.location=classpath:${TRUSTSTORE}"
echo "  spring.ssl.bundle.jks.connector.truststore.password=${TRUSTSTORE_PASSWORD}"
echo ""
echo "=================================================================="

