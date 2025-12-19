@echo off
REM ==================================================================
REM Generate DCP-Issuer Certificate Script
REM Assumes root and intermediate CA already exist in current directory
REM ==================================================================

setlocal enabledelayedexpansion

REM Configuration
set ROOT_ALIAS=dsp-root-ca
set ROOT_KEYSTORE=dsp-root-ca.p12
set ROOT_PASSWORD=password
set INTERMEDIATE_ALIAS=dsp-intermediate-ca
set INTERMEDIATE_KEYSTORE=dsp-intermediate-ca.p12
set INTERMEDIATE_PASSWORD=password
set SERVER_PASSWORD=password
set KEY_ALG=RSA
set KEY_SIZE=2048
set SERVER_VALIDITY=365
set SAN_DCP_ISSUER=DNS:localhost,DNS:dcp-issuer,IP:127.0.0.1
set DCP_ISSUER_NAME=dcp-issuer
set DCP_ISSUER_DN=CN=dcp-issuer, OU=Issuer, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS
set DCP_ISSUER_KEYSTORE=dcp-issuer-temp.p12
set DCP_ISSUER_ALIAS=dcp-issuer

REM Clean up old files
del dcp-issuer-temp.p12 >nul 2>&1
del dcp-issuer.csr >nul 2>&1
del dcp-issuer-signed.crt >nul 2>&1
del dcp-issuer.key >nul 2>&1
del dcp-issuer.crt >nul 2>&1

echo Generating key pair for DCP-Issuer...
keytool -genkeypair ^
    -alias %DCP_ISSUER_ALIAS% ^
    -keyalg %KEY_ALG% ^
    -keysize %KEY_SIZE% ^
    -dname "%DCP_ISSUER_DN%" ^
    -validity %SERVER_VALIDITY% ^
    -keystore %DCP_ISSUER_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -keypass %SERVER_PASSWORD% ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%SAN_DCP_ISSUER%"
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to generate key pair for DCP-Issuer
    exit /b 1
)
echo Done.

echo Generating Certificate Signing Request for DCP-Issuer...
keytool -certreq ^
    -alias %DCP_ISSUER_ALIAS% ^
    -keystore %DCP_ISSUER_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file dcp-issuer.csr ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%SAN_DCP_ISSUER%"
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to generate CSR for DCP-Issuer
    exit /b 1
)
echo Done.

echo Signing DCP-Issuer certificate with Intermediate CA...
keytool -gencert ^
    -alias %INTERMEDIATE_ALIAS% ^
    -keystore %INTERMEDIATE_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %INTERMEDIATE_PASSWORD% ^
    -infile dcp-issuer.csr ^
    -outfile dcp-issuer-signed.crt ^
    -validity %SERVER_VALIDITY% ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%SAN_DCP_ISSUER%" ^
    -rfc
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to sign certificate for DCP-Issuer
    exit /b 1
)
echo Done.

echo Importing certificate chain for DCP-Issuer...
echo   - Importing Root CA...
keytool -importcert ^
    -alias %ROOT_ALIAS% ^
    -keystore %DCP_ISSUER_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file root-ca.crt ^
    -noprompt
echo   - Importing Intermediate CA...
keytool -importcert ^
    -alias %INTERMEDIATE_ALIAS% ^
    -keystore %DCP_ISSUER_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file intermediate-ca.crt ^
    -noprompt
echo   - Importing signed DCP-Issuer certificate...
keytool -importcert ^
    -alias %DCP_ISSUER_ALIAS% ^
    -keystore %DCP_ISSUER_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file dcp-issuer-signed.crt ^
    -noprompt
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to import certificate chain for DCP-Issuer
    exit /b 1
)
echo Done.

echo Exporting DCP-Issuer private key to PEM format (dcp-issuer.key)...
openssl pkcs12 -in %DCP_ISSUER_KEYSTORE% -nocerts -nodes -passin pass:%SERVER_PASSWORD% -out dcp-issuer.key 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo WARNING: OpenSSL not found. Using alternative method...
    echo You will need to manually convert dcp-issuer-temp.p12 to dcp-issuer.key
    echo Command: openssl pkcs12 -in dcp-issuer-temp.p12 -nocerts -nodes -passin pass:%SERVER_PASSWORD% -out dcp-issuer.key
    echo.
    echo Creating placeholder dcp-issuer.key file...
    echo # DCP-Issuer Private Key > dcp-issuer.key
    echo # Convert from dcp-issuer-temp.p12 using OpenSSL >> dcp-issuer.key
    echo # Command: openssl pkcs12 -in dcp-issuer-temp.p12 -nocerts -nodes -passin pass:%SERVER_PASSWORD% -out dcp-issuer.key >> dcp-issuer.key
) else (
    echo Done.
)
echo.
echo Exporting DCP-Issuer certificate to PEM format (dcp-issuer.crt)...
openssl pkcs12 -in %DCP_ISSUER_KEYSTORE% -clcerts -nokeys -passin pass:%SERVER_PASSWORD% -out dcp-issuer.crt 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo WARNING: OpenSSL not found. Using keytool export...
    keytool -exportcert ^
        -alias %DCP_ISSUER_ALIAS% ^
        -keystore %DCP_ISSUER_KEYSTORE% ^
        -storetype PKCS12 ^
        -storepass %SERVER_PASSWORD% ^
        -file dcp-issuer.crt ^
        -rfc
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: Failed to export DCP-Issuer certificate
        exit /b 1
    )
) else (
    echo Done.
)
echo.
echo DCP-Issuer certificate files generated:
echo   - dcp-issuer.key (Private key in PEM format)
echo   - dcp-issuer.crt (Certificate in PEM format, signed by Intermediate CA)
echo   - SAN: %SAN_DCP_ISSUER%
echo   - dcp-issuer-temp.p12 (Temporary PKCS12 keystore, can be deleted)
echo.
