# Comprehensive Implementation Plan: Eclipse Decentralized Claims Protocol

This document outlines a complete, phased implementation and integration plan for making the `dsp-true-connector` fully
compliant with the **Eclipse Decentralized Claims Protocol - v1.0** specification. Each phase is designed as a
self-contained, testable, and committable unit of work.

---

### **Overall Strategy & Guiding Principles**

* **Modularization:** A new, self-contained Maven module named `dcp` will be created. This isolates the core DCP logic,
  preventing premature coupling with existing modules and promoting clean architecture.
* **Leverage Existing Stack:** The plan explicitly utilizes the project's existing MongoDB persistence layer via Spring
  Data, avoiding the introduction of new database technologies. It also assumes the use of a standard HTTP client and a
  dependency injection framework like Spring.
* **Phased & Testable Implementation:** The project is broken down into five distinct phases. Each phase delivers a
  testable unit of functionality, building upon the last. This ensures a controlled, verifiable, and incremental
  rollout.
* **Transport Security (MUST):** As per **Specification Section 3.3**, any production deployment of the connector **must
  ** be configured to serve all traffic exclusively over HTTPS to be compliant and secure.

---

### **Phase 0: Foundation, Setup, and Core Models**

#### 1. Detailed Objective

To construct the complete foundational skeleton of the DCP implementation. This includes creating the new `dcp` module,
adding all necessary dependencies, and defining all required data models as Java records. This phase ensures the project
is structurally ready for feature development and covers the message structures defined throughout **Sections 5 and 6**
of the specification.

#### 2. Detailed Implementation Steps

* **Module Structure:**
    * **Action:** A new directory named `dcp` will be created at the project root.
    * **Structure:** Inside `dcp`, a `pom.xml` file and the standard `src/main/java/it/eng/dcp/` and
      `src/test/java/it/eng/dcp/` directory structures will be created.
    * **Integration:** The root `pom.xml` will be modified to include `<module>dcp</module>` in its `<modules>` section.

* **Dependency Specification (`dcp/pom.xml`):**
    * **JWT Handling:** The `com.nimbusds:nimbus-jose-jwt` library will be added for creating, signing, and verifying
      JWTs.
    * **JSON Processing:** The `jakarta.json:jakarta.json-api` and its implementation `org.glassfish:jakarta.json` will
      be included for standard JSON manipulation.
    * **JSON-LD Processing:** The `com.github.jsonld-java:jsonld-java` library will be added to process the `@context`
      field in DCP messages.

* **Core Data Model Definitions (Package: `it.eng.dcp.model`):**
    * These will be defined as immutable Java classes using the builder pattern.
    * **`PresentationQueryMessage` (Spec 5.4.1):** Attributes: `@context` (`List<String>`), `type` (`String`), `scope` (
      `List<String>`), `presentationDefinition` (`Map<String, Object>`).
    * **`PresentationResponseMessage` (Spec 5.4.2):** Attributes: `@context` (`List<String>`), `type` (`String`),
      `presentation` (`List<Object>`), `presentationSubmission` (`Map<String, Object>`).
    * **`CredentialRequestMessage` (Spec 6.4.1):** Attributes: `@context` (`List<String>`), `type` (`String`),
      `holderPid` (`String`), `credentials` (`List<CredentialReference>`).
        * **`CredentialReference` (Nested Class):** Attribute: `id` (`String`).
    * **`CredentialMessage` (Spec 6.5.1):** Attributes: `@context` (`List<String>`), `type` (`String`), `issuerPid` (
      `String`), `holderPid` (`String`), `status` (`String`), `rejectionReason` (`String`), `credentials` (
      `List<CredentialContainer>`).
        * **`CredentialContainer` (Nested Class, Spec 6.5.2):** Attributes: `credentialType` (`String`), `payload` (
          `Object`), `format` (`String`).
    * **`IssuerMetadata` (Spec 6.7.1):** Attributes: `@context` (`List<String>`), `type` (`String`), `issuer` (
      `String`), `credentialsSupported` (`List<CredentialObject>`).
      **`CredentialObject` (Nested Class, Spec 6.6.2):** Attributes: `id` (`String`), `type` (`String`),
      `credentialType` (`String`), `credentialSchema` (`String`), `profile` (`String`), `issuancePolicy` (
      `Map<String, Object>`), `bindingMethods` (`List<String>`).

* **Configuration:**
    * A property named `dcp.connector.did` will be added to `connector/src/main/resources/application.properties` to
      store the connector's own Decentralized Identifier.

- PH0-01: Create `dcp` module skeleton (add `<module>dcp</module>` to root `pom.xml`; create directories). Success:
  `mvn -pl dcp clean compile` builds.
    - Tests: Run `mvn -pl dcp clean compile`; verify target/classes exists; ensure root build includes module. Negative:
      remove module entry -> build fails referencing missing module.
- PH0-02: Add dependencies (nimbus-jose-jwt, jakarta.json-api + impl, jsonld-java, spring-boot starters). Success:
  dependency tree resolves without conflicts.
    - Tests: `mvn -pl dcp dependency:tree` contains artifacts; attempt import of Nimbus classes in dummy test compiles.
      Negative: duplicate version conflict triggers Maven warning (none expected).
- PH0-03: Base abstraction `BaseDcpMessage` enforcing non-empty `@context` and `type`.
    - Tests: Construct valid subclass passes; empty context throws; null type throws; serialization retains order.
- PH0-04: `PresentationQueryMessage` with validation (non-null scope list; empty allowed).
    - Tests: Empty scope list accepted; null scope rejected; JSON includes provided presentationDefinition keys.
- PH0-05: `PresentationResponseMessage` ensuring non-null presentations list.
    - Tests: Empty list serializes as [] not null; null presentations builder triggers exception.
- PH0-06: `CredentialRequestMessage` (+ nested `CredentialReference`) enforcing at least one credential id.
    - Tests: Single id accepted; blank id rejected; multiple ids preserved order.
- PH0-07: `CredentialMessage` (+ `CredentialContainer`) handling ISSUED/REJECTED status; rejection reason mandatory if
  REJECTED.
    - Tests: REJECTED without reason fails; ISSUED with two containers passes; JSON contains credentialType/format
      pairs.
- PH0-08: `IssuerMetadata` (+ `CredentialObject`) representing supported credentials; reject duplicate ids.
    - Tests: Duplicate id triggers exception; missing optional schema accepted; JSON-LD context list preserved.
