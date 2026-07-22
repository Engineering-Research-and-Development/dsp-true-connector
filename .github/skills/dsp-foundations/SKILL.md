---
name: dsp-foundations
description: Common requirements and cross-protocol foundations for Dataspace Protocol 2025-1. Use when implementing or reviewing DSP 2025-1 authorization, schemas, JSON-LD contexts, version discovery, service discovery, and conformance rules.
---

# DSP Foundations

# Purpose

Use this skill as the default DSP 2025-1 reference before working on catalog, contract negotiation, or transfer behavior.

Treat `Dataspace.txt` in this repository as the local normative source for DSP 2025-1. The document states that version 2025-1 is considered stable and that further changes shall not affect conformity.

# Use this skill when

- A task mentions DSP 2025-1 generally.
- A task touches multiple DSP areas.
- A task involves `/.well-known/dspace-version`, service discovery, JSON-LD, JSON Schema, authorization headers, or conformance.
- You need to separate what the specification mandates from what the implementation may choose.

# Instructions for Copilot

1. Start from the normative DSP text, not from older examples in markdown docs.
2. Distinguish clearly between:
   - normative protocol requirements
   - HTTPS binding requirements
   - implementation-specific decisions
3. Do not invent protocol fields, states, endpoints, or response semantics.
4. When a requirement is implementation-specific, call it out explicitly instead of presenting it as mandated by the spec.
5. When local docs and code disagree, prefer current repository serializers, constants, and protocol controllers over stale examples.

# Normative anchors

- Requests to HTTPS endpoints SHOULD use the Authorization header. Token semantics are out of scope.
- All protocol messages are normatively defined by JSON Schema.
- JSON-LD 1.1 is used so implementations can interoperate between plain JSON and JSON-LD processing.
- The shared DSP 2025-1 context is `https://w3id.org/dspace/2025/1/context.jsonld`.
- Each Connector MUST expose an unversioned and unauthenticated `/.well-known/dspace-version` endpoint.
- The version response links protocol version, binding, and path.
- If a Connector cannot identify a matching protocol version, it MUST terminate communication.
- Participants MAY advertise services in DID documents using `CatalogService` or `DataService`.

# Implementation-specific decisions to surface

- Authorization protocol and auth profile details.
- Participant identifier types.
- DID resolution and trust verification.
- Support for custom JSON-LD terms and profiles.
- Handling and logging strategy when a remote peer does not support a matching version.

# Repository hints for TRUE Connector

- Read `.github/copilot-instructions.md` first.
- Respect the repository split between protocol JSON(-LD) and plain internal JSON.
- Use existing serializers when crossing that boundary:
  - `CatalogSerializer`
  - `NegotiationSerializer`
  - `TransferSerializer`
  - `ToolsSerializer`
- Shared behavior commonly lives in `tools`.
- Security and auth changes should be checked against `doc/security.md`.

# Expected output style

When using this skill, structure answers and changes like this:

1. State the exact DSP rule or section that matters.
2. Map that rule to likely repository touchpoints.
3. Identify any implementation-specific decisions that remain.
4. Suggest validation steps or tests for the affected behavior.
