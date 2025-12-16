# Verifiable Credentials Documentation - Index

## 📚 Overview

This directory contains comprehensive documentation about Verifiable Credentials (VCs) and Verifiable Presentations (VPs) implementation in the TRUE Connector. The documentation is organized for different audiences and use cases.

## 🎯 Choose Your Path

### For Non-Technical Readers
**Start here:** [Verifiable Credentials: A Simple Overview](verifiable-credentials-overview.md)

This document explains:
- What verifiable credentials are in simple terms
- Why they're important for dataspaces
- How they work in our application
- Real-world analogies and comparisons

**Best for:** Project managers, business stakeholders, non-developers

---

### For Developers & Technical Staff
**Start here:** [Verifiable Credentials: Technical Guide](verifiable-credentials-technical.md)

This document covers:
- Complete VC/VP flow with code references
- DID and DID Documents explained
- All API endpoints with examples
- Authentication flow implementation
- Debugging tips and common issues
- MongoDB queries and testing tools

**Best for:** Developers, DevOps, QA engineers, technical architects

---

### For Quick Reference
**Use this:** [Verifiable Credentials: Quick Reference](verifiable-credentials-quick-reference.md)

This cheat sheet includes:
- Common API curl commands
- Configuration properties
- MongoDB queries
- Troubleshooting quick fixes
- Status codes reference
- DID format examples

**Best for:** Daily development work, quick lookups, troubleshooting

---

## 📖 Complete Documentation Tree

### New Focused Documentation (This Set)

| Document                                                                         | Audience | Purpose | Size |
|----------------------------------------------------------------------------------|----------|---------|------|
| [Verifiable credentials overview](verifiable-credentials-overview.md)            | Non-technical | Understanding VCs conceptually | ~5KB |
| [Verifiable credentials technical](verifiable-credentials-technical.md)          | Developers | Implementation details & debugging | ~27KB |
| [Verifiable credentials quick reference](verifiable-credentials-quick-reference.md) | Developers | Quick commands & troubleshooting | ~8KB |

### Existing Detailed Documentation

| Document                                                            | Focus | Location |
|---------------------------------------------------------------------|-------|----------|
| [Verifiable credentials](verifiable_credentials.md)              | DID & VC concepts, theory | `doc/` |
| [DCP README](../README.md)                                          | DCP module overview | `dcp/` |
| [Quick start](quick_start.md)                                       | Fast-track Postman testing | `dcp/doc/` |
| [Practical example](practical_example.md)                           | Complete VP with JWT VCs example | `dcp/doc/` |
| [Presentation verification flow](presentation_verification_flow.md) | VP creation & validation flow | `dcp/doc/` |
| [Credential message examples](credential_message_examples.md)       | Ready-to-use JSON examples | `dcp/doc/` |
| [VC/VP Authentication](vc_vp_authentication.md)                     | Authentication implementation | `dcp/doc/` |
| [VC/VP Architecture diagram](vc_vp_architecture_diagram.md)         | Architecture flow diagrams | `dcp/doc/` |

---

## 🚀 Getting Started Paths

### Path 1: I'm New to Verifiable Credentials
1. Read: [Verifiable credentials overview](verifiable-credentials-overview.md)
2. Read: [Verifiable credentials](verifiable_credentials.md) (DID & VC theory)
3. Try: [Quick start](quick_start.md) (hands-on with Postman)
4. Reference: [verifiable credentials quick reference](verifiable-credentials-quick-reference.md)

### Path 2: I Need to Implement/Debug VCs
1. Reference: [Verifiable Credentials Technical](verifiable-credentials-technical.md)
2. Try: [Quick Start](quick_start.md)
3. Study: [Practical Example](practical_example.md)
4. Deep dive: [Presentation Verification Flow](presentation_verification_flow.md)
5. Keep handy: [Verifiable Credentials Quick Reference](verifiable-credentials-quick-reference.md)

