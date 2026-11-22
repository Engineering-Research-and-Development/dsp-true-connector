# DCP Issuer Service

> **Status**: ✅ Active - [Decentralized Claims Protocol (DCP)](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/) Issuer Implementation

Standalone Spring Boot application that implements the Decentralized Claims Protocol (DCP) for issuing Verifiable Credentials in dataspaces. It enables trusted authorities to issue cryptographically signed credentials to participants following the DCP specification.

**Future Vision**: This module is designed to become a reusable library that any project can integrate to implement DCP-compliant credential issuance, not limited to TRUE Connector deployments.

## 🎯 What is DCP Issuer?

The DCP Issuer is a **production-ready, DCP-compliant credential issuer** that:
- Issues W3C Verifiable Credentials (VC 1.1 and VC 2.0)
- Implements [DCP v1.0 specification](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/)
- Works with any DCP-compliant holder implementation
- Provides automated key management and rotation
- Supports multiple credential profiles (`vc11-sl2021/jwt` and `vc20-bssl/jwt`)

## ✨ Key Features

- ✅ **DCP Compliant**: Fully implements Eclipse Dataspace DCP v1.0 specification
- ✅ **Interoperable**: Works with any DCP-compliant holder, not vendor-locked
- ✅ **Production Ready**: Battle-tested in TRUE Connector deployments
- ✅ **Secure**: Automated key rotation, separate DID, isolated keystore
- ✅ **Standards-Based**: W3C Verifiable Credentials, DIDs, DCP profiles
- ✅ **Easy to Deploy**: Docker support, comprehensive configuration options
- ✅ **Future-Proof**: Evolving into reusable library for broader ecosystem

## 🚀 Quick Start

Want to get started quickly? We have a comprehensive guide to get you up and running in minutes!

📖 **[Quick Start Guide](QUICKSTART.md)** - Complete setup instructions including:
- Prerequisites and installation options (local, Docker, Maven)
- Step-by-step build and run instructions  
- Key pair generation for development
- Verification steps and first API calls
- Common issues and troubleshooting

**TL;DR**: Build with `mvn clean package`, run with `java -jar target/dcp-issuer.jar`, access DID document at http://localhost:8084/.well-known/did.json

## 📚 Documentation

### Getting Started
- 📖 [Quick Start Guide](QUICKSTART.md) - Get up and running in minutes
- 🔧 [Configuration Guide](CONFIGURATION.md) - All configuration options
- 🚀 [Deployment Guide](DEPLOYMENT.md) - Production deployment strategies

### API & Integration
- 📡 [API Reference](API.md) - Complete endpoint documentation
- 🔌 [Integration Guide](INTEGRATION.md) - Use in your own dataspace project
- 🔑 [Key Management](KEY_MANAGEMENT.md) - Key rotation and security

### Development
- 💻 [Development Guide](DEVELOPMENT.md) - Contributing and building
- 🧪 [Testing Guide](TESTING.md) - Test strategies and coverage
- 🐛 [Troubleshooting](TROUBLESHOOTING.md) - Common issues and solutions

### Standards & Specifications
- 📜 [DCP Specification v1.0](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/)
- 📜 [W3C Verifiable Credentials](https://www.w3.org/TR/vc-data-model/)
- 🆔 [W3C DIDs](https://www.w3.org/TR/did-core/)

## 🏗️ Architecture

```
┌─────────────────────────────────────────┐
│         DCP Issuer Service              │
│         (Port 8084)                     │
├─────────────────────────────────────────┤
│  DID: did:web:localhost%3A8084:issuer   │
│                                         │
│  Public:  /.well-known/did.json         │
│  API:     /issuer/*                     │
│  Admin:   /api/issuer/* (HTTP Basic)    │
└─────────────────────────────────────────┘
            │
            ▼
    ┌───────────────┐
    │  MongoDB      │
    └───────────────┘
```

**Key Components:**
- **REST API**: DCP-compliant credential issuance endpoints
- **DID Management**: Issuer identity and DID document exposure
- **Key Service**: Automated key rotation and signing
- **Storage**: MongoDB for requests, credentials, and key history

See [Architecture Documentation](doc/ISSUER_MODULE_ARCHITECTURE_DIAGRAMS.md) for detailed diagrams.

## 🌐 Use Cases

This DCP issuer can be deployed in various dataspace scenarios:

- **Manufacturing**: Supplier verification credentials
- **Healthcare**: Provider authorization credentials
- **Financial**: KYC/AML compliance credentials
- **Logistics**: Carrier authorization credentials
- **Energy**: Grid participant identification
- **Academic**: Researcher verification credentials
- **Supply Chain**: Partner certification credentials

📖 See [Integration Guide](INTEGRATION.md) for integration examples.

## 🔐 Security

- **Separate Identity**: Dedicated DID and keystore (not shared with holder)
- **Key Rotation**: Automated rotation every 90 days (configurable)
- **Authentication**: Bearer token (Self-Issued ID Token) validation on the DCP protocol endpoints
- **Administration API**: `/api/issuer/**` requires HTTP Basic and the authority `DCP_ISSUER_ADMIN`; no admin user is configured by default, so it is closed until one is added (see [Deployment Guide](DEPLOYMENT.md#administration-api-users))
- **Cryptography**: ES256 (ECDSA P-256 + SHA-256) for signing
- **Database Isolation**: Separate MongoDB database

For deployment security best practices, see [Deployment Guide](DEPLOYMENT.md).

## 🤝 Using in Your Project

### Current Options

1. **Deploy Standalone**: Use as independent service in your dataspace
2. **Fork & Customize**: Adapt for your specific requirements
3. **Reference**: Study implementation for DCP compliance

## 🤝 Contributing

We welcome contributions from the **DCP and dataspace community**! 

Whether you're working on TRUE Connector, building your own DCP-compliant system, or implementing a different dataspace protocol, your contributions are valuable.

- 📖 Read [Development Guide](DEVELOPMENT.md)
- 🐛 Report issues via GitHub
- 💡 Share your use cases
- 🔧 Submit pull requests

## 💬 Support

### For TRUE Connector Users
- 📖 [TRUE Connector Documentation](../README.md)
- 🐛 [Issue Tracker](https://github.com/Engineering-Research-and-Development/dsp-true-connector/issues)

### For DCP Community
- 📖 [DCP Specification](https://eclipse-dataspace-dcp.github.io/decentralized-claims-protocol/v1.0/)
- 🌐 [Eclipse Dataspace Project](https://projects.eclipse.org/projects/technology.dataspace)
- 💬 Join Eclipse Dataspace discussions

---

⭐ **Star this repo** if you find it useful for your dataspace project!