- PH0-09: `CredentialOfferMessage` (+ `OfferedCredential`) list size >= 1.
    - Tests: Single offered credential OK; empty list throws; verify issuancePolicy map round-trip.
- PH0-10: `CredentialStatus` with terminal states ISSUED/REJECTED and method `isTerminal()`.
    - Tests: PENDING -> isTerminal false; ISSUED true; REJECTED true; attempt to revert status (if mutator) rejected.
- PH0-11: `ConsentRecord` entity with `isExpired(Instant)` and granted ⊆ requested enforcement.
    - Tests: Expired boundary equals now: expired; granted superset rejected; subset allowed.
- PH0-12: `ProfileId` enum & `ProfileResolver` stub mapping format + statusList presence.
    - Tests: jwt+statusList -> VC11_SL2021_JWT; jsonld no statusList -> VC11_SL2021_JSONLD; unsupported format returns
      null.
- PH0-13: Configuration properties & `DcpProperties` binding (`dcp.connector.did`, `dcp.base-url`,
  `dcp.clock-skew-seconds`, `dcp.supported-profiles`, `dcp.trusted-issuers.*`).
    - Tests: Spring loads context; default skew =120; override via test properties changes value; missing DID property
      flagged in startup (if required).
- PH0-14: Stub `SchemaRegistryService` (in-memory map) `exists/getSchema`.
    - Tests: Insert schema then exists true; getSchema returns same instance; missing returns false/null.
- PH0-15: `IssuerTrustService` (in-memory mapping credentialType→issuerDid set).
    - Tests: addTrust then isTrusted true; case sensitivity test; unknown type false.
- PH0-16: Module-level README summarizing models & config keys.
    - Tests: Markdown presence; CI check ensures README contains heading 'DCP Module'.
- PH0-17: `AbstractJsonTest` utility for round-trip serialization tests.
    - Tests: Using it with a sample model returns equivalent object; failure surfaces difference in field value.
- PH0-18: Update checkstyle (if needed) to include `it.eng.dcp` packages.
    - Tests: `mvn checkstyle:check` passes; intentional violation triggers failure.

#### 3. Test Coverage (Detailed)

Categories & Representative Tests:

1. Serialization / Deserialization
    - Each model: round-trip JSON using `AbstractJsonTest.assertRoundTrip`.
    - Empty optional lists vs omitted fields.
2. Validation Logic
    - `CredentialRequestMessage` rejects empty credentials list.
    - `CredentialMessage` REJECTED without reason throws.
    - `ConsentRecord` granted ⊄ requested triggers builder failure.
3. Enum & Resolver
    - `ProfileId` value mapping; unsupported format returns null.
4. Error Conditions
    - Builders throw `IllegalArgumentException` on blank IDs or contexts.
5. Performance Smoke
    - Bulk serialization of 1000 `CredentialContainer` objects (<250ms threshold).
6. SchemaRegistryService Stub
    - `exists` false when absent; `getSchema` returns same object instance on repeated calls (identity equality).
7. IssuerTrustService
    - Trust added then evaluated positive; absent trust negative.
      Edge Cases:

- Very long credentialType strings (truncate or accept?).
- Unicode characters in IDs.
- Empty `scope` list vs null in `PresentationQueryMessage`.

Test Classes (planned names):

- `BaseDcpMessageTest`
- `PresentationQueryMessageTest`
- `PresentationResponseMessageTest`
- `CredentialRequestMessageTest`
- `CredentialMessageTest`
- `IssuerMetadataTest`
- `CredentialOfferMessageTest`
- `CredentialStatusTest`
- `ConsentRecordTest`
- `ProfileResolverStubTest`
- `SchemaRegistryServiceStubTest`
- `IssuerTrustServiceTest`

#### 4. Phase Outcome (Logical Unit)

Upon completion, this phase delivers a compilable, structurally sound project with a new `dcp` module containing all
necessary data structures. The code can be committed and pushed to GitHub as a foundational checkpoint.

---

### **Phase 1: Identity Services (Token & DID Document)**

#### 1. Detailed Objective

To create the complete set of foundational identity services. This phase covers the core identity concepts from *
*Section 4** (Identities, Self-Issued ID Tokens) and the discovery requirements from **Sections 5.2 and 6.2**. This
includes:

1. The `SelfIssuedIdTokenService` for creating and validating tokens (**Spec 4.3, 4.3.3**).
2. A new `DidDocumentService` and controller to dynamically generate and serve the connector's own `did:web` document,
   making its public keys and service endpoints discoverable (**Spec 5.2**).

#### 2. Detailed Implementation Steps

* **Interface Definitions (Package: `it.eng.dcp.core`):**
    * **`JtiReplayCache` Interface:**
        * **Method:** `void checkAndPut(String jti, Instant expiry)`.
    * **`DidResolverService` Interface:**
        * **Purpose:** To abstract the process of resolving a DID and finding a public key **for a specific purpose**.
        * **Method:** `JWK resolvePublicKey(String did, String kid, String verificationRelationship)`.
        * **Logic:** An implementation for `did:web` will fetch the `did.json` document. It will then find the
          `verificationMethod` entry matching the `kid`. Crucially, it **MUST** then check if that `verificationMethod`'
          s ID is listed in the specified `verificationRelationship` array (e.g., the `capabilityInvocation` array) of
          the DID document. If it is not listed for that purpose, the method must throw a `SecurityException`.
          Otherwise, it returns the `publicKeyJwk`.

* **`SelfIssuedIdTokenService` Class (Package: `it.eng.dcp.service`):**
    * **Attributes:** `connectorDid` (`String`), `connectorPrivateKey` (e.g., `RSAKey`), `didResolver` (
      `DidResolverService`), `jtiCache` (`JtiReplayCache`).
* **`createAndSignToken` Method:**
    * **Parameters:** `audienceDid` (`String`), `accessToken` (`String`, nullable).
    * **Logic:** Constructs a JWT with all required claims (`iss`, `sub`, `aud`, `jti`, `iat`, `exp`) and the optional
      nested `token` claim. Signs it using the `connectorPrivateKey` and returns the serialized JWT string.
        * **`validateToken` Method:**
    * **Logic (Refined):** 1. Parse the token. 2. Resolve the issuer's public key by calling
      `didResolver.resolvePublicKey(issuerDid, kid, "capabilityInvocation")`. This now includes the purpose check. 3.
      Verify the signature. 4. Validate claims. 5. Check for replay attacks. 6. Return claims or throw exception.

