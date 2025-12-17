# Issuer Metadata Configuration

## Overview

The `/issuer/metadata` endpoint provides information about the credentials that the issuer supports. This metadata helps holders/wallets understand what credentials are available and how to request them.

**Important:** This endpoint requires credentials to be configured in `dcp.credentials.supported` properties. No default credentials are provided.

## Endpoint

**GET** `/issuer/metadata`

### Authentication
Requires a valid Bearer token in the Authorization header.

### Response
Returns an `IssuerMetadata` object containing:
- `issuer`: The DID of the issuer
- `credentialsSupported`: Array of credential objects describing available credentials

## Configuration

### Configuration-Based Metadata (Recommended)

You can configure supported credentials using application properties. This provides flexibility to manage credentials without code changes.

#### Basic Configuration

```properties
# Configure the issuer DID
dcp.connector-did=did:web:localhost:8090

# Configure supported profiles
dcp.supportedProfiles[0]=VC11_SL2021_JWT
dcp.supportedProfiles[1]=VC11_SL2021_JSONLD

# Add a credential
dcp.credentials.supported[0].credentialType=MembershipCredential
dcp.credentials.supported[0].credentialSchema=https://example.com/schemas/membership-credential.json
dcp.credentials.supported[0].profile=vc11-sl2021/jwt
```

#### Complete Configuration Example

```properties
# Credential with all fields
dcp.credentials.supported[0].id=550e8400-e29b-41d4-a716-446655440000
dcp.credentials.supported[0].type=CredentialObject
dcp.credentials.supported[0].credentialType=MembershipCredential
dcp.credentials.supported[0].offerReason=reissue
dcp.credentials.supported[0].credentialSchema=https://example.com/schemas/membership-credential.json
dcp.credentials.supported[0].bindingMethods[0]=did:web
dcp.credentials.supported[0].bindingMethods[1]=did:key
dcp.credentials.supported[0].profile=vc11-sl2021/jwt
```

### Configuration Fields

#### Required Fields
- **credentialType**: The type of credential (e.g., "MembershipCredential", "CompanyCredential")

#### Optional Fields
- **id**: Unique identifier for this credential (auto-generated UUID if not provided)
- **type**: Type of the credential object (defaults to "CredentialObject")
- **offerReason**: Reason for offering (e.g., "new", "reissue", "renewal")
- **credentialSchema**: URL to the JSON schema for this credential type
- **bindingMethods**: Array of supported DID methods (defaults to ["did:web"])
- **profile**: Credential profile format (defaults to first supported profile or "vc11-sl2021/jwt")
- **issuancePolicy**: Policy requirements for issuing this credential (see below)

### Issuance Policy Configuration

Issuance policies define requirements that holders must meet to receive a credential.

```properties
dcp.credentials.supported[0].issuancePolicy.id=Trust Framework Policy
dcp.credentials.supported[0].issuancePolicy.input_descriptors[0].id=descriptor-1
dcp.credentials.supported[0].issuancePolicy.input_descriptors[0].constraints.fields[0].path[0]=$.vc.type
dcp.credentials.supported[0].issuancePolicy.input_descriptors[0].constraints.fields[0].filter.type=string
dcp.credentials.supported[0].issuancePolicy.input_descriptors[0].constraints.fields[0].filter.pattern=^AttestationCredential$
```

### Profile Configuration

Profiles determine the format and cryptographic suite used for credentials:

- **vc11-sl2021/jwt**: JWT format with SL2021 proof suite
- **vc11-sl2021/jsonld**: JSON-LD format with SL2021 proof suite

The profile can be set:
1. Per credential: `dcp.credentials.supported[0].profile=vc11-sl2021/jwt`
2. Globally (used as default): `dcp.supportedProfiles[0]=VC11_SL2021_JWT`
3. Fallback: If not configured, defaults to "vc11-sl2021/jwt"

## Default Behavior

**Credentials must be configured** via `dcp.credentials.supported` properties. If no credentials are configured, the endpoint will return an HTTP 500 error with the message:

