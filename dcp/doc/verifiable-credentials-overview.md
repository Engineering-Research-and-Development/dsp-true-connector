# Verifiable Credentials: A Simple Overview

## What are Verifiable Credentials?

Verifiable Credentials (VCs) are digital versions of physical documents like ID cards, diplomas, membership cards, or certificates. They allow you to prove something about yourself or your organization online in a secure, privacy-preserving way—just like showing your driver's license in person, but for the digital world.

Unlike traditional digital credentials that are stored in centralized databases (like your Facebook or Google account), verifiable credentials put **you** in control. You own them, store them securely, and decide when and with whom to share them.

## Why are They Important?

### Traditional Identity Problems
When you use traditional identity providers (Google, Facebook, etc.):
- **They own your data**, not you
- They can **track** where you use your identity
- If you **lose access** to your account, you lose everything
- Your data is vulnerable to **breaches**
- You can't easily **transfer** your identity to another provider

### Verifiable Credentials Solution
- ✅ **Privacy:** You control what information you share and with whom
- ✅ **Security:** Cryptographically signed, making them nearly impossible to forge
- ✅ **Portability:** Use your credentials across different services and organizations
- ✅ **Ownership:** You store them in your own "wallet" and control them
- ✅ **Selective Disclosure:** Share only what's needed (e.g., prove you're over 18 without revealing your exact birthdate)

## How VCs Work in Our Application

Our TRUE Connector application implements the **Decentralized Claims Protocol (DCP)** to enable secure, privacy-preserving data sharing between organizations in a dataspace.

### Three Key Players

![Triangle of Trust](VC_triangle_of_Trust.svg.png)

#### 1. **Issuer** 
The trusted authority that creates and signs credentials.
- **Example:** A consortium authority certifying that your organization is a trusted member
- **In the dataspace:** Issues membership credentials, access rights, or compliance certificates

#### 2. **Holder**
The entity (person or organization) that receives and stores credentials.
- **Example:** Your organization receiving a "Gold Member" credential
- **In the dataspace:** Stores credentials in a secure vault and presents them when needed

#### 3. **Verifier**
The entity that checks if a credential is authentic and valid.
- **Example:** Another organization checking if you're an authorized member before sharing data
- **In the dataspace:** Validates credentials before granting access to catalogs, contracts, or data transfers

## Real-World Analogies

### Driver's License
1. **Issuer:** Government (DMV) issues your driver's license
2. **Holder:** You keep it in your wallet
3. **Verifier:** A bar checks it to verify your age

### Membership Card
1. **Issuer:** Gym issues you a membership card
2. **Holder:** You carry it with you
3. **Verifier:** Gym staff checks it when you enter

### In Our Dataspace
1. **Issuer:** Dataspace authority issues a "Trusted Member" credential
2. **Holder:** Your connector stores it securely
3. **Verifier:** Another connector checks it before allowing data exchange

## How It's Different from Passwords

| Traditional Login | Verifiable Credentials |
|------------------|----------------------|
| Username + Password stored on server | Cryptographic proof you control a credential |
| Server can be hacked | No central server to hack |
| Same password everywhere = risky | Different proof each time |
| Provider tracks your usage | Privacy-preserving |
| Lost password = locked out | Backup and recovery options |

## What Happens Behind the Scenes?

When you present a verifiable credential:

1. **You sign it** with your private key (proving you control it)
2. **Verifier checks**:
   - Is the signature valid?
   - Was it issued by a trusted authority?
   - Is it still valid (not expired or revoked)?
   - Does it contain the required information?
3. **Decision:** Grant or deny access based on the checks

All of this happens **without** the verifier needing to contact the issuer directly, and **without** revealing more information than necessary.

## Benefits for Our Dataspace

- 🔐 **Secure Authentication:** Connectors authenticate using credentials instead of shared passwords
- 🤝 **Trust:** Only verified members can participate in data exchanges
- 📜 **Compliance:** Prove regulatory compliance without sharing sensitive internal data
- 🚀 **Automation:** Credentials enable automated trust decisions in contract negotiations
- 🌐 **Decentralization:** No single point of failure or control

## Learn More

- For technical implementation details, see: [verifiable-credentials-technical.md](verifiable-credentials-technical.md)
- For deep dive into DID and VC concepts, see: [verifiable_credentials.md](verifiable_credentials.md)
- For hands-on quick reference, see: [verifiable-credentials-quick-reference.md](verifiable-credentials-quick-reference.md)

