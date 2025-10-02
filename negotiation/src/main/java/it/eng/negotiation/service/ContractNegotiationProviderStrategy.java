package it.eng.negotiation.service;

import it.eng.negotiation.model.*;

public interface ContractNegotiationProviderStrategy {
    ContractNegotiation getNegotiationById(String id);

    ContractNegotiation getNegotiationByProviderPid(String providerPid);

    ContractNegotiation startContractNegotiation(ContractRequestMessage contractRequestMessage);

    void verifyNegotiation(ContractAgreementVerificationMessage cavm);

    ContractNegotiation handleContractNegotiationEventMessage(
            ContractNegotiationEventMessage contractNegotiationEventMessage);

    void handleTerminationRequest(String providerPid,
                                  ContractNegotiationTerminationMessage contractNegotiationTerminationMessage);
}