### Path 3: I'm Troubleshooting an Issue
1. Check: [Verifiable Credentials Quick Reference](verifiable-credentials-quick-reference.md) (Troubleshooting section)
2. Enable debug logging from: [Verifiable Credentials Technical](verifiable-credentials-technical.md) (Debugging section)
3. Review flow: [Presentation Verification Flow](presentation_verification_flow.md)
4. Check authentication: [VC/VP Authentication](vc_vp_authentication.md)

---

## 🔑 Key Concepts Quick Reference

### The Triangle of Trust
- **Issuer**: Creates and signs credentials
- **Holder**: Stores credentials and creates presentations
- **Verifier**: Validates presentations and credentials

### DID (Decentralized Identifier)
- Format: `did:web:example.com#key-1`
- Resolves to DID Document with public keys
- No central authority needed

### Verifiable Credential (VC)
- Digital credential signed by issuer
- Contains claims about subject
- Can be verified cryptographically

### Verifiable Presentation (VP)
- Bundle of VCs signed by holder
- Proves holder controls credentials
- Sent to verifier for validation

---

## 📦 Code Locations

### DCP Module
```
dcp/src/main/java/it/eng/dcp/
├── rest/          # API controllers
├── service/       # Business logic
├── model/         # Data models
├── repository/    # MongoDB repositories
└── signer/        # VP signing
```

### Connector Authentication
```
connector/src/main/java/it/eng/connector/configuration/
├── VcVpAuthenticationFilter.java
├── VcVpAuthenticationProvider.java
└── WebSecurityConfig.java
```

See [Verifiable Credentials Technical](verifiable-credentials-technical.md#code-references) for detailed code references.

---

## 🌐 External Resources

### W3C Standards
- [Verifiable Credentials Data Model](https://www.w3.org/TR/vc-data-model/)
- [DID Core Specification](https://www.w3.org/TR/did-core/)

### Eclipse Decentralized Claims Protocol
- [DCP Protocol](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/)

### Eclipse Dataspace Protocol
- [DCP Specification](https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/)

### Tools
- [JWT.io](https://jwt.io) - Decode and inspect JWT tokens
- [DID Web Resolver](https://dev.uniresolver.io/) - Test DID resolution

---

## 🎓 Learning Path

### Beginner
1. ✅ Understand what VCs are ([Overview](verifiable-credentials-overview.md))
2. ✅ Learn about DIDs ([Verifiable Credentials](verifiable_credentials.md))
3. ✅ Try examples in Postman ([Quick Start](QUICK_START.md))

### Intermediate
1. ✅ Study the complete flow ([Technical Guide](verifiable-credentials-technical.md))
2. ✅ Understand authentication ([VC/VP Authentication](vc_vp_authentication.md))
3. ✅ Work with real credentials ([Practical Example](PRACTICAL_EXAMPLE.md))

### Advanced
1. ✅ Deep dive into validation ([Presentation Verification Flow](PRESENTATION_VERIFICATION_FLOW.md))
2. ✅ Study architecture diagrams ([VC/VP Architecture Diagram](vc_vp_architecture_diagram.md))
3. ✅ Contribute to implementation (review code references)

---

## 💡 Quick Tips

- **Always start with the overview** if you're new to VCs
- **Use the quick reference** for daily work
- **Check the technical guide** when debugging
- **Enable debug logging** when troubleshooting: `logging.level.it.eng.dcp=DEBUG`
- **Use Postman collection** for testing: `True_connector_DSP.postman_collection.json`
- **Verify MongoDB** to check stored credentials
- **Decode JWTs** at jwt.io to inspect credentials

---

## 📞 Need Help?

1. Check the troubleshooting sections in:
   - [Quick Reference](verifiable-credentials-quick-reference.md#troubleshooting-quick-fixes)
   - [Technical Guide](verifiable-credentials-technical.md#debugging-tips)

2. Review related documentation:
   - [Security](../../doc/security.md)
   - [Development Procedure](../../doc/development_procedure.md)

3. Check logs with debug enabled:
   ```properties
   logging.level.it.eng.dcp=DEBUG
   logging.level.org.springframework.security=DEBUG
   ```
   

