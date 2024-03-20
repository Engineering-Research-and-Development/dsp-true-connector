# TRUE Connector

Implementation of the new [Dataspace protocol](https://docs.internationaldataspaces.org/ids-knowledgebase/v/dataspace-protocol/overview/readme) (current version v0.8)

## Development requirements

* Java 17
* Maven 3.9.x (compatible with java 17)
* Spring 5

Multi module maven project: 

* catalog - module containing logic for processing catalog document
* negotiation - module containing logic for performing contract negotiation
* connector - wrapper module for starting application
* statemachine - module for finite state machine (negotiation and transfer process)
* data-transfer - module maintaining transfer of the data
* tools - various tools and utilities needed across modules

## h2-console

It is available on http://localhost:8080/h2-console with following connection parameters url=jdbc:h2:mem:trueconnector user=sa password=sa. Be sure to choose Generic H2 (Server) and not Embedded.