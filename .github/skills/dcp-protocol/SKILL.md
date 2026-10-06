---
name: dcp-protocol
description: Use when implementing, reviewing, or explaining Eclipse Decentralized Claims Protocol v1.0 issuance, credential storage, presentation queries, verification, DID service discovery, Self-Issued ID Tokens, or issuer-holder-verifier roles.
---

# Eclipse Decentralized Claims Protocol v1.0

Use the [official DCP v1.0 specification](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/) as the normative source. Distinguish its MUST, SHOULD, and MAY requirements from its non-normative examples and flows. Protocol messages are defined by JSON Schema and may be processed as JSON or JSON-LD. The DCP JSON-LD context is `https://w3id.org/dspace-dcp/v1.0/dcp.jsonld`.

## Roles and service discovery

The specification's Issuer-Holder-Verifier model: the Issuer creates a Verifiable Credential (VC) and gives it to the Holder, who can later present it to a Verifier. The Verifier decides which VCs it accepts; the Holder decides which VCs it presents. All three rely on a Verifiable Data Registry for identifier and schema information, and communicate using the Dataspace Protocol (DSP) plus the protocols defined here.

| Role | Responsibility |
|---|---|
| **Issuer** | Creates and signs Verifiable Credentials (VCs), and provides supported-credential metadata. |
| **Holder** | Receives and stores VCs, controls which VCs to present, and operates a Credential Service. |
| **Verifier** | Requests presentations from a Holder's Credential Service, validates them, and decides which VCs to accept. |

Core terminology: a **Claim** is an assertion made about a **Subject** (in a dataspace, a participant); a **Resource** is anything the Credential Service manages (a VC or a Verifiable Presentation); the **Verifiable Data Registry** maintains DIDs and VC schemas used to resolve identifiers across all three roles.

Participant IDs MUST be DIDs. A Holder's DID document MUST advertise a `CredentialService` entry; the `serviceEndpoint` is the Credential Service base URL. The Issuance Protocol's endpoint-discovery section requires an `IssuerService` entry in the DID document it describes; preserve that wording rather than inferring which participant publishes it. DCP endpoint paths are relative to their service base URL. Participants MUST communicate over HTTPS.

The Issuer-Holder-Verifier model does not prescribe the Secure Token Service (STS) endpoint or how a Self-Issued ID Token is obtained. Dataspace policy-to-credential mappings are also domain-specific.

### Wider dataspace ecosystem

DCP's roles sit inside a broader dataspace ecosystem. A **Dataspace** is a policy-based data-sharing context between two or more entities; a **Participant** is a dataspace member that MAY hold multiple roles (Issuer, Holder, Verifier) attested by VCs; a **Participant Agent** performs dataspace tasks (e.g. publishing a catalog, transferring data) on a participant's behalf and is a logical construct, not necessarily one runtime process. The **Dataspace Governance Authority** manages participant registration/onboarding and designates trusted credential issuers; this, and the **Registration System** it runs, are out of scope for DCP itself. Each participant also runs its own **Identity Provider** to generate identity tokens for its agents.

A Holder's logical systems map onto the protocols as follows:

| System | Responsibility |
|---|---|
| **Secure Token Service (STS)** | Creates Self-Issued ID Tokens (with identity claims, and optionally an access token) for participant agents under the same participant's control. Its API and discovery are implementation-specific and out of scope. |
| **Credential Service (CS)** | The Holder's endpoint for storing and presenting VCs/Verifiable Presentations; implements the Verifiable Presentation Protocol's storage and resolution APIs. |
| **DID Service (DIDS)** | Creates, signs, and publishes DID documents for a participant. |
| **Issuer Service (IS)** | Run by a trust anchor (Credential Issuer); issues VCs, manages their revocation lists using the Bitstring Status List specification, and provides cryptographic material for verification. A dataspace MAY have multiple Issuer Services run by different trust anchors. |

### Decentralization principles

