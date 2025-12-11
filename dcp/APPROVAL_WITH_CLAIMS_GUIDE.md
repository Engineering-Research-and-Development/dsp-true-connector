# How to Pass Country Code and Custom Claims When Approving VCs

## Overview

When approving a credential request, you can now pass custom claims (like `country_code`, `role`, `department`, etc.) that will be included in the generated Verifiable Credentials.

## Quick Example

### Approve with Country Code = IT
```bash
POST /api/dcp/issuer/requests/{requestId}/approve
Content-Type: application/json

{
  "claims": {
    "country_code": "IT",
    "region": "Lombardy",
    "city": "Milan"
  }
}
```

### Approve with Country Code = RS
```bash
POST /api/dcp/issuer/requests/{requestId}/approve
Content-Type: application/json

{
  "claims": {
    "country_code": "RS",
    "region": "Belgrade",
    "city": "Belgrade"
  }
}
```

## Complete Usage Flow

### 1. Holder Requests Credentials

```bash
POST /api/dcp/credentials
Content-Type: application/json

{
  "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
  "type": "CredentialRequestMessage",
  "holderPid": "holder-123",
  "credentials": [
    { "id": "LocationCredential" },
    { "id": "OrganizationCredential" }
  ]
}
```

**Response:** 201 Created with `Location: /api/dcp/issuer/requests/req-xyz123`

### 2. Issuer Lists Pending Requests

```bash
GET /api/dcp/issuer/requests
```

**Response:**
```json
{
  "status": "success",
  "data": [
    {
      "issuerPid": "req-xyz123",
      "holderPid": "holder-123",
      "credentialIds": ["LocationCredential", "OrganizationCredential"],
      "status": "RECEIVED",
      "createdAt": "2025-12-11T10:00:00Z"
    }
  ]
}
```

### 3. Issuer Approves with Custom Claims

#### Option A: Auto-generate with Custom Claims (Recommended)

```bash
POST /api/dcp/issuer/requests/req-xyz123/approve
Content-Type: application/json

{
  "claims": {
    "country_code": "IT",
    "region": "Lombardy",
    "organizationType": "Corporation",
    "organizationName": "Italian Tech Corp"
  },
  "constraints": [
    {
      "claimName": "country_code",
      "operator": "IN",
      "value": ["IT", "FR", "DE"]
    }
  ]
}
```

#### Option B: Auto-generate without Claims (Default Values)

```bash
POST /api/dcp/issuer/requests/req-xyz123/approve
Content-Type: application/json

{}
```
Or simply:
```bash
POST /api/dcp/issuer/requests/req-xyz123/approve
```

#### Option C: Provide Pre-signed Credentials

```bash
POST /api/dcp/issuer/requests/req-xyz123/approve
Content-Type: application/json

{
  "credentials": [
    {
      "credentialType": "LocationCredential",
      "format": "jwt",
      "payload": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
  ]
}
```

## Approval Request Body Schema

```json
{
  "claims": {
    "type": "object",
    "description": "Custom claims to include in generated credentials",
    "properties": {
      "country_code": { "type": "string", "example": "IT" },
      "region": { "type": "string", "example": "Lombardy" },
      "role": { "type": "string", "example": "admin" },
      "department": { "type": "string", "example": "Engineering" },
      "clearance_level": { "type": "number", "example": 5 }
    }
  },
  "constraints": {
    "type": "array",
    "description": "Optional constraints to verify before issuance",
    "items": {
      "type": "object",
      "properties": {
        "claimName": { "type": "string" },
        "operator": { 
          "type": "string", 
          "enum": ["EQ", "NEQ", "IN", "NOT_IN", "GT", "LT", "GTE", "LTE", "MATCHES"] 
        },
        "value": { "type": ["string", "number", "array"] }
      }
    }
  },
  "credentials": {
    "type": "array",
    "description": "Manually provided credentials (overrides auto-generation)",
    "items": {
      "type": "object",
      "properties": {
        "credentialType": { "type": "string" },
        "format": { "type": "string", "enum": ["jwt", "json"] },
        "payload": { "type": "string" }
      }
    }
  }
}
```

## Real-World Examples

### Example 1: Italian Organization Credential

```bash
POST /api/dcp/issuer/requests/req-001/approve
Content-Type: application/json

{
  "claims": {
    "country_code": "IT",
    "region": "Tuscany",
    "city": "Florence",
    "organizationType": "SRL",
    "organizationName": "Florentine Innovations SRL",
    "vatNumber": "IT12345678901",
    "registrationDate": "2020-01-15"
  }
}
```

**Generated LocationCredential:**
```json
{
  "vc": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiableCredential", "LocationCredential"],
    "credentialSubject": {
      "id": "did:example:holder123",
      "country_code": "IT",
      "region": "Tuscany",
      "verification_method": "GeoIP",
      "verification_timestamp": "2025-12-11T10:30:00Z"
    }
  }
}
```

### Example 2: Serbian Employee Credential

