@echo off
REM ==================================================================
REM Certificate Renewal Script for DSP True Connector
REM Renews or generates server certificates (connector-a, connector-b, minio)
REM without regenerating Root CA and Intermediate CA
REM ==================================================================

setlocal enabledelayedexpansion

REM ==================================================================
REM CONFIGURATION - Edit these values as needed
REM ==================================================================

REM Intermediate CA Configuration (must exist)
set INTERMEDIATE_ALIAS=dsp-intermediate-ca
set INTERMEDIATE_KEYSTORE=dsp-intermediate-ca.p12
set INTERMEDIATE_PASSWORD=password

REM Server Certificate Configuration
set SERVER_VALIDITY=365
set SERVER_PASSWORD=password

REM Subject Alternative Names (SAN) - Edit these lists as needed for each service
set SAN_CONNECTOR_A=DNS:localhost,DNS:connector-a,IP:127.0.0.1
set SAN_CONNECTOR_B=DNS:localhost,DNS:connector-b,IP:127.0.0.1
set SAN_MINIO=DNS:localhost,DNS:minio,IP:127.0.0.1

REM Key Algorithm and Size
set KEY_ALG=RSA
set KEY_SIZE=2048

REM ==================================================================
REM MENU
REM ==================================================================

:MENU
echo.
echo ==================================================================
echo DSP True Connector - Certificate Renewal Script
echo ==================================================================
echo.
echo This script allows you to renew individual server certificates
echo without regenerating the Root CA and Intermediate CA.
echo.
echo PREREQUISITES:
echo   - dsp-intermediate-ca.p12 must exist
echo   - intermediate-ca.crt must exist
echo   - root-ca.crt must exist (for certificate chain)
echo.
echo ==================================================================
echo.
echo Select certificate(s) to renew:
echo.
echo   1. Renew connector-a certificate
echo   2. Renew connector-b certificate
echo   3. Renew minio certificate (PKCS12 + PEM)
echo   4. Renew ui-a certificate (PEM + fullchain for nginx)
echo   5. Renew ui-b certificate (PEM + fullchain for nginx)
echo   6. Renew ALL server certificates
echo   7. Exit
echo.
echo ==================================================================

set /p choice="Enter your choice (1-7): "

if "%choice%"=="1" goto RENEW_CONNECTOR_A
if "%choice%"=="2" goto RENEW_CONNECTOR_B
if "%choice%"=="3" goto RENEW_MINIO
if "%choice%"=="4" goto RENEW_UI_A
if "%choice%"=="5" goto RENEW_UI_B
if "%choice%"=="6" goto RENEW_ALL
if "%choice%"=="7" goto END

echo Invalid choice. Please try again.
goto MENU

REM ==================================================================
REM PREREQUISITE CHECK
REM ==================================================================

:CHECK_PREREQUISITES
echo.
echo Checking prerequisites...
echo.

if not exist %INTERMEDIATE_KEYSTORE% (
    echo ERROR: %INTERMEDIATE_KEYSTORE% not found!
    echo Please run generate-certificates.cmd first to create the CA hierarchy.
    pause
    goto END
)

if not exist intermediate-ca.crt (
    echo WARNING: intermediate-ca.crt not found.
    echo Exporting from keystore...
    keytool -exportcert ^
        -alias %INTERMEDIATE_ALIAS% ^
        -keystore %INTERMEDIATE_KEYSTORE% ^
        -storetype PKCS12 ^
        -storepass %INTERMEDIATE_PASSWORD% ^
        -file intermediate-ca.crt ^
        -rfc
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: Failed to export Intermediate CA certificate
        pause
        goto END
    )
)

if not exist root-ca.crt (
    echo WARNING: root-ca.crt not found.
    echo Checking if Root CA is in Intermediate CA keystore...
    keytool -exportcert ^
        -alias dsp-root-ca ^
        -keystore %INTERMEDIATE_KEYSTORE% ^
        -storetype PKCS12 ^
        -storepass %INTERMEDIATE_PASSWORD% ^
        -file root-ca.crt ^
        -rfc >nul 2>&1
    if %ERRORLEVEL% NEQ 0 (
        echo WARNING: root-ca.crt not available. Certificate chain may be incomplete.
        echo This is OK if you only need the server certificate.
    ) else (
        echo Root CA certificate exported successfully.
    )
)

