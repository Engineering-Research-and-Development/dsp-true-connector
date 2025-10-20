package it.eng.negotiation.service;

import it.eng.negotiation.model.*;

public interface ContractNegotiationConsumerStrategy {
    ContractNegotiation handleContractOfferMessage(String consumerPid, ContractOfferMessage contractOfferMessage);

    ContractNegotiation handleContractAgreementMessage(ContractAgreementMessage contractAgreementMessage);

    void handleFinalizeEvent(ContractNegotiationEventMessage contractNegotiationEventMessage);

    void handleTerminationRequest(String consumerPid, ContractNegotiationTerminationMessage contractNegotiationTerminationMessage);

    ContractNegotiation processTCKRequest(TCKContractNegotiationRequest contractNegotiationRequest);
}