* **`DidDocumentService` Class (Package: `it.eng.dcp.service`):**
    * **Purpose:** To dynamically construct the connector's DID document.
    * **Attributes:** `connectorDid` (`String`), `verificationMethodId` (`String`), `publicKeyJwk` (
      `Map<String, Object>`), `credentialServiceEndpoint` (`String`).
    * **`generateDidDocument` Method:**
        * **Logic:** Dynamically builds the DID document JSON structure, including the `verificationMethod`,
          `authentication`, `capabilityInvocation`, and `service` sections, from the configured attributes.

* **`DidDocumentController` Class (Package: `it.eng.connector.api.wellknown`):**
    * **`getDidDocument` Method (`GET /.well-known/did.json`):**
        * **Logic:** Calls `didDocumentService.generateDidDocument()` and returns the result as a JSON response.

* **New: Key Management Strategy (Spec 6.9):**
    * **Objective:** To define a clear strategy for rotating and revoking the connector's own cryptographic keys,
      ensuring long-term security and alignment with the specification.
    * **Implementation:**
        * The connector will support multiple active keys. The `DidDocumentService` will be enhanced to read a list of
          key configurations (e.g., from a secure properties file or a vault), not just a single key.
        * The `generateDidDocument` method will include all active public keys in the `verificationMethod` section of
          the `did.json`.
        * The `SelfIssuedIdTokenService` will be configured to use the *newest* key for signing new tokens.
    * **Key Rotation (`6.9.1`):**
        * **Process:** To rotate a key, a new key pair is generated and added to the configuration. The old key is
          marked as "rotated".
        * **Behavior:** The `DidDocumentService` will keep the public key of the rotated key in the `did.json` to allow
          verification of older tokens, but it will no longer be used for signing. A timestamp will be associated with
          the rotated key to guide eventual removal.
    * **Key Revocation (`6.9.2`):**
        * **Process:** In case of a compromise, a key is marked as "revoked" in the configuration.
        * **Behavior:** The `DidDocumentService` will immediately remove the public key of the revoked key from the
          `did.json`, invalidating any token signed with it.

- PH1-01: Implement `JtiReplayCache` (ConcurrentHashMap or Caffeine) with `checkAndPut(jti, exp)` throwing on replay.
    - Tests: First add succeeds; second before expiry throws; after artificial time advance allowed; concurrent adds (
      race) only one succeeds.
- PH1-02: Implement `DidResolverService` (`fetchDid`, `resolvePublicKey` with relationship enforcement,
  `resolveServiceEndpoint`).
    - Tests: Valid DID returns key; key absent -> KeyNotFound; wrong relationship -> SecurityException; service endpoint
      discovery returns correct URL.
- PH1-03: Implement `SelfIssuedIdTokenService` (`createAndSignToken`, `validateToken`) including iss=sub, aud check,
  skew handling, replay detection.
    - Tests: Happy path; tampered signature; future iat beyond skew; expired token; incorrect aud; duplicate jti.
- PH1-04: Implement `KeySetProvider` supporting rotate/revoke; retain ROTATED keys in DID doc; remove REVOKED.
    - Tests: rotate adds new active; revoke removes from DID doc; verify old key still validates token (if not revoked).
- PH1-05: Implement `DidDocumentService` generating multi-key DID document (authentication & capabilityInvocation
  arrays).
    - Tests: Document contains expected verificationMethod entries; rotated key present; revoked absent; JSON schema
      basic assertions.
- PH1-06: `DidDocumentController` `GET /.well-known/did.json` returning generated document.
    - Tests: 200 OK; ETag optional; content-type application/json.
- PH1-07: STS endpoint `POST /dcp/tokens` issuing Self-Issued ID Token with optional scopes.
    - Tests: With scopes includes token claim; without scopes excludes; invalid aud returns 422; missing aud 400.
- PH1-08: `TokenContext` capturing issuer/subject/audience/scopes/expiry/rawJwt.
    - Tests: Scope parsing merges duplicates; hasScope accurate; expiration matches JWT exp.
- PH1-09: Structured logging (TOKEN_ISSUED / TOKEN_VALIDATED) excluding sensitive material.
    - Tests: Log entry regex ensures no 'PRIVATE KEY' substring; jti present.
- PH1-10: Integration tests (token issuance, validation, DID document fetch).
    - Tests: End-to-end issuance then validation chain; DID doc retrieval; negative invalid token path.
- PH1-11: Documentation update for key rotation workflow & security notes.
    - Tests: CI doc linter ensures presence of rotation section.

#### 3. Test Coverage (Detailed)

Identity Coverage Matrix:

1. Token Issuance
    - Happy path: token contains all claims + optional access token claim when scopes provided.
    - Scopes absent: `token` claim omitted.
    - Unsupported audience format (non-did) -> 422.
2. Token Validation
    - Signature invalid (tampered) -> failure.
    - Replay attack: same jti twice; second fails.
    - Clock skew: iat > now+skew -> reject; exp < now-skew -> reject.
    - Audience mismatch -> reject.
    - Expired token near boundary (exp == now+skew) accepted; exp == now-(skew+1) rejected.
    - Concurrent validations (10 threads) verify `JtiReplayCache` thread safety.
3. Key Management
    - Rotation: new key becomes current; old still verifies existing token.
    - Revocation: revoked key removed from DID document; token signed by revoked key fails validation.
4. DID Resolution
    - Correct relationship: key listed in `authentication` returns JWK when requested with `authentication`
      relationship.
    - Wrong relationship: request `capabilityInvocation` but key not listed -> SecurityException.
    - Missing kid -> KeyNotFoundException.
5. DID Document Generation
    - Multi-key doc contains all ACTIVE+ROTATED keys only.
    - JSON shape validation (presence of required arrays).
6. STS Endpoint
    - 400 missing aud.
    - 422 malformed aud.
    - 200 returns JWT with proper header `kid` referencing active key.
7. Logging & Observability
    - Structured log line contains jti & aud; does NOT contain private key material (regex assert no 'PRIVATE').
8. Negative Security
    - Attempt path traversal DID fetch (invalid domain) sanitized.
9. Performance Smoke
    - Issuing 100 tokens sequentially < 1s.

Planned Test Classes:

- `SelfIssuedIdTokenServiceTest`
- `SelfIssuedIdTokenServiceValidationTest`
- `JtiReplayCacheConcurrencyTest`
- `KeySetProviderRotationTest`
- `DidResolverServiceTest`
- `DidDocumentServiceTest`
- `DidDocumentControllerIT`
- `TokenIssuanceControllerIT`
- `IdentityLoggingTest`

