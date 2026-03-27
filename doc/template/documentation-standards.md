# Documentation Standards for Protocol & Feature Implementation

> **Note:** This is a standards reference for future documentation work. File paths and links shown as examples (e.g., `MODULE-TECHNICAL.md`, `protocol-implementation-guide.md`) are **placeholders** illustrating the naming pattern — replace them with the actual module and protocol names. Example links inside code blocks are not intended to be clickable.

**Version**: 1.0  
**Last Updated**: 2026-03-26  
**Purpose**: Standardized guidelines for creating consistent, high-quality documentation for new features, protocols, and modules

---

## Overview

This document establishes documentation standards used successfully for DSP (Dataspace Protocol) implementation in the TRUE Connector project. When implementing new protocols or features (e.g., Decentralized Claims Protocol, additional DSP components), follow these guidelines to ensure consistency, maintainability, and excellent user experience across all audiences.

---

## Core Principles

### 1. Three Documentation Layers

All features should produce three distinct layers of documentation:

#### Layer 1: Bridge Document (PRIMARY)
- **Purpose**: Maps external specification → implementation
- **Audience**: All personas (operators, developers, implementers)
- **Content**:
  - What is this feature/protocol? (explanation in simple terms)
  - What does the specification define? (key concepts, state machines)
  - What does the connector implement? (endpoints, payloads, examples)
  - How do I use it? (step-by-step workflows, cURL examples)
  - What's the end-to-end flow? (complete example with all steps)
- **Example**: `doc/dsp-implementation-guide.md`
- **Key Benefit**: Single source of truth for protocol ↔ implementation mapping

#### Layer 2: Reference & Glossary
- **Purpose**: Define terms and concepts
- **Audience**: Developers, implementers
- **Content**:
  - Glossary of protocol concepts
  - Message types and state machines
  - Links to external specification
  - Links back to bridge document for implementation details
- **Example**: `doc/dsp-protocol-reference.md`
- **Maintenance**: Minimal; mostly links and definitions

#### Layer 3: Specialized Guides
- **Purpose**: Serve specific audiences and use cases
- **Audience**: Varies (operators, developers, implementers)
- **Types**:
  - **User Guide**: "How do I use this?" (operators/non-technical)
  - **Developer Guide**: "How do I extend this?" (developers)
  - **Implementation Reference**: "How do I deploy/configure this?" (implementers)
  - **Technical Documentation**: "How does this code work?" (developers extending)
- **Key Characteristic**: All reference the Bridge Document for protocol details

### 2. Two Documentation Audiences

Create both **technical** and **non-technical** documentation:

#### Technical Documentation
- **For**: Developers implementing or extending features
- **Content**: Code structure, API endpoints, class diagrams, implementation patterns
- **File Example**: `module/doc/module-technical.md`
- **Quality Standards**:
  - Complete API documentation (parameters, return types, exceptions)
  - Design patterns and architectural decisions
  - Code examples where applicable
  - Performance considerations
  - Security implications

#### Non-Technical Documentation
- **For**: Operators, system administrators, business users
- **Content**: High-level workflows, configuration options, troubleshooting
- **File Example**: `module/doc/module.md` or `module/doc/module-implementation.md`
- **Quality Standards**:
  - Plain language explanations (avoid jargon)
  - Step-by-step procedures
  - Example configurations
  - Common issues and solutions
  - When to use this feature
  - Prerequisites and dependencies

### 3. Specification Alignment (For Standard-Based Features)

For features based on external specifications (e.g., DSP, DID standards):

#### What to Include
- Reference to specification version and section numbers
- Link to official specification document
- Mapping of specification concepts to implementation
- Which parts of spec are implemented vs not implemented
- Conformance level (e.g., "fully compliant" vs "partial support")

#### What NOT to Include
- Don't duplicate specification content (link instead)
- Don't copy specification examples verbatim (adapt them to connector)
- Don't enforce specification reading (bridge document should explain concepts)

#### Example Structure
```
Feature: Catalog Discovery
Specification: DSP 2025-1, Section 4 (Catalog Protocol)
Link: https://spec.example.com/catalog-protocol

What DSP Defines:
- Catalog as collection of datasets
- Discovery mechanism using DCAT-AP vocabulary
- Search capabilities required

How Connector Implements:
- GET /api/catalogs/{id} returns catalog metadata
- POST /api/catalogs/search accepts filter criteria
- Response includes DCAT-AP formatted data
[See Bridge Document for full endpoint documentation]
```

