package it.eng.negotiation.listener;

import it.eng.negotiation.policy.service.PolicyAdministrationPoint;
import it.eng.tools.event.policyenforcement.ArtifactTransferredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PolicyEnforcementListener {

    private final PolicyAdministrationPoint policyAdministrationPoint;

    public PolicyEnforcementListener(PolicyAdministrationPoint policyAdministrationPoint) {
        this.policyAdministrationPoint = policyAdministrationPoint;
    }

    /**
     * Handles the artifact transferred event and updates the access count.
     * @param artifactTransferredEvent the event carrying the agreementId
     */

    @EventListener
    public void handleArtifactTransferredEvent(ArtifactTransferredEvent artifactTransferredEvent) {
        log.info("Increasing access count for artifactId {}", artifactTransferredEvent.getAgreementId());
        policyAdministrationPoint.updateAccessCount(artifactTransferredEvent.getAgreementId());

    }


}
