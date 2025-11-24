# Certificate Documentation Index

Welcome to the DSP True Connector Certificate Documentation! This index helps you find the right documentation for your needs.

## 🎯 Quick Navigation

### For Your Specific Questions
**"Will connector-a to connector-b TLS work?"**
→ **[CONNECTOR_TLS_COMMUNICATION.md](CONNECTOR_TLS_COMMUNICATION.md)** - Complete answer

**"Will Docker with hostnames connector-a and connector-b work?"**
→ **[DOCKER_HOSTNAME_SETUP.md](DOCKER_HOSTNAME_SETUP.md)** - **YES! Read this!**

### Getting Started
→ **[CERTIFICATES_README.md](CERTIFICATES_README.md)** - Complete setup guide and concepts

### Specific Topics
- **MinIO Configuration:** [MINIO_SETUP.md](MINIO_SETUP.md)
- **PKI Deep Dive:** [../../../doc/pki_architecture_guide.md](../../../doc/pki_architecture_guide.md)

## 📚 Documentation Files

### 1. DOCKER_HOSTNAME_SETUP.md (NEW)
**Purpose:** Answers "Will Docker with hostnames connector-a and connector-b work?"

**Contents:**
- ✅ Quick YES answer with proof
- ✅ Why it works (SANs match Docker hostnames)
- ✅ Complete Docker Compose example
- ✅ Visual TLS handshake flow in Docker
- ✅ Comparison: what works vs what doesn't
- ✅ Testing checklist

**Best For:** Docker deployment with hostname-based setup

### 2. CONNECTOR_TLS_COMMUNICATION.md (NEW)
**Purpose:** Answers the question "Will TLS work between connector-a and connector-b?"