---

## Documentation Layers - Complete Structure

### Phase 1: Module-Level Documentation

#### Technical Documentation (Module-Specific)
**Filename**: `module/doc/MODULE-TECHNICAL.md`

**Structure**:
```
# MODULE Technical Documentation

## Architecture Overview
- High-level component diagram
- Key classes and their responsibilities
- Integration points with other modules

## API Endpoints
### [GET/POST/PUT] /api/endpoint/{id}
- Description
- Path parameters
- Request body (if applicable)
- Response body with example
- Error responses
- Authentication requirements

## Core Classes
### ClassName
- Purpose and responsibilities
- Key methods (signature + description)
- Example usage

## State Machines (if applicable)
- State diagram
- Valid transitions
- Terminal states

## Error Handling
- Common error codes
- What causes each error
- Recovery strategies

## Performance Considerations
- Scalability limitations
- Optimization techniques
- Resource usage

## Security
- Authentication mechanism
- Authorization rules
- Encryption/signing
- Sensitive data handling
```

#### User-Friendly Documentation (Module-Specific)
**Filename**: `module/doc/module.md` or `module/doc/module-implementation.md`

**Structure**:
```
# Using MODULE

## What is this feature?
[Plain language explanation without jargon]

## When should I use this?
[Use cases and scenarios]

## Prerequisites
[What needs to be configured first]

## Quick Start
[Simplest possible example to get started]

## How It Works
[Step-by-step explanation of workflow]

## Configuration
[Required and optional settings with examples]

## Common Workflows
### Workflow 1: Basic scenario
[Step-by-step with examples]

### Workflow 2: Advanced scenario
[Step-by-step with examples]

## Troubleshooting
| Problem | Cause | Solution |
|---------|-------|----------|
| X happens | Y reason | Z fix |

## Integration with Other Modules
[How this feature connects with others]

## See Also
- [Technical Documentation](./MODULE-TECHNICAL.md)
- [For DSP Details, see Specification Guide]
```

### Phase 2: Cross-Module Bridge Documentation

#### Bridge Document (PRIMARY - Most Important)
**Filename**: `doc/PROTOCOL-IMPLEMENTATION-GUIDE.md` (generalize for your feature)

**Purpose**: Show users how external specification maps to actual implementation

**Structure**:
```
# [Protocol/Feature] Implementation Guide

## Introduction
What is [Protocol]? Why does the connector implement it?

## Concepts Overview
[Define 3-5 core concepts in simple terms]

## [Concept 1]: Specification & Implementation
### What does the specification define?
[Explain specification concepts]

### How does the connector implement it?
- Endpoint: [METHOD /path/{param}]
- Request example with cURL
- Response example with explanation
- State machine (if applicable)

### Step-by-step: How to use it
[Numbered steps with examples]

## [Concept 2]: Specification & Implementation
[Repeat same structure]

## [Concept 3]: Specification & Implementation
[Repeat same structure]

## End-to-End Workflow
Complete example showing all concepts working together
[Numbered steps, cURL examples, request/response bodies]

## Testing & Validation
- Postman collection reference
- Health check endpoints
- Common test scenarios

## API Reference (Quick Lookup)
Table of all endpoints with brief description

## Common Issues
[Troubleshooting for protocol-related problems]

## Specification Reference
- Full specification: [Link]
- Key sections: [Links to specific sections]
- Version: [Version number]
```

#### Protocol Reference (Glossary)
**Filename**: `doc/PROTOCOL-REFERENCE.md`

**Content**:
- Glossary of protocol terms
- Message types with descriptions
- State machine definitions
- Links to specification
- Links to implementation guide

#### Updated Navigation in doc/README.md
```
## For [Feature/Protocol] Information

### I want to use this feature
→ Start with [User Guide](user-guide.md)
→ See technical details in [Implementation Guide](protocol-implementation-guide.md)

### I want to implement/extend this
→ Start with [Developer Guide](developer-guide.md)
→ Understand protocol in [Implementation Guide](protocol-implementation-guide.md)
→ Code details in [Technical Docs](module/doc/MODULE-TECHNICAL.md)

### I want to understand the specification
→ See [Protocol Reference](protocol-reference.md)
→ Map to implementation in [Implementation Guide](protocol-implementation-guide.md)
```

---

## Audience Considerations