DCP decentralizes trust in three ways: participants self-issue and cryptographically sign their own identity tokens (no central identity provider is required to issue them); each participant stores its own VCs and cryptographic material and presents them directly (no third-party verification service is required); and VCs are signed by one or more third-party Issuers acting as trust anchors, with multiple trust anchors permitted per dataspace.

## Consent and trust

DCP interactions are machine-to-machine; its consent model does not depend on an end-user prompt. Consent is the Holder approving release of a Verifiable Presentation and the Verifier agreeing to grant access to a protected resource. The Verifier matches returned presentations to the credentials required by the dataspace interaction. How dataspace policies map to credential requirements, and how governance distributes trusted Issuer and participant DIDs, are outside this specification.

### How the roles interact

- **Holder ⇄ Verifier** (Verifiable Presentation Protocol): the Holder's Participant Agent and the Verifier's Participant Agent exchange DSP messages (Offers/Agreements) that determine which VCs the Holder MUST present. The Verifier's Participant Agent then resolves the Holder's DID document to find its Credential Service endpoint and requests a Verifiable Presentation from it, authenticating with its own Self-Issued ID Token; the Credential Service validates that token (and any embedded access token) and returns only the VCs the Holder consents to release.
- **Issuer ⇄ Holder** (Credential Issuance Protocol): the Issuer Service resolves the Holder's DID to validate the Holder's request and, once a credential is approved, resolves the Holder's Credential Service endpoint to push the issued VC there asynchronously, again authenticating with its own Self-Issued ID Token.
- Mapping DSP Offer/Agreement policies to the specific VCs required is dataspace-specific and out of scope for DCP.

Non-normative presentation flow: (1) the client requests a Self-Issued ID Token (optionally with an access token) from its STS; (2) the STS returns it; (3) the client calls the Verifier, presenting the token; (4)-(5) the Verifier resolves the client's DID document and validates the token; (6) the Verifier requests a Verifiable Presentation from the client's Credential Service, forwarding any access token inside its own Self-Issued ID Token; (7) the Credential Service validates the token and returns the Verifiable Presentation.

Non-normative issuance flow: (1)-(2) the client obtains a Self-Issued ID Token (optionally with an access token) from its STS; (3) the client sends a `CredentialRequestMessage` to the Issuer Service, presenting the token; (4)-(5) the Issuer Service resolves the client's DID and validates the token; (6) the Issuer Service acknowledges or rejects the request; (7)-(8) once approved, the Issuer Service asynchronously resolves the client's Credential Service endpoint and posts the issued `CredentialMessage` to it, authenticating with its own Self-Issued ID Token, and the Credential Service validates and stores the result.

### Trust relationships

Trust is established per relationship, not globally:

- **All entities** expect the Verifiable Data Registry to be tamper-evident, all VCs/VPs to be tamper-proof and support revocation, and all interactions to use TLS.
- **Issuer** expects Credential Service integrity — VCs are stored securely and not disclosed to unauthorized parties.
- **Holder** expects: a secure list of trusted Issuer and participant DIDs from the Dataspace Governance Authority (mechanism out of scope, MAY be part of the Verifiable Data Registry); Credential Service integrity; the Issuer to issue credentials correctly and bind them to the Holder tamper-proof; and the Verifier not to leak information to third parties.
- **Verifier** expects: a secure list of trusted Issuer DIDs from the Dataspace Governance Authority (a Verifier MAY recognize several governance authorities); and the Issuer to issue credentials correctly and maintain their integrity.

## Self-Issued ID Tokens

A Self-Issued ID Token is a participant-signed JWT. It MUST contain:

- `iss` and `sub`, equal to the participant's DID.
- `aud`, set to the Verifier's DID.
- `jti`, used to prevent replay.
- `exp`, after which the token MUST NOT be accepted.
- `iat`, the token issue time.

