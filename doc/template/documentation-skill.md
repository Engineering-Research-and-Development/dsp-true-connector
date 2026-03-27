---
mode: 'agent'
description: 'Documentation generator for protocol/feature implementation following standardized guidelines'
model: 'claude-sonnet-4.6'
tools: ['explore', 'view', 'grep', 'bash']
---

# Documentation Generation Skill

This skill generates standardized, audience-aware documentation for new protocols and features in the TRUE Connector project, ensuring consistency with the DSP implementation documentation approach.

## Skill Activation

This skill activates when you need to create documentation for:
- New protocols based on external specifications
- New features or modules
- Decentralized Claims Protocol (example)
- Any future protocol implementation

## Input Requirements

When invoking this skill, provide:

1. **Feature/Protocol Name**: What are you documenting?
2. **Specification Reference** (if applicable): Link to external spec
3. **Module Location**: Where is the code? (e.g., `tools/src/main/java/it/eng/tools/`)
4. **Audience Scope**: Who will use this? (operators, developers, both)
5. **Key Functionality**: What core features does this implement?

## Skill Execution Process

The skill executes in this sequence:

### Phase 1: Analysis
```
Analyze codebase in module location
├─ Identify main classes and APIs
├─ Extract endpoints/interfaces
├─ Map specification concepts to code
└─ Identify state machines and workflows
```

### Phase 2: Documentation Generation
```
Create standardized documentation
├─ Technical Documentation (MODULE-TECHNICAL.md)
│  ├─ Architecture overview
│  ├─ API endpoints/classes
│  ├─ State machines
│  └─ Performance/security
│
├─ Implementation/User Guide (module-implementation.md or module.md)
│  ├─ Plain language explanations
│  ├─ Configuration examples
│  ├─ Step-by-step workflows
│  └─ Troubleshooting section
│
└─ Bridge Document (PROTOCOL-IMPLEMENTATION-GUIDE.md)
   ├─ Specification concepts → implementation mapping
   ├─ All endpoints with payloads
   ├─ cURL examples
   └─ End-to-end workflows
```

### Phase 3: Audience Customization
```
Ensure documentation serves all audiences
├─ Operators: Find configuration + examples
├─ Developers: Find architecture + extension points
└─ Implementers: Find protocol mapping + workflows
```

### Phase 4: Quality Assurance
```
Validate against standards
├─ Check naming conventions
├─ Validate link references
├─ Verify examples accuracy
├─ Ensure audience clarity
└─ Review completeness
```

## Output

The skill produces:

**For Module-Level Documentation**:
- `module/doc/MODULE-TECHNICAL.md` - Technical deep-dive
- `module/doc/module-implementation.md` - User/operator guide

**For Cross-Module Documentation**:
- `doc/protocol-implementation-guide.md` - PRIMARY bridge document
- `doc/protocol-reference.md` - Glossary (optional)
- Updated `doc/README.md` with navigation paths

**All output follows**:
- File naming convention: lowercase-with-hyphens
- Quality standards from DOCUMENTATION-STANDARDS.md
- Three-layer documentation structure
- Audience-specific language and detail levels

## Usage Example

### Invoke the Skill

When implementing a new feature (e.g., Decentralized Claims Protocol):

```
/skill DOCUMENTATION-GENERATION

Please create documentation for:
- Feature: Decentralized Claims Protocol
- Specification: https://spec.example.com/dcp/v1.0
- Module: claims/src/main/java/it/eng/claims/
- Key Features:
  - Claim creation and verification
  - Subject/issuer/credential management
  - Claim validation workflow
- Primary Audiences: Developers (70%), Operators (20%), Implementers (10%)
```

### Expected Output

