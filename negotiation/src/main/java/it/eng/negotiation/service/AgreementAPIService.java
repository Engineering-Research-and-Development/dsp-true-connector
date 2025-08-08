package it.eng.negotiation.service;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.exception.PolicyEnforcementException;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.service.PolicyEnforcementPoint;
import it.eng.negotiation.repository.AgreementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgreementAPIService {

    private final PolicyEnforcementPoint policyEnforcementPoint;
    private final AgreementRepository agreementRepository;

    public AgreementAPIService(AgreementRepository agreementRepository, PolicyEnforcementPoint policyEnforcementPoint) {
        super();
        this.policyEnforcementPoint = policyEnforcementPoint;
        this.agreementRepository = agreementRepository;
    }

    public void enforceAgreement(String agreementId) {
        Agreement agreement = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ContractNegotiationAPIException("Agreement with Id " + agreementId + " not found."));
        // TODO add additional checks like contract dates
        //		LocalDateTime agreementStartDate = LocalDateTime.parse(agreement.getTimestamp(), FORMATTER);
        //		agreementStartDate.isBefore(LocalDateTime.now());

        PolicyDecision policyDecision = policyEnforcementPoint.enforcePolicy(agreement, "enforceAgreement");

        if (policyDecision.isAllowed()) {
            log.info("Agreement is valid");
        } else {
            log.info("Agreement is invalid");
            throw new PolicyEnforcementException("Agreement with id'" + agreementId + "' evaluated as invalid");
        }
    }
}
