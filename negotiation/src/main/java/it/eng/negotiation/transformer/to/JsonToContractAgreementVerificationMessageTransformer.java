package it.eng.negotiation.transformer.to;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

public class JsonToContractAgreementVerificationMessageTransformer implements TransformInterface<JsonNode, ContractAgreementVerificationMessage> {

	@Override
	public Class<JsonNode> getInputType() {
		return JsonNode.class;
	}

	@Override
	public Class<ContractAgreementVerificationMessage> getOutputType() {
		return ContractAgreementVerificationMessage.class;
	}

	@Override
	public ContractAgreementVerificationMessage transform(JsonNode input) {
		var builder = ContractAgreementVerificationMessage.Builder.newInstance();
		
		builder.consumerPid(input.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		builder.providerPid(input.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		return builder.build();
	} 

}