The Verifier MUST validate the signature using the resolved `sub` DID document and a verification method with the `capabilityInvocation` relationship. It MUST check `iss == sub`, the audience, the DID document ID, the `exp` claim, and that `jti` has not been used. If `kid` is absent, a DID document with multiple verification methods makes the token invalid; a specified `kid` must identify a method with the required relationship. A present `nbf` MUST be checked. The Verifier MAY use `iat` to reject tokens issued too long ago and MAY allow clock-skew leeway when evaluating `exp` and `nbf`.

A token MAY carry an access token in its `token` claim. Its format is implementation-specific and opaque to the Verifier. When present, the Issuer MUST use it for the requested write to the Holder's Credential Service. A Credential Service that supports access control MUST receive the Self-Issued ID Token in the HTTP Authorization header using the `Bearer` scheme, with the access token in its `token` claim.

How a Self-Issued ID Token is obtained from an STS is non-normative and implementation-specific; the spec illustrates (non-normatively) an OAuth 2 Client Credential Grant against an STS endpoint, where a `bearer_access_scope` request parameter MAY convey the space-delimited scopes the `token` claim should be enabled for. Do not treat this flow as a normative requirement.

## Normative APIs

Use the DID-advertised service endpoint as the base URL. The same relative path `/credentials` has different meanings depending on the service:

| Service and sender | Method and path | Request → response |
|---|---|---|
| Issuer Service; Holder/client → Issuer | `POST /credentials` | `CredentialRequestMessage` → `201 Created` with `Location` for status, or a client error |
| Issuer Service; client → Issuer | `GET /metadata` | `IssuerMetadata` → `200 OK` |
| Issuer Service; requesting client → Issuer | `GET /requests/{request id}` | `CredentialStatus` → `200 OK`; only the client that created the request may access it |
| Holder Credential Service; Issuer → Holder | `POST /credentials` | `CredentialMessage` → `2xx` or `4xx` |
| Holder Credential Service; Issuer → Holder | `POST /offers` | `CredentialOfferMessage` → `200 OK` or `4xx` |
| Holder Credential Service; Verifier → Holder | `POST /presentations/query` | `PresentationQueryMessage` → `PresentationResponseMessage` (`2xx`) or `4xx` |

### Issuance and storage messages

**`CredentialRequestMessage`** requires `@context`, `type`, `holderPid`, and a `credentials` array. Each entry has an `id` that MUST match an `id` in the Issuer's `credentialsSupported` metadata. `holderPid` identifies the request on the Holder side; it is not specified as the Holder's DID. The request MUST carry a Self-Issued ID Token in HTTP authentication using the `Bearer` scheme. If the Issuer supports a pre-authorization code flow, the client MUST provide the code in the `pre-authorized_code` claim. On success, the Issuer returns `201 Created` and a `Location` for the status resource.

**`IssuerMetadata`** requires `@context`, `type`, and `issuer`. Its prose definition marks `credentialsSupported` optional, but the published v1.0 JSON Schema lists it as required; do not assume omission is schema-valid. Each supported credential is a `CredentialObject`. **`CredentialObject`** requires `type` and a unique, stable `id`. Other properties are optional: `@context`, `credentialType`, `bindingMethods`, `credentialSchema`, `profile`, `issuancePolicy`, and `offerReason`. Every `CredentialObject` in `credentialsSupported` MUST include all of those optional properties. A sparse credential offer may contain only an `id` per the prose, but the published schema requires both `id` and `type`; do not assume an ID-only offer validates against that schema.

**`CredentialStatus`** requires `@context`, `type`, `issuerPid`, `holderPid`, and `status`. Status is `RECEIVED`, `REJECTED`, or `ISSUED`. Access to a status resource MUST be restricted to the client that made the request, using a Self-Issued ID Token in HTTP authentication with the `Bearer` scheme.

**`CredentialMessage`** prose requires `@context`, `type`, `issuerPid`, and `status`; status is `ISSUED` or `REJECTED`. `credentials`, `holderPid`, and `rejectionReason` are optional. The prose permits a null `rejectionReason`, while the schema types it as a string when present; it should not disclose confidential information. The prose requires a `type` property, but the published schema requires `type` without defining it under `properties` and instead defines `credentialType` as the constant `CredentialMessage`. Each **`CredentialContainer`** requires `credentialType`, a JSON-LD VC `payload`, and `format`.