echo Prerequisites check complete.
echo.
goto :EOF

REM ==================================================================
REM RENEW CONNECTOR-A
REM ==================================================================

:RENEW_CONNECTOR_A
call :CHECK_PREREQUISITES
echo.
echo ==================================================================
echo Renewing connector-a certificate
echo ==================================================================
echo.
call :RenewServerCert connector-a "CN=connector-a, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "%SAN_CONNECTOR_A%"
pause
goto MENU

REM ==================================================================
REM RENEW CONNECTOR-B
REM ==================================================================

:RENEW_CONNECTOR_B
call :CHECK_PREREQUISITES
echo.
echo ==================================================================
echo Renewing connector-b certificate
echo ==================================================================
echo.
call :RenewServerCert connector-b "CN=connector-b, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "%SAN_CONNECTOR_B%"
pause
goto MENU

REM ==================================================================
REM RENEW MINIO
REM ==================================================================

:RENEW_MINIO
call :CHECK_PREREQUISITES
echo.
echo ==================================================================
echo Renewing MinIO certificate (PKCS12 + PEM format)
echo ==================================================================
echo.

set MINIO_NAME=minio
set MINIO_DN=CN=minio, OU=Storage, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS
set MINIO_KEYSTORE=minio-temp.p12
set MINIO_ALIAS=minio

REM Delete old minio certificates
echo Cleaning up old MinIO certificate files...
if exist %MINIO_KEYSTORE% del %MINIO_KEYSTORE%
if exist minio.csr del minio.csr
if exist minio-signed.crt del minio-signed.crt
if exist private.key del private.key
if exist public.crt del public.crt
echo Done.
echo.

REM Generate MinIO key pair
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
    pause
    goto MENU
)
echo Done.
echo.

REM Generate CSR
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
    pause
    goto MENU
)
echo Done.
echo.

REM Sign with Intermediate CA
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
    pause
    goto MENU
)
echo Done.
echo.

REM Import certificate chain
echo Importing certificate chain for MinIO...
echo   - Importing Root CA...
keytool -importcert ^
    -alias dsp-root-ca ^
    -keystore %MINIO_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file root-ca.crt ^
    -noprompt >nul 2>&1

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
    pause
    goto MENU
)
echo Done.
echo.

REM Export to PEM format
echo Exporting MinIO private key to PEM format (private.key)...
openssl pkcs12 -in %MINIO_KEYSTORE% -nocerts -nodes -passin pass:%SERVER_PASSWORD% -out private.key 2>nul

if %ERRORLEVEL% NEQ 0 (
    echo WARNING: OpenSSL not found. Cannot convert to PEM format automatically.
    echo Please convert manually using:
    echo   openssl pkcs12 -in minio-temp.p12 -nocerts -nodes -passin pass:%SERVER_PASSWORD% -out private.key
    echo   openssl pkcs12 -in minio-temp.p12 -clcerts -nokeys -passin pass:%SERVER_PASSWORD% -out public.crt
    echo.
    echo Alternatively, install OpenSSL and run this script again.
) else (
    echo Done.
    echo.

    echo Exporting MinIO certificate to PEM format (public.crt)...
    openssl pkcs12 -in %MINIO_KEYSTORE% -clcerts -nokeys -passin pass:%SERVER_PASSWORD% -out public.crt 2>nul

    if %ERRORLEVEL% NEQ 0 (
        echo WARNING: Failed to export certificate to PEM
        echo Using keytool fallback...
        keytool -exportcert ^
            -alias %MINIO_ALIAS% ^
            -keystore %MINIO_KEYSTORE% ^
            -storetype PKCS12 ^
            -storepass %SERVER_PASSWORD% ^
            -file public.crt ^
            -rfc
    )
    echo Done.
)
echo.

