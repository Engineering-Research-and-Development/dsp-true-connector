# Documentation Generation Prompt Template

> **Note:** This is a reusable template for future documentation work. File paths and links shown inside code blocks (e.g., `dcp-implementation-guide.md`, `module/doc/CLAIMS-TECHNICAL.md`) are **placeholders** — replace them with the actual paths for the feature you are documenting. They do not exist yet and are not intended to be clickable links.

**Purpose**: Ready-to-use prompt template for generating standardized documentation for new features/protocols

**How to Use**: 
1. Copy the template below
2. Replace `[PLACEHOLDERS]` with your feature/protocol details
3. Paste into GitHub Copilot CLI or use as `/skill DOCUMENTATION-GENERATION` input
4. Follow the execution phases

---

## Prompt Template: Create Documentation for [FEATURE NAME]

```
I need to create standardized documentation for a new feature implementation 
following the TRUE Connector documentation standards.

## Feature Information

**Feature/Protocol Name**: [FEATURE_NAME]
Example: Decentralized Claims Protocol

**Specification Reference** (if applicable):
- Name: [SPEC_NAME]
- Version: [VERSION]
- URL: [SPEC_URL]
- Key Sections: [LIST_SECTIONS]
Example:
- Name: Decentralized Claims Specification
- Version: 1.0
- URL: https://spec.example.com/claims/v1.0
- Key Sections: Claims Creation (S3), Claim Verification (S4), Credential Management (S5)

**Module Location**: [MODULE_PATH]
Example: claims/src/main/java/it/eng/claims/

**Key Classes to Document**: [LIST_CLASSES]
Example: ClaimService, CredentialValidator, IssuerRegistry, ClaimRepository

## What This Feature Does

### Core Functionality
[DESCRIPTION_1]
Example: The Decentralized Claims module enables creation, issuance, and verification of cryptographic claims between entities in the dataspace.

[DESCRIPTION_2]
[DESCRIPTION_3]

### Key Features
- [FEATURE_1]: [DESCRIPTION]
- [FEATURE_2]: [DESCRIPTION]
- [FEATURE_3]: [DESCRIPTION]

Example:
- **Claim Creation**: Entities can create digital claims with subject, issuer, and claim data
- **Credential Validation**: System validates issuer credentials and cryptographic signatures
- **Subject Management**: Registry of subjects with associated claims and credentials

### Specification Alignment
[How does this feature relate to the spec?]

Example: This module fully implements the Decentralized Claims specification 2025-1,
including claim structure, validation rules, and credential management workflows.

## Primary Audiences & Their Needs

**Operators** (System Administrators):
- How to configure this feature
- What resources does it need
- Troubleshooting common issues
- Example configurations

**Developers** (Feature Implementers):
- Architecture and design patterns
- How to extend/customize
- API endpoints and classes
- Implementation details

**Implementers** (Deployment Engineers):
- What data flows through the system
- Protocol state machines
- Integration with other components
- Configuration requirements

## API Endpoints to Document

[LIST_ENDPOINTS]
Example:
- POST /api/claims - Create a new claim
- GET /api/claims/{id} - Retrieve claim details
- POST /api/claims/{id}/verify - Verify claim signature
- GET /api/credentials - List registered credentials

## State Machines (if applicable)

[DESCRIBE_STATE_MACHINES]
Example: 
- Claim Lifecycle: CREATED → ISSUED → VERIFIED → REVOKED
- Credential Status: ACTIVE → SUSPENDED → REVOKED

## Configuration Requirements

**Required Configuration**:
[LIST_REQUIRED]
Example:
- Issuer Registry URL
- Credential Cache Size
- Signature Algorithm

**Optional Configuration**:
[LIST_OPTIONAL]
Example:
- Claim TTL (default: 365 days)
- Verification Timeout (default: 30 seconds)
- Max Claims per Subject (default: 1000)

## External Dependencies

[LIST_DEPENDENCIES]
Example:
- OpenID Connect for issuer verification
- X.509 certificates for signature validation
- Redis for credential caching

## Tasks to Complete

Using the DOCUMENTATION-STANDARDS.md guidelines, please create:

### 1. Technical Documentation (claims/doc/CLAIMS-TECHNICAL.md)
- Architecture overview with component diagram or description
- Complete API endpoint documentation:
  - HTTP method and path
  - Parameters and request body with examples
  - Response body with examples
  - Error responses and codes
  - Authentication requirements
- Core classes documentation:
  - Purpose and responsibilities
  - Key methods with signatures
  - Example usage
- State machines documented with:
  - Current state and valid transitions
  - Terminal states
  - Error transitions
- Performance considerations
- Security implications

### 2. User/Implementation Guide (claims/doc/claims-implementation.md)
- What is this feature? (plain language, no jargon)
- When should I use it? (use cases)
- Prerequisites
- Quick start example
- How it works (step-by-step explanation)
- Configuration guide with examples
- Common workflows:
  - Basic workflow: [DESCRIBE]
  - Advanced workflow: [DESCRIBE]
- Troubleshooting section with common issues
- Integration with other modules

### 3. Bridge Document (doc/dcp-implementation-guide.md or doc/[FEATURE]-implementation-guide.md)
- Introduction to the feature/protocol
- Specification concepts explained (if applicable):
  - Concept 1: Definition and purpose
  - Concept 2: Definition and purpose
  - Concept 3: Definition and purpose
- For each concept, document:
  - What does the specification define?
  - How does the connector implement it?
  - Endpoint(s) involved
  - Request and response payloads
  - cURL examples
  - Step-by-step usage
- End-to-end workflow showing all concepts together
- Complete cURL script demonstrating entire flow
- Testing and validation section
- API reference (quick lookup table)
- Common issues and solutions

### 4. Reference Documentation (doc/dcp-reference.md or doc/[FEATURE]-reference.md - optional)
- Glossary of feature concepts
- Message types
- State machine definitions
- Links to specification
- Links to implementation guide

### 5. Updated Navigation (Update doc/README.md)
Add new section with clear paths for each audience:
```
## [Feature/Protocol] Information

