package it.eng.negotiation.transformer.to;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.model.Offer;
import it.eng.tools.model.DSpaceConstants;

@Disabled("Until transformers are needed or for deletion")
public class JsonToContractOfferMessageTransformerTest extends AbstractToTransformerTest {

	private JsonToContractOfferMessageTransformer transformer = new JsonToContractOfferMessageTransformer();
	
	@Test
	public void transformInitial() {
		/*
		 * {
  "@context":  "https://w3id.org/dspace/v0.8/context.json",
  "@type": "dspace:ContractOfferMessage",
  "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
  "dspace:dataset": "urn:uuid:3dd1add8-4d2d-569e-d634-8394a8836a88",
  "dspace:offer": {
    "@type": "odrl:Offer",
    "@id": "urn:uuid:d526561f-528e-4d5a-ae12-9a9dd9b7a518",
    "target": "urn:uuid:3dd1add8-4d2d-569e-d634-8394a8836a88"
  },
  "dspace:callbackAddress": "https://......"
}
		 */
		ContractOfferMessage contractOfferMessage = transformer.transform(createJsonNode());
		assertNotNull(contractOfferMessage, "Contract offer message must not be null");
		assertNotNull(contractOfferMessage.getProviderPid(), "Provider should not be null");
		assertNull(contractOfferMessage.getConsumerPid(), "Consumer should not be present");
		assertNotNull(contractOfferMessage.getOffer(), "Offer should not be null");
		assertNotNull(contractOfferMessage.getCallbackAddress(), "Callback should not be null");
	}
	
	@Test
	public void transformOfferFull() {
		Map<String, Object> in = new HashMap<>();
		in.put(DSpaceConstants.CONTEXT, DSpaceConstants.CATALOG_CONTEXT_VALUE);
		in.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + transformer.getOutputType().getSimpleName());
		in.put(DSpaceConstants.DSPACE_CONSUMER_PID, "urn:uuid:" + UUID.randomUUID());
		in.put(DSpaceConstants.DSPACE_PROVIDER_PID, "urn:uuid:" + UUID.randomUUID());
		in.put(DSpaceConstants.DSPACE_CALLBACK_ADDRESS, "https://callback.address.test.mock");
		
		/*
		 * "dspace:offer": {
		    "@type": "odrl:Offer",
		    "@id": "urn:uuid:6bcea82e-c509-443d-ba8c-8eef25984c07",
		    "odrl:target": "urn:uuid:3dd1add8-4d2d-569e-d634-8394a8836a88",
		    "dspace:providerId": "urn:tsdshhs636378",
		    "dspace:consumerId": "urn:jashd766",
		    "odrl:permission": [{
		      "odrl:action": "odrl:use" ,
		      "odrl:constraint": [{
		        "odrl:leftOperand": "odrl:dateTime",
		        "odrl:operand": "odrl:lteq",
		        "odrl:rightOperand": { "@value": "2023-12-31T06:00Z", "@type": "xsd:dateTime" }
		      }]
		    }]
		  },
		 */
		Map<String, Object> offerMap = new HashMap<>();
		offerMap.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + Offer.class.getSimpleName());
		offerMap.put(DSpaceConstants.TARGET, generateUUID());
		offerMap.put(DSpaceConstants.TYPE, generateUUID());
		offerMap.put(DSpaceConstants.DSPACE_CONSUMER_PID, "urn:uuid:" + UUID.randomUUID());
		offerMap.put(DSpaceConstants.DSPACE_PROVIDER_PID, "urn:uuid:" + UUID.randomUUID());
		
		Map<String, Object> permission = new HashMap<>();
		permission.put(DSpaceConstants.ODRL_ACTION, "odrl:use");
		
		Map<String, Object> constraint = new HashMap<>();
		constraint.put(DSpaceConstants.ODRL_LEFT_OPERAND, "odrl:dateTime");
		constraint.put(DSpaceConstants.ODRL_OPERATOR, "odrl:lteq");
		Map<String, Object> rightOperand = new HashMap<>();
		rightOperand.put(DSpaceConstants.VALUE, "2023-12-31T06:00Z");
		rightOperand.put(DSpaceConstants.TYPE, "xsd:dateTime");
		constraint.put(DSpaceConstants.ODRL_RIGHT_OPERAND, rightOperand);
		
		List<Map<String, Object>> constraints = new ArrayList<>();
		constraints.add(constraint);
		permission.put(DSpaceConstants.ODRL_CONSTRAINT, constraints);
		
		List<Map<String, Object>> permissions = new ArrayList<>();
		permissions.add(permission);
		offerMap.put(DSpaceConstants.ODRL_PERMISSION, permissions);
		in.put(DSpaceConstants.DSPACE_OFFER, offerMap);
		JsonNode jsonNode = mapper.convertValue(in, JsonNode.class);
		
		ContractOfferMessage contractOfferMessage = transformer.transform(jsonNode);
		assertNotNull(contractOfferMessage, "Contract offer message must not be null");
		assertNotNull(contractOfferMessage.getProviderPid(), "Provider should not be null");
		assertNotNull(contractOfferMessage.getConsumerPid(), "Consumer should not be null");
		assertNotNull(contractOfferMessage.getOffer(), "Offer should not be null");
		assertNotNull(contractOfferMessage.getCallbackAddress(), "Callback should not be null");
		//TODO check offer
//		Offer offer = contractOfferMessage.getOffer();
	}

	@Override
	protected JsonNode createJsonNode() {
		Map<String, Object> in = new HashMap<>();
		in.put(DSpaceConstants.CONTEXT, DSpaceConstants.CATALOG_CONTEXT_VALUE);
		in.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + transformer.getOutputType().getSimpleName());
//		in.put(DSpaceConstants.DSPACE + DSpaceConstants.CONSUMER_ID, "urn:uuid:" + UUID.randomUUID());
		in.put(DSpaceConstants.DSPACE_PROVIDER_PID, "urn:uuid:" + UUID.randomUUID());
//		in.put(DSpaceConstants.DSPACE_CONSUMER_PID, "urn:uuid:" + UUID.randomUUID());
		in.put(DSpaceConstants.DSPACE_CALLBACK_ADDRESS, "https://callback.address.test.mock");
		
		Map<String, Object> offer = new HashMap<>();
		offer.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + Offer.class.getSimpleName());
		offer.put(DSpaceConstants.TARGET, generateUUID());
		offer.put(DSpaceConstants.TYPE, generateUUID());
		
		in.put(DSpaceConstants.DSPACE_OFFER, offer);
		
		return mapper.convertValue(in, JsonNode.class);
	}
}
