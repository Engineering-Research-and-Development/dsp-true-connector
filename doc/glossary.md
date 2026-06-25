# Glossary

Domain terminology used across the DSP TRUE Connector. For architecture context see [architecture.md](architecture.md).

## Dataspace
A federated, decentralized ecosystem in which organizations share data under common governance rules while each participant retains sovereignty over its own data.

## Dataspace Protocol (DSP)
The Eclipse specification (version 2025-1) defining how connectors expose catalogs, negotiate contracts, and transfer data. The TRUE Connector implements DSP 2025-1 and passes its full compliance suite.

## Connector
The software component through which a participant joins a dataspace. It publishes or consumes data offers and enforces agreed usage policies. One TRUE Connector codebase can run as provider or consumer.

## Provider
The connector role that offers datasets: it publishes a catalog, answers negotiation requests, and serves data transfers. Locally configured via the `provider` Spring profile (port 8090).

## Consumer
The connector role that requests data: it browses a provider's catalog, initiates contract negotiation, and pulls data once an agreement exists. Locally configured via the `consumer` Spring profile.

## Catalog
The collection of datasets a provider offers, expressed as a DCAT-AP document. Each dataset carries one or more offers describing the conditions under which it may be used.

## DCAT-AP
The DCAT Application Profile — a standard RDF vocabulary for describing data catalogs, datasets, and distributions, used as the catalog format in DSP.

## Dataset
A described unit of data offered in the catalog, with one or more distributions and associated usage policies.

## Distribution
A concrete way to obtain a dataset (format plus transfer endpoint), e.g. an HTTPS pull endpoint or an S3 object.

## Artifact
The actual stored data behind a dataset, uploaded to the connector (file or URL reference) and served during transfers.

## ODRL Policy
A machine-readable usage policy (Open Digital Rights Language) attached to a dataset offer, expressing permissions and constraints.

## Constraint
A condition inside an ODRL policy that must hold for usage to be allowed. The connector evaluates COUNT, DATE_TIME, PURPOSE, and SPATIAL constraints — see [policy_enforcement.md](../negotiation/doc/policy_enforcement.md).

## Contract Negotiation
The DSP message exchange in which consumer and provider converge on usage terms for a dataset. Modeled as a state machine; a successful negotiation produces an Agreement, an unsuccessful one is terminated.

## Agreement
The finalized, binding contract resulting from a successful negotiation. Every data transfer must reference a valid agreement; the connector enforces agreement validity (including expiry, handled by the agreement scheduler).

## Transfer Process
The DSP state machine governing the actual movement of data after an agreement exists — from the consumer's `TransferRequestMessage` through the DSP transfer states to completion or termination.

## HttpData-PULL
A transfer format in which the consumer pulls data from a provider endpoint over HTTPS, authorized by the agreement.

## Policy Enforcement Point (PEP)
The component that intercepts a data access attempt and asks for a policy decision before allowing it.

## Policy Decision Point (PDP)
The component that evaluates the applicable policy constraints and decides whether access is permitted.

## Policy Information Point (PIP)
The component that supplies the facts (e.g. access count, current time, location) the PDP needs to evaluate constraints.

## TCK (Technical Compliance Kit)
The official DSP test suite (65 tests across metadata, catalog, contract negotiation, and transfer process). The connector maintains a 100% pass rate — see [tck/tck_compliancy.md](tck/tck_compliancy.md).

## Verifiable Credential (VC)
A W3C-standard, cryptographically verifiable attestation (e.g. dataspace membership) issued by a trusted party and presented during interactions — see [verifiable_credentials.md](verifiable_credentials.md).

## DID (Decentralized Identifier)
A W3C-standard, self-sovereign identifier resolvable to a DID document containing public keys and service endpoints, used to identify dataspace participants without a central registry.

## Identity Hub
The component/service managing a participant's DIDs and Verifiable Credentials, integrated for participant onboarding and credential workflows — see [identity_hub.md](identity_hub.md).

## Triangle of Trust
The issuer–holder–verifier relationship underlying verifiable credentials: an issuer attests something about a holder, who presents it to a verifier that trusts the issuer.

## OCSP (Online Certificate Status Protocol)
A protocol for checking X.509 certificate revocation in real time during TLS handshakes — see [ocsp/OCSP_GUIDE.md](ocsp/OCSP_GUIDE.md).

## Presigned URL
A time-limited, pre-authorized URL for direct upload/download against S3-compatible storage, used in artifact handling — see [s3_configuration.md](s3_configuration.md).

## Initial Data
The `initial_data*.json` seed files that populate MongoDB on startup with connector metadata, users, and properties, varying per Spring profile (provider/consumer/TCK).
