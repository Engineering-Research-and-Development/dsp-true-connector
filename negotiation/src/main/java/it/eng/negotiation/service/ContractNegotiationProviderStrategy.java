package it.eng.negotiation.service;

import it.eng.negotiation.model.*;

public interface ContractNegotiationProviderStrategy {
    ContractNegotiation getNegotiationById(String id);

    ContractNegotiation getNegotiationByProviderPid(String providerPid);

    ContractNegotiation handleInitialContractRequestMessage(ContractRequestMessage contractRequestMessage);

    ContractNegotiation verifyNegotiation(ContractAgreementVerificationMessage cavm);

    ContractNegotiation handleContractNegotiationEventMessage(
            ContractNegotiationEventMessage contractNegotiationEventMessage);

    void handleTerminationRequest(String providerPid,
                                  ContractNegotiationTerminationMessage contractNegotiationTerminationMessage);

    ContractNegotiation handleContractRequestMessageAsCounterOffer(String providerPid, ContractRequestMessage crm);
}
