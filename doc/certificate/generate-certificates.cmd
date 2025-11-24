@echo off
REM ==================================================================
REM Certificate Generation Script for DSP True Connector
REM Creates a complete PKI hierarchy:
REM   1. Root CA (self-signed)
REM   2. Intermediate CA (signed by Root CA)
REM   3. Server certificates (signed by Intermediate CA)
REM ==================================================================

setlocal enabledelayedexpansion

REM ==================================================================
REM CONFIGURATION - Edit these values as needed
REM ==================================================================

REM Root CA Configuration
set ROOT_ALIAS=dsp-root-ca
set ROOT_DNAME=CN=DSP Root CA, OU=Security, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS
set ROOT_VALIDITY=3650
set ROOT_KEYSTORE=dsp-root-ca.p12
set ROOT_PASSWORD=password

REM Intermediate CA Configuration
set INTERMEDIATE_ALIAS=dsp-intermediate-ca
set INTERMEDIATE_DNAME=CN=DSP Intermediate CA, OU=Security, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS
set INTERMEDIATE_VALIDITY=1825
set INTERMEDIATE_KEYSTORE=dsp-intermediate-ca.p12
set INTERMEDIATE_PASSWORD=password

REM Server Certificate Configuration
set SERVER_VALIDITY=365
set SERVER_PASSWORD=password

REM Subject Alternative Names (SAN) - Edit these lists as needed for each service
REM Each server should only have the SANs it actually needs for security best practices
set SAN_CONNECTOR_A=DNS:localhost,DNS:connector-a,IP:127.0.0.1
set SAN_CONNECTOR_B=DNS:localhost,DNS:connector-b,IP:127.0.0.1
set SAN_MINIO=DNS:localhost,DNS:minio,IP:127.0.0.1

REM Legacy: All SANs combined (for backward compatibility or development)
REM set SAN_LIST=DNS:localhost,DNS:connector-a,DNS:connector-b,DNS:minio,DNS:mongodb-a,DNS:mongodb-b,DNS:ui-a,DNS:ui-b,IP:127.0.0.1

REM Truststore Configuration
set TRUSTSTORE=dsp-truststore.p12
set TRUSTSTORE_PASSWORD=password

REM Key Algorithm and Size
set KEY_ALG=RSA
set KEY_SIZE=2048

REM ==================================================================
REM START CERTIFICATE GENERATION
REM ==================================================================

echo.
echo ==================================================================
echo DSP True Connector - Certificate Generation Script
echo ==================================================================
echo.
echo This script will generate:
echo   1. Root CA (self-signed)
echo   2. Intermediate CA (signed by Root CA)
echo   3. Server certificates for connector-a and connector-b
echo   4. Truststore with Intermediate CA certificate
echo.
echo Configuration:
echo   - connector-a SANs: %SAN_CONNECTOR_A%
echo   - connector-b SANs: %SAN_CONNECTOR_B%
echo   - MinIO SANs: %SAN_MINIO%
echo   - Key Algorithm: %KEY_ALG% %KEY_SIZE% bits
echo   - Root CA Validity: %ROOT_VALIDITY% days
echo   - Intermediate CA Validity: %INTERMEDIATE_VALIDITY% days
echo   - Server Cert Validity: %SERVER_VALIDITY% days
echo.
echo ==================================================================
echo.

pause

REM Clean up old files
echo Cleaning up old certificate files...
if exist %ROOT_KEYSTORE% del %ROOT_KEYSTORE%
if exist %INTERMEDIATE_KEYSTORE% del %INTERMEDIATE_KEYSTORE%
if exist connector-a.p12 del connector-a.p12
if exist connector-b.p12 del connector-b.p12
if exist minio-temp.p12 del minio-temp.p12
if exist private.key del private.key
if exist public.crt del public.crt
if exist %TRUSTSTORE% del %TRUSTSTORE%
if exist *.csr del *.csr
if exist *.crt del *.crt
if exist *.cer del *.cer
echo Done.
echo.

REM ==================================================================
REM STEP 1: Generate Root CA (Self-Signed)
REM ==================================================================

echo ==================================================================
echo STEP 1: Generating Root CA (Self-Signed)
echo ==================================================================
echo.