REM Cleanup temporary files
echo Cleaning up temporary files...
del minio.csr >nul 2>&1
del minio-signed.crt >nul 2>&1
echo Done.
echo.

echo MinIO certificate renewed successfully!
echo   - minio-temp.p12 (PKCS12 format)
echo   - private.key (PEM format, for MinIO)
echo   - public.crt (PEM format, for MinIO)
echo   - SANs: %SAN_MINIO%
echo.

pause
goto MENU

REM ==================================================================
REM RENEW UI-A
REM ==================================================================

:RENEW_UI_A
call :CHECK_PREREQUISITES
echo.
echo ==================================================================
echo Renewing ui-a certificate (PEM + fullchain for nginx)
echo ==================================================================
echo.
call :RenewUICert ui-a "CN=ui-a, OU=UI, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "%SAN_UI_A%"
pause
goto MENU

REM ==================================================================
REM RENEW UI-B
REM ==================================================================

:RENEW_UI_B
call :CHECK_PREREQUISITES
echo.
echo ==================================================================
echo Renewing ui-b certificate (PEM + fullchain for nginx)
echo ==================================================================
echo.

call :RenewUICert ui-b "CN=ui-b, OU=UI, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "%SAN_UI_B%"

pause
goto MENU

REM ==================================================================
REM RENEW ALL
REM ==================================================================

:RENEW_ALL
call :CHECK_PREREQUISITES
echo.
echo ==================================================================
echo Renewing ALL server certificates
echo ==================================================================
echo.

echo.
echo ------------------------------------------------------------------
echo Renewing connector-a
echo ------------------------------------------------------------------
call :RenewServerCert connector-a "CN=connector-a, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "%SAN_CONNECTOR_A%"

echo.
echo ------------------------------------------------------------------
echo Renewing connector-b
echo ------------------------------------------------------------------
call :RenewServerCert connector-b "CN=connector-b, OU=Connectors, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "%SAN_CONNECTOR_B%"

echo.
echo ------------------------------------------------------------------
echo Renewing MinIO
echo ------------------------------------------------------------------
REM Renew MinIO using the same logic as RENEW_MINIO
set MINIO_NAME=minio
set MINIO_DN=CN=minio, OU=Storage, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS
set MINIO_KEYSTORE=minio-temp.p12
set MINIO_ALIAS=minio

if exist %MINIO_KEYSTORE% del %MINIO_KEYSTORE%
if exist minio.csr del minio.csr
if exist minio-signed.crt del minio-signed.crt
if exist private.key del private.key
if exist public.crt del public.crt

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
    -ext "SAN=%SAN_MINIO%" >nul

keytool -certreq ^
    -alias %MINIO_ALIAS% ^
    -keystore %MINIO_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file minio.csr ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%SAN_MINIO%" >nul

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
    -rfc >nul

keytool -importcert -alias dsp-root-ca -keystore %MINIO_KEYSTORE% -storetype PKCS12 -storepass %SERVER_PASSWORD% -file root-ca.crt -noprompt >nul 2>&1
keytool -importcert -alias %INTERMEDIATE_ALIAS% -keystore %MINIO_KEYSTORE% -storetype PKCS12 -storepass %SERVER_PASSWORD% -file intermediate-ca.crt -noprompt >nul
keytool -importcert -alias %MINIO_ALIAS% -keystore %MINIO_KEYSTORE% -storetype PKCS12 -storepass %SERVER_PASSWORD% -file minio-signed.crt -noprompt >nul

openssl pkcs12 -in %MINIO_KEYSTORE% -nocerts -nodes -passin pass:%SERVER_PASSWORD% -out private.key 2>nul
openssl pkcs12 -in %MINIO_KEYSTORE% -clcerts -nokeys -passin pass:%SERVER_PASSWORD% -out public.crt 2>nul

del minio.csr >nul 2>&1
del minio-signed.crt >nul 2>&1

echo MinIO certificate renewed successfully!