### Audience: Operators/System Administrators

**Their Questions**:
- How do I configure this?
- What resources does it need?
- How do I troubleshoot problems?
- What should I monitor?

**Documentation They Need**:
- Non-technical module documentation
- User guide (simplified)
- Implementation reference (configuration)
- Troubleshooting section in bridge document

**What NOT to Show**:
- Internal code structure
- Algorithm details
- Deep technical specifications
- Complete API reference (summary is enough)

### Audience: Developers

**Their Questions**:
- How does this work?
- How do I extend/customize it?
- What design patterns are used?
- What are the limitations?

**Documentation They Need**:
- Technical module documentation
- Developer guide (architecture)
- Bridge document (protocol context)
- API reference

**What NOT to Show**:
- Basic configuration (too simple)
- Operational troubleshooting (not their responsibility)
- End-user use cases

### Audience: Implementers

**Their Questions**:
- What operations can I perform?
- What data do I send/receive?
- How do protocols interact?
- What is the complete workflow?

**Documentation They Need**:
- Bridge document (PRIMARY)
- Implementation reference (configuration)
- Protocol reference (concepts)
- End-to-end workflows

**What NOT to Show**:
- Internal code details
- Unnecessary theory

---

## Quality Standards & Checklist

### For All Documentation

- [ ] Written in clear, active voice
- [ ] Uses consistent terminology throughout
- [ ] Includes real examples (not abstractions)
- [ ] Explains WHY, not just WHAT
- [ ] Has clear heading hierarchy (proper markdown levels)
- [ ] Includes table of contents for documents > 5 sections
- [ ] All links are relative and validated
- [ ] Code examples are tested (or marked as "untested")
- [ ] Includes version information (when relevant)

### For Technical Documentation

- [ ] Complete API documentation
- [ ] Parameter types clearly specified
- [ ] Return types documented
- [ ] Exceptions/error codes listed
- [ ] Performance implications noted
- [ ] Thread safety documented (if applicable)
- [ ] Includes architecture diagram or description
- [ ] Code examples compile/execute
- [ ] Design patterns explained

### For Non-Technical Documentation

- [ ] No unexplained jargon or acronyms
- [ ] Step-by-step procedures are numbered
- [ ] Each step has clear purpose
- [ ] Example configurations provided
- [ ] Troubleshooting section addresses common issues
- [ ] Prerequisites are listed upfront
- [ ] Screenshots/diagrams where helpful
- [ ] "See also" section links to deeper details

### For Bridge Documents

- [ ] Specification concepts explained clearly
- [ ] Implementation mapping is explicit
- [ ] Endpoints documented with:
  - HTTP method
  - Path with parameters
  - Request body (with example)
  - Response body (with example)
  - Error responses
  - Authentication requirements
- [ ] cURL examples are ready to execute
- [ ] State machines have clear transitions
- [ ] End-to-end workflow shows all steps
- [ ] Can be used without reading external specification

---

## Naming Conventions

### File Naming
- **Critical files**: UPPERCASE (`README.md`, `CHANGELOG.md`)
- **All documentation files**: lowercase-with-hyphens (`dsp-implementation-guide.md`, `protocol-reference.md`)
- **Technical files**: include `-technical` suffix (`module-technical.md`)
- **Implementation files**: include `-implementation` suffix (`module-implementation.md`)

### Heading Levels
```markdown
# Document Title (H1 - one per document)

## Main Section (H2)

### Subsection (H3)

#### Detail Level (H4 - rarely used)
```

### Link References
- Internal: `[Text](./filename.md#section)` (relative paths)
- External spec: `[DSP Catalog](https://spec-url)` (absolute URLs, versioned if possible)
- Cross-references: Always validate links work

---

## Common Patterns

### Pattern 1: Protocol Implementation Mapping

**Use When**: Documenting features based on external specifications

```markdown
## Feature: [Name]

### Specification Definition
From [Spec Name] Section X.Y:
- Concept A: [explanation]
- Concept B: [explanation]

### Connector Implementation
The connector implements this through:

**Endpoint**: [METHOD /path]
**Authentication**: [Type]

**Request Body**:
```json
{ example }
```

**Response Body**:
```json
{ example }
```

**State Transitions**:
[Diagram or description]

**Usage Example**:
```bash
$ curl -X METHOD https://connector/path
```
```

### Pattern 2: Step-by-Step Workflow

**Use When**: Explaining multi-step processes