keytool -genkeypair ^
    -alias %ROOT_ALIAS% ^
    -keyalg %KEY_ALG% ^
    -keysize %KEY_SIZE% ^
    -dname "%ROOT_DNAME%" ^
    -validity %ROOT_VALIDITY% ^
    -keystore %ROOT_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %ROOT_PASSWORD% ^
    -keypass %ROOT_PASSWORD% ^
    -ext BasicConstraints:critical=ca:true ^
    -ext KeyUsage:critical=keyCertSign,cRLSign

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to generate Root CA
    exit /b 1
)

echo.
echo Root CA generated successfully!
echo   - Keystore: %ROOT_KEYSTORE%
echo   - Alias: %ROOT_ALIAS%
echo.

REM Export Root CA certificate
echo Exporting Root CA certificate...
keytool -exportcert ^
    -alias %ROOT_ALIAS% ^
    -keystore %ROOT_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %ROOT_PASSWORD% ^
    -file root-ca.crt ^
    -rfc

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to export Root CA certificate
    exit /b 1
)
echo Done.
echo.

REM ==================================================================
REM STEP 2: Generate Intermediate CA
REM ==================================================================

echo ==================================================================
echo STEP 2: Generating Intermediate CA
echo ==================================================================
echo.

REM Generate Intermediate CA key pair
echo Generating Intermediate CA key pair...
keytool -genkeypair ^
    -alias %INTERMEDIATE_ALIAS% ^
    -keyalg %KEY_ALG% ^
    -keysize %KEY_SIZE% ^
    -dname "%INTERMEDIATE_DNAME%" ^
    -validity %INTERMEDIATE_VALIDITY% ^
    -keystore %INTERMEDIATE_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %INTERMEDIATE_PASSWORD% ^
    -keypass %INTERMEDIATE_PASSWORD% ^
    -ext BasicConstraints:critical=ca:true,pathlen:0 ^
    -ext KeyUsage:critical=keyCertSign,cRLSign

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to generate Intermediate CA key pair
    exit /b 1
)
echo Done.
echo.

REM Generate CSR for Intermediate CA
echo Generating Certificate Signing Request for Intermediate CA...
keytool -certreq ^
    -alias %INTERMEDIATE_ALIAS% ^
    -keystore %INTERMEDIATE_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %INTERMEDIATE_PASSWORD% ^
    -file intermediate-ca.csr

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to generate Intermediate CA CSR
    exit /b 1
)
echo Done.
echo.

REM Sign Intermediate CA certificate with Root CA
echo Signing Intermediate CA certificate with Root CA...
keytool -gencert ^
    -alias %ROOT_ALIAS% ^
    -keystore %ROOT_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %ROOT_PASSWORD% ^
    -infile intermediate-ca.csr ^
    -outfile intermediate-ca.crt ^
    -validity %INTERMEDIATE_VALIDITY% ^
    -ext BasicConstraints:critical=ca:true,pathlen:0 ^
    -ext KeyUsage:critical=keyCertSign,cRLSign ^
    -rfc

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to sign Intermediate CA certificate
    exit /b 1
)
echo Done.
echo.

REM Import Root CA certificate into Intermediate CA keystore
echo Importing Root CA into Intermediate CA keystore...
keytool -importcert ^
    -alias %ROOT_ALIAS% ^
    -keystore %INTERMEDIATE_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %INTERMEDIATE_PASSWORD% ^
    -file root-ca.crt ^
    -noprompt

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to import Root CA into Intermediate keystore
    exit /b 1
)
echo Done.
echo.

REM Import signed Intermediate CA certificate
echo Importing signed Intermediate CA certificate...
keytool -importcert ^
    -alias %INTERMEDIATE_ALIAS% ^
    -keystore %INTERMEDIATE_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %INTERMEDIATE_PASSWORD% ^
    -file intermediate-ca.crt ^
    -noprompt

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to import Intermediate CA certificate
    exit /b 1
)
echo Done.
echo.

echo Intermediate CA generated successfully!
echo   - Keystore: %INTERMEDIATE_KEYSTORE%
echo   - Alias: %INTERMEDIATE_ALIAS%
echo.

REM ==================================================================
REM STEP 3: Generate Server Certificates
REM ==================================================================

echo ==================================================================
echo STEP 3: Generating Server Certificates
echo ==================================================================
echo.

REM Function to generate server certificate
call :GenerateServerCert connector-a "CN=connector-a, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "%SAN_CONNECTOR_A%"
call :GenerateServerCert connector-b "CN=connector-b, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "%SAN_CONNECTOR_B%"

echo.
echo All server certificates generated successfully!
echo.