echo.
echo ------------------------------------------------------------------
echo Renewing ui-a
echo ------------------------------------------------------------------
call :RenewUICert ui-a "CN=ui-a, OU=UI, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "%SAN_UI_A%"
echo.
echo ------------------------------------------------------------------
echo Renewing ui-b
echo ------------------------------------------------------------------
call :RenewUICert ui-b "CN=ui-b, OU=UI, O=DSP True Connector, L=Belgrade, ST=Serbia, C=RS" "%SAN_UI_B%"

echo.
echo ==================================================================
echo ALL certificates renewed successfully!
echo ==================================================================
echo.
pause
goto MENU

REM ==================================================================
REM SUBROUTINE: Renew Server Certificate
REM Parameters: %1=server name, %2=DN, %3=SAN list
REM ==================================================================
:RenewServerCert
setlocal
set SERVER_NAME=%~1
set SERVER_DN=%~2
set SERVER_SAN=%~3
set SERVER_KEYSTORE=%SERVER_NAME%.p12
set SERVER_ALIAS=%SERVER_NAME%

echo.
echo Renewing server certificate: %SERVER_NAME%
echo.

REM Backup old certificate if it exists
if exist %SERVER_KEYSTORE% (
    echo Backing up old certificate...
    set TIMESTAMP=%DATE:~-4%%DATE:~-10,2%%DATE:~-7,2%_%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%
    set TIMESTAMP=!TIMESTAMP: =0!
    copy %SERVER_KEYSTORE% %SERVER_KEYSTORE%.backup_!TIMESTAMP! >nul
    echo Old certificate backed up to: %SERVER_KEYSTORE%.backup_!TIMESTAMP!
    echo.
)

REM Delete old certificate files
echo Cleaning up old certificate files...
if exist %SERVER_KEYSTORE% del %SERVER_KEYSTORE%
if exist %SERVER_NAME%.csr del %SERVER_NAME%.csr
if exist %SERVER_NAME%.crt del %SERVER_NAME%.crt
echo Done.
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
    endlocal
    goto :EOF
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
    endlocal
    goto :EOF
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
    endlocal
    goto :EOF
)
echo Done.
echo.

REM Import certificate chain
echo Importing certificate chain for %SERVER_NAME%...

REM First import Root CA (if available)
echo   - Importing Root CA...
keytool -importcert ^
    -alias dsp-root-ca ^
    -keystore %SERVER_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file root-ca.crt ^
    -noprompt >nul 2>&1

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
    endlocal
    goto :EOF
)
echo Done.
echo.

REM Cleanup temporary files
echo Cleaning up temporary files...
del %SERVER_NAME%.csr
del %SERVER_NAME%.crt
echo Done.
echo.

echo Server certificate %SERVER_NAME% renewed successfully!
echo   - Keystore: %SERVER_KEYSTORE%
echo   - Alias: %SERVER_ALIAS%
echo   - SAN: %SERVER_SAN%
echo   - Valid for: %SERVER_VALIDITY% days
echo.

REM Verify the new certificate
echo Verifying certificate...
keytool -list -v -keystore %SERVER_KEYSTORE% -storepass %SERVER_PASSWORD% -storetype PKCS12 | findstr "Valid Alias Owner DNS"
echo.

endlocal
goto :EOF

REM ==================================================================
REM SUBROUTINE: Renew UI Certificate
REM Parameters: %1=ui name, %2=DN, %3=SAN list
REM ==================================================================
:RenewUICert
setlocal
set UI_NAME=%~1
set UI_DN=%~2
set UI_SAN=%~3
set UI_KEYSTORE=%UI_NAME%-temp.p12
set UI_ALIAS=%UI_NAME%

