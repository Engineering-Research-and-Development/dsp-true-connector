# Documentation Templates

This directory contains reusable templates and standards for creating new documentation when features or protocols are added to the TRUE Connector.

## Files

| File | Purpose |
|------|---------|
| [documentation-standards.md](documentation-standards.md) | Comprehensive guidelines: audience layers, quality standards, naming conventions, doc structure |
| [documentation-skill.md](documentation-skill.md) | Copilot skill definition — use this to invoke automated documentation generation |
| [documentation-prompt-template.md](documentation-prompt-template.md) | Ready-to-use prompt: copy, fill in placeholders, paste into Copilot CLI |

## When to Use

Use these templates when adding a **new protocol** (e.g., Decentralized Claims Protocol) or a **new feature module**:

1. Read `documentation-standards.md` to understand the required documentation layers (technical, user-facing, bridge document)
2. Use `documentation-skill.md` as a Copilot skill for automated generation
3. Copy `documentation-prompt-template.md`, replace the `[PLACEHOLDERS]`, and run it in Copilot CLI

## Documentation Layers (Summary)

Every new protocol or significant feature requires three documentation layers:

| Layer | Audience | Location |
|-------|---------|---------|
| Technical doc (`*-technical.md`) | Developers | `module/doc/` |
| User guide (`*.md`) | Operators | `module/doc/` |
| Bridge/implementation guide | Both | `doc/` |

See [documentation-standards.md](documentation-standards.md) for full details.
