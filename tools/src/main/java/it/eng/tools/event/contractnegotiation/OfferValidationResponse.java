package it.eng.tools.event.contractnegotiation;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response from catalog to negotiation module</br>
 * Containing response if offer is accepted or not
 *
 */
@Data
@AllArgsConstructor
public class OfferValidationResponse {

	private String consumerPid;
	private String providerPid;
	private boolean offerAccepted;
	private JsonNode offer;
}