package it.eng.tools.event.contractnegotiation;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Used to send offer from negotiation module to catalog for evaluation</br>
 * Serialized offer from negotiation will be deserialized into catalog offer and compared with one from catalog.
 *
 */
@AllArgsConstructor
@Getter
public class OfferValidationRequest {

	private List<Boolean> isValid;
	private String consumerPid;
	private String providerPid;
	private JsonNode offer;
}