**Contents:**
- ✅ Quick answer with visual diagrams
- ✅ TLS handshake flow step-by-step
- ✅ Connection URL matrix (what works, what doesn't)
- ✅ Configuration examples for different deployments
- ✅ Docker deployment scenarios
- ✅ Troubleshooting guide

**Best For:** Understanding connector-to-connector TLS communication

### 3. CERTIFICATES_README.md (UPDATED)
**Purpose:** Complete certificate setup and configuration guide

**Contents:**
- ✅ Quick start guide
- ✅ Certificate generation script usage
- ✅ PKI hierarchy explanation
- ✅ Certificate types and roles
- ✅ Understanding hostname verification (NEW)
- ✅ Connector-to-connector communication (NEW)
- ✅ Spring Boot configuration
- ✅ Verification commands
- ✅ Troubleshooting
- ✅ Security best practices

**Best For:** First-time setup and comprehensive reference

### 4. MINIO_SETUP.md (NEW)
**Purpose:** Complete MinIO TLS configuration guide

**Contents:**
- ✅ MinIO certificate requirements (PEM format)
- ✅ Certificate generation for MinIO
- ✅ Docker configuration (3 different approaches)
- ✅ Verification procedures
- ✅ Spring Boot S3 client configuration
- ✅ Troubleshooting (PKIX, hostname verification, etc.)
- ✅ Certificate renewal procedures
- ✅ Security best practices

**Best For:** Setting up MinIO with TLS

### 5. PKI Architecture Guide (NEW)
**Location:** `doc/pki_architecture_guide.md`

**Purpose:** Deep dive into PKI concepts and architecture

**Contents:**
- ✅ Complete PKI hierarchy explanation
- ✅ Certificate types and roles in detail
- ✅ TLS handshake process with diagrams
- ✅ Hostname verification deep dive
- ✅ Subject Alternative Names (SANs) strategy
- ✅ Practical scenario: connector-a to connector-b (NEW)
- ✅ Trust chain validation
- ✅ Security best practices
- ✅ Certificate renewal strategies

**Best For:** Understanding the "why" behind the configuration

### 6. CERTIFICATE_UPDATES_SUMMARY.md (NEW)
**Purpose:** Summary of all changes made to certificates and documentation

**Contents:**
- ✅ Script changes (specific SANs per service)
- ✅ Documentation updates
- ✅ Key concepts explained
- ✅ Testing recommendations
- ✅ Documentation correlation matrix

**Best For:** Understanding what changed and why

### 7. generate-certificates.cmd (UPDATED)
**Purpose:** Automated certificate generation script

**Updates:**
- ✅ Specific SANs per service (connector-a, connector-b, minio)
- ✅ Configurable SAN variables
- ✅ Generates complete PKI hierarchy
- ✅ Creates both PKCS12 and PEM formats
- ✅ Automatic verification

**Best For:** Generating all certificates with one command

### 8. renew-certificates.cmd (NEW)
**Purpose:** Selective certificate renewal without regenerating CAs

**Features:**
- ✅ Interactive menu for selective renewal
- ✅ Renew connector-a, connector-b, or minio individually
- ✅ Automatic backup of old certificates (timestamped)
- ✅ Preserves Root CA and Intermediate CA
- ✅ Update SANs without full regeneration
- ✅ MinIO PEM format support

**Best For:** Annual certificate rotation, SAN updates, individual certificate renewal

### 9. CERTIFICATE_RENEWAL_GUIDE.md (NEW)
**Purpose:** Complete guide for renewing certificates

**Contents:**
- ✅ When to use renewal vs full generation
- ✅ Step-by-step renewal procedures
- ✅ Renewal scenarios (single cert, all certs, SAN updates)
- ✅ Configuration and customization
- ✅ Certificate lifecycle management
- ✅ Verification and testing
- ✅ Troubleshooting
- ✅ Backup and recovery
- ✅ Best practices

**Best For:** Understanding and performing certificate renewals

## 🎓 Learning Path

### Beginner: Just Want It to Work
1. Read: [CONNECTOR_TLS_COMMUNICATION.md](CONNECTOR_TLS_COMMUNICATION.md) - Quick answer
2. Run: `generate-certificates.cmd` - Generate certificates
3. Test: Start both connectors and verify TLS works

### Intermediate: Understand the Concepts
1. Read: [CERTIFICATES_README.md](CERTIFICATES_README.md) - Complete guide
2. Read: "Understanding Hostname Verification" section
3. Read: "Connector-to-Connector Communication" section
4. Configure: MinIO using [MINIO_SETUP.md](MINIO_SETUP.md)

### Advanced: Deep Understanding
1. Read: [PKI Architecture Guide](../../../doc/pki_architecture_guide.md) - Full theory
2. Study: Trust chain validation
3. Study: TLS handshake flow diagrams
4. Implement: Custom SAN configurations for production

## 🔍 Find Answers by Topic

### Trust Validation
- **What is it?** [PKI Architecture Guide](../../../doc/pki_architecture_guide.md) → "Trust Chain Validation"
- **How to configure?** [CERTIFICATES_README.md](CERTIFICATES_README.md) → "Configuration in Spring Boot"
- **Troubleshooting?** [CERTIFICATES_README.md](CERTIFICATES_README.md) → "PKIX Exception"

### Hostname Verification
- **Concept explained:** [CERTIFICATES_README.md](CERTIFICATES_README.md) → "Understanding Hostname Verification"
- **Deep dive:** [PKI Architecture Guide](../../../doc/pki_architecture_guide.md) → "Hostname Verification"
- **Practical example:** [CONNECTOR_TLS_COMMUNICATION.md](CONNECTOR_TLS_COMMUNICATION.md) → "How It Works"

### Subject Alternative Names (SANs)
- **Configuration:** [CERTIFICATES_README.md](CERTIFICATES_README.md) → "Edit Configuration"
- **Strategy:** [PKI Architecture Guide](../../../doc/pki_architecture_guide.md) → "Subject Alternative Names"
- **Security benefits:** All three main guides

### Certificate Generation
- **Quick start:** [CERTIFICATES_README.md](CERTIFICATES_README.md) → "Run the Script"
- **Script details:** Comments in `generate-certificates.cmd`
- **Workflow explained:** [PKI Architecture Guide](../../../doc/pki_architecture_guide.md) → "Certificate Generation"

### Certificate Renewal
- **Complete guide:** [CERTIFICATE_RENEWAL_GUIDE.md](CERTIFICATE_RENEWAL_GUIDE.md)
- **Renewal script:** `renew-certificates.cmd` (interactive menu)
- **When to renew:** [CERTIFICATE_RENEWAL_GUIDE.md](CERTIFICATE_RENEWAL_GUIDE.md) → "When to Use This Script"
- **Lifecycle:** [CERTIFICATE_RENEWAL_GUIDE.md](CERTIFICATE_RENEWAL_GUIDE.md) → "Certificate Lifecycle"

### Connector Communication
- **Quick answer:** [CONNECTOR_TLS_COMMUNICATION.md](CONNECTOR_TLS_COMMUNICATION.md)
- **Detailed flow:** [PKI Architecture Guide](../../../doc/pki_architecture_guide.md) → "Practical Scenario"
- **Configuration:** [CERTIFICATES_README.md](CERTIFICATES_README.md) → "Connector-to-Connector"

### MinIO Configuration
- **Complete guide:** [MINIO_SETUP.md](MINIO_SETUP.md)
- **Quick reference:** [CERTIFICATES_README.md](CERTIFICATES_README.md) → mentions MinIO
- **PEM format:** [MINIO_SETUP.md](MINIO_SETUP.md) → "Certificate Generation"

### Docker Deployment
- **Connector scenarios:** [CONNECTOR_TLS_COMMUNICATION.md](CONNECTOR_TLS_COMMUNICATION.md) → "Docker Compose"
- **MinIO scenarios:** [MINIO_SETUP.md](MINIO_SETUP.md) → "Docker Configuration"
- **Network setup:** [CONNECTOR_TLS_COMMUNICATION.md](CONNECTOR_TLS_COMMUNICATION.md) → "Container Network"

### Troubleshooting
- **PKIX errors:** All main guides have troubleshooting sections
- **Hostname verification errors:** [CONNECTOR_TLS_COMMUNICATION.md](CONNECTOR_TLS_COMMUNICATION.md) → "Troubleshooting"
- **MinIO specific:** [MINIO_SETUP.md](MINIO_SETUP.md) → "Troubleshooting"

### Security Best Practices
- **Overview:** [CERTIFICATES_README.md](CERTIFICATES_README.md) → "Production Recommendations"
- **Detailed:** [PKI Architecture Guide](../../../doc/pki_architecture_guide.md) → "Security Best Practices"
- **MinIO specific:** [MINIO_SETUP.md](MINIO_SETUP.md) → "Security Best Practices"

## ❓ Common Questions

### Q: Will connector-a to connector-b TLS work?
**A:** ✅ YES! Read [CONNECTOR_TLS_COMMUNICATION.md](CONNECTOR_TLS_COMMUNICATION.md) for complete explanation.

### Q: Why do I get PKIX exceptions?
**A:** Usually truststore issue. See [CERTIFICATES_README.md](CERTIFICATES_README.md) → "Troubleshooting" → "PKIX Exception"

### Q: Why do I get hostname verification errors?
**A:** URL hostname doesn't match certificate SANs. See [CONNECTOR_TLS_COMMUNICATION.md](CONNECTOR_TLS_COMMUNICATION.md) → "Troubleshooting"

### Q: Do intermediate CA SANs apply to server certificates?
**A:** ❌ NO! Each server certificate needs its own SANs. See [CERTIFICATES_README.md](CERTIFICATES_README.md) → "Understanding Hostname Verification"

### Q: How do I configure MinIO with TLS?
**A:** Follow [MINIO_SETUP.md](MINIO_SETUP.md) step-by-step.

### Q: What SANs should each certificate have?
**A:** See `generate-certificates.cmd` configuration section for current settings.

### Q: How do I update SANs for Docker containers?
**A:** See [CONNECTOR_TLS_COMMUNICATION.md](CONNECTOR_TLS_COMMUNICATION.md) → "Docker Compose (Container Network)"

### Q: How do I renew certificates?
**A:** Use `renew-certificates.cmd` for selective renewal. See [CERTIFICATE_RENEWAL_GUIDE.md](CERTIFICATE_RENEWAL_GUIDE.md)

### Q: When do certificates expire?
**A:** Server certificates: 1 year, Intermediate CA: 5 years, Root CA: 10 years

### Q: How do I renew certificates?
**A:** Run `renew-certificates.cmd` again. See [CERTIFICATES_README.md](CERTIFICATES_README.md) → "Certificate Renewal"

## 🛠️ Quick Actions

### Generate All Certificates (Initial Setup)
```cmd
cd connector\src\main\resources
generate-certificates.cmd
```

### Renew Certificates (Annual Rotation)
```cmd
cd connector\src\main\resources
renew-certificates.cmd
# Select from menu: connector-a, connector-b, minio, or all
```

### Verify Certificate SANs
```cmd
keytool -list -v -keystore connector-a.p12 -storepass password | findstr "DNS:"
keytool -list -v -keystore connector-b.p12 -storepass password | findstr "DNS:"
```

### Test TLS Connection
```cmd
curl -v https://localhost:8090/actuator/health
```

### Start Connectors
```cmd
REM Terminal 1
mvn spring-boot:run -Dspring-boot.run.profiles=provider

REM Terminal 2
mvn spring-boot:run -Dspring-boot.run.profiles=consumer
```

## 📊 Documentation Stats

| File | Lines | Status | Purpose |
|------|-------|--------|---------|
| DOCKER_HOSTNAME_SETUP.md | ~400 | ✅ NEW | Docker hostname setup |
| CONNECTOR_TLS_COMMUNICATION.md | ~350 | ✅ NEW | Connector-to-connector TLS |
| CERTIFICATES_README.md | ~600 | ✅ UPDATED | Complete setup guide |
| MINIO_SETUP.md | ~400 | ✅ NEW | MinIO TLS configuration |
| pki_architecture_guide.md | ~1,050 | ✅ NEW | PKI deep dive |
| CERTIFICATE_UPDATES_SUMMARY.md | ~280 | ✅ NEW | Change summary |
| CERTIFICATE_RENEWAL_GUIDE.md | ~600 | ✅ NEW | Certificate renewal procedures |
| generate-certificates.cmd | ~600 | ✅ UPDATED | Certificate generation |
| renew-certificates.cmd | ~600 | ✅ NEW | Certificate renewal script |

**Total Documentation: 4,880+ lines**

## 🎉 Summary

All certificate documentation is complete and covers:
- ✅ Certificate generation (automated script)
- ✅ TLS configuration (Spring Boot)
- ✅ Connector-to-connector communication (your question!)
- ✅ MinIO TLS setup
- ✅ PKI architecture and concepts
- ✅ Troubleshooting guides
- ✅ Security best practices
- ✅ Docker deployment scenarios

**Start here:** [CONNECTOR_TLS_COMMUNICATION.md](CONNECTOR_TLS_COMMUNICATION.md) to answer your specific question about connector-a to connector-b TLS!

---

**Last Updated:** Based on latest script changes with specific SANs per service
**Script Version:** generate-certificates.cmd with SAN_CONNECTOR_A, SAN_CONNECTOR_B, SAN_MINIO