### **Phase 2: Verifiable Presentation Protocol (Holder Role)**

#### 1. Detailed Objective

To implement the server-side ("Holder") logic for securely storing Verifiable Credentials (VCs) and responding to
presentation queries from authorized Verifiers. This phase covers the core of **Section 5**, specifically the Resolution
API (**Spec 5.4**), and ensures that all returned presentations adhere to the homogeneity requirement from **Appendix
A.1.1**.

#### 2. Detailed Implementation Steps

* **`MongoCredentialRepository` Interface (Package: `it.eng.dcp.persistence`):**
    * **Framework:** Extends Spring Data's `MongoRepository<VerifiableCredential, String>`.
    * **Methods:** Inherits CRUD methods. A custom method
      `List<VerifiableCredential> findByVcTypeIn(List<String> vcTypes)` will be added to fetch credentials by their
      type.

* **`VerifiablePresentationSigner` Service (Conceptual, in `dcp` module):**
    * **Purpose:** To encapsulate the logic for cryptographically signing a Verifiable Presentation (VP).
    * **Method:** `Object sign(VerifiablePresentation vp, String format)`.
    * **Logic:** If `format` is "jwt", returns a signed JWT string. If `format` is "json-ld", adds a `proof` block to
      the VP JSON object and returns the object.

* **`PresentationService` Class (Package: `it.eng.dcp.service`):**
    * **Purpose:** To orchestrate the handling of a presentation query, ensuring profile homogeneity.
    * **Attributes:** `credentialRepository` (`MongoCredentialRepository`), `vpSigner` (`VerifiablePresentationSigner`).
    * **`createPresentation` Method:**
        * **Parameter:** `query` (`PresentationQueryMessage`).
        * **Logic (Refined for Homogeneity):**
            1. Parses `query.scope()` to determine required credential types.
            2. Fetches all matching VCs from the database using `credentialRepository`.
            3. **Groups the fetched VCs by their profile** (e.g., `vc11-sl2021/jwt`). This assumes the
               `VerifiableCredential` entity has a field to store its profile.
            4. Initializes an empty list for signed presentations.
            5. **For each group of credentials with the same profile:**
               a. Constructs a new, homogenous `VerifiablePresentation` object containing only the VCs from that group.
               b. Signs this homogenous VP using `vpSigner`.
               c. Adds the signed VP to the list of signed presentations.
            6. Wraps the list of all signed, homogenous VPs in a single `PresentationResponseMessage` and returns it.

* **`DcpController` Class (Package: `it.eng.connector.api.dcp`):**
    * **`queryPresentations` Method (`POST /dcp/presentations/query`):**
        * **Parameters:** `Authorization` header, `PresentationQueryMessage` request body.
        * **Logic (Security First):** 1. Validates the bearer token using `SelfIssuedIdTokenService.validateToken()`. If
          invalid, returns 401 Unauthorized. 2. If valid, calls `presentationService.createPresentation()` with the
          query message. 3. Returns the result with a 200 OK status.

- PH2-01: `VerifiableCredential` Mongo entity + repository (indexes on holderDid+credentialType; optional TTL on
  expirationDate).
    - Tests: Persist & retrieve; query by credentialType list; TTL index optional (if configured) reduces expired docs (
      manual check).
- PH2-02: `VerifiablePresentation` model (id, holderDid, credentialIds, profileId, proof?).
    - Tests: Serialization retains credentialIds order; missing profileId throws.
- PH2-03: `VerifiablePresentationSigner` (JWT signing with vp claims; JSON-LD adds provisional proof block).
    - Tests: JWT decodes claims; proof block fields exist; unsupported format error.
- PH2-04: `PresentationService` implementing grouping by profile & signing; handles empty results.
    - Tests: Mixed profiles produce two VPs; single profile one VP; empty credentials -> response presentations list
      empty but non-null.
- PH2-05: Consent enforcement prior to presentation creation.
    - Tests: Missing consent returns 403; expired consent returns 403; valid consent proceeds.
- PH2-06: `/dcp/presentations/query` endpoint (401 invalid token; 403 missing/expired consent; 429 rate limit).
    - Tests: Each status scenario; success returns signed presentations; rate limit threshold respected.
- PH2-07: Stub `PresentationValidationService.preflight` returning success (extended later).
    - Tests: Always returns success object; placeholder metrics unaffected.
- PH2-08: Rate limiting (token bucket per holder DID).
    - Tests: Exceed bucket then wait refill period -> request accepted.
- PH2-09: Unit tests (homogeneity grouping, consent missing, rate limit exceeded).
    - Tests: Combined test ensures ordering: token validation before consent check.
- PH2-10: Documentation sequence diagram for presentation flow.
    - Tests: Diagram file present; referenced in README.

#### 3. Test Coverage (Detailed)

1. Repository
    - Save credential; fetch by id; fetch by credentialType list.
    - Query returns empty when none match.
2. PresentationService Homogeneity
    - Mixed profiles produce multiple presentations.
    - Single profile groups all.
    - No credentials -> empty presentation list.
3. Signer
    - JWT format: decode header & claims; verify presence of vpId, credentialIds.
    - JSON-LD format: proof block present with expected fields.
    - Unsupported format triggers `UnsupportedFormatException`.
4. Consent Enforcement
    - Existing valid consent allows query.
    - Expired consent (expiresAt < now) -> 403.
    - Missing consent -> 403.
5. Rate Limiting
    - Exceed bucket -> 429.
    - Refill after interval passes.
6. Token Validation Integration
    - Invalid bearer token -> 401 before repository access.
7. Error Ordering
    - When both consent missing & token invalid -> token invalid prioritized.
8. Performance
    - Sign 50 presentations with 10 credentials each < 2s.
9. Data Integrity Placeholder
    - JSON-LD proof placeholder included; not performing full cryptographic verification yet.

Test Classes:

- `VerifiableCredentialRepositoryIT`
- `PresentationServiceHomogeneityTest`
- `VerifiablePresentationSignerTest`
- `ConsentEnforcementTest`
- `PresentationRateLimitingTest`
- `DcpControllerPresentationsIT`

### **Phase 2.1: Presentation Validation, Profiles & Schema Enforcement**

#### 1. Detailed Objective

