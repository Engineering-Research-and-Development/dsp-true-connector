#!/usr/bin/env bash
# ==================================================================
# Generate DCP-Issuer & EC Issuer Certificates Script
# Assumes root and intermediate CA already exist in current directory
# ==================================================================

set -euo pipefail

# ==================================================================
# Certificate Authority (CA) Constants
# ==================================================================
ROOT_ALIAS="dsp-root-ca"
ROOT_KEYSTORE="dsp-root-ca.p12"
ROOT_PASSWORD="password"
ROOT_CERT_FILE="root-ca.crt"

INTERMEDIATE_ALIAS="dsp-intermediate-ca"
INTERMEDIATE_KEYSTORE="dsp-intermediate-ca.p12"
INTERMEDIATE_PASSWORD="password"
INTERMEDIATE_CERT_FILE="intermediate-ca.crt"

# ==================================================================
# 1. DCP-Issuer (RSA) Constants
# ==================================================================
DCP_ISSUER_ALIAS="dcp-issuer"
DCP_ISSUER_KEY_ALG="RSA"
DCP_ISSUER_KEY_SIZE="2048"
DCP_ISSUER_VALIDITY="365"
DCP_ISSUER_PASSWORD="password"
DCP_ISSUER_DN="CN=dcp-issuer, OU=Issuer, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS"
DCP_ISSUER_SAN="DNS:localhost,DNS:dcp-issuer,IP:127.0.0.1"
DCP_ISSUER_KEYSTORE="dcp-issuer-temp.p12"
DCP_ISSUER_CSR="dcp-issuer.csr"
DCP_ISSUER_SIGNED_CERT="dcp-issuer-signed.crt"
DCP_ISSUER_KEY_OUT="dcp-issuer.key"
DCP_ISSUER_CERT_OUT="dcp-issuer.crt"

# ==================================================================
# 2. EC Issuer (secp256r1 ECDSA) Constants
# ==================================================================
EC_ISSUER_ALIAS="issuer"
EC_KEY_ALG="EC"
EC_GROUP_NAME="secp256r1"
EC_SIG_ALG="SHA256withECDSA"
EC_VALIDITY="365"
EC_PASSWORD="password"
EC_DN="CN=Issuer, OU=DCP, O=TrueConnector, L=City, ST=State, C=IT"
EC_SAN="DNS:localhost,DNS:issuer,IP:127.0.0.1"
EC_KEYSTORE="eckey-issuer.p12"
EC_CSR="eckey-issuer.csr"
EC_SIGNED_CERT="eckey-issuer-signed.crt"
EC_KEY_OUT="eckey-issuer.key"
EC_CERT_OUT="eckey-issuer.crt"

# ==================================================================
# Pre-requisite Checks
# ==================================================================
if ! command -v keytool >/dev/null 2>&1; then
    echo "ERROR: 'keytool' command not found. Please install a Java JDK/JRE." >&2
    exit 1
fi

if [[ ! -f "$ROOT_CERT_FILE" ]]; then
    echo "WARNING: Root CA certificate '$ROOT_CERT_FILE' not found in current directory."
fi
if [[ ! -f "$INTERMEDIATE_CERT_FILE" ]]; then
    echo "WARNING: Intermediate CA certificate '$INTERMEDIATE_CERT_FILE' not found in current directory."
fi
if [[ ! -f "$INTERMEDIATE_KEYSTORE" ]]; then
    echo "WARNING: Intermediate keystore '$INTERMEDIATE_KEYSTORE' not found in current directory."
fi

# ==================================================================
# SECTION 1: Generate & Sign DCP-Issuer Certificate (RSA)
# ==================================================================
echo ""
echo "=================================================================="
echo "1. Generating DCP-Issuer Certificate (RSA $DCP_ISSUER_KEY_SIZE)..."
echo "=================================================================="

