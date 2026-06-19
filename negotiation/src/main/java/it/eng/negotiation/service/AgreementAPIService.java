package it.eng.negotiation.service;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.exception.PolicyEnforcementException;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.policy.model.PolicyDecision;
import it.eng.negotiation.policy.service.PolicyEnforcementPoint;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;

@Slf4j
@Service
public class AgreementAPIService {

    private final PolicyEnforcementPoint policyEnforcementPoint;
    private final AgreementRepository agreementRepository;
    private final PolicyEnforcementRepository policyEnforcementRepository;

    public AgreementAPIService(AgreementRepository agreementRepository, PolicyEnforcementPoint policyEnforcementPoint, PolicyEnforcementRepository policyEnforcementRepository) {
        super();
        this.policyEnforcementPoint = policyEnforcementPoint;
        this.agreementRepository = agreementRepository;
        this.policyEnforcementRepository = policyEnforcementRepository;
    }

    /**
     * Returns a single agreement by its ID.
     *
     * @param agreementId the ID of the agreement to retrieve
     * @return the agreement with the given ID
     * @throws ContractNegotiationAPIException if no agreement is found with the given ID
     */
    public Agreement findAgreementById(String agreementId) {
        return agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ContractNegotiationAPIException("Agreement with id " + agreementId + " not found."));
    }

    /**
     * Returns a paginated list of all agreements.
     *
     * @param pageable pagination and sorting parameters
     * @return a page of agreements
     */
    public Page<Agreement> findAgreements(Pageable pageable) {
        log.info("Fetching agreements...");
        return agreementRepository.findAll(pageable);
    }

    /**
     * Enforces the policy for the given agreement.
     *
     * @param agreementId the ID of the agreement to enforce
     * @throws ContractNegotiationAPIException if no agreement is found with the given ID
     * @throws PolicyEnforcementException if the agreement policy evaluation fails
     */
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

    /**
     * Retrieves the current count for the given agreement.
     *
     * @param agreementId the ID of the agreement
     * @return an Optional containing the count, or empty if no enforcement found
     */
    public Optional<Integer> getCurrentCount(String agreementId) {
        return policyEnforcementRepository.findByAgreementId(agreementId)
                .map(PolicyEnforcement::getCount);
    }

    /**
     * Returns a single agreement by its ID, enriched with current policy enforcement count.
     *
     * @param agreementId the ID of the agreement to retrieve
     * @return JsonNode representation of the agreement, with currentCount field if enforcement exists
     * @throws ContractNegotiationAPIException if no agreement is found with the given ID
     */
    public JsonNode findAgreementByIdEnriched(String agreementId) {
        Agreement agreement =  agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ContractNegotiationAPIException("Agreement with id " + agreementId + " not found."));
        ObjectNode json = (ObjectNode) NegotiationSerializer.serializePlainJsonNode(agreement);
        getCurrentCount(agreementId).ifPresent(count -> json.put("currentCount", count));
        return json;

    }
}