Introduce complete Presentation & Credential validation pipeline per Spec 5.4.3 and Appendix A homogeneity, plus
schema/context checks (4.4 / 4.4.1).

#### 2. Detailed Implementation Steps

* **`PresentationValidationService`**:
* `validate(PresentationResponseMessage rsp, List<String> requiredCredentialTypes, TokenContext tokenCtx)`.
* Steps:
    1. For each presentation: verify signature; key MUST have `authentication` relationship.
    2. Enforce homogeneity: all credentials inside a VP share identical `ProfileId` (derived by `ProfileResolver` from
       credential format & vc type); reject mixed profiles.
    3. For each credential:
        * Validate signature; key MUST have `capabilityInvocation` relationship.
        * Validate issuer trust via `IssuerTrustService`.
        * Check issuanceDate, expirationDate.
        * Revocation via `RevocationService.isRevoked(vc)`.
        * Validate `@context` contains required DCP + VC DataModel contexts; no undefined terms (via JSON-LD expansion +
          check against known vocabularies).
        * Validate credential schema via `SchemaRegistryService`.
    4. Confirm requiredCredentialTypes are satisfied by non-revoked credentials.
* **`ProfileResolver`**: Determines ProfileId from `format` plus presence of StatusList and VC DataModel version.
* **Consent Enforcement**: Before querying, ensure there is a valid `ConsentRecord` matching `verifierDid` & requested
  credential types.

- PH2.1-01: Complete `PresentationValidationService.validate` with signature, profile, issuer trust, date, revocation,
  schema, context checks, producing `ValidationReport`.
    - Tests: Each rejection path sets proper error code; aggregated errors list non-empty; valid path returns accepted
      types set.
- PH2.1-02: Full `ProfileResolver` logic (format + statusList presence mapping to enum).
    - Tests: All format/status combinations; unsupported combination -> null.
- PH2.1-03: JSON-LD term validator (expansion & unknown term detection -> error code `VC_UNKNOWN_TERM`).
    - Tests: Credential with extra property flagged; known context passes.
- PH2.1-04: Schema validation integration (unknown schema -> `VC_SCHEMA_NOT_FOUND`).
    - Tests: Known schema returns valid; unknown triggers error.
- PH2.1-05: Problem Details renderer (global exception handler returning RFC 9457 JSON).
    - Tests: Simulated exception returns JSON with type/title/status/detail/correlationId.
- PH2.1-06: Composite validation test matrix (revoked, expired, untrusted issuer, unknown context term, mixed profile,
  missing required credential type).
    - Tests: Parameterized matrix enumerating combinations; ensures independence of errors.

#### 3. Test Coverage (Detailed)

Validation Pipeline Categories:

1. VP Signature Verification
    - Tampered signature -> VP rejected; error code VP_SIGNATURE_INVALID.
2. Profile Homogeneity
    - Mixed profile credentials inside single VP -> error PROFILE_MIXED.
3. Issuer Trust
    - Untrusted issuer -> error ISSUER_UNTRUSTED.
4. Date Checks
    - Credential with `expirationDate` past -> error VC_EXPIRED.
    - NotBefore future issuanceDate -> error VC_NOT_YET_VALID.
5. Revocation
    - Bit at index =1 -> VC_REVOKED.
    - Status list fetch failure -> error REVOCATION_LIST_UNREACHABLE (non-fatal if configured?).
6. Context Validation
    - Unknown term -> VC_UNKNOWN_TERM.
    - Missing required VC DataModel context -> VC_CONTEXT_INCOMPLETE.
7. Schema Validation
    - Unknown schema -> VC_SCHEMA_NOT_FOUND.
    - Known schema but structural mismatch -> VC_SCHEMA_INVALID (future extension).
8. Required Credential Types Satisfaction
    - Missing one required type -> REQUIREMENT_UNMET.
9. Aggregated Report
    - ValidationReport contains all errors with severity; VP-level vs credential-level separation.
10. Performance

- Validate VP with 25 credentials in <1s on dev machine.

11. Caching

- Repeated validation of same context uses cache (profiling test).

12. Problem Details

- Map domain errors to HTTP 422 with proper JSON body.

Parameterized Tests:

- Profile combinations.
- Revocation bit indices.
- Missing vs present schema IDs.

Test Classes:

- `PresentationValidationServiceTest`
- `ProfileResolverTest`
- `JsonLdTermValidatorTest`
- `SchemaValidationTest`
- `RevocationIntegrationTest` (shared with Phase 4)
- `ValidationPerformanceTest`
- `ProblemDetailsHandlerTest`

### **Phase 3: Credential Issuance Protocol (Holder Role)**

#### 1. Detailed Objective

To implement the client-side ("Holder") logic for requesting credentials from an Issuer and the server-side logic for
receiving them asynchronously. This phase covers the Holder's role in the Credential Issuance Protocol as defined in *
*Section 6**.

#### 2. Detailed Implementation Steps

* **`CredentialIssuanceClient` Service (Package: `it.eng.dcp.service`):**
    * **Purpose:** To encapsulate all client-side HTTP interactions with an external Issuer.
    * **Attributes:** `httpClient`, `tokenService` (`SelfIssuedIdTokenService`).
    * **`requestCredential` Method (Spec 6.4):**
        * **Parameters:** `issuerMetadata` (`IssuerMetadata`), `credentialId` (`String`), `holderPid` (`String`).
        * **Logic:** Creates a `CredentialRequestMessage`, generates a token for the Issuer's audience, and sends an
          authenticated `POST` request to the Issuer's `/credentials` endpoint. Returns the `Location` header for status
          checking.
    * **`getIssuerMetadata` Method (Spec 6.7):** Performs a `GET` request to an Issuer's `/metadata` endpoint.
    * **`getRequestStatus` Method (Spec 6.8):** Performs an authenticated `GET` request to a credential request status
      URL.

* **New Endpoints in `DcpController`:**
    * **`receiveCredentials` Method (`POST /dcp/credentials`, Spec 6.5):**
        * **Purpose:** The callback endpoint for an Issuer to deliver credentials (Storage API).
        * **Logic:** Expects a `CredentialMessage`. If `status` is "ISSUED", it iterates through the `credentials` list,
          deserializes each VC, and saves it to the database via `credentialRepository`. Returns 200 OK.
    * **`receiveOffer` Method (`POST /dcp/offers`, Spec 6.6):**
        * **Purpose:** To receive unsolicited credential offers.
        * **Logic:** Expects a `CredentialOfferMessage`. The initial implementation will log the offer's details for
          administrative review.

