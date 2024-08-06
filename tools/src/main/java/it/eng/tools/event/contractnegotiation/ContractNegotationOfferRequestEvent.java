package it.eng.tools.event.contractnegotiation;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Used in automatic negotiation to send offer from negotiation module to catalog for evaluation<br>
 * Serialized offer from negotiation will be deserialized into catalog offer and compared with one from catalog.
 *
 */
@AllArgsConstructor
@Getter
public class ContractNegotationOfferRequestEvent {

	private String consumerPid;
	private String providerPid;
	private JsonNode offer;
	
}
