# Changelog
All notable changes to this project will be documented in this file.

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