+Clarification: the `/credentials` path in the specification is used in two distinct roles and message shapes which
+must not be confused:
+
+- Credential Request API (Spec 6.4) — sent by a client (a potential Holder) to the Issuer Service at the Issuer's
+  base URL + `/credentials`. The request body MUST be a `CredentialRequestMessage` and on success the Issuer MUST
+  return HTTP 201 with a `Location` header pointing to the credential request status (6.8). This client-facing request
+  is implemented by `CredentialIssuanceClient.requestCredential()` in this plan (Phase 3).
+
+- Storage API (Spec 6.5) — invoked by the Issuer Service to write issued credentials into the Holder's Credential
+  Service. This is also a `POST /credentials` operation but on the Holder's Credential Service and the request body
+  MUST be a `CredentialMessage` (not a CredentialRequestMessage). To avoid an endpoint collision with local client
+  requests and to namespace implementation details, this plan exposes the Holder-side callback at `/dcp/credentials`.
+  The storage API receiver (accepting `CredentialMessage`) is implemented in Phase 3 as the `receiveCredentials`
+  controller method.
+
+Make sure any implementation or documentation clearly states which role (Issuer vs Holder) an observed `/credentials`
+request refers to. Tests and integration wiring should validate both message shapes independently.

- PH3-01: Issuer discovery (`discoverIssuerService` via DID document; fallback base URL) failing with
  `IssuerServiceNotFoundException` if missing.
    - Tests: DID with IssuerService returns URL; DID missing service triggers exception.
- PH3-02: `requestCredential` adds `requestId` UUID and returns Location header (handle 202/201; 4xx -> exception).
    - Tests: 202 returns Location; 400 triggers exception; requestId recorded.
- PH3-03: Polling `awaitStatus(requestId, timeout, backoff)` until terminal state or timeout.
    - Tests: Pending transitions then ISSUED; timeout scenario; exponential intervals captured.
- PH3-04: Callback `/dcp/credentials` persisting VCs on ISSUED or status record on REJECTED.
    - Tests: ISSUED saves credentials; REJECTED stores status only.
- PH3-05: Offer endpoint `/dcp/offers` logging & optional persistence; reject empty offered list.
    - Tests: Valid offer returns 200; empty list returns 400.
- PH3-06: `CredentialStatusRepository` find/save by requestId.
    - Tests: Retrieval after save; nonexistent returns empty Optional.
- PH3-07: Issuance E2E test (WireMock Issuer simulation).
    - Tests: Full sequence completes with persisted credential.
- PH3-08: Issuance sequence diagram & documentation.
    - Tests: Diagram existence; doc references diagram.

#### 3. Test Coverage (Detailed)

1. Issuer Discovery
    - DID document contains IssuerService → success.
    - Missing service endpoint → IssuerServiceNotFoundException.
2. Request Credential
    - 202 Accepted returns Location; stored requestId correlation.
    - 400 Bad Request → maps to CredentialRequestFailedException.
3. Status Polling
    - PENDING → ISSUED transition captured; backoff sequence executed (e.g., 200ms,400ms,...).
    - Timeout after max duration raises StatusPollingTimeoutException.
4. Callback Handling
    - ISSUED: credentials persisted; count matches `CredentialMessage.credentials.size`.
    - REJECTED: status record stored with reason.
    - Missing status field -> 400.
5. Offer Reception
    - Valid offer logs credential IDs.
    - Empty offered list → 400.
6. Resilience
    - Network failure during polling (socket exception) triggers retry until timeout.
7. Concurrency
    - Two simultaneous issuance flows maintain independent status records.
8. Security
    - Invalid signature on `CredentialMessage` (stub for later verification) -> reject.
9. Performance
    - Polling with 5 intermediate PENDING responses completes under expected timeout.

Test Classes:

- `CredentialIssuanceClientDiscoveryTest`
- `CredentialIssuanceClientRequestTest`
- `CredentialStatusPollingTest`
- `CredentialsCallbackControllerIT`
- `CredentialOfferControllerIT`
- `IssuanceConcurrencyIT`

### **Phase 4: Integration with DSP Core Logic**

#### 1. Detailed Objective

To replace the existing identity and trust mechanisms within the core DSP negotiation flow with the newly implemented
DCP services. This makes DCP the active protocol for authentication and authorization and implements the Verifier's
validation duties, including credential revocation checks, as described in **Sections 3.1, 5.4.3, and 6.10**.

#### 2. Detailed Implementation Steps (Expanded)

Algorithmic & Integration Detail:
A. RevocationService

- Data Model: `CachedStatusList { vcPayload:JsonNode, decodedBits:BitSet, fetchedAt:Instant }`.
- Cache: Map<URL, CachedStatusList> with TTL (configurable `dcp.revocation.cache.ttlSeconds`).
- Algorithm:
    1. Extract `credentialStatus.statusListCredential` and `statusListIndex`.
    2. Look up cache; if miss or expired, HTTP GET status list credential.
    3. Verify signature (reuse PresentationValidationService logic for VC signature).
    4. Decode `encodedList` Base64URL → byte[] → BitSet via iteration.
    5. Return bit at index; true = revoked.
- Error Handling: Network error -> raise `RevocationListFetchException`; malformed list →
  `RevocationListFormatException`.
  B. DcpVerifierClient
- Request: POST `holderEndpoint/dcp/presentations/query` with JSON body `{ "scope": [credentialTypes...] }`.
- Headers: `Authorization: Bearer <accessToken>`, `X-Correlation-Id`, `Accept: application/json`.
- Retries: Exponential backoff on 5xx (max 3 attempts).
- Response Mapping: 200 → deserialize; 401/403 → `RemoteHolderAuthException`; 4xx other → `RemoteHolderClientException`.
  C. PolicyCredentialExtractor
- Input: ODRL policy JSON (from existing offer object).
- Paths: `policy.permissions[*].target` OR custom extension `constraints[*].credentialType`.
- Extraction: Collect distinct credentialType strings; validate non-empty.
- Errors: Missing credential types -> `PolicyParseException`.
  D. Negotiation Inbound Flow Pseudocode:

```
validateBearerToken();
accessToken = extractNestedAccessToken();
requiredTypes = policyExtractor.extract(offer.policy);
holderEndpoint = didResolver.resolveServiceEndpoint(consumerDid, "CredentialService");
queryResponse = dcpVerifierClient.fetchPresentations(holderEndpoint, requiredTypes, accessToken);
validationReport = presentationValidationService.validate(queryResponse, requiredTypes, tokenCtx);
if (!validationReport.valid) { publishEvent(PRESENTATION_INVALID, errors); abortNegotiation(); }
else { publishEvent(PRESENTATION_ACCEPTED); continueNegotiation(); }
```

E. Negotiation Outbound Flow Pseudocode:

```
requiredTypes = policyExtractor.extract(offer.policy);
scopes = requiredTypes; // one-to-one mapping
sidt = selfIssuedIdTokenService.createAndSignToken(providerDid, accessToken(scopes), scopes);
consentRecord = consentService.create(holderDid, verifierDid, requiredTypes, scopes);
sendContractRequest(offer, sidt);
```

F. Event Integration

- New Events: TOKEN_VALIDATION_FAILED (fields: negotiationId, reason), PRESENTATION_INVALID (negotiationId,
  list<errorCodes>), CREDENTIAL_REVOKED (negotiationId, credentialId).
- AuditEventPublisher: Ensure correlationId included.
  G. Correlation ID Filter
- Servlet Filter: If header absent generate UUID; store in MDC; propagate via REST template interceptor.
  H. Failure Mode Prioritization
- Token validation failures short-circuit before any external calls.
- Presentation fetch errors (auth) vs validation errors: auth failure reported as TOKEN_VALIDATION_FAILED (distinct
  event from PRESENTATION_INVALID).
  I. Trust & Consent Interplay
- If issuer untrusted AND revoked → report both errors in validationReport; negotiation aborted.
  J. Security Considerations
- All outbound calls use HTTPS enforced by configuration; non-HTTPS endpoint results in `InsecureEndpointException`.
- Access token scope minimization (only required credential types).
  K. Performance Goals
- Inbound negotiation validation ≤ 1.5s for 10 credentials.
- Revocation bitset decoding amortized by cache reuse.
  L. Extensibility Hooks
- Strategy interfaces: `RevocationFetchStrategy`, `PolicyParsingStrategy` for future alternative implementations.

- PH4-01: `RevocationService` (StatusList2021 fetch + cache + bitstring decoding; malformed list exception).
    - Tests: Cache hit reduces network calls; malformed encodedList throws; revoked bit index returns true.
- PH4-02: `DcpVerifierClient` remote query with auth; error handling for 401/403 & non-2xx codes.
    - Tests: 200 returns response; 401 throws RemoteHolderAuthException; 500 triggers retry then success.
- PH4-03: `PolicyCredentialExtractor` parsing ODRL to extract credential types.
    - Tests: Policy with multiple constraints returns distinct set; missing credentialType triggers
      PolicyParseException.
- PH4-04: Negotiation inbound integration pipeline (token → access token → policy types → DID → presentations →
  validation → decision).
    - Tests: Successful flow reaches agreement state; invalid token aborts before external calls; invalid presentation
      aborts after fetch.
- PH4-05: Negotiation outbound token issuance with scopes aligned to required credential types.
    - Tests: Outbound SIDT scopes match required; mismatch simulation fails validation at counterparty.
- PH4-06: Issuer trust integration (fail negotiation if any VC issuer untrusted).
    - Tests: One untrusted credential triggers rejection event containing VC id.
- PH4-07: Auto consent creation (granted types subset of policy required & token scopes intersection).
    - Tests: Consent record persisted; expiration applied; subset scenario when scopes narrower.
- PH4-08: New audit/negotiation events: TOKEN_VALIDATION_FAILED, PRESENTATION_INVALID, CREDENTIAL_REVOKED.
    - Tests: Each error path publishes correct event with correlationId.
- PH4-09: Correlation ID filter & MDC propagation across DCP calls.
    - Tests: Logs across steps share correlationId; missing header generates new value.
- PH4-10: E2E tests (success, auth failure, revoked credential, untrusted issuer).
    - Tests: Each scenario executes full negotiation path, asserts final state and event emissions.

#### 3. Test Coverage (Detailed)

Negotiation & Integration Tests:

1. Inbound Negotiation Success
    - Valid token, all credentials valid & trusted & not revoked → negotiation proceeds.
2. Token Failure Cases
    - Expired token → TOKEN_VALIDATION_FAILED event; no remote call.
    - Replay token → TOKEN_VALIDATION_FAILED.
3. Presentation Fetch Failures
    - 401 from holder → RemoteHolderAuthException → negotiation aborted (event PRESENTATION_INVALID with error
      AUTH_FAILED).
    - Network timeout → retry logic; after retries failure event PRESENTATION_INVALID (error FETCH_TIMEOUT).
4. Validation Failures
    - Mixed profile → PRESENTATION_INVALID (PROFILE_MIXED).
    - Revoked credential → CREDENTIAL_REVOKED + PRESENTATION_INVALID.
    - Untrusted issuer → PRESENTATION_INVALID (ISSUER_UNTRUSTED).
    - Missing required credential type → PRESENTATION_INVALID (REQUIREMENT_UNMET).
5. Outbound Negotiation
    - SIDT constructed with correct scopes and nested access token.
    - ConsentRecord persisted; scopes subset enforcement.
6. RevocationService
    - Cache hit vs miss timing test.
    - Malformed encodedList triggers RevocationListFormatException.
7. PolicyCredentialExtractor
    - Valid policy returns expected types.
    - Missing credentialType field → PolicyParseException.
8. Correlation ID Propagation
    - Same correlationId in inbound request log, verifier client request log, validation event.
9. Audit Events Content
    - EVENT fields include negotiationId and error codes; no sensitive data.
10. Concurrency

- Two parallel negotiations with different correlationIds; distinct events.

11. Performance

- Negotiation with 15 credentials validated under SLA threshold.

12. Security

- Non-HTTPS holder endpoint -> InsecureEndpointException and abort.

13. Retry Logic

- 500 then 500 then 200 resolves; only one PRESENTATION_FETCH_RETRY log.

Test Classes:

- `NegotiationInboundFlowIT`
- `NegotiationOutboundFlowIT`
- `RevocationServiceCacheTest`
- `PolicyCredentialExtractorTest`
- `DcpVerifierClientRetryTest`
- `CorrelationIdPropagationIT`
- `AuditEventPublishingTest`
- `NegotiationPerformanceTest`

### **Phase 5: TCK Compliance Testing and Finalization**

#### 1. Detailed Objective