# Clean up old RSA files
rm -f "$DCP_ISSUER_KEYSTORE" "$DCP_ISSUER_CSR" "$DCP_ISSUER_SIGNED_CERT" "$DCP_ISSUER_KEY_OUT" "$DCP_ISSUER_CERT_OUT" 2>/dev/null || true

echo "Generating RSA key pair for DCP-Issuer..."
if ! keytool -genkeypair \
    -alias "$DCP_ISSUER_ALIAS" \
    -keyalg "$DCP_ISSUER_KEY_ALG" \
    -keysize "$DCP_ISSUER_KEY_SIZE" \
    -dname "$DCP_ISSUER_DN" \
    -validity "$DCP_ISSUER_VALIDITY" \
    -keystore "$DCP_ISSUER_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$DCP_ISSUER_PASSWORD" \
    -keypass "$DCP_ISSUER_PASSWORD" \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=$DCP_ISSUER_SAN"; then
    echo "ERROR: Failed to generate key pair for DCP-Issuer" >&2
    exit 1
fi
echo "Done."

echo "Generating Certificate Signing Request for DCP-Issuer..."
if ! keytool -certreq \
    -alias "$DCP_ISSUER_ALIAS" \
    -keystore "$DCP_ISSUER_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$DCP_ISSUER_PASSWORD" \
    -file "$DCP_ISSUER_CSR" \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=$DCP_ISSUER_SAN"; then
    echo "ERROR: Failed to generate CSR for DCP-Issuer" >&2
    exit 1
fi
echo "Done."

echo "Signing DCP-Issuer certificate with Intermediate CA..."
if ! keytool -gencert \
    -alias "$INTERMEDIATE_ALIAS" \
    -keystore "$INTERMEDIATE_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$INTERMEDIATE_PASSWORD" \
    -infile "$DCP_ISSUER_CSR" \
    -outfile "$DCP_ISSUER_SIGNED_CERT" \
    -validity "$DCP_ISSUER_VALIDITY" \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=$DCP_ISSUER_SAN" \
    -rfc; then
    echo "ERROR: Failed to sign certificate for DCP-Issuer" >&2
    exit 1
fi
echo "Done."

echo "Importing certificate chain for DCP-Issuer into $DCP_ISSUER_KEYSTORE..."
echo "  - Importing Root CA..."
keytool -importcert \
    -alias "$ROOT_ALIAS" \
    -keystore "$DCP_ISSUER_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$DCP_ISSUER_PASSWORD" \
    -file "$ROOT_CERT_FILE" \
    -noprompt

echo "  - Importing Intermediate CA..."
keytool -importcert \
    -alias "$INTERMEDIATE_ALIAS" \
    -keystore "$DCP_ISSUER_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$DCP_ISSUER_PASSWORD" \
    -file "$INTERMEDIATE_CERT_FILE" \
    -noprompt

echo "  - Importing signed DCP-Issuer certificate..."
if ! keytool -importcert \
    -alias "$DCP_ISSUER_ALIAS" \
    -keystore "$DCP_ISSUER_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$DCP_ISSUER_PASSWORD" \
    -file "$DCP_ISSUER_SIGNED_CERT" \
    -noprompt; then
    echo "ERROR: Failed to import certificate chain for DCP-Issuer" >&2
    exit 1
fi
echo "Done."

echo "Exporting DCP-Issuer private key to PEM format ($DCP_ISSUER_KEY_OUT)..."
if openssl pkcs12 -in "$DCP_ISSUER_KEYSTORE" -nocerts -nodes -passin "pass:$DCP_ISSUER_PASSWORD" -out "$DCP_ISSUER_KEY_OUT" 2>/dev/null; then
    echo "Done."