```
✅ DOCUMENTATION GENERATION COMPLETE

Phase 1: Analysis
├─ Scanned 8 classes (ClaimService, CredentialValidator, IssuerRegistry)
├─ Identified 6 REST endpoints (/api/claims/*, /api/credentials/*)
├─ Mapped 4 spec concepts to implementation
└─ Found 3 state machines (claim lifecycle, credential validation)

Phase 2: Generated Files
├─ claims/doc/CLAIMS-TECHNICAL.md (4 KB)
│  └─ 6 API endpoints documented, state machines explained
├─ claims/doc/claims-implementation.md (6 KB)
│  └─ Configuration guide, troubleshooting, examples
├─ doc/dcp-implementation-guide.md (12 KB)
│  └─ Bridge document: spec concepts + endpoints + workflows
└─ doc/dcp-reference.md (3 KB)
   └─ Glossary of claims concepts

Phase 3: Navigation Updated
├─ Updated doc/README.md with Claims section
└─ Created navigation paths for all three audiences

Phase 4: Quality Check
├─ ✅ File naming validated
├─ ✅ 24 links checked (all valid)
├─ ✅ cURL examples tested
├─ ✅ Audience clarity verified
└─ ✅ Completeness checklist passed (32/32 items)

READY FOR REVIEW
→ Review claims/doc/CLAIMS-TECHNICAL.md for completeness
→ Review claims/doc/claims-implementation.md for clarity
→ Review doc/dcp-implementation-guide.md for accuracy
→ Update doc/README.md if needed
```

## Quality Standards

The skill ensures all output meets these standards:

### Technical Documentation
- [x] Complete API documentation
- [x] Architecture overview included
- [x] State machines documented
- [x] Performance/security noted
- [x] Code examples tested

### User/Implementation Documentation
- [x] Plain language explanations
- [x] Configuration examples provided
- [x] Step-by-step procedures
- [x] Troubleshooting section
- [x] Prerequisites listed

### Bridge Documentation
- [x] Specification concepts explained
- [x] All endpoints documented
- [x] Request/response payloads shown
- [x] cURL examples ready to execute
- [x] End-to-end workflow included

### Overall
- [x] Naming conventions followed
- [x] All links validated
- [x] Appropriate for audiences
- [x] Single source of truth established
- [x] Maintenance burden minimized

## Customization Options

### Audience Focus
```
--audience operators        # Focus on configuration and troubleshooting
--audience developers       # Focus on architecture and extension
--audience all              # Balance for all three (default)
```

### Documentation Depth
```
--depth quick              # Minimal documentation (getting started)
--depth standard           # Standard depth (default)
--depth comprehensive      # Extensive documentation (all scenarios)
```

### Specification Alignment
```
--with-spec                # Include specification mapping (default)
--without-spec             # Skip specification references
--spec-only                # Only specification-related content
```

### Output Location
```
--output session            # Save to session workspace (for review)
--output repo              # Commit directly to repository (requires approval)
```

## Integration with Standards

This skill automatically applies the guidelines from `DOCUMENTATION-STANDARDS.md`:

- **Three-layer structure**: Bridge → Reference → Specialized guides
- **Audience considerations**: Operators, developers, implementers
- **Quality standards**: All checklists validated
- **Naming conventions**: lowercase-with-hyphens for new files
- **File structure**: Organized as specified
- **Maintenance guidelines**: Single source of truth established

## When to Use This Skill

✅ **Use this skill when**:
- Implementing a new protocol (DSP component, claims protocol, etc.)
- Adding a major feature that needs user documentation
- Onboarding new developers to a module
- Creating documentation that multiple audiences will consume
- Need to maintain consistency with existing DSP documentation

❌ **Don't use this skill for**:
- Code comments (use inline documentation)
- Commit messages (use conventional commit format)
- API quick-start (unless as part of larger feature)
- Single-audience technical documentation (use manual approach)

## Related References

- **DOCUMENTATION-STANDARDS.md** - Complete documentation guidelines this skill implements
- **PROMPT-TEMPLATE.md** - Ready-to-use prompt for specific features
- **Successful Example**: `doc/dsp-implementation-guide.md` - Reference implementation

## Troubleshooting

### "Generated documentation is too technical"
→ Set `--audience operators` to focus on non-technical clarity

### "Missing configuration details"
→ Ask skill to specifically document property files and config classes

### "Links to external spec are wrong"
→ Provide correct specification URL in input or update in output

### "cURL examples don't work"
→ Skill will note any assumptions about connector running and port numbers

## Feedback & Improvements

This skill learns from usage. After execution, feedback on:
- Documentation quality
- Missing sections
- Clarity for each audience
- Specification mapping accuracy

helps refine future documentation generation.

---

**Skill Version**: 1.0  
**Last Updated**: 2026-03-26  
**Maintenance**: Update when DOCUMENTATION-STANDARDS.md changes
