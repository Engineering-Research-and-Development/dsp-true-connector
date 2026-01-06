package it.eng.dcp.service;

import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.model.PresentationQueryMessage;
import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.model.VerifiableCredential;
import it.eng.dcp.model.VerifiablePresentation;
import it.eng.dcp.repository.VerifiableCredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
     * @param query the presentation query message containing scope and definition
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

        // Group by profileId (null profile grouped under default VC20_BSSL_JWT)
        Map<ProfileId, List<VerifiableCredential>> groups = fetched.stream()
                .collect(Collectors.groupingBy(vc ->
                    vc.getProfileId() != null ? vc.getProfileId() : ProfileId.VC20_BSSL_JWT));

        List<Object> signedPresentations = new ArrayList<>();

        for (Map.Entry<ProfileId, List<VerifiableCredential>> e : groups.entrySet()) {
            List<VerifiableCredential> groupCreds = e.getValue();
            if (groupCreds.isEmpty()) continue;

            // Collect credential IDs for reference
            List<String> credentialIds = groupCreds.stream()
                    .map(VerifiableCredential::getId)
                    .collect(Collectors.toList());

            // Collect full credentials for embedding in VP per DCP spec Section 5.4.2
            // This allows the verifier to validate VC signatures without additional fetch
            List<Object> fullCredentials = groupCreds.stream()
                    .map(vc -> {
                        // If VC already has JWT representation, use it
                        if (vc.getJwtRepresentation() != null && !vc.getJwtRepresentation().isBlank()) {
                            return vc.getJwtRepresentation();
                        }
                        // Otherwise, if credential JSON is available, use it
                        if (vc.getCredential() != null) {
                            return vc.getCredential();
                        }
                        // Fallback to credential ID
                        return vc.getId();
                    })
                    .collect(Collectors.toList());

            ProfileId profile = e.getKey();
            VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                    .holderDid(groupCreds.get(0).getHolderDid())
                    .credentialIds(credentialIds)  // Keep IDs for reference/tracking
                    .credentials(fullCredentials)   // Embed full VCs per DCP spec
                    .profileId(profile)
                    .build();

            // All official DCP profiles use JWT format
            String format = profile.getFormat();
            Object signed = vpSigner.sign(vp, format);
            signedPresentations.add(signed);
        }

        PresentationResponseMessage.Builder respBuilder = PresentationResponseMessage.Builder.newInstance();
        respBuilder.presentation(signedPresentations);
        return respBuilder.build();
    }

}