else
    echo "WARNING: OpenSSL not found or failed. Using alternative method..."
    echo "You will need to manually convert $DCP_ISSUER_KEYSTORE to $DCP_ISSUER_KEY_OUT"
    echo "Command: openssl pkcs12 -in $DCP_ISSUER_KEYSTORE -nocerts -nodes -passin pass:$DCP_ISSUER_PASSWORD -out $DCP_ISSUER_KEY_OUT"
    echo ""
    echo "Creating placeholder $DCP_ISSUER_KEY_OUT file..."
    printf "# DCP-Issuer Private Key\n# Convert from %s using OpenSSL\n# Command: openssl pkcs12 -in %s -nocerts -nodes -passin pass:%s -out %s\n" \
        "$DCP_ISSUER_KEYSTORE" "$DCP_ISSUER_KEYSTORE" "$DCP_ISSUER_PASSWORD" "$DCP_ISSUER_KEY_OUT" > "$DCP_ISSUER_KEY_OUT"
fi
echo ""

echo "Exporting DCP-Issuer certificate to PEM format ($DCP_ISSUER_CERT_OUT)..."
if openssl pkcs12 -in "$DCP_ISSUER_KEYSTORE" -clcerts -nokeys -passin "pass:$DCP_ISSUER_PASSWORD" -out "$DCP_ISSUER_CERT_OUT" 2>/dev/null; then
    echo "Done."
else
    echo "WARNING: OpenSSL not found. Using keytool export..."
    if ! keytool -exportcert \
        -alias "$DCP_ISSUER_ALIAS" \
        -keystore "$DCP_ISSUER_KEYSTORE" \
        -storetype PKCS12 \
        -storepass "$DCP_ISSUER_PASSWORD" \
        -file "$DCP_ISSUER_CERT_OUT" \
        -rfc; then
        echo "ERROR: Failed to export DCP-Issuer certificate" >&2
        exit 1
    fi
    echo "Done."
fi
echo "DCP-Issuer certificate generation complete."

# ==================================================================
# SECTION 2: Generate & Sign EC Issuer Certificate (secp256r1 ECDSA)
# ==================================================================
echo ""
echo "=================================================================="
echo "2. Generating EC Issuer Certificate ($EC_GROUP_NAME ECDSA)..."
echo "=================================================================="

# Clean up old EC files
rm -f "$EC_KEYSTORE" "$EC_CSR" "$EC_SIGNED_CERT" "$EC_KEY_OUT" "$EC_CERT_OUT" 2>/dev/null || true

echo "Generating EC key pair for Issuer..."
if ! keytool -genkeypair \
    -alias "$EC_ISSUER_ALIAS" \
    -keyalg "$EC_KEY_ALG" \
    -groupname "$EC_GROUP_NAME" \
    -sigalg "$EC_SIG_ALG" \
    -validity "$EC_VALIDITY" \
    -keystore "$EC_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$EC_PASSWORD" \
    -keypass "$EC_PASSWORD" \
    -dname "$EC_DN" \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=$EC_SAN"; then
    echo "ERROR: Failed to generate EC key pair for Issuer" >&2
    exit 1
fi
echo "Done."

echo "Generating Certificate Signing Request for EC Issuer..."
if ! keytool -certreq \
    -alias "$EC_ISSUER_ALIAS" \
    -keystore "$EC_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$EC_PASSWORD" \
    -sigalg "$EC_SIG_ALG" \
    -file "$EC_CSR" \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=$EC_SAN"; then
    echo "ERROR: Failed to generate CSR for EC Issuer" >&2
    exit 1
fi
echo "Done."

echo "Signing EC Issuer certificate with Intermediate CA..."
if ! keytool -gencert \
    -alias "$INTERMEDIATE_ALIAS" \
    -keystore "$INTERMEDIATE_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$INTERMEDIATE_PASSWORD" \
    -infile "$EC_CSR" \
    -outfile "$EC_SIGNED_CERT" \
    -validity "$EC_VALIDITY" \
    -ext KeyUsage:critical=digitalSignature,keyEncipherment \
    -ext ExtendedKeyUsage=serverAuth,clientAuth \
    -ext "SAN=$EC_SAN" \
    -rfc; then
    echo "ERROR: Failed to sign EC certificate for Issuer" >&2
    exit 1
