package it.eng.dcp.service;

import it.eng.dcp.model.PresentationQueryMessage;
import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.model.VerifiableCredential;
import it.eng.dcp.model.VerifiablePresentation;
import it.eng.dcp.repository.VerifiableCredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service orchestrating presentation creation: fetch credentials, group by profile, build homogenous VPs and sign them.
 */
@Service
public class PresentationService {

    private final VerifiableCredentialRepository credentialRepository;
    private final VerifiablePresentationSigner vpSigner;

    @Autowired
    public PresentationService(VerifiableCredentialRepository credentialRepository, VerifiablePresentationSigner vpSigner) {
        this.credentialRepository = credentialRepository;
        this.vpSigner = vpSigner;
    }

    /**
     * Create a PresentationResponseMessage from a PresentationQueryMessage.
     * Groups fetched credentials by profileId and signs each homogenous group.
     * @param query the presentation query message
     * @return the presentation response message containing signed presentations
     */
    public PresentationResponseMessage createPresentation(PresentationQueryMessage query) {
        List<String> requiredTypes = query.getScope();
        List<VerifiableCredential> fetched;
        if (requiredTypes == null || requiredTypes.isEmpty()) {
            // fetch all credentials
            fetched = credentialRepository.findAll();
        } else {
            fetched = credentialRepository.findByCredentialTypeIn(requiredTypes);
        }

        // Group by profileId (null profile grouped under empty string)
        Map<String, List<VerifiableCredential>> groups = fetched.stream()
                .collect(Collectors.groupingBy(vc -> vc.getProfileId() == null ? "" : vc.getProfileId()));

        List<Object> signedPresentations = new ArrayList<>();

        for (Map.Entry<String, List<VerifiableCredential>> e : groups.entrySet()) {
            List<VerifiableCredential> groupCreds = e.getValue();
            if (groupCreds.isEmpty()) continue;

            // Build a VerifiablePresentation containing the group's credential ids
            List<String> credentialIds = groupCreds.stream().map(VerifiableCredential::getId).collect(Collectors.toList());
            String profile = e.getKey().isEmpty() ? null : e.getKey();
            VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                    .holderDid(groupCreds.get(0).getHolderDid())
                    .credentialIds(credentialIds)
                    .profileId(profile)
                    .build();

            // Decide format based on profile or default to json-ld; for now assume json-ld
            String format = "json-ld";
            Object signed = vpSigner.sign(vp, format);
            signedPresentations.add(signed);
        }

        PresentationResponseMessage.Builder respBuilder = PresentationResponseMessage.Builder.newInstance();
        respBuilder.presentation(signedPresentations);
        return respBuilder.build();
    }
}
