package it.eng.negotiation.service;

import it.eng.negotiation.model.*;

public interface ContractNegotiationConsumerStrategy {
    ContractNegotiation handleContractOfferMessage(ContractOfferMessage contractOfferMessage);

    ContractNegotiation handleContractOfferMessageAsCounteroffer(String consumerPid, ContractOfferMessage contractOfferMessage);

    ContractNegotiation handleContractAgreementMessage(String consumerPid, ContractAgreementMessage contractAgreementMessage);

    void handleContractNegotiationEventMessageFinalize(String consumerPid, ContractNegotiationEventMessage contractNegotiationEventMessage);

    void handleContractNegotiationTerminationMessage(String consumerPid, ContractNegotiationTerminationMessage contractNegotiationTerminationMessage);

    ContractNegotiation processTCKRequest(TCKContractNegotiationRequest contractNegotiationRequest);
}
