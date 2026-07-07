package it.eng.connector.integration.multitenant;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.model.PolicyEnforcement;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.PolicyEnforcementRepository;
import it.eng.negotiation.service.AgreementAPIService;
import it.eng.tools.service.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests verifying that two tenants sharing one MongoDB instance can each persist an
 * {@link Agreement} with the same DSP protocol {@code id} without one tenant's document
 * overwriting the other's. Regression coverage for the bug described in GitHub issue #277.
 */
public class AgreementCrossTenantIT extends BaseIntegrationTest {

    private static final String TENANT_A = "agreement-tenant-a";
    private static final String TENANT_B = "agreement-tenant-b";

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;

    @Autowired
    private PolicyEnforcementRepository policyEnforcementRepository;

    @Autowired
    private AgreementAPIService agreementAPIService;

    @AfterEach
    public void cleanup() {
        TenantContextHolder.clear();
        contractNegotiationRepository.deleteAll();
        agreementRepository.deleteAll();
        policyEnforcementRepository.deleteAll();
    }

    @Test
    @DisplayName("Same protocol agreement id can be persisted independently for two tenants")
    void samAgreementId_persistsIndependently_forTwoTenants() {
        String sharedAgreementId = createNewId();

        Agreement agreementForTenantA = buildAgreement(sharedAgreementId);
        agreementForTenantA.injectTenantId(TENANT_A);
        agreementRepository.save(agreementForTenantA);

        Agreement agreementForTenantB = buildAgreement(sharedAgreementId);
        agreementForTenantB.injectTenantId(TENANT_B);
        agreementRepository.save(agreementForTenantB);

        Optional<Agreement> foundForTenantA = agreementRepository.findByIdAndTenantId(sharedAgreementId, TENANT_A);
        Optional<Agreement> foundForTenantB = agreementRepository.findByIdAndTenantId(sharedAgreementId, TENANT_B);

        assertTrue(foundForTenantA.isPresent(), "Tenant A's agreement must still exist after tenant B's save");
        assertTrue(foundForTenantB.isPresent(), "Tenant B's agreement must exist");
        assertEquals(TENANT_A, foundForTenantA.get().getTenantId());
        assertEquals(TENANT_B, foundForTenantB.get().getTenantId());
        assertNotEquals(foundForTenantA.get().getTechnicalId(), foundForTenantB.get().getTechnicalId(),
                "Each tenant's copy must have its own MongoDB technical id");

        List<Agreement> allWithSharedId = agreementRepository.findAll().stream()
                .filter(agreement -> sharedAgreementId.equals(agreement.getId()))
                .toList();
        assertEquals(2, allWithSharedId.size(), "Both tenants' documents with the shared protocol id must coexist");
    }

    @Test
    @DisplayName("enforceAgreement succeeds for both tenants when they share the same protocol agreement id")
    void enforceAgreement_succeeds_forBothTenants_withSharedAgreementId() {
        String sharedAgreementId = createNewId();

        Agreement agreementForTenantA = buildAgreement(sharedAgreementId);
        agreementForTenantA.injectTenantId(TENANT_A);
        agreementRepository.save(agreementForTenantA);

        Agreement agreementForTenantB = buildAgreement(sharedAgreementId);
        agreementForTenantB.injectTenantId(TENANT_B);
        agreementRepository.save(agreementForTenantB);

        ContractNegotiation contractNegotiationForTenantA = buildFinalizedContractNegotiation(agreementForTenantA);
        contractNegotiationForTenantA.injectTenantId(TENANT_A);
        contractNegotiationRepository.save(contractNegotiationForTenantA);

        ContractNegotiation contractNegotiationForTenantB = buildFinalizedContractNegotiation(agreementForTenantB);
        contractNegotiationForTenantB.injectTenantId(TENANT_B);
        contractNegotiationRepository.save(contractNegotiationForTenantB);

        // PolicyEnforcement lookup is not yet tenant-scoped in PolicyInformationPoint (tracked separately,
        // see issue #273), so a single shared usage-count record is used here for both tenants.
        policyEnforcementRepository.save(new PolicyEnforcement(createNewId(), sharedAgreementId, 0, null));

        TenantContextHolder.setTenantId(TENANT_A);
        assertDoesNotThrow(() -> agreementAPIService.enforceAgreement(sharedAgreementId));

        TenantContextHolder.setTenantId(TENANT_B);
        assertDoesNotThrow(() -> agreementAPIService.enforceAgreement(sharedAgreementId));
    }

    private ContractNegotiation buildFinalizedContractNegotiation(Agreement agreement) {
        return ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .agreement(agreement)
                .state(ContractNegotiationState.FINALIZED)
                .build();
    }

    private Agreement buildAgreement(String agreementId) {
        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Collections.singletonList(Constraint.Builder.newInstance()
                        .leftOperand(LeftOperand.COUNT)
                        .operator(Operator.LTEQ)
                        .rightOperand("5")
                        .build()))
                .build();

        return Agreement.Builder.newInstance()
                .id(agreementId)
                .assignee("assignee")
                .assigner("assigner")
                .target("test_dataset")
                .permission(Collections.singletonList(permission))
                .build();
    }
}