### I want to use this feature
→ Start with [User Guide](user-guide.md)
→ See technical details in [Implementation Guide](dcp-implementation-guide.md)

### I want to implement/extend this
→ Start with [Developer Guide](developer-guide.md)
→ Understand protocol in [Implementation Guide](dcp-implementation-guide.md)
→ Code details in [Technical Docs](module/doc/CLAIMS-TECHNICAL.md)

### I want to understand the specification
→ See [Reference](dcp-reference.md)
→ Map to implementation in [Implementation Guide](dcp-implementation-guide.md)
```

## Quality Standards Checklist

Ensure all generated documentation meets these standards:

**Technical Documentation**:
- [ ] Complete API documentation with all parameters
- [ ] Architecture overview included
- [ ] State machines documented with diagrams/descriptions
- [ ] Performance considerations noted
- [ ] Security implications documented
- [ ] Code examples are accurate

**User Guide**:
- [ ] Plain language (no unexplained jargon)
- [ ] Step-by-step procedures are numbered
- [ ] Example configurations provided
- [ ] Troubleshooting section included
- [ ] Prerequisites listed upfront
- [ ] "See also" links provided

**Bridge Document**:
- [ ] Specification concepts explained clearly
- [ ] Implementation mapping is explicit (spec concept → endpoint)
- [ ] All endpoints documented with examples
- [ ] cURL examples are ready to execute
- [ ] State machines have clear transitions
- [ ] End-to-end workflow shown
- [ ] Can be used without reading external specification

**Overall**:
- [ ] File naming follows convention (lowercase-with-hyphens)
- [ ] All links validated (no broken references)
- [ ] Appropriate language for each audience
- [ ] Single source of truth established (bridge doc)
- [ ] Maintenance burden minimized

## Related References

- **DOCUMENTATION-STANDARDS.md**: Complete guidelines these docs should follow
- **DOCUMENTATION-SKILL.md**: Automated skill for generating this documentation
- **Reference Implementation**: `doc/dsp-implementation-guide.md`

---

## Example: Claims Protocol Documentation

To see a complete example of properly documented feature, review:
- `doc/dsp-implementation-guide.md` - Bridge document template
- `doc/dsp-protocol-reference.md` - Reference template
- Module technical documentation for architectural patterns

---

**Tips for Best Results**:

1. **Be specific about the feature scope** - What does this implement, what doesn't it?
2. **Provide real examples** - cURL commands, config files, request bodies
3. **List all endpoints** - Even if some are read-only or administrative
4. **Explain state machines clearly** - Show what transitions are valid and why
5. **Include error scenarios** - What happens when things go wrong?
6. **Test examples** - Verify all cURL commands and config examples actually work
7. **Consider all audiences** - Make sure operators, developers, and implementers each have what they need
8. **Reference the bridge** - All other docs should point users to the bridge document for protocol details

---

**After Documentation is Generated**:

1. Review each document for accuracy
2. Update examples if implementation details change
3. Test cURL commands against actual running connector
4. Validate all links in doc/README.md
5. Share with team for feedback
6. Keep documentation in sync as feature evolves

```

---

## Quick Reference: Prompt by Feature Type

### For Protocol-Based Features (DSP Components, etc.)

```
[Use full template above, emphasizing]:
- **Specification Reference**: [SPEC_URL]
- **State Machines**: Document all protocol states
- **Bridge Document**: PRIMARY deliverable
- **Spec Alignment**: Map all protocol concepts to implementation
```

### For Operational Features (Tools, Configuration, etc.)

```
[Use full template above, but]:
- Skip Specification Reference (or mark N/A)
- Focus on Configuration Requirements
- Emphasize Troubleshooting section
- Bridge Document: Configuration guide + workflows (no spec concepts)
```

### For Module Extensions

```
[Use full template above, but]:
- **Module Location**: Specific package path
- **Key Classes**: List all public APIs
- **Integration Points**: How this integrates with other modules
- Bridge Document: How this feature fits in larger workflow
```

---

## Checklist Before Running

- [ ] Feature/protocol name is clear
- [ ] Specification URL provided (if applicable)
- [ ] Module location confirmed
- [ ] All key classes listed
- [ ] Key features documented
- [ ] API endpoints identified
- [ ] State machines identified
- [ ] Configuration options documented
- [ ] External dependencies listed
- [ ] Audiences identified
- [ ] Related standards reviewed (DOCUMENTATION-STANDARDS.md)

---

**Next Steps**:

1. Fill in all [PLACEHOLDERS] above
2. Copy completed prompt
3. Run with: `/skill DOCUMENTATION-GENERATION` or paste into GitHub Copilot
4. Review generated documentation against quality checklist
5. Make adjustments as needed
6. Commit documentation to repository

---

**Version**: 1.0  
**Last Updated**: 2026-03-26  
**Compatible With**: DOCUMENTATION-STANDARDS.md v1.0, DOCUMENTATION-SKILL.md v1.0