```
No credentials configured. Please configure at least one credential in dcp.credentials.supported properties
```

This ensures that issuers explicitly define which credentials they support.

## Example Response

```json
{
  "@context": [
    "https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"
  ],
  "type": "IssuerMetadata",
  "issuer": "did:web:localhost:8090",
  "credentialsSupported": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "type": "CredentialObject",
      "credentialType": "MembershipCredential",
      "credentialSchema": "https://example.com/schemas/membership-credential.json",
      "bindingMethods": [
        "did:web",
        "did:key"
      ],
      "profile": "vc11-sl2021/jwt"
    },
    {
      "id": "d5c77b0e-7f4e-4fd5-8c5f-28b5fc3f96d1",
      "type": "CredentialObject",
      "credentialType": "CompanyCredential",
      "offerReason": "reissue",
      "credentialSchema": "https://example.com/schemas/company-credential.json",
      "bindingMethods": [
        "did:web"
      ],
      "profile": "vc11-sl2021/jwt",
      "issuancePolicy": {
        "id": "Scalable trust example",
        "input_descriptors": [
          {
            "id": "pd-id",
            "constraints": {
              "fields": [
                {
                  "path": [
                    "$.vc.type"
                  ],
                  "filter": {
                    "type": "string",
                    "pattern": "^AttestationCredential$"
                  }
                }
              ]
            }
          }
        ]
      }
    }
  ]
}
```

## Implementation Details

### Key Components

1. **CredentialMetadataConfig**: Configuration properties class that binds to `dcp.credentials` properties
2. **CredentialMetadataService**: Service that builds IssuerMetadata from configuration or defaults
3. **IssuerService.getMetadata()**: Service method that delegates to CredentialMetadataService
4. **IssuerController.getMetadata()**: REST endpoint that returns the metadata with authentication

### Service Flow

```
Request → IssuerController.getMetadata()
    → Validate Bearer Token
    → IssuerService.getMetadata()
        → CredentialMetadataService.buildIssuerMetadata()
            → Read CredentialMetadataConfig
            → Build credential objects
            → Return IssuerMetadata
    → Return JSON response
```

## Testing

### Using curl

```bash
# Get a valid token first (through your authentication flow)
TOKEN="your-bearer-token"

# Call the metadata endpoint
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8090/issuer/metadata
```

### Expected Responses

- **200 OK**: Successfully retrieved metadata
- **401 Unauthorized**: Missing or invalid Bearer token
- **500 Internal Server Error**: Server error building metadata

## Migration from Hardcoded Values

**Important:** If you were using the previous hardcoded implementation, you now **must configure credentials**.

### Migration Steps:

1. **ACTION REQUIRED**: Add at least one credential to `dcp.credentials.supported` in your properties file
2. **Recommended**: Set `dcp.connector-did` to your actual issuer DID
3. **Test**: Verify the `/issuer/metadata` endpoint returns your configured credentials

### Minimal Migration Example

Add this to your `application.properties`:

```properties
# Required: Configure issuer DID
dcp.connector-did=did:web:your-domain.com

# Required: Configure at least one credential
dcp.credentials.supported[0].credentialType=MembershipCredential
dcp.credentials.supported[0].credentialSchema=https://your-domain.com/schemas/membership.json
dcp.credentials.supported[0].profile=vc11-sl2021/jwt
```

If credentials are not configured, the endpoint will return HTTP 500 with an error message.

## Best Practices

1. **Use UUIDs for credential IDs**: Ensures uniqueness across systems
2. **Define credential schemas**: Helps consumers understand credential structure
3. **Configure supported profiles**: Match your cryptographic capabilities
4. **Document issuance policies**: Make requirements clear to credential requesters
5. **Use environment-specific configuration**: Different credentials for dev/test/prod

## See Also

- [Credential Issuance Flow](./credential-issuance.md)
- [DCP Configuration Guide](./dcp-configuration.md)
- [Example Configuration](./credential-metadata-configuration.properties)