fi
echo "Done."

echo "Importing certificate chain for EC Issuer into $EC_KEYSTORE..."
echo "  - Importing Root CA..."
keytool -importcert \
    -alias "$ROOT_ALIAS" \
    -keystore "$EC_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$EC_PASSWORD" \
    -file "$ROOT_CERT_FILE" \
    -noprompt

echo "  - Importing Intermediate CA..."
keytool -importcert \
    -alias "$INTERMEDIATE_ALIAS" \
    -keystore "$EC_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$EC_PASSWORD" \
    -file "$INTERMEDIATE_CERT_FILE" \
    -noprompt

echo "  - Importing signed EC Issuer certificate..."
if ! keytool -importcert \
    -alias "$EC_ISSUER_ALIAS" \
    -keystore "$EC_KEYSTORE" \
    -storetype PKCS12 \
    -storepass "$EC_PASSWORD" \
    -file "$EC_SIGNED_CERT" \
    -noprompt; then
    echo "ERROR: Failed to import certificate chain for EC Issuer" >&2
    exit 1
fi
echo "Done."

echo "Exporting EC Issuer private key to PEM format ($EC_KEY_OUT)..."
if openssl pkcs12 -in "$EC_KEYSTORE" -nocerts -nodes -passin "pass:$EC_PASSWORD" -out "$EC_KEY_OUT" 2>/dev/null; then
    echo "Done."
else
    echo "WARNING: OpenSSL not found or failed. Using alternative method..."
    echo "You will need to manually convert $EC_KEYSTORE to $EC_KEY_OUT"
    echo "Command: openssl pkcs12 -in $EC_KEYSTORE -nocerts -nodes -passin pass:$EC_PASSWORD -out $EC_KEY_OUT"
    echo ""
    echo "Creating placeholder $EC_KEY_OUT file..."
    printf "# EC Issuer Private Key\n# Convert from %s using OpenSSL\n# Command: openssl pkcs12 -in %s -nocerts -nodes -passin pass:%s -out %s\n" \
        "$EC_KEYSTORE" "$EC_KEYSTORE" "$EC_PASSWORD" "$EC_KEY_OUT" > "$EC_KEY_OUT"
fi
echo ""

echo "Exporting EC Issuer certificate to PEM format ($EC_CERT_OUT)..."
if openssl pkcs12 -in "$EC_KEYSTORE" -clcerts -nokeys -passin "pass:$EC_PASSWORD" -out "$EC_CERT_OUT" 2>/dev/null; then
    echo "Done."
else
    echo "WARNING: OpenSSL not found. Using keytool export..."
    if ! keytool -exportcert \
        -alias "$EC_ISSUER_ALIAS" \
        -keystore "$EC_KEYSTORE" \
        -storetype PKCS12 \
        -storepass "$EC_PASSWORD" \
        -file "$EC_CERT_OUT" \
        -rfc; then
        echo "ERROR: Failed to export EC Issuer certificate" >&2
        exit 1
    fi
    echo "Done."
fi
echo "EC Issuer certificate generation complete."

echo ""
echo "=================================================================="
echo "Certificate generation completed successfully!"
echo "------------------------------------------------------------------"
echo "1. DCP-Issuer (RSA):"
echo "   - Private Key: $DCP_ISSUER_KEY_OUT"
echo "   - Certificate: $DCP_ISSUER_CERT_OUT (Signed by $INTERMEDIATE_ALIAS)"
echo "   - Keystore:    $DCP_ISSUER_KEYSTORE (PKCS12 with root & intermediate chain)"
echo ""
echo "2. EC Issuer (secp256r1 ECDSA):"
echo "   - Private Key: $EC_KEY_OUT"
echo "   - Certificate: $EC_CERT_OUT (Signed by $INTERMEDIATE_ALIAS)"
echo "   - Keystore:    $EC_KEYSTORE (PKCS12 with root & intermediate chain)"
echo "=================================================================="
echo ""