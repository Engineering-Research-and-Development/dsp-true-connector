# VC/VP Authentication Architecture

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Client Application                           │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Creates Verifiable Presentation (VP)                        │   │
│  │  - Holder DID: did:example:holder123                         │   │
│  │  - Credentials: [cred-1, cred-2]                             │   │
│  │  - Profile: VC11_SL2021_JWT                                  │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              ↓                                        │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Encodes VP as Base64 JSON                                   │   │
│  │  Authorization: Bearer eyJAY29udGV4dCI6...                   │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                               ↓ HTTP Request
┌─────────────────────────────────────────────────────────────────────┐
│                        TRUE Connector Server                         │
│                                                                       │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │              Spring Security Filter Chain                       │ │
│  │                                                                  │ │
│  │  1. DataspaceProtocolEndpointsAuthenticationFilter             │ │
│  │     └─> Check if protocol auth is enabled                      │ │
│  │                         ↓                                        │ │
│  │  2. VcVpAuthenticationFilter ◄────────────────┐                │ │
│  │     └─> Extract VP from Authorization header   │                │ │
│  │                         ↓                       │                │ │
│  │     ┌──────────────────────────────────────┐   │                │ │
│  │     │  Parse VP (JSON/Base64)              │   │                │ │
│  │     │  - Direct JSON                        │   │                │ │
│  │     │  - Base64-decoded JSON                │   │                │ │
│  │     └──────────────────────────────────────┘   │                │ │
│  │                         ↓                       │                │ │
│  │     ┌──────────────────────────────────────┐   │                │ │
│  │     │  Create VcVpAuthenticationToken      │   │                │ │
│  │     └──────────────────────────────────────┘   │ If not VP      │ │
│  │                         ↓                       │ format         │ │
│  │  3. JwtAuthenticationFilter ◄──────────────────┼────────────┐  │ │
│  │     └─> Try JWT authentication                 │            │  │ │
│  │                         ↓                       │            │  │ │
│  │  4. BasicAuthenticationFilter ◄─────────────────┼────────────┘  │ │
│  │     └─> Try Basic authentication (failsafe)    │               │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                               ↓                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │              Authentication Manager                             │ │
│  │                                                                  │ │
│  │  ProviderManager:                                               │ │
│  │    1. VcVpAuthenticationProvider ◄─────┐                       │ │
│  │    2. JwtAuthenticationProvider        │ Fallback              │ │
│  │    3. DaoAuthenticationProvider        │ if fails              │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                               ↓                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │          VcVpAuthenticationProvider                             │ │
│  │                                                                  │ │
│  │  validate(VcVpAuthenticationToken)                              │ │
│  │    └─> Get PresentationResponseMessage                         │ │
│  │    └─> Call PresentationValidationService                      │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                               ↓                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                     DCP Module                                  │ │
│  │                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────┐   │ │
│  │  │  PresentationValidationService                          │   │ │
│  │  │                                                          │   │ │
│  │  │  validate(presentation, requiredTypes, tokenContext)    │   │ │
│  │  │    │                                                     │   │ │
│  │  │    ├─> ProfileResolver                                  │   │ │
│  │  │    │   └─> Check profile homogeneity                    │   │ │
│  │  │    │                                                     │   │ │
│  │  │    ├─> IssuerTrustService                               │   │ │
│  │  │    │   └─> Verify issuer is trusted                     │   │ │
│  │  │    │                                                     │   │ │
│  │  │    ├─> Check expiration dates                           │   │ │
│  │  │    │   └─> issuanceDate < now < expirationDate          │   │ │
│  │  │    │                                                     │   │ │
│  │  │    ├─> RevocationService                                │   │ │
│  │  │    │   └─> Check if credential revoked                  │   │ │
│  │  │    │                                                     │   │ │
│  │  │    └─> SchemaRegistryService                            │   │ │
│  │  │        └─> Validate credential schema                   │   │ │
│  │  │                                                          │   │ │
│  │  │    ↓                                                     │   │ │
│  │  │  Return ValidationReport                                │   │ │
│  │  └────────────────────────────────────────────────────────┘   │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                               ↓                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  Result Processing                                              │ │
│  │                                                                  │ │
│  │  If ValidationReport.isValid():                                 │ │
│  │    └─> Extract holder DID                                      │ │
│  │    └─> Create authenticated VcVpAuthenticationToken            │ │
│  │    └─> Set SecurityContext                                     │ │
│  │    └─> Grant ROLE_CONNECTOR                                    │ │
│  │                                                                  │ │
│  │  Else:                                                          │ │
│  │    └─> Throw BadCredentialsException                           │ │
│  │    └─> Try next provider (JWT)                                 │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                               ↓                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  Protected Endpoint Accessed                                    │ │
│  │                                                                  │ │
│  │  Authentication: VcVpAuthenticationToken                        │ │
│  │  Principal: PresentationResponseMessage                         │ │
│  │  Name: did:example:holder123                                   │ │
│  │  Authorities: [ROLE_CONNECTOR]                                  │ │
│  └────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
                    HTTP 200 OK (Success)
