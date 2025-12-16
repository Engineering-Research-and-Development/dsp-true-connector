# Credential Message Examples for Postman

## Example 1: JWT Credential (MembershipCredential)

Use this JSON in Postman for `POST /dcp/credentials`:

```json
{
  "@context" : [ "https://w3id.org/dspace-dcp/v1.0/dcp.jsonld" ],
  "type" : "CredentialMessage",
  "issuerPid" : "issuer-pid-12345",
  "holderPid" : "did:example:holder123",
  "status" : "ISSUED",
  "rejectionReason" : null,
  "requestId" : "request-67890",
  "credentials" : [ {
    "credentialType" : "MembershipCredential",
    "payload" : "eyJraWQiOiJkaWQ6ZXhhbXBsZTppc3N1ZXI0NTYja2V5LTEiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJkaWQ6ZXhhbXBsZTpob2xkZXIxMjMiLCJleHAiOjE3NjQ4OTk0MzEsImlzcyI6ImRpZDpleGFtcGxlOmlzc3VlcjQ1NiIsImp0aSI6InVybjp1dWlkOmNyZWRlbnRpYWwtMTIzNDU2NzgtMTIzNC0xMjM0LTEyMzQtMTIzNDU2Nzg5MGFiIiwiaWF0IjoxNzMzMzYzNDMxLCJ2YyI6eyJjcmVkZW50aWFsU3ViamVjdCI6eyJzdGF0dXMiOiJBY3RpdmUiLCJtZW1iZXJzaGlwVHlwZSI6IlByZW1pdW0iLCJpZCI6ImRpZDpleGFtcGxlOmhvbGRlcjEyMyIsIm1lbWJlcnNoaXBJZCI6Ik1FTUJFUi0yMDI0LTAwMSJ9LCJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vZXhhbXBsZS5vcmcvY3JlZGVudGlhbHMvdjEiXSwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIk1lbWJlcnNoaXBDcmVkZW50aWFsIl19fQ.J7kD9xQ2MpLwN5vR3tY6uF8sA1bC4eG9hI2jK3lM4nO5pQ6rS7tU8vW9xY0zA1bC2dE3fG4hI5jK6lM7nO8pQ",
    "format" : "jwt"
  } ]
}
```

### Decoded JWT Claims (for reference):
```json
{
  "sub": "did:example:holder123",
  "exp": 1764899431,
  "iss": "did:example:issuer456",
  "jti": "urn:uuid:credential-12345678-1234-1234-1234-1234567890ab",
  "iat": 1733363431,
  "vc": {
    "credentialSubject": {
      "status": "Active",
      "membershipType": "Premium",
      "id": "did:example:holder123",
      "membershipId": "MEMBER-2024-001"
    },
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://example.org/credentials/v1"
    ],
    "type": [
      "VerifiableCredential",
      "MembershipCredential"
    ]
  }
}
```

---

## Example 2: JSON-LD Credential (OrganizationCredential)

Use this JSON in Postman for `POST /dcp/credentials`:

```json
{
  "@context" : [ "https://w3id.org/dspace-dcp/v1.0/dcp.jsonld" ],
  "type" : "CredentialMessage",
  "issuerPid" : "issuer-pid-12345",
  "holderPid" : "did:example:holder123",
  "status" : "ISSUED",
  "rejectionReason" : null,
  "requestId" : "request-67890",
  "credentials" : [ {
    "credentialType" : "OrganizationCredential",
    "payload" : {
      "@context" : [ "https://www.w3.org/2018/credentials/v1", "https://w3id.org/security/suites/jws-2020/v1", "https://example.org/credentials/v1" ],
      "id" : "http://example.org/credentials/3732",
      "type" : [ "VerifiableCredential", "OrganizationCredential" ],
      "issuer" : "did:example:issuer456",
      "issuanceDate" : "2024-12-08T12:30:00Z",
      "expirationDate" : "2025-12-08T12:30:00Z",
      "credentialSubject" : {
        "id" : "did:example:holder123",
        "organizationName" : "Example Corp",
        "role" : "Member",
        "membershipLevel" : "Gold"
      },
      "proof" : {
        "type" : "JsonWebSignature2020",
        "created" : "2024-12-08T12:30:00Z",
        "verificationMethod" : "did:example:issuer456#key-1",
        "proofPurpose" : "assertionMethod",
        "jws" : "eyJhbGciOiJFUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..mD3kL8pQ2rS7vW9zA1bC4eG9hI5jK6lM7nO8pQ9rT0uV1wX2yZ3aB4cD5eF6gH7iJ8kL9mN0oP1qR2sT3uV4"
      }
    },
    "format" : "json-ld"
  } ]
}
```

