package it.eng.negotiation.service;

import it.eng.negotiation.model.*;

public interface ContractNegotiationProviderStrategy {
    ContractNegotiation getNegotiationById(String id);

    ContractNegotiation getNegotiationByProviderPid(String providerPid);

    ContractNegotiation handleContractRequestMessage(ContractRequestMessage contractRequestMessage);

    ContractNegotiation handleContractRequestMessageAsCounteroffer(String providerPid, ContractRequestMessage crm);

    ContractNegotiation handleContractNegotiationEventMessageAccepted(String providerPid,
            ContractNegotiationEventMessage contractNegotiationEventMessage);

    ContractNegotiation handleContractAgreementVerificationMessage(String providerPid, ContractAgreementVerificationMessage cavm);

    void handleContractNegotiationTerminationMessage(String providerPid,
                                                     ContractNegotiationTerminationMessage contractNegotiationTerminationMessage);
}