REM ==================================================================
REM STEP 4: Generate MinIO Certificate (PEM format)
REM ==================================================================

echo ==================================================================
echo STEP 4: Generating MinIO Certificate (PEM format)
echo ==================================================================
echo.

set MINIO_NAME=minio
set MINIO_DN=CN=minio, OU=Storage, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS
set MINIO_KEYSTORE=minio-temp.p12
set MINIO_ALIAS=minio

echo Generating key pair for MinIO...
keytool -genkeypair ^
    -alias %MINIO_ALIAS% ^
    -keyalg %KEY_ALG% ^
    -keysize %KEY_SIZE% ^
    -dname "%MINIO_DN%" ^
    -validity %SERVER_VALIDITY% ^
    -keystore %MINIO_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -keypass %SERVER_PASSWORD% ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%SAN_MINIO%"

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to generate key pair for MinIO
    exit /b 1
)
echo Done.
echo.

echo Generating Certificate Signing Request for MinIO...
keytool -certreq ^
    -alias %MINIO_ALIAS% ^
    -keystore %MINIO_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file minio.csr ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%SAN_MINIO%"

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to generate CSR for MinIO
    exit /b 1
)
echo Done.
echo.

echo Signing MinIO certificate with Intermediate CA...
keytool -gencert ^
    -alias %INTERMEDIATE_ALIAS% ^
    -keystore %INTERMEDIATE_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %INTERMEDIATE_PASSWORD% ^
    -infile minio.csr ^
    -outfile minio-signed.crt ^
    -validity %SERVER_VALIDITY% ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%SAN_MINIO%" ^
    -rfc

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to sign certificate for MinIO
    exit /b 1
)
echo Done.
echo.

echo Importing certificate chain for MinIO...
echo   - Importing Root CA...
keytool -importcert ^
    -alias %ROOT_ALIAS% ^
    -keystore %MINIO_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file root-ca.crt ^
    -noprompt

echo   - Importing Intermediate CA...
keytool -importcert ^
    -alias %INTERMEDIATE_ALIAS% ^
    -keystore %MINIO_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file intermediate-ca.crt ^
    -noprompt

echo   - Importing signed MinIO certificate...
keytool -importcert ^
    -alias %MINIO_ALIAS% ^
    -keystore %MINIO_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file minio-signed.crt ^
    -noprompt

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to import certificate chain for MinIO
    exit /b 1
)
echo Done.
echo.

echo Exporting MinIO private key to PEM format (private.key)...
REM Export to PKCS12 then convert to PEM using OpenSSL
REM Note: If OpenSSL is not available, the .p12 file can be manually converted
openssl pkcs12 -in %MINIO_KEYSTORE% -nocerts -nodes -passin pass:%SERVER_PASSWORD% -out private.key 2>nul

if %ERRORLEVEL% NEQ 0 (
    echo WARNING: OpenSSL not found. Using alternative method...
    echo You will need to manually convert minio-temp.p12 to private.key
    echo Command: openssl pkcs12 -in minio-temp.p12 -nocerts -nodes -passin pass:%SERVER_PASSWORD% -out private.key
    echo.
    echo Creating placeholder private.key file...
    echo # MinIO Private Key > private.key
    echo # Convert from minio-temp.p12 using OpenSSL >> private.key
    echo # Command: openssl pkcs12 -in minio-temp.p12 -nocerts -nodes -passin pass:%SERVER_PASSWORD% -out private.key >> private.key
) else (
    echo Done.
)
echo.

echo Exporting MinIO certificate to PEM format (public.crt)...
REM Export certificate chain (includes intermediate CA)
openssl pkcs12 -in %MINIO_KEYSTORE% -clcerts -nokeys -passin pass:%SERVER_PASSWORD% -out public.crt 2>nul

if %ERRORLEVEL% NEQ 0 (
    echo WARNING: OpenSSL not found. Using keytool export...
    keytool -exportcert ^
        -alias %MINIO_ALIAS% ^
        -keystore %MINIO_KEYSTORE% ^
        -storetype PKCS12 ^
        -storepass %SERVER_PASSWORD% ^
        -file public.crt ^
        -rfc

    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: Failed to export MinIO certificate
        exit /b 1
    )
) else (
    echo Done.
)
echo.

echo MinIO certificate files generated:
echo   - private.key (Private key in PEM format)
echo   - public.crt (Certificate in PEM format, signed by Intermediate CA)
echo   - SAN: %SAN_MINIO%
echo   - minio-temp.p12 (Temporary PKCS12 keystore, can be deleted)
echo.