```bash
POST /api/dcp/issuer/requests/req-002/approve
Content-Type: application/json

{
  "claims": {
    "country_code": "RS",
    "region": "Belgrade",
    "role": "senior_developer",
    "department": "Engineering",
    "clearance_level": 4,
    "employee_id": "EMP-RS-2025-001"
  },
  "constraints": [
    {
      "claimName": "clearance_level",
      "operator": "GTE",
      "value": 3
    }
  ]
}
```

### Example 3: Multi-National with Constraints

```bash
POST /api/dcp/issuer/requests/req-003/approve
Content-Type: application/json

{
  "claims": {
    "country_code": "DE",
    "region": "Bavaria",
    "compliance_level": "GDPR_full"
  },
  "constraints": [
    {
      "claimName": "country_code",
      "operator": "IN",
      "value": ["DE", "FR", "IT", "ES", "NL"]
    },
    {
      "claimName": "compliance_level",
      "operator": "MATCHES",
      "value": "^GDPR_(full|partial)$"
    }
  ]
}
```

## Different Credentials for Different Requests

### Scenario: Two Holders, Different Countries

**Holder 1 (Italian Company):**
```bash
# Request comes in
POST /api/dcp/credentials
{
  "holderPid": "holder-italian-corp",
  "credentials": [{"id": "LocationCredential"}]
}
# Returns: req-IT-001

# Approve with IT claims
POST /api/dcp/issuer/requests/req-IT-001/approve
{
  "claims": {
    "country_code": "IT",
    "region": "Lazio",
    "city": "Rome"
  }
}
```

**Holder 2 (Serbian Company):**
```bash
# Request comes in
POST /api/dcp/credentials
{
  "holderPid": "holder-serbian-corp",
  "credentials": [{"id": "LocationCredential"}]
}
# Returns: req-RS-001

# Approve with RS claims
POST /api/dcp/issuer/requests/req-RS-001/approve
{
  "claims": {
    "country_code": "RS",
    "region": "Vojvodina",
    "city": "Novi Sad"
  }
}
```

## Claim Extraction Logic

The `CredentialIssuanceService` will extract relevant claims for each credential type:

### LocationCredential
- Extracts: `country_code`, `region`, `city`, anything starting with `location_*`

### RoleCredential
- Extracts: `role`, `department`, `clearance_level`

### OrganizationCredential
- Extracts: `organizationType`, `organizationName`, `vatNumber`, `registrationDate`

### ComplianceCredential
- Extracts: `certification_type`, `compliance_level`, `audit_date`, `region`

## Error Handling

### Constraint Violation
```bash
POST /api/dcp/issuer/requests/req-001/approve
{
  "claims": {
    "country_code": "US"
  },
  "constraints": [
    {
      "claimName": "country_code",
      "operator": "IN",
      "value": ["IT", "RS"]
    }
  ]
}
```

**Response:** 400 Bad Request
```json
{
  "status": "error",
  "message": "Claim 'country_code' with value 'US' does not satisfy constraint: IN [IT, RS]"
}
```

### Missing Required Claims
If a credential type requires certain claims and they're not provided, default values are used:
- `country_code`: defaults to `"US"`
- `role`: defaults to `"viewer"`
- `clearance_level`: defaults to `1`

## Testing with cURL

### Test 1: Italian Location
```bash
curl -X POST http://localhost:8080/api/dcp/issuer/requests/req-xyz/approve \
  -H "Content-Type: application/json" \
  -d '{
    "claims": {
      "country_code": "IT",
      "region": "Lombardy"
    }
  }'
```

### Test 2: Serbian Role
```bash
curl -X POST http://localhost:8080/api/dcp/issuer/requests/req-abc/approve \
  -H "Content-Type: application/json" \
  -d '{
    "claims": {
      "country_code": "RS",
      "role": "admin",
      "department": "IT"
    }
  }'
```

### Test 3: With Constraints
```bash
curl -X POST http://localhost:8080/api/dcp/issuer/requests/req-def/approve \
  -H "Content-Type: application/json" \
  -d '{
    "claims": {
      "country_code": "IT",
      "clearance_level": 5
    },
    "constraints": [
      {
        "claimName": "country_code",
        "operator": "IN",
        "value": ["IT", "RS", "DE"]
      }
    ]
  }'
```

## Integration with Existing Code

The approval flow now works like this:

1. **Controller** receives approval request with optional `claims` and `constraints`
2. **IssuerService** creates `CredentialGenerationContext` with:
   - Original `CredentialRequest` (protocol-defined)
   - Custom `claims` (from approval request)
   - Optional `constraints` (from approval request)
3. **CredentialIssuanceService** generates credentials:
   - Extracts relevant claims for each credential type
   - Verifies constraints (if present)
   - Generates signed JWTs with custom claims
4. **CredentialDeliveryService** delivers to holder

## Summary

✅ **Country-specific credentials**: Pass `country_code` in claims  
✅ **Role-based credentials**: Pass `role`, `department`, `clearance_level`  
✅ **Organization credentials**: Pass `organizationType`, `organizationName`, etc.  
✅ **Constraint verification**: Ensure claims meet requirements before issuance  
✅ **Flexible defaults**: Missing claims get sensible default values  
✅ **Per-request customization**: Each approval can have different claims