---

## Example 3: Mixed Credentials (JWT + JSON-LD)

Use this JSON in Postman for `POST /dcp/credentials`:

```json
{
  "@context" : [ "https://w3id.org/dspace-dcp/v1.0/dcp.jsonld" ],
  "type" : "CredentialMessage",
  "issuerPid" : "issuer-pid-12345",
  "holderPid" : "did:example:holder123",
  "status" : "ISSUED",
  "rejectionReason" : null,
  "requestId" : "request-67890",
  "credentials" : [ 
    {
      "credentialType" : "MembershipCredential",
      "payload" : "eyJraWQiOiJkaWQ6ZXhhbXBsZTppc3N1ZXI0NTYja2V5LTEiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJkaWQ6ZXhhbXBsZTpob2xkZXIxMjMiLCJleHAiOjE3NjQ4OTk0MzEsImlzcyI6ImRpZDpleGFtcGxlOmlzc3VlcjQ1NiIsImp0aSI6InVybjp1dWlkOmNyZWRlbnRpYWwtMTIzNDU2NzgtMTIzNC0xMjM0LTEyMzQtMTIzNDU2Nzg5MGFiIiwiaWF0IjoxNzMzMzYzNDMxLCJ2YyI6eyJjcmVkZW50aWFsU3ViamVjdCI6eyJzdGF0dXMiOiJBY3RpdmUiLCJtZW1iZXJzaGlwVHlwZSI6IlByZW1pdW0iLCJpZCI6ImRpZDpleGFtcGxlOmhvbGRlcjEyMyIsIm1lbWJlcnNoaXBJZCI6Ik1FTUJFUi0yMDI0LTAwMSJ9LCJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vZXhhbXBsZS5vcmcvY3JlZGVudGlhbHMvdjEiXSwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIk1lbWJlcnNoaXBDcmVkZW50aWFsIl19fQ.J7kD9xQ2MpLwN5vR3tY6uF8sA1bC4eG9hI2jK3lM4nO5pQ6rS7tU8vW9xY0zA1bC2dE3fG4hI5jK6lM7nO8pQ",
      "format" : "jwt"
    },
    {
      "credentialType" : "OrganizationCredential",
      "payload" : {
        "@context" : [ "https://www.w3.org/2018/credentials/v1", "https://example.org/credentials/v1" ],
        "id" : "http://example.org/credentials/9876",
        "type" : [ "VerifiableCredential", "OrganizationCredential" ],
        "issuer" : "did:example:issuer456",
        "issuanceDate" : "2024-12-08T12:30:00Z",
        "credentialSubject" : {
          "id" : "did:example:holder123",
          "organizationName" : "Dataspace Corp",
          "registrationNumber" : "REG-2024-001"
        }
      },
      "format" : "json-ld"
    }
  ]
}
```

---

## Example 4: Rejected Credential Request

Use this JSON in Postman for `POST /dcp/credentials`:

```json
{
  "@context" : [ "https://w3id.org/dspace-dcp/v1.0/dcp.jsonld" ],
  "type" : "CredentialMessage",
  "issuerPid" : "issuer-pid-12345",
  "holderPid" : "did:example:holder123",
  "status" : "REJECTED",
  "rejectionReason" : "Holder organization not verified",
  "requestId" : "request-67890",
  "credentials" : [ ]
}
```

---

## Expected Responses

### Success (All credentials saved):
```json
{
  "saved": 1
}
```

### Success (Some credentials saved, some skipped):
```json
{
  "saved": 1,
  "skipped": 1
}
```

### Error (All credentials had empty payloads):
```json
{
  "saved": 0,
  "skipped": 2,
  "error": "All credentials had empty or invalid payloads. Per DCP spec 6.5.2, payload must contain a Verifiable Credential."
}
```

### Success (Rejected request):
```json
{
  "status": "rejected"
}
```

---

## Notes

1. **Authentication**: Don't forget to add the `Authorization: Bearer <token>` header in Postman
2. **Content-Type**: Set to `application/json`
3. **JWT Tokens**: The JWT examples use placeholder signatures that won't verify. In production, use properly signed JWTs.
4. **Timestamps**: Update `issuanceDate` and `expirationDate` fields to current/future dates as needed.
5. **DIDs**: Replace example DIDs (`did:example:*`) with actual DIDs from your system.

## Postman Setup

1. Create a new POST request to: `http://your-host:port/dcp/credentials`
2. Set Headers:
   - `Content-Type: application/json`
   - `Authorization: Bearer <your-jwt-token>`
3. Copy one of the JSON examples above into the Body (raw)
4. Send the request
5. Check the response and application logs