echo.
echo Renewing %UI_NAME% certificate (PEM + fullchain for nginx)
echo.
REM Delete old UI certificate files
if exist %UI_KEYSTORE% del %UI_KEYSTORE%
if exist %UI_NAME%.csr del %UI_NAME%.csr
if exist %UI_NAME%-signed.crt del %UI_NAME%-signed.crt
if exist %UI_NAME%-cert.key del %UI_NAME%-cert.key
if exist %UI_NAME%-cert.crt del %UI_NAME%-cert.crt
if exist %UI_NAME%-fullchain.crt del %UI_NAME%-fullchain.crt
echo Done.
echo.
REM Generate UI key pair
keytool -genkeypair ^
    -alias %UI_ALIAS% ^
    -keyalg %KEY_ALG% ^
    -keysize %KEY_SIZE% ^
    -dname "%UI_DN%" ^
    -validity %SERVER_VALIDITY% ^
    -keystore %UI_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -keypass %SERVER_PASSWORD% ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%UI_SAN%"
echo Done.
echo.
REM Generate CSR
keytool -certreq ^
    -alias %UI_ALIAS% ^
    -keystore %UI_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file %UI_NAME%.csr ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%UI_SAN%"
echo Done.
echo.
REM Sign with Intermediate CA
keytool -gencert ^
    -alias %INTERMEDIATE_ALIAS% ^
    -keystore %INTERMEDIATE_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %INTERMEDIATE_PASSWORD% ^
    -infile %UI_NAME%.csr ^
    -outfile %UI_NAME%-signed.crt ^
    -validity %SERVER_VALIDITY% ^
    -ext KeyUsage:critical=digitalSignature,keyEncipherment ^
    -ext ExtendedKeyUsage=serverAuth,clientAuth ^
    -ext "SAN=%UI_SAN%" ^
    -rfc
echo Done.
echo.
REM Import certificate chain
echo Importing certificate chain for %UI_NAME%...
echo   - Importing Root CA...
keytool -importcert ^
    -alias dsp-root-ca ^
    -keystore %UI_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file root-ca.crt ^
    -noprompt >nul 2>&1
echo   - Importing Intermediate CA...
keytool -importcert ^
    -alias %INTERMEDIATE_ALIAS% ^
    -keystore %UI_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file intermediate-ca.crt ^
    -noprompt
echo   - Importing signed %UI_NAME% certificate...
keytool -importcert ^
    -alias %UI_ALIAS% ^
    -keystore %UI_KEYSTORE% ^
    -storetype PKCS12 ^
    -storepass %SERVER_PASSWORD% ^
    -file %UI_NAME%-signed.crt ^
    -noprompt
echo Done.
echo.
REM Export to PEM format
echo Exporting %UI_NAME% private key to PEM format (%UI_NAME%-cert.key)...
openssl pkcs12 -in %UI_KEYSTORE% -nocerts -nodes -passin pass:%SERVER_PASSWORD% -out %UI_NAME%-cert.key 2>nul
echo Exporting %UI_NAME% certificate to PEM format (%UI_NAME%-cert.crt)...
openssl pkcs12 -in %UI_KEYSTORE% -clcerts -nokeys -passin pass:%SERVER_PASSWORD% -out %UI_NAME%-cert.crt 2>nul
echo Creating fullchain certificate for %UI_NAME% (server cert + intermediate CA)...
if exist %UI_NAME%-cert.crt if exist intermediate-ca.crt (
    type %UI_NAME%-cert.crt intermediate-ca.crt > %UI_NAME%-fullchain.crt
    echo Created %UI_NAME%-fullchain.crt (server cert + intermediate CA)
) else (
    echo WARNING: Could not create %UI_NAME%-fullchain.crt (missing %UI_NAME%-cert.crt or intermediate-ca.crt)
)
echo.
REM Cleanup temporary files
del %UI_NAME%.csr >nul 2>&1
del %UI_NAME%-signed.crt >nul 2>&1
echo %UI_NAME% certificate renewed successfully!
echo   - %UI_KEYSTORE% (PKCS12 format)
echo   - %UI_NAME%-cert.key (PEM format, private key for nginx)
echo   - %UI_NAME%-cert.crt (PEM format, server certificate)
echo   - %UI_NAME%-fullchain.crt (PEM format, fullchain for nginx - USE THIS)
echo   - SANs: %UI_SAN%
endlocal
goto :EOF

REM ==================================================================
REM END
REM ==================================================================

:END
echo.
echo Exiting...
endlocal
exit /b 0

