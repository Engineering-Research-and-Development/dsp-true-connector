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
   - `/.well-known/dspace-version` exists.
   - Version endpoint is unversioned and unauthenticated.
   - Supported versions and bindings are declared correctly.
   - The shared DSP JSON-LD context is used where required.

2. Message validation
   - Required `@context` is present.
   - Required `@type` value is exact.
   - Required identifiers and required fields are present.
   - Response types match the protocol section being implemented.

3. Endpoint validation
   - Catalog endpoints match the binding.
   - Negotiation endpoints and callback endpoints match the binding.
   - Transfer endpoints and callback endpoints match the binding.
   - HTTP status codes match spec behavior for success and error paths.

4. State machine validation
   - Negotiation only allows valid state transitions.
   - Transfer process only allows valid state transitions.
   - Terminal states do not transition further.

5. Error handling
   - Invalid state transitions return the correct client error.
   - Not-found behavior matches the binding.
   - Unauthorized behavior matches the binding.
   - Error bodies use the correct DSP error type when specified.

6. Callback handling
   - Callback URLs are resolved relative to `callbackAddress`.
   - HTTPS is supported.
   - Base URL joining handles trailing slash variations.

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
