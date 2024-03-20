package it.eng.negotiation.transformer.to;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonToContractAgreementMessageTransformer implements TransformInterface<JsonNode, ContractAgreementMessage> {

	@Override
	public Class<JsonNode> getInputType() {
		return JsonNode.class;
	}

	@Override
	public Class<ContractAgreementMessage> getOutputType() {
		return ContractAgreementMessage.class;
	}

	@Override
	public ContractAgreementMessage transform(JsonNode input) {
		
		var builder = ContractAgreementMessage.Builder.newInstance();

		JsonNode callbackAddress = input.get(DSpaceConstants.DSPACE + DSpaceConstants.CALLBACK_ADDRESS);
		if(callbackAddress != null) {
			builder.callbackAddress(callbackAddress.asText());
		}
		
		builder.consumerPid(input.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		builder.providerPid(input.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		
		JsonNode agreementJson = input.get(DSpaceConstants.DSPACE + DSpaceConstants.AGREEMENT);
		
		JsonToAgreementTransformer agreementTransformer = new JsonToAgreementTransformer();
				
		Agreement agreement = agreementTransformer.transform(agreementJson);
		
		builder.agreement(agreement);
		
		return builder.build();
	}

}
