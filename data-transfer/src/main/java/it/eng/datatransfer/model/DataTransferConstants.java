package it.eng.datatransfer.model;

public enum DataTransferConstants {

	HTTP_PUSH("HttpData-PUSH"),
	HTTP_PULL("HttpData-PULL"),
	DATA_ADDRESS_ENDPOINT_TYPE("https://w3id.org/idsa/v4.1/HTTP"),
	
	DATA_ADDRESS_ENDPOINT_ENDPOINT_PROPERTY("https://w3id.org/edc/v0.0.1/ns/endpoint"),
	DATA_ADDRESS_ENDPOINT_AUTH_TYPE("https://w3id.org/edc/v0.0.1/ns/authType"),
	DATA_ADDRESS_ENDPOINT_ENDPOINT_TYPE("https://w3id.org/edc/v0.0.1/ns/endpointType"),
	DATA_ADDRESS_ENDPOINT_AUTHORIZATION("https://w3id.org/edc/v0.0.1/ns/authorization");
	
	DataTransferConstants(String dataTransferValue) {
	}
}
