# TRUE Connector

<p align='left'>
  <a href="https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions/workflows/build.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/Engineering-Research-and-Development/dsp-true-connector/build.yml?branch=develop&label=Build" /></a>
  &nbsp;
  <a href="https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions/workflows/tck_compliance.yml"><img alt="TCK compliance" src="https://img.shields.io/github/actions/workflow/status/Engineering-Research-and-Development/dsp-true-connector/tck_compliance.yml?branch=main&label=TCK%20compliance" /></a>
  &nbsp;
  <a href="https://eclipse-dataspace-protocol-base.github.io/DataspaceProtocol/2025-1/"><img alt="Dataspace protocol" src="https://img.shields.io/badge/Dataspace%20protocol-2025--1-blue" /></a>
</p>

## Development requirements

 - IDE : Eclipse STS, IntelliJ, VS Code etc.
  - Resources: 16 GB RAM, 5 GB of disk space, 4 Cores multithreaded processor
 - Languages/Frameworks: Java 17, Maven 3.9.4 (compatible with java 17), SpringBoot 3.1.2 (Spring framework 6)
 - Database: MongoDB 7.0.12
 - Libraries: lombok, fasterxml.jackson, okhttp3, com.auth0:java-jwt, org.apache.commons:commons-lang3, org.apache.sshd:sshd-core, org.apache.sshd:sshd-sftp
  - Testing: Junit, Mockito; integration tests - MockMvc, Test Containers, Docker
  - Debugging tools: IDE debug
  - FE Technologies: Angular 19
  - Other technologies/Protocols used: Dataspace Protocol, HTTPS, sftp, DCAT-AP
    - Useful Tools: Postman, Robo 3T (or any other MongoDB visualization tool)
  - [Repository source code and versioning](https://github.com/Engineering-Research-and-Development/dsp-true-connector)
  - [Task Management and Monitoring](https://github.com/users/Engineering-Research-and-Development/projects/2)
  - [CI/CD](https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions)
  - Deploy management: Not yet, planned to be dockerized and maybe some cloud solution

Please refer to the [development procedure](doc/development_procedure.md) for more details.
	
## Project structure

Project is structured as multi-module maven project: 

* catalog - module containing logic for processing catalog document
* negotiation - module containing logic for performing contract negotiation
* connector - wrapper module for starting application
* data-transfer - module maintaining transfer of the data
* dcp - module implementing Decentralized Claims Protocol (DCP) for Verifiable Credentials/Presentations
* tools - various tools and utilities needed across modules

## GUI tool for DSP TRUEConnector

* [GUI frontend](https://github.com/Engineering-Research-and-Development/dsp-true-connector-ui)

## Verifiable Credentials Documentation

📖 **Start Here:** [Verifiable Credentials Documentation Index](dcp/doc/verifiable-credentials-index.md) - Complete guide to all VC/VP documentation

### Quick Links by Audience

**For Non-Technical Readers:**
- [Verifiable Credentials: A Simple Overview](dcp/doc/verifiable-credentials-overview.md) - What VCs are and why they matter

**For Developers:**
- [Verifiable Credentials: Technical Guide](dcp/doc/verifiable-credentials-technical.md) - Implementation details, API examples, and debugging
- [Verifiable Credentials: Quick Reference](dcp/doc/verifiable-credentials-quick-reference.md) - Cheat sheet with commands and troubleshooting