**`CredentialOfferMessage`** requires `@context`, `type`, `issuer`, and a non-empty `credentials` array of `CredentialObject`s. The prose specifies non-empty cardinality; the published schema does not set `minItems`.

### Presentation messages

**`PresentationQueryMessage`** requires `@context` and `type`, and MUST contain exactly one of:

- A non-empty `scope` array. A scope has the form `[alias]:[discriminator]`. Implementations MUST support `org.eclipse.dspace.dcp.vc.type:<type>` and `org.eclipse.dspace.dcp.vc.id:<id>`. Other aliases and their mappings are implementation-specific. An empty scope requires a `4xx` response. If the array requests scopes the client is not entitled to, the Credential Service MUST still return `2xx`, with the `presentation` array holding fewer entries than requested rather than an error.
- A non-empty, valid `presentationDefinition` conforming to Presentation Exchange 2.1.1. Support is MAY; an implementation that does not support it MUST return `501 Not Implemented`. Supplying both query properties is an error and MUST result in `400 Bad Request`.

The prose requires exactly one query property. The published query schema's `anyOf` can validate a message containing both; do not treat schema acceptance as overriding the prose requirement.

**`PresentationResponseMessage`** requires `@context`, `type`, and a `presentation` array. Entries may be serialized presentations as strings, JSON objects, or a mixture. `presentationSubmission` is optional generally, but implementations supporting `presentationDefinition` MUST return a valid `presentationSubmission` when answering such a query. The response SHOULD contain only valid, non-expired, non-revoked, and non-suspended credentials.

## Presentation validation and profiles

The Verifier SHOULD perform the specified presentation checks. It MUST confirm the presentation satisfies the requested scope or definition; validate the VP signature using its resolved verification method and require the `Authentication` relationship; verify that the VC verification method's DID matches the VC issuer; and validate the VC signature. If a VC includes a revocation mechanism, its status MUST be checked. Any presentation expiry or validity claims MUST be checked. Failure of any required check makes the VP invalid. When dataspace or credential semantics require cryptographic holder binding, the Verifier MUST compare the VC's `credentialSubject.id` with the DID in the VP verification method.

The two DCP profiles are:

| Profile alias | VC data model | Revocation profile | Proof format |
|---|---|---|---|
| `vc20-bssl/jwt` | VC Data Model 2.0 | BitStringStatusList | Enveloped JWT/JOSE |
| `vc11-sl2021/jwt` | VC Data Model 1.1 | StatusList2021 | External JWT |

The Issuance Protocol also states that VC revocation MUST be supported using the Bitstring Status List specification. Read this global requirement alongside the profile-specific mechanisms above; the v1.0 text does not state how to resolve their differing mechanisms.

Credentials and presentations MUST be homogeneous: a presentation uses the same data-model version and proof mechanism as its credentials. Put heterogeneous credentials into separate presentations.

## Normative schemas

Consult the schemas linked by the v1.0 specification when implementing serialization or validation:

- [Credential request](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/resources/issuance/credential-request-message-schema.json), [credential message](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/resources/issuance/credential-message-schema.json), [credential offer](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/resources/issuance/credential-offer-message-schema.json), [issuer metadata](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/resources/issuance/issuer-metadata-schema.json), and [credential status](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/resources/issuance/credential-status-schema.json).
- [Presentation query](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/resources/presentation/presentation-query-message-schema.json) and [presentation response](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/resources/presentation/presentation-response-message-schema.json).

## Specification boundaries

Do not invent protocol fields, endpoint paths, token formats, or scope mappings. STS APIs and access-token formats are implementation-specific; DCP scopes outside the two required aliases are implementation-specific; and dataspace policy-to-credential mappings are out of scope. Treat examples and flow diagrams as non-normative unless a normative requirement independently establishes the same behavior.