```markdown
## Workflow: [Name]

**Time Required**: [estimate]
**Prerequisites**: [list]

### Step 1: [Action]
[Explanation]

Example:
```bash
command here
```

Expected result: [what should happen]

### Step 2: [Action]
...

### Verification
How to confirm the workflow completed successfully:
```bash
verification command
```
```

### Pattern 3: Configuration Reference

**Use When**: Documenting configuration options

```markdown
## Configuration

### Required Settings
| Setting | Type | Description | Example |
|---------|------|-------------|---------|
| name | String | What is this? | value |

### Optional Settings
| Setting | Default | Description |
|---------|---------|-------------|
| name | default | What is this? |

### Example Configuration
```properties
key1=value1
key2=value2
```
```

### Pattern 4: Troubleshooting

**Use When**: Documenting common problems

```markdown
## Troubleshooting

### Problem: [Symptom]
**Error Message**: [if applicable]

**Possible Causes**:
1. Cause 1
2. Cause 2

**Solutions**:
1. Try this first → [steps]
2. If that doesn't work → [steps]
3. As a last resort → [steps]

**Where to Get Help**: [link to issues, support]
```

---

## File Structure Template

### For New Module Documentation

```
module/doc/
  ├── MODULE-TECHNICAL.md          # Technical deep-dive for developers
  ├── module-implementation.md      # User guide for operators
  ├── README.md                     # Module documentation index
  └── images/                       # Diagrams and screenshots (if needed)
      └── architecture.png
```

### For Cross-Module Bridge Documents

```
doc/
  ├── protocol-implementation-guide.md    # PRIMARY: specification ↔ implementation
  ├── protocol-reference.md               # Glossary and concepts
  ├── user-guide.md                       # Simplified how-to
  ├── developer-guide.md                  # Architecture and extension
  ├── implementation-reference.md         # Configuration and deployment
  ├── README.md                           # Documentation index (with navigation)
  └── images/                             # Shared diagrams
      └── protocol-architecture.png
```

---

## Maintenance Guidelines

### When Specification Changes
1. Update `protocol-reference.md` with new definitions
2. Update `protocol-implementation-guide.md` with new endpoints/payloads
3. Update module technical documentation if implementation affected
4. Validate all links still work
5. Update version numbers where applicable

### When Implementation Changes
1. Update `protocol-implementation-guide.md` endpoints/payloads/examples
2. Update relevant technical documentation
3. Update troubleshooting if new issues arise
4. All other documentation references bridge doc automatically

### Regular Review
- Monthly: Check for broken links
- Per-release: Validate documentation matches current functionality
- Per-quarter: Review for outdated patterns or missing scenarios

---

## Anti-Patterns to Avoid

### ❌ Don't
- Duplicate specification content (link instead)
- Write for a single audience (consider all three)
- Create massive documents (break into layers)
- Leave dead links (validate during reviews)
- Copy-paste code examples without testing
- Use unexplained acronyms (define first use)
- Hide important information in code comments
- Create multiple sources of truth (bridge doc is primary)
- Write too technically for operators
- Write too simply for developers

### ✅ Do
- Link to specifications
- Create three documentation layers
- Break large topics into focused files
- Validate all links work
- Test all code examples
- Define terms on first use
- Write clear, maintainable documentation
- Establish single source of truth
- Adjust language for audience
- Use clear examples

---

## Success Criteria

Documentation is successful when:

1. **Operators can use the feature** without reading code or external specifications
2. **Developers can extend the feature** using technical documentation and code
3. **Implementers can deploy and configure** using implementation reference
4. **All audiences understand** how specification maps to implementation
5. **Navigation is clear** - users reach the right document first try
6. **Maintenance is minimal** - single source of truth reduces updates
7. **Links all work** - no broken references
8. **Content is current** - matches actual implementation
9. **Language is appropriate** for each audience
10. **Examples are real** and tested

---

## Document Version & Change History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-03-26 | Initial standards document extracted from DSP documentation project |

---

## Related Documents

- `DOCUMENTATION-SKILL.md` - Copilot skill for automating documentation creation
- `PROMPT-TEMPLATE.md` - Ready-to-use prompt for new features
- Successful Example: `doc/dsp-implementation-guide.md` - Bridge document for DSP protocols

---

**Questions or Feedback?**

These standards are living documentation. If you find gaps or have improvements, update this document and share with the team.
