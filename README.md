# TRUE Connector

Implementation of the new [Dataspace protocol](https://docs.internationaldataspaces.org/ids-knowledgebase/v/dataspace-protocol/overview/readme) (current version 2024-1)

## Development requirements

 - IDE : Eclipse STS, IntelliJ, VS Code
 - Resources: 16Gb RAM, 5Gb of disk space, 8 thread Processor
 - Languages/Frameworks: Java 17, Maven 3.9.4 (compatible with java 17), SpringBoot 3.1.2 (Spring framework 6)
 - Database: MongoDB 7.0.12
 - Libraries: lombok, fasterxml.jackson, okhttp3, com.auth0:java-jwt, org.apache.commons:commons-lang3, org.apache.sshd:sshd-core, org.apache.sshd:sshd-sftp
  - Testing: Junit, Mockito; integration tests - MockMvc, Test Containers, Docker
  - Debugging tools: IDE debug
  - FE Technologies: Angular 17
  - Other technologies/Protocols used: Dataspace Protocol, HTTPS, sftp, DCAT-AP
  - Useful Tools: Postman, Robo 3T (or any other MongoDb visualization tool)
  - [Repository source code and versioning](https://github.com/Engineering-Research-and-Development/dsp-true-connector)
  - [Task Management and Monitoring](https://github.com/users/Engineering-Research-and-Development/projects/2)
  - [CI/CD](https://github.com/Engineering-Research-and-Development/dsp-true-connector/actions)
  - Deploy management: Not yet, planned to be dockerized and maybe some cloud solution

Please refer to the [development procedure](doc/development_procedure.md) for more details.
	
## Project structure

Project is structured as multi module maven project: 

* catalog - module containing logic for processing catalog document
* negotiation - module containing logic for performing contract negotiation
* connector - wrapper module for starting application
* data-transfer - module maintaining transfer of the data
* tools - various tools and utilities needed across modules

## GUI tool for DSP TRUEConnector

* [GUI frontend](https://github.com/Engineering-Research-and-Development/dsp-true-connector-ui)