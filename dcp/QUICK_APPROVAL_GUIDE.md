# Quick Reference: Passing Country Code When Approving VCs

## Simple Examples

### Approve with Country = IT
```bash
curl -X POST http://localhost:8080/api/dcp/issuer/requests/req-xyz/approve \
  -H "Content-Type: application/json" \
  -d '{"claims": {"country_code": "IT"}}'
```

### Approve with Country = RS
```bash
curl -X POST http://localhost:8080/api/dcp/issuer/requests/req-abc/approve \
  -H "Content-Type: application/json" \
  -d '{"claims": {"country_code": "RS"}}'
```

### Approve with Multiple Claims
```bash
curl -X POST http://localhost:8080/api/dcp/issuer/requests/req-def/approve \
  -H "Content-Type: application/json" \
  -d '{
    "claims": {
      "country_code": "IT",
      "region": "Lombardy",
      "city": "Milan",
      "organizationName": "Italian Tech SRL"
    }
  }'
```

## Postman Examples

### Request 1: Italian Company
```json
POST /api/dcp/issuer/requests/req-001/approve
{
  "claims": {
    "country_code": "IT",
    "region": "Tuscany"
  }
}
```

### Request 2: Serbian Company  
```json
POST /api/dcp/issuer/requests/req-002/approve
{
  "claims": {
    "country_code": "RS",
    "region": "Vojvodina"
  }
}
```

### Request 3: With Constraints
```json
POST /api/dcp/issuer/requests/req-003/approve
{
  "claims": {
    "country_code": "IT",
    "clearance_level": 5
  },
  "constraints": [
    {
      "claimName": "country_code",
      "operator": "IN",
      "value": ["IT", "RS", "DE", "FR"]
    },
    {
      "claimName": "clearance_level",
      "operator": "GTE",
      "value": 3
    }
  ]
}
```

## JSON Body Schema

```json
{
  "claims": {
    "country_code": "IT",           // or "RS", "US", etc.
    "region": "string",
    "city": "string",
    "role": "string",
    "department": "string",
    "clearance_level": 1,
    "organizationName": "string",
    "organizationType": "string",
    // ... any custom claim
  },
  "constraints": [
    {
      "claimName": "country_code",
      "operator": "IN",              // EQ, NEQ, IN, NOT_IN, GT, LT, GTE, LTE, MATCHES
      "value": ["IT", "RS"]          // Can be string, number, or array
    }
  ]
}
```

## Available Operators

- **EQ**: Equals
- **NEQ**: Not equals
- **IN**: Value is in list
- **NOT_IN**: Value is not in list
- **GT**: Greater than
- **LT**: Less than
- **GTE**: Greater than or equal
- **LTE**: Less than or equal
- **MATCHES**: Regex pattern match

## Complete Flow Example

```bash
# 1. Holder requests credential
curl -X POST http://localhost:8080/api/dcp/credentials \
  -H "Content-Type: application/json" \
  -d '{
    "@context": ["https://w3id.org/dspace-dcp/v1.0/dcp.jsonld"],
    "type": "CredentialRequestMessage",
    "holderPid": "holder-italian-corp",
    "credentials": [{"id": "LocationCredential"}]
  }'
# Response: 201 Created, Location: /api/dcp/issuer/requests/req-IT-001

# 2. Issuer lists pending requests
curl http://localhost:8080/api/dcp/issuer/requests

# 3. Issuer approves with IT country code
curl -X POST http://localhost:8080/api/dcp/issuer/requests/req-IT-001/approve \
  -H "Content-Type: application/json" \
  -d '{
    "claims": {
      "country_code": "IT",
      "region": "Lazio",
      "city": "Rome"
    }
  }'
# Response: 200 OK
```

## Different Countries for Different Requests

### Scenario: Two Companies

**Italian Company:**
```bash
# Their request ID: req-IT-001
curl -X POST .../requests/req-IT-001/approve \
  -d '{"claims": {"country_code": "IT", "region": "Lombardy"}}'
```

**Serbian Company:**
```bash
# Their request ID: req-RS-001  
curl -X POST .../requests/req-RS-001/approve \
  -d '{"claims": {"country_code": "RS", "region": "Belgrade"}}'
```

Each request gets its own custom claims!

## Testing Tips

1. **Default behavior**: If you don't provide claims, defaults are used (country_code="US")
2. **Constraint validation**: If constraints fail, you get a 400 error with details
3. **Multiple credentials**: If requesting multiple credential types, claims are shared across all
4. **View generated JWT**: Decode the returned JWT at https://jwt.io to see the claims

## Common Use Cases

### Manufacturing Company
```json
{
  "claims": {
    "country_code": "DE",
    "industry": "Manufacturing",
    "certification": "ISO9001"
  }
}
```

### IT Services
```json
{
  "claims": {
    "country_code": "RS",
    "industry": "IT Services",
    "employees": 50
  }
}
```

### Multi-National HQ
```json
{
  "claims": {
    "country_code": "IT",
    "hq_location": "Milan",
    "subsidiaries": ["RS", "DE", "FR"]
  }
}
```