To formally verify the complete implementation against the official Eclipse DCP Technology Compliance Kit (TCK), fix any
deviations, and prepare the project for a compliant release. This phase ensures the implementation meets the full
requirements of **Section 7: Conformance**.

#### 2. Detailed Implementation Steps

* **TCK Environment Setup:**
    * The official `dcp-tck` project will be cloned and its configuration files will be edited to point to the local
      running instance of the `dsp-true-connector`.

* **Execution and Analysis Cycle:**
    * The TCK is run, which executes a suite of automated tests against the live connector.
    * For each failure, the TCK's logs ("what I expected") are compared against the connector's logs ("what I did") to
      pinpoint the exact deviation from the specification.

* **Iterative Fixing:**
    * The identified bug is fixed in the Java code.
    * The connector is rebuilt, restarted, and the TCK is run again. This cycle repeats until all tests pass.

* **Finalization:**
    * **Code Review:** A thorough review of all new DCP-related code is conducted.
    * **Cleanup:** All mock implementations, temporary logging, and hardcoded values are removed and replaced with
      production-ready, configurable solutions.
    * **Documentation:** Internal documentation is updated to explain the new DCP configuration and architecture.

- PH5-01: TCK setup (clone, configure endpoints, verify health reachability).
    - Tests: Health endpoint accessible; TCK configuration passes dry-run.
- PH5-02: Internal regression suite orchestrating full validation matrix pre-TCK.
    - Tests: Aggregated report lists all categories green; failing test blocks TCK run.
- PH5-03: TCK execution loop documenting failures with spec reference & fix commit IDs.
    - Tests: Each failure produces remediation test first; post-fix test passes.
- PH5-04: Hardening (replace stub proof, remove TODOs, finalize rotation docs).
    - Tests: Grep for 'TODO' in dcp module returns 0; proof implementation adds cryptographic verification.
- PH5-05: Final documentation (`doc/dcp-profile-authoring.md`, consent & trust guides).
    - Tests: Docs include examples and configuration tables; links valid.
- PH5-06: Release packaging (CHANGELOG, version bump, tag, release notes referencing task IDs & spec coverage).
    - Tests: CHANGELOG entry includes task IDs; version increment follows semantic versioning.

#### 3. Test Coverage (Detailed)

Internal Pre-TCK Suite:

1. Identity Regression: All Phase 1 tests.
2. Presentation Validation Matrix: Parameterized across profile + revocation + trust + schema states.
3. Issuance Flow: Request/Status/Callback scenarios.
4. Negotiation Scenarios: Success, token failure, validation failure, revoked, untrusted.
5. Error Object Consistency: Problem Details shape for every domain exception.
6. Performance Benchmarks: Negotiation & validation SLA.
7. Security: HTTPS enforcement, no plain HTTP endpoints, log scrubbing (no secrets/keys).
8. Race Conditions: Concurrency tests for replay cache & consent creation.
   TCK Execution:

- Map each failing test to internal component; create remediation test before fix.
  Coverage Metrics:
- Target: 90% line coverage in dcp module, 80% branch coverage for validation and token services.
- Mutation testing (optional) on `SelfIssuedIdTokenService` & `PresentationValidationService` (surviving mutants <15%).

Test Reporting:

- Generate `target/dcp-test-report.html` aggregated with categories and spec section cross-reference.

---

## Cross-Cutting Concerns & Operational Hardening

### Rate Limiting

Add a simple token bucket per holder DID for `/dcp/presentations/query` and `/dcp/credentials` callbacks.

### JTI Cache TTL

Use time-based eviction aligned with `exp` + skew; consider Caffeine or Mongo TTL index.

### DID Document Caching

Cache DID documents with short TTL (e.g. 5 min) and ETag support (optional).

### Correlation IDs

Propagate `X-Correlation-Id` header across DCP endpoints; include in error responses.

### Error Format

Use RFC 9457 Problem Details JSON: `type`, `title`, `status`, `detail`, `correlationId`.

### Documentation Updates

Add profile authoring recommendations summary (Appendix A.2) to internal docs and Phase 5 deliverables.

---

## Expanded Test Matrix Summary

| Area                    | New Tests                                                          |
|-------------------------|--------------------------------------------------------------------|
| Token Acquisition       | Scopes inclusion/exclusion, clock skew boundary                    |
| DID Discovery           | Missing CredentialService / IssuerService                          |
| Presentation Validation | Mixed profile, unknown context, untrusted issuer, revoked, expired |
| Issuance Flow           | Polling success/rejection, requestId correlation                   |
| Consent                 | Missing consent rejection                                          |
| Rate Limiting           | Excess presentation queries -> 429                                 |
| Error Formatting        | Problem Details structure correctness                              |
| JTI Replay              | Immediate and delayed replay attempt                               |
| Schema Validation       | Unknown schema id rejection                                        |

---

## Requirements Coverage Mapping (Updated)

| Spec Section                             | Coverage Status                             |
|------------------------------------------|---------------------------------------------|
| 3.1 Consent                              | Added (ConsentRecord + integration)         |
| 3.2 Trust Relationships                  | Added (IssuerTrustService)                  |
| 3.3 Transport Security                   | HTTPS only                                  |
| 4.3.2 Obtaining Tokens                   | Added (STS endpoint)                        |
| 4.3.3 Validating Tokens                  | Expanded validations                        |
| 4.4 / 4.4.1 Context & Message Processing | Added JSON-LD & schema validation pipeline  |
| 4.5 Base URL                             | Added `dcp.base-url` config                 |
| 5.1 Presentation Flow                    | Clarified via validation + consent sequence |
| 5.2 Endpoint Discovery                   | Explicit in DidResolverService              |
| 5.3 Service Security                     | Expanded authorization, scopes, errors      |
| 5.4.3 Presentation Validation            | Full validation service                     |
| 6.1 Issuance Flow                        | Added sequence & polling                    |
| 6.2/6.3 Issuer Discovery/Base URL        | Added discovery logic                       |
| 6.6.1 Offer Message                      | Model added                                 |
| 6.8.1 CredentialStatus                   | Model + polling added                       |
| Appendix A Profiles & Homogeneity        | ProfileId enum + resolver + enforcement     |
| Appendix A.2 Authoring Recommendations   | Documentation deliverable in Phase 5        |

---

## Next Steps After Plan Update

1. Implement Phase 0 addendum models & config.
2. Wire validation & discovery services incrementally (Phases 1–3).
3. Maintain mapping table; update when spec evolves.

