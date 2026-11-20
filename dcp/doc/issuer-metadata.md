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

Supported credentials are configured in `credential-metadata-configuration.properties`, which `CredentialMetadataConfigLoader` reads from the issuer's classpath (`dcp/dcp-issuer/src/main/resources`). It is not read from `application.properties`, nor from a file mounted at `/config`.

#### Basic Configuration

```properties
# Configure the issuer DID
dcp.connector-did=did:web:localhost:8090

# Configure supported profiles
dcp.supportedProfiles[0]=VC11_SL2021_JWT
dcp.supportedProfiles[1]=VC11_SL2021_JSONLD

# Add a credential
dcp.credentials.supported[0].id=550e8400-e29b-41d4-a716-446655440000
dcp.credentials.supported[0].credentialType=MembershipCredential
dcp.credentials.supported[0].credentialSchema=https://example.com/schemas/membership-credential.json
dcp.credentials.supported[0].offerReason=issuance
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

The published IssuerMetadata schema requires five properties on every `credentialsSupported` entry: `credentialType`, `credentialSchema`, `offerReason`, `bindingMethods` and `profile`. The issuer checks every configured entry at startup and refuses to start if one misses `id`, `credentialType`, `credentialSchema` or `offerReason`, or if two entries share an `id`. `bindingMethods` and `profile` have defaults.

#### Required Fields
- **credentialType**: The type of credential (e.g., "MembershipCredential", "CompanyCredential")
- **credentialSchema**: URL to the JSON schema for this credential type
- **offerReason**: Reason for offering (e.g., "issuance", "reissue", "renewal")
- **id**: Stable, unique identifier that holders use to request the credential. The issuer refuses to start if an entry has no `id`, or if two entries share one.

#### Fields with defaults
- **bindingMethods**: Array of supported DID methods (defaults to ["did:web"])
- **profile**: Credential profile format (defaults to the first supported profile, see below). Configure it explicitly, so the advertised profile matches the issued one.

#### Optional Fields
- **type**: Type of the credential object (defaults to "CredentialObject")
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

- **vc11-sl2021/jwt**: VC 1.1 JWT with StatusList2021
- **vc20-bssl/jwt**: VC 2.0 JWT with BitstringStatusList

Set the profile per credential: `dcp.credentials.supported[0].profile=vc11-sl2021/jwt`. This is the recommended way, and every shipped configuration does it.

If an entry has no profile, the metadata advertises the first value of the issuer property `dcp.supportedProfiles` (from `application.properties`; the same key in `credential-metadata-configuration.properties` is ignored). That value is emitted as written, so it must be a profile alias such as `vc20-bssl/jwt`, not an enum name such as `VC20_BSSL_JWT`. If that property is not set either, the metadata advertises `vc20-bssl/jwt`. Issuance of an entry without a profile always uses `vc20-bssl/jwt`, so configure the profile explicitly to keep the two consistent.

## Default Behavior

If no credentials are configured in `dcp.credentials.supported`, the endpoint returns the metadata with an empty `"credentialsSupported": []`. The schema requires the property and allows an empty array. No credential can be requested from such an issuer.

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

**Important:** If you were using the previous hardcoded implementation, configure the credentials you issue. Without any, the metadata lists none.

### Migration Steps:

1. **ACTION REQUIRED**: Add your credentials to `dcp.credentials.supported` in your properties file, each with `id`, `credentialType`, `credentialSchema` and `offerReason`
2. **Recommended**: Set `dcp.connector-did` to your actual issuer DID
3. **Test**: Verify the `/issuer/metadata` endpoint returns your configured credentials

### Minimal Migration Example

Add this to `credential-metadata-configuration.properties`:

```properties
# Required: Configure issuer DID
dcp.connector-did=did:web:your-domain.com

# Configure each credential you issue
dcp.credentials.supported[0].id=550e8400-e29b-41d4-a716-446655440000
dcp.credentials.supported[0].credentialType=MembershipCredential
dcp.credentials.supported[0].credentialSchema=https://your-domain.com/schemas/membership.json
dcp.credentials.supported[0].offerReason=issuance
dcp.credentials.supported[0].profile=vc11-sl2021/jwt
```

If an entry misses `id`, `credentialType`, `credentialSchema` or `offerReason`, or repeats another entry's `id`, the issuer fails at startup with a message that names the entry (for example `dcp.credentials.supported[2]`) and the problem.

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

