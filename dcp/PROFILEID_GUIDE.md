# ProfileId Field Guide

## Overview

The `profileId` field is a **mandatory field** for both `VerifiableCredential` and `VerifiablePresentation` entities. It identifies the credential/presentation profile being used (e.g., `VC11_SL2021_JWT`, `VC11_SL2021_JSONLD`).

## Available Profiles

The system supports the following profiles:

- **`VC11_SL2021_JWT`** - JWT format with StatusList2021 revocation support
- **`VC11_SL2021_JSONLD`** - JSON-LD format with StatusList2021 revocation support

## How ProfileId is Set

### For Verifiable Credentials (Storage)

The `profileId` is **automatically determined** when credentials are received and stored via the `POST /dcp/credentials` endpoint. You do NOT need to provide it in the request.

**Determination Logic:**

The system uses `ProfileResolver` to determine the profile based on:
1. **Format** - `"jwt"` or `"json-ld"`
2. **Attributes** - Presence of `credentialStatus` field

**Resolution Rules:**
- Format `"jwt"` + has `credentialStatus` → `VC11_SL2021_JWT`
- Format `"json-ld"` + no `credentialStatus` → `VC11_SL2021_JSONLD`
- Otherwise → Default to `VC11_SL2021_JWT`

**Example:**

When you send a credential delivery message:
```json
{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialMessage",
  "issuerPid": "issuer-pid-12345",
  "holderPid": "did:example:holder123",
  "status": "ISSUED",
  "credentials": [
    {
      "credentialType": "MembershipCredential",
      "payload": "eyJraWQi...",  // JWT string
      "format": "jwt"             // ← Profile determined from this
    }
  ]
}
```

The system will:
1. Parse the `format` field → `"jwt"`
2. Check for `credentialStatus` in the JWT payload
3. If statusList exists → assign `profileId = "VC11_SL2021_JWT"`
4. Store the credential with the profileId

### For Verifiable Presentations (Query Response)

The `profileId` is **automatically set** when creating presentations via `POST /dcp/presentations/query`.

**Determination Logic:**

1. **Retrieve** credentials matching the query scope
2. **Group** credentials by their `profileId` field
3. **Create** one presentation per profile group
4. **Use** the profile from the credentials (or default to `VC11_SL2021_JWT` if null)

**Example:**

When you query for presentations:
```json
{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "PresentationQueryMessage",
  "scope": [
    "org.eclipse.dspace.dcp.vc.type:MembershipCredential"
  ]
}
```

The system will:
1. Fetch credentials of type `MembershipCredential`
2. Group them by `profileId` (e.g., all `VC11_SL2021_JWT` together)
3. Create presentations with matching `profileId`
4. Sign and return the presentations

## Configuration

### Supported Profiles Configuration

In your `application.properties`, you can configure which profiles are supported:

```properties
dcp.supported-profiles=VC11_SL2021_JWT,VC11_SL2021_JSONLD
```

This validates that incoming credentials use a supported profile.

## Troubleshooting

### Error: "profileId must not be null"

**Cause:** The validation fails when building a `VerifiableCredential` or `VerifiablePresentation` without a profileId.

**Solution:** This has been fixed in the latest code. The system now:
- Automatically determines profileId using `ProfileResolver` when storing credentials
- Uses a default profile (`VC11_SL2021_JWT`) when the resolver returns null
- Sets profileId to default when creating presentations for credentials with null profileId

### Credentials Stored Without ProfileId

If you have existing credentials in MongoDB without a `profileId`:

**Option 1: Update via MongoDB**
```javascript
db.verifiable_credentials.updateMany(
  { profileId: null },
  { $set: { profileId: "VC11_SL2021_JWT" } }
)
```

**Option 2: Re-import Credentials**
Delete and re-deliver the credentials - the system will now assign profileId automatically.

## Developer Notes

### ProfileResolver Interface

If you need custom profile resolution logic, implement the `ProfileResolver` interface:

```java
@Service
public class CustomProfileResolver implements ProfileResolver {
    @Override
    public ProfileId resolve(String format, Map<String, Object> attributes) {
        // Your custom logic here
        if ("jwt".equals(format) && attributes.containsKey("customField")) {
            return ProfileId.VC11_SL2021_JWT;
        }
        return null; // System will use default
    }
}
```

### Model Validation

Both models have `@NotNull` validation on `profileId`:

```java
// VerifiableCredential
@NotNull
private String profileId;

// VerifiablePresentation
@NotNull
private String profileId;
```

This ensures data consistency across the system.

## Summary

- **ProfileId is mandatory** for both credentials and presentations
- **You don't need to provide it** in API requests
- **The system automatically determines it** based on format and attributes
- **Default profile** is `VC11_SL2021_JWT` when resolution fails
- **Grouping by profile** ensures presentation homogeneity

