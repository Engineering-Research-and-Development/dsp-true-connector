package it.eng.tools.event.contractnegotiation;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Response from catalog to negotiation module</br>
 * Containing response if offer is accepted or not
 *
 */
@AllArgsConstructor
@Getter
public class ContractNegotiationOfferResponse {

	private String consumerPid;
	private String providerPid;
	private boolean offerAccepted;
	private JsonNode offer;
}
