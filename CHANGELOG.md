# Changelog
All notable changes to this project will be documented in this file.

## [0.1.1] - 05-11-2024

### Added

 - Json-ld request processing filter to compact incoming protocol request

## [0.1.1] - 24-10-2024

### Added

 - Offer validation - check if offer.target == dataset.id
 
## [0.1.1] - 23-10-2024

### Added

 - Upload and list artifacts that will be shared as dataset
 - When verifying agreement additional check (policyEnforcement for agreement exists) added
 - New CatalogErrorAPIException that translates to HTTP 400 response
 
## [0.1.1] - 20-10-2024

### Added

 - Added logic for DataTransfer API
 
### Changed
 
 - DataTransfer now checks that the Agreement exists and that it's linked to a FINALIZED Contract Negotiation

## [0.1.1] - 09-10-2024

### Added

 - Initial logic for policy enforcement (count and dateTime as left operands)
 - PolicyEnforcement model, repo and service classes that holds count for agreement
 - PolicyManager - class that gets access count and update counter when artifact will be accessed
 - AgreementAPI - enforceAgreement logic
 - Event when accessing resource, used to increase count for agreementId
 
### Changed
 
 - Operator TERM_LTEQ to LTEQ
 
## [0.1.1] - 04-10-2024

### Added

 - New mandatory property application.protocol.authentication.enabled=true
 - DataspaceProtocolEndpointsExceptionHandler - returns valid protocol error based on resource accessed
 - ProtocolEndpointsAuthenticationFilter - filter that creates dummy authorization if security for protocol endpoints is disabled
 - DataspaceProtocolEndpointsAuthenticationEntryPoint - custom authentication class to handle Spring Security errors in protocol way

## [0.1.0] - 13-09-2024

### Added
 
 - Setup project structure (multimodule maven project:tools, catalog, negotiation, dataTransfer)
 - Catalog protocol and API logic implementation (controller, service, model; junit and integration tests)
 - Negotiation protocol and API logic implementation (controller, service, model; junit and integration tests) - Agreement enforcement is currently checking only if agreement is present, it does not check for constraints
 - Data Transfer protocol logic implementation (controller, service, model; junit and integration tests) - REST pull implementation without authorization, with hardcoded value/artifact
 - Postman collection for testing endpoints	
 - Configured GitHub actions to run tests
 
## [0.0.1] - 12-09-2024

### Added

 - GHA test for API endpoints
 - Distribution - format as reference
 
### Changed

 - DataService is connector
 - Plain serializers returns '@id'
 - Postman collection updated
 - generated identifiers have 'urn:uuid' as prefix (catalog, negotiation and dataTransfer)
 
### Removed
 
  - removed servesDataset from DataService as per protocol (https://docs.internationaldataspaces.org/ids-knowledgebase/v/dataspace-protocol/catalog/catalog.protocol#id-1.1.3-data-service)

## [0.0.1] - 27-08-2024

### Added

 - Added role (consumer or provider) to Contract Negotiation
 - Added Agreement reference to to Contract Negotiation
 
### Removed

 - Removed consumerPid and providerPid from Offer and Agreement
 
## [0.0.1] - xx-08-2024

### Added 

 - Junit tests to cover Catalog module classes java->String->java2 java.equals(java2) 

### Changed

 - Plain Serializer - JacksonAnnotationIntrospector to skip JsonProperty annotation
 - Model classes implements Serializable
 - Enum classes JsonCreator - create enum from String (plain and protocol string)
 - Builder creates 'id' in "urn:uuid" + UUID.randomUUID() format if 'id' not present
 - Collections reverted to Set

## [0.0.1] - 07-08-2024

### Added

 - TransferTerminationMessage message provider and consumer callback logic (plus junit and integration tests)
 - DataTransferConsumerCallbackTest - integration test class for consumer callback logic
 - DataTransferApiTest - integration test for API logic
 
### Changed

 - Agreement service (filter download url) in data transfer module sends request to check if agreement is valid
 
## [0.0.1] - 06-08-2024

### Added

 - TransferSuspensionMessage message provider and consumer callback logic (plus junit and integration tests)
 - Added TransferProcessChangeEvent and listener to log transition change
 - DataTransferEventListener - placeholder logic for manipulating data transfers (start/stop/suspend)
 - DataTransferFormat enum
 
### Changed

 - Updated GitHub action to include suspend message in transfer artifact
 - SFTP server starting on event published (TransferStartMessage from TransferRequestMessage.foramt=example:SFTP
 
## [0.0.1] - 02-08-2024

### Added
 
 - New catalog API exceptions
 
### Changed

 - Catalog API exceptions now wrapped in GenericApiResponse
 
### Removed

 - Status code from GenericApiResponse

## [0.0.1] - 30-07-2024

### Added
 
 - TransferCompletionMessage message provider and consumer callback logic (plus junit and integration tests)
 - GitHub action to test request transfer, start, download artifact and send completion message
 
### Changed

 - ROLE_CONNECTOR to fix authorization for protocol endpoints using jwt

## [0.0.1] - 2024-07-24
 
### Added

 - Added CORS configuration
 
### Changed

 - Moved some common service logic to BaseService
 - Renamed APIs to be REST compliant
 
### Removed

 - Removed transformers from Negotiation module


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
