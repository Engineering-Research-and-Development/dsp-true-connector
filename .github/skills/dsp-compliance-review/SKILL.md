---
name: dsp-compliance-review
description: Review and validation skill for Dataspace Protocol 2025-1 compliance. Use when auditing DSP behavior, preparing TCK-oriented fixes, checking message shapes, verifying state transitions, or reviewing endpoint and error semantics.
---

# DSP Compliance Review

# Purpose

Use this skill when the task is not primarily to add new DSP features, but to verify, review, or repair DSP 2025-1 conformance.

Load `dsp-foundations` and the relevant protocol skill together with this skill.

# Use this skill when

- A task asks whether an implementation is DSP 2025-1 compliant.
- A task fixes protocol bugs or TCK failures.
- A task reviews message shape, endpoint binding, or error semantics.
- A task needs a concrete checklist rather than open-ended design help.

# Compliance checklist

1. Common requirements
   - `/.well-known/dspace-version` exists and is unversioned and unauthenticated.
   - `VersionResponse` contains at least one entry with `version`, `path`, and `binding`.
   - `serviceId`, `identifierType`, and `auth` (with `protocol`, `version`, `profile`) are present when used.
   - Supported versions and bindings are declared correctly.
   - The shared DSP JSON-LD context (`https://w3id.org/dspace/2025/1/context.jsonld`) is used where required.

2. Message validation
   - Required `@context` is present.
   - Required `@type` value is exact.
   - Required identifiers and required fields are present (see per-message rules below).
   - Response types match the protocol section being implemented.

3. Catalog message checks
   - `CatalogRequestMessage` has correct `@type` and optional `filter`.
   - `DatasetRequestMessage` has required `dataset` property.
   - Catalog response has zero-to-many Datasets and one-to-many Data Services.
   - Dataset response has `hasPolicy` with at least one Offer, and `distribution` with at least one Distribution.
   - Each Distribution has at least one `DataService` with `endpointURL`.
   - Offer has `@id`; inside Catalog/Dataset response it MUST NOT have a `target` attribute.
   - Rules inside Offers MUST NOT have `target` attributes (ODRL compact policy constraint).

4. Negotiation message checks
   - `ContractRequestMessage`: `offer.@id` references a known Catalog Offer; `offer` has `target`; Rules inside Offer do NOT have `target`.
   - `ContractOfferMessage`: if initiating, contains `callbackAddress`; Offer has `target`; Rules do NOT.
   - `ContractAgreementMessage`: contains `consumerPid`, `providerPid`, and Agreement with `timestamp`, `assigner`, `assignee`, and `target`.
   - `ContractAgreementVerificationMessage`: contains `consumerPid` and `providerPid`.
   - `ContractNegotiationEventMessage`: Consumer MUST NOT send `FINALIZED`; Provider MUST NOT send `ACCEPTED`.
   - `ContractNegotiationTerminationMessage`: contains `consumerPid` and `providerPid`.

5. Transfer message checks
   - `TransferRequestMessage`: contains `consumerPid`, `agreementId`, `format`, `callbackAddress`; `dataAddress` only if push.
   - `TransferStartMessage`: contains `dataAddress` (with `endpointType`) for pull transfers; optional `authorization`/`authType` in `endpointProperties`.
   - `TransferSuspensionMessage`, `TransferCompletionMessage`, `TransferTerminationMessage`: valid in correct states.

6. Endpoint validation
   - Catalog endpoints match the binding.
   - Negotiation endpoints and callback endpoints match the binding.
   - Transfer endpoints and callback endpoints match the binding.
   - HTTP status codes match spec behavior: 201 for initiation, 200 for state changes, 400 for bad transitions, 404 for not-found and unauthorized.

7. State machine validation
   - Negotiation only allows valid state transitions through REQUESTED → OFFERED ↔ ACCEPTED → AGREED → VERIFIED → FINALIZED | TERMINATED.
   - Transfer process only allows valid state transitions.
   - Terminal states do not transition further.

8. Error handling
   - Invalid state transitions return HTTP 400 with the correct DSP error type.
   - Not-found behavior returns HTTP 404.
   - Unauthorized behavior returns HTTP 404 (not 401).
   - Error bodies use the correct DSP error type (`CatalogError`, `ContractNegotiationError`, `TransferError`).

9. Callback handling
   - Callback URLs are resolved relative to `callbackAddress`.
   - HTTPS is supported.
   - Base URL joining handles trailing slash variations.
   - Unknown `callbackAddress` in initiation messages is treated as an unrecoverable error.

# Repository-specific review guidance

- Read `.github/copilot-instructions.md` first.
- Prefer serializer and controller code over old markdown examples.
- Preserve the split between protocol JSON(-LD) and plain JSON.
- Check module-specific tests plus TCK-oriented coverage when protocol behavior changes.

# Useful validation commands for TRUE Connector

- Full verification: `mvn clean verify`
- Single module verification:
  - `mvn -pl catalog -am verify`
  - `mvn -pl negotiation -am verify`
  - `mvn -pl data-transfer -am verify`
- TCK-oriented run: `mvn -pl connector -Ptck verify`

# Expected output style

When using this skill:

1. List concrete compliance findings.
2. Separate confirmed violations from implementation-specific choices.
3. Identify exact files or modules likely responsible.
4. Suggest the smallest safe fix and the tests needed to prove it.