```

## Authentication Flow Sequence

```
Client                    Filter                Provider              DCP Module
  │                         │                       │                     │
  │ POST /connector/req     │                       │                     │
  │ Auth: Bearer {VP}       │                       │                     │
  ├────────────────────────>│                       │                     │
  │                         │                       │                     │
  │                         │ Parse VP              │                     │
  │                         │ (JSON/Base64)         │                     │
  │                         │                       │                     │
  │                         │ Create Token          │                     │
  │                         │                       │                     │
  │                         │ authenticate(token)   │                     │
  │                         ├──────────────────────>│                     │
  │                         │                       │                     │
  │                         │                       │ validate(VP, ...)   │
  │                         │                       ├────────────────────>│
  │                         │                       │                     │
  │                         │                       │ ┌─────────────────┐ │
  │                         │                       │ │ Profile check   │ │
  │                         │                       │ │ Issuer trust    │ │
  │                         │                       │ │ Expiration      │ │
  │                         │                       │ │ Revocation      │ │
  │                         │                       │ │ Schema          │ │
  │                         │                       │ └─────────────────┘ │
  │                         │                       │                     │
  │                         │                       │ ValidationReport    │
  │                         │                       │<────────────────────┤
  │                         │                       │                     │
  │                         │                       │ isValid()?          │
  │                         │                       │ Yes ✓               │
  │                         │                       │                     │
  │                         │ Authenticated Token   │                     │
  │                         │<──────────────────────┤                     │
  │                         │                       │                     │
  │                         │ Set SecurityContext   │                     │
  │                         │ Grant ROLE_CONNECTOR  │                     │
  │                         │                       │                     │
  │                         │ Continue filter chain │                     │
  │                         ├──────────────────────────────────────────┐  │
  │                         │                       │                  │  │
  │                         │                       │          Endpoint│  │
  │                         │                       │          Handler │  │
  │<────────────────────────┴───────────────────────┴──────────────────┘  │
  │                                                                        │
  │ HTTP 200 OK                                                           │
  │                                                                        │
```

## Fallback Flow

```
Request with VP format that fails validation:

VcVpFilter ──(parse VP)──> VcVpProvider ──(validate)──> DCP Module
    │                            │                           │
    │                            │◄──(ValidationReport)──────┤
    │                            │   errors: [VC_EXPIRED]
    │                            │
    │◄───(BadCredentialsException)─┤
    │
    └──> Continue to JwtFilter
              │
              └──> JwtProvider ──(validate JWT)──> DapsService
                        │                               │
                        │◄───(if valid)─────────────────┤
                        │
                        └──> Authenticated with ROLE_CONNECTOR
                        
              If JWT fails:
              └──> Continue to BasicAuthenticationFilter
                        │
                        └──> DaoAuthenticationProvider ──> UserRepository
                                  │                              │
                                  │◄───(if valid)────────────────┤
                                  │
                                  └──> Authenticated with user roles
```

## Component Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                        Connector Module                          │
│                                                                   │
│  ┌──────────────────────┐      ┌──────────────────────┐         │
│  │ VcVpAuthentication   │      │ VcVpAuthentication   │         │
│  │ Filter               │─────>│ Provider             │         │
│  └──────────────────────┘      └──────────────────────┘         │
│           │                             │                        │
│           │                             │                        │
│           │                             ↓                        │
│           │                    ┌──────────────────────┐         │
│           │                    │ VcVpAuthentication   │         │
│           └───────────────────>│ Token                │         │
│                                └──────────────────────┘         │
│                                         │                        │
│                                         │                        │
│        ┌────────────────────────────────┘                        │
│        │                                                         │
│        │  ┌──────────────────────┐                              │
│        └─>│ VcVpAuthentication   │ (utility)                    │
│           │ Util                 │                              │
│           └──────────────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
                        │
                        │ depends on
                        ↓
┌─────────────────────────────────────────────────────────────────┐
│                          DCP Module                              │
│                                                                   │
│  ┌──────────────────────┐      ┌──────────────────────┐         │
│  │ PresentationValidation│      │ IssuerTrust          │         │
│  │ Service              │─────>│ Service              │         │
│  └──────────────────────┘      └──────────────────────┘         │
│           │                                                      │
│           ├─────>┌──────────────────────┐                       │
│           │      │ Revocation           │                       │
│           │      │ Service              │                       │
│           │      └──────────────────────┘                       │
│           │                                                      │
│           ├─────>┌──────────────────────┐                       │
│           │      │ SchemaRegistry       │                       │
│           │      │ Service              │                       │
│           │      └──────────────────────┘                       │
│           │                                                      │
│           └─────>┌──────────────────────┐                       │
│                  │ ProfileResolver      │                       │
│                  └──────────────────────┘                       │
│                                                                   │
│  Models: PresentationResponseMessage, VerifiablePresentation,   │
│          ValidationReport, ValidationError                       │
└─────────────────────────────────────────────────────────────────┘
```

---

**Legend:**
- `─>` : Depends on / Calls
- `◄─` : Returns to
- `├─>` : Also uses
- `[ ]` : Component
- `{ }` : Data