REM ==================================================================
REM STEP 5: Create Truststore with Intermediate CA
REM ==================================================================

echo ==================================================================
echo STEP 5: Creating Truststore
echo ==================================================================
echo.

echo Creating truststore with Intermediate CA certificate...
keytool -importcert ^
    -trustcacerts ^
    -alias %INTERMEDIATE_ALIAS% ^
    -file intermediate-ca.crt ^
    -keystore %TRUSTSTORE% ^
    -storetype PKCS12 ^
    -storepass %TRUSTSTORE_PASSWORD% ^
    -noprompt

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to create truststore
    exit /b 1
)

echo.
echo Importing Root CA certificate into truststore (optional, for complete chain)...
keytool -importcert ^
    -trustcacerts ^
    -alias %ROOT_ALIAS% ^
    -file root-ca.crt ^
    -keystore %TRUSTSTORE% ^
    -storetype PKCS12 ^
    -storepass %TRUSTSTORE_PASSWORD% ^
    -noprompt

echo Done.
echo.

REM ==================================================================
REM STEP 6: Verification
REM ==================================================================

echo ==================================================================
echo STEP 6: Verifying Generated Certificates
echo ==================================================================
echo.

echo Root CA Keystore:
keytool -list -v -keystore %ROOT_KEYSTORE% -storepass %ROOT_PASSWORD% -storetype PKCS12 | findstr "Alias\|Owner\|Issuer\|Valid"
echo.

echo Intermediate CA Keystore:
keytool -list -v -keystore %INTERMEDIATE_KEYSTORE% -storepass %INTERMEDIATE_PASSWORD% -storetype PKCS12 | findstr "Alias\|Owner\|Issuer\|Valid"
echo.

echo Connector-A Keystore:
keytool -list -v -keystore connector-a.p12 -storepass %SERVER_PASSWORD% -storetype PKCS12 | findstr "Alias\|Owner\|Issuer\|Valid\|DNS"
echo.

echo Connector-B Keystore:
keytool -list -v -keystore connector-b.p12 -storepass %SERVER_PASSWORD% -storetype PKCS12 | findstr "Alias\|Owner\|Issuer\|Valid\|DNS"
echo.

echo MinIO Certificate Files:
echo   - private.key: Private key in PEM format
echo   - public.crt: Certificate in PEM format
if exist private.key (
    echo   private.key exists: YES
    findstr /C:"BEGIN" private.key
) else (
    echo   private.key exists: NO
)
if exist public.crt (
    echo   public.crt exists: YES
    findstr /C:"BEGIN CERTIFICATE" public.crt
) else (
    echo   public.crt exists: NO
)
echo.

echo Truststore:
keytool -list -v -keystore %TRUSTSTORE% -storepass %TRUSTSTORE_PASSWORD% -storetype PKCS12 | findstr "Alias\|Owner\|Issuer\|Valid"
echo.

REM ==================================================================
REM CLEANUP
REM ==================================================================

echo ==================================================================
echo Cleaning up temporary files...
echo ==================================================================
echo.

del *.csr
del root-ca.crt
del intermediate-ca.crt
del minio-signed.crt
REM Keep public.crt for MinIO
REM Keep private.key for MinIO
if exist connector-a.crt del connector-a.crt
if exist connector-b.crt del connector-b.crt
del *.cer

echo Done.
echo.

REM ==================================================================
REM SUMMARY
REM ==================================================================

