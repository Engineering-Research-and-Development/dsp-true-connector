# Changelog
All notable changes to this project will be documented in this file.

## [0.1.0] - 2024-08-xx

### Added
 
 - Setup project structure (multimodule maven project:tools, catalog, negotiation, dataTransfer)
 - Catalog protocol and API logic implementation (controller, service, model; junit and integration tests)
 - Negotiation protocol and API logic implementation (controller, service, model; junit and integration tests) - Agreement enforcement is currently checking only if agreement is present, it does not check for constraints
 - Data Transfer protocol and API logic implementation (controller, service, model; junit and integration tests) - REST pull implementation without authorization, with hardcoded value/artifact
 - Postman collection for testing endpoints	
 - Configured GitHub actions to run tests

## [0.0.1] - 29-07-2024

### Added
 
 - TransferCompletionMessage message provider and consumer callback logic (plus junit and integration tests)
 - GitHub action to test request transfer, start, download artifact and send completion message
 
### Changed

 - ROLE_CONNECTOR to fix authorization for protocol endpoints using jwt

## [0.0.1] - 2024-07-22

### Added

 - TransferStartMessage logic for provider and consumer callback (controller and service layer)
 - DataTransfer API controller, service, junit and integration tests (get TransferProcess, by state and all)
 - Negotiation module - API endpoint for agreement check (valid or not)
 - AbstractTransferMessage implements Serializable
 
## Updated

 - Code coverage (junit and integration)
 - TransferRequestMessage - call to negotiation for agreement validity check before proceeding
 - Negotiation module - renamed ModelUtil to MockObjectUtil (aligned with other modules)
 - postman collection
 
## [0.0.1] - 2024-07-12

### Added

 - provider endpoint and logic for initiating data transfer
 - junit and integration tests
 - GHA for data transfer request

### Changed
 
 - updated Postman collection for /transfers/request
   
## [0.0.1] - 2024-07-10

### Added

 - dockerized the application
 - added integration tests to GHA
 
### Changed

 - certificate private key password now used from application.properties
 
## [0.0.1] - 2024-07-09

### Added 

 - Added API endpoints for accepting and declining negotiation from provider side
 - Added API endpoint for finding contract negotiations by state or all

### Changed

 - Reviewed negotiation flow
 - Updated postman collection

## [0.0.1] - 2024-06-28

### Changed

 - updated verified and finalized states in negotiation module
 - separated consumer and provider callback addresses in negotiation module

## [0.0.1] - 2024-06-25

### Added

- model, service and repository to manage Application Properties
- API controller to expose services
- Configuration @Component class to insert application.properties entries in Mongodb at startup

### Changed

- Postman collection and enviroment (with new API)
- initial_data.json (adding application_properties)
- 

## [0.0.1] - 2024-06-25

### Added

 - DataTransfer Consumer callback controller and junit tests

## [0.0.1] - 2024-06-21

### Added

 - dataTransfer module, POC for REST pull artifact
 - duplicate TransferProcess and ContractNegotiation with new status
 - DataService, update method
 - DataTransfer exception advice
 
### Removed
