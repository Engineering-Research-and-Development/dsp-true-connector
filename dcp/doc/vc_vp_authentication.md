# Verifiable Credentials / Verifiable Presentations Authentication

## Overview

This implementation adds support for authenticating requests using Verifiable Credentials (VC) and Verifiable Presentations (VP) to the True Connector. This provides an alternative to the existing JWT bearer token authentication for protocol endpoints.

## Authentication Flow

The authentication system now supports three authentication methods, prioritized in the following order:

1. **VC/VP Authentication** (Highest priority) - Uses Verifiable Presentations
2. **JWT Authentication** (Fallback) - Uses DAPS tokens
3. **Basic Authentication** (Failsafe) - Uses username/password

## Components

### 1. VcVpAuthenticationToken
- **Location**: `connector/src/main/java/it/eng/connector/configuration/VcVpAuthenticationToken.java`
- **Purpose**: Spring Security Authentication token that carries a `PresentationResponseMessage`
- **Key Fields**:
  - `presentation`: The PresentationResponseMessage to be validated
  - `subject`: The DID or identifier extracted from the validated presentation
  - `authorities`: Granted authorities (ROLE_CONNECTOR)

### 2. VcVpAuthenticationProvider
- **Location**: `connector/src/main/java/it/eng/connector/configuration/VcVpAuthenticationProvider.java`
- **Purpose**: Validates Verifiable Presentations using the DCP module's `PresentationValidationService`
- **Validation Process**:
  - Checks if presentation is not null
  - Validates presentation using `PresentationValidationService`
  - Performs the following checks (via DCP module):
    - Profile homogeneity
    - Issuer trust
    - Credential expiration
    - Revocation status
    - Schema validation
  - Extracts holder DID from the presentation
  - Returns authenticated token if validation succeeds

### 3. VcVpAuthenticationFilter
- **Location**: `connector/src/main/java/it/eng/connector/configuration/VcVpAuthenticationFilter.java`
- **Purpose**: Extracts and parses Verifiable Presentations from the Authorization header
- **Supported Formats**:
  - Direct JSON (PresentationResponseMessage as JSON string)
  - Base64-encoded JSON
  - Future: JWT format (can be added)
- **Processing**:
  - Extracts Bearer token from Authorization header
  - Attempts to parse as PresentationResponseMessage
  - If successful, creates VcVpAuthenticationToken and delegates to VcVpAuthenticationProvider
  - If parsing fails, continues to next filter (allows fallback to JWT/Basic auth)

## Configuration

The new authentication is integrated into `WebSecurityConfig.java`:

```java
@Bean
AuthenticationManager authenticationManager() {
    // VcVpAuthenticationProvider is listed first to give priority to VC/VP authentication
    // If VP validation fails, it will fall back to JWT, then to username/password (DAO)
    return new ProviderManager(vcVpAuthenticationProvider, jwtAuthenticationProvider, daoAUthenticationProvider());
}
```

Filter chain order:
1. `DataspaceProtocolEndpointsAuthenticationFilter` - Handles protocol auth enable/disable
2. `VcVpAuthenticationFilter` - Processes VC/VP tokens
3. `JwtAuthenticationFilter` - Processes JWT tokens (fallback)
4. `BasicAuthenticationFilter` - Processes username/password (failsafe)

## Endpoints Protected

The following endpoints now support VC/VP authentication:

- `/connector/**` - Connector protocol endpoints
- `/negotiations/**` - Contract negotiation endpoints  
- `/catalog/**` - Catalog endpoints
- `/transfers/**` - Data transfer endpoints

**Note**: The `/api/**` endpoints continue to use the existing authentication (username/password or JWT with ROLE_ADMIN).

## Usage Examples

### Sending a Request with VC/VP

To authenticate using a Verifiable Presentation, send the presentation in the Authorization header:

#### Option 1: JWT Token with VP Claim (Recommended)
```http
POST /connector/something HTTP/1.1
Authorization: Bearer eyJraWQiOiJvOVdzdTN2SF9LTXM3VE5UMFA4UDlKTHRIMmJRZWUtbVRwNVBleUdDNHQ4IiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJkaWQ6d2ViOmxvY2FsaG9zdDo4MDgwIiwic3ViIjoiZGlkOndlYjpsb2NhbGhvc3Q6ODA4MCIsInZwIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIl0sInR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6W3t9XSwicHJvZmlsZUlkIjoiVkMxMV9TTDIwMjFfSldUIn0sImlhdCI6MTc2NTU0NzM1NywianRpIjoidXJuOnV1aWQ6ODc1YmM2YjEtNDg0ZC00NmU0LWEwZGYtYmMyYmFiNDM1OThhIn0...
```

The JWT token contains a `vp` claim with the Verifiable Presentation:
```json
{
  "iss": "did:web:localhost:8080",
  "sub": "did:web:localhost:8080",
  "vp": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiablePresentation"],
    "verifiableCredential": [...],
    "profileId": "VC11_SL2021_JWT"
  },
  "iat": 1765547357,
  "jti": "urn:uuid:875bc6b1-484d-46e4-a0df-bc2bab43598a"
}
```

#### Option 2: Direct JSON (for testing)
```http
POST /connector/something HTTP/1.1
Authorization: Bearer {"@context":["https://dspace.org/dcp/v0.8/"],"presentation":[{...VP data...}]}
```

#### Option 3: Base64-encoded JSON
```http
POST /connector/something HTTP/1.1
Authorization: Bearer eyJAY29udGV4dCI6WyJodHRwczovL2RzcGFjZS5vcmcvZGNwL3YwLjgvIl0sInByZXNlbnRhdGlvbiI6W3suLi5WUCB...
```

The PresentationResponseMessage should follow the DCP specification format:
```json
{
  "@context": ["https://dspace.org/dcp/v0.8/"],
  "presentation": [
    {
      "id": "presentation-id",
      "holderDid": "did:example:holder123",
      "credentialIds": ["credential-id-1", "credential-id-2"],
      "profileId": "VC11_SL2021_JWT",
      "credentials": [...],
      "proof": {...}
    }
  ]
}
```

### Fallback Behavior

If the Authorization header does not contain a valid VP:
- The system will attempt JWT validation (existing behavior)
- If JWT validation fails, it will fall back to Basic authentication
- This ensures backward compatibility with existing clients

## DCP Module Integration

The VC/VP authentication relies on the following services from the DCP module:

- `PresentationValidationService`: Core validation logic
- `IssuerTrustService`: Verifies credential issuers are trusted
- `SchemaRegistryService`: Validates credential schemas
- `RevocationService`: Checks credential revocation status
- `ProfileResolver`: Ensures profile homogeneity

## Configuration

### Enabling/Disabling VC/VP Authentication

VC/VP authentication can be enabled or disabled using the following property:
```properties
# Enable VC/VP authentication (default: false)
dcp.vp.enabled=true
```

When `dcp.vp.enabled=false`:
- VcVpAuthenticationFilter skips processing entirely
- VcVpAuthenticationProvider returns null (allows fallback to next provider)
- Request continues to JWT authentication → Basic authentication

When `dcp.vp.enabled=true`:
- VcVpAuthenticationFilter extracts VP from Authorization header
- VcVpAuthenticationProvider validates the VP
- If validation succeeds → authenticated with ROLE_CONNECTOR
- If validation fails → returns null and falls back to next provider

### Protocol Authentication Enable/Disable

The authentication can also be disabled/enabled using the existing property:
```properties
# Enable/disable all protocol endpoint authentication (default: true)
application.protocol.authentication.enabled=true
```

**Note**: If `application.protocol.authentication.enabled=false`, all protocol endpoint authentication is bypassed (including VC/VP, JWT, and Basic).

## Testing

To test the VC/VP authentication:

1. Create a valid PresentationResponseMessage with credentials
2. Serialize to JSON
3. Base64-encode (optional, for cleaner transmission)
4. Send in Authorization header as `Bearer {encoded-presentation}`
5. The system will validate and authenticate if credentials are valid

## Security Considerations

1. **Transport Security**: Always use HTTPS in production
2. **Presentation Validation**: All presentations are validated against:
   - Trusted issuers
   - Expiration dates
   - Revocation status
   - Schema compliance
3. **Fallback Security**: The failsafe basic authentication should use strong passwords
4. **Rate Limiting**: Consider implementing rate limiting for presentation validation

## Future Enhancements

Potential improvements for future versions:

1. Support for JWT-encoded Verifiable Presentations
2. Configurable required credential types per endpoint
3. Credential type-based authorization (beyond ROLE_CONNECTOR)
4. Caching of validated presentations
5. Integration with external DID resolvers
6. Support for Presentation Exchange protocol
7. Credential-specific permissions and roles

## Migration Guide

For existing deployments:

1. The new authentication is additive - existing JWT and Basic auth continue to work
2. No configuration changes required to maintain current behavior
3. To start using VC/VP:
   - Clients need to implement VP creation logic
   - Send presentations in the Authorization header
   - The system will automatically use VC/VP validation

## Troubleshooting

### Common Issues

1. **"Presentation validation failed"**
   - Check that all credentials in the presentation are valid
   - Verify issuers are in the trusted issuer list
   - Ensure credentials are not expired or revoked

2. **"Presentation is null"**
   - Verify the JSON format is correct
   - Check Base64 encoding if used
   - Ensure Content-Type is application/json

3. **Falls back to JWT authentication**
   - The token format doesn't match PresentationResponseMessage
   - This is expected behavior for JWT tokens

### Logging

Enable debug logging to see authentication flow:
```properties
logging.level.it.eng.connector.configuration.VcVpAuthenticationFilter=DEBUG
logging.level.it.eng.connector.configuration.VcVpAuthenticationProvider=DEBUG
logging.level.it.eng.dcp.holder.service.PresentationValidationServiceImpl=DEBUG
```

## Related Documentation

- DCP Module: `dcp/README.md`
- Verifiable Credentials: `doc/verifiable_credentials.md`
- Security Configuration: `doc/security.md`