echo ==================================================================
echo CERTIFICATE GENERATION COMPLETE!
echo ==================================================================
echo.
echo Generated files:
echo   1. %ROOT_KEYSTORE% - Root CA (keep secure, used for signing Intermediate CA)
echo   2. %INTERMEDIATE_KEYSTORE% - Intermediate CA (keep secure, used for signing server certs)
echo   3. connector-a.p12 - Server certificate for connector-a
echo   4. connector-b.p12 - Server certificate for connector-b
echo   5. private.key - MinIO private key in PEM format (for MinIO certs/private.key)
echo   6. public.crt - MinIO certificate in PEM format (for MinIO certs/public.crt)
echo   7. minio-temp.p12 - MinIO certificate in PKCS12 format (optional, can be deleted)
echo   8. %TRUSTSTORE% - Truststore with Intermediate CA (use for TLS validation)
echo.
echo Certificate Chain:
echo   Root CA --signs--^> Intermediate CA --signs--^> Server Certificates (including MinIO)
echo.
echo For TLS handshake:
echo   - Servers present: connector-a.p12, connector-b.p12, or MinIO PEM files
echo   - Clients trust: %TRUSTSTORE% (contains Intermediate CA)
echo.
echo For MinIO Docker setup:
echo   Copy to MinIO certs directory:
echo     - private.key --^> /root/.minio/certs/private.key
echo     - public.crt --^> /root/.minio/certs/public.crt
echo   Or mount as Docker volume:
echo     - ./private.key:/root/.minio/certs/private.key
echo     - ./public.crt:/root/.minio/certs/public.crt
echo.
echo Update your application.properties:
echo   spring.ssl.bundle.jks.connector.keystore.location=classpath:connector-a.p12 (or connector-b.p12)
echo   spring.ssl.bundle.jks.connector.keystore.password=%SERVER_PASSWORD%
echo   spring.ssl.bundle.jks.connector.truststore.location=classpath:%TRUSTSTORE%
echo   spring.ssl.bundle.jks.connector.truststore.password=%TRUSTSTORE_PASSWORD%
echo.
echo ==================================================================

goto :eof

REM ==================================================================
REM SUBROUTINE: Generate Server Certificate
REM Parameters: %1=server name, %2=DN, %3=SAN list
REM ==================================================================
:GenerateServerCert
setlocal
set SERVER_NAME=%~1
set SERVER_DN=%~2
set SERVER_SAN=%~3
set SERVER_KEYSTORE=%SERVER_NAME%.p12
set SERVER_ALIAS=%SERVER_NAME%

echo.
echo ------------------------------------------------------------------
echo Generating server certificate: %SERVER_NAME%
echo ------------------------------------------------------------------
echo.

REM Generate server key pair
echo Generating key pair for %SERVER_NAME%...
keytool -genkeypair ^
    -alias %SERVER_ALIAS% ^
    -keyalg %KEY_ALG% ^
    -keysize %KEY_SIZE% ^
    -dname "%SERVER_DN%" ^
    -validity %SERVER_VALIDITY% ^
    -keystore %SERVER_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -keypass %SERVER_PASSWORD% ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%SERVER_SAN%"

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to generate key pair for %SERVER_NAME%
    exit /b 1
)
echo Done.
echo.

REM Generate CSR
echo Generating Certificate Signing Request for %SERVER_NAME%...
keytool -certreq ^
    -alias %SERVER_ALIAS% ^
    -keystore %SERVER_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file %SERVER_NAME%.csr ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%SERVER_SAN%"

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to generate CSR for %SERVER_NAME%
    exit /b 1
)
echo Done.
echo.

REM Sign with Intermediate CA
echo Signing %SERVER_NAME% certificate with Intermediate CA...
keytool -gencert ^
    -alias %INTERMEDIATE_ALIAS% ^
    -keystore %INTERMEDIATE_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %INTERMEDIATE_PASSWORD% ^
    -infile %SERVER_NAME%.csr ^
    -outfile %SERVER_NAME%.crt ^
    -validity %SERVER_VALIDITY% ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%SERVER_SAN%" ^
    -rfc

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to sign certificate for %SERVER_NAME%
    exit /b 1
)
echo Done.
echo.

REM Import certificate chain (Root CA, Intermediate CA, Server Cert)
echo Importing certificate chain for %SERVER_NAME%...

REM First import Root CA
echo   - Importing Root CA...
keytool -importcert ^
    -alias %ROOT_ALIAS% ^
    -keystore %SERVER_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file root-ca.crt ^
    -noprompt

REM Then import Intermediate CA
echo   - Importing Intermediate CA...
keytool -importcert ^
    -alias %INTERMEDIATE_ALIAS% ^
    -keystore %SERVER_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file intermediate-ca.crt ^
    -noprompt

REM Finally import signed server certificate
echo   - Importing signed server certificate...
keytool -importcert ^
    -alias %SERVER_ALIAS% ^
    -keystore %SERVER_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file %SERVER_NAME%.crt ^
    -noprompt

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to import certificate chain for %SERVER_NAME%
    exit /b 1
)

echo Done.
echo.
echo Server certificate %SERVER_NAME% generated successfully!
echo   - Keystore: %SERVER_KEYSTORE%
echo   - Alias: %SERVER_ALIAS%
echo   - SAN: %SERVER_SAN%
echo.

endlocal
goto :eof

