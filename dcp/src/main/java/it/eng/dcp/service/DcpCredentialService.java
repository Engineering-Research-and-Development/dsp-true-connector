package it.eng.dcp.service;

import it.eng.dcp.model.PresentationQueryMessage;
import it.eng.dcp.model.PresentationResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Service to generate Verifiable Presentation JWT for connector authentication.
 *
 * This service uses DCP module classes to create signed VPs for authentication.
 * The feature is controlled by the dcp.vp.enabled property.
 */
@Service
public class DcpCredentialService {

    private static final Logger logger = LoggerFactory.getLogger(DcpCredentialService.class);

    private final PresentationService presentationService;

    @Value("${dcp.vp.enabled:false}")
    private boolean vpEnabled;

    @Value("${dcp.vp.scope:}")
    private String vpScope;

    /**
     * Constructor with DCP PresentationService dependency.
     *
     * @param presentationService DCP service for creating verifiable presentations
     */
    @Autowired
    public DcpCredentialService(PresentationService presentationService) {
        this.presentationService = presentationService;
        if (vpEnabled) {
            logger.info("VP JWT authentication is enabled for connector credentials");
        } else {
            logger.debug("VP JWT authentication is disabled (dcp.vp.enabled=false)");
        }
    }

    /**
     * Check if VP JWT authentication is enabled.
     * @return true if VP is enabled via property
     */
    public boolean isVpJwtEnabled() {
        return vpEnabled;
    }

    /**
     * Generate a Verifiable Presentation JWT for connector authentication.
     *
     * @return VP JWT string (without "Bearer " prefix) or null if generation fails
     */
    public String getVerifiablePresentationJwt() {
        if (!vpEnabled) {
            logger.debug("VP JWT generation skipped - not enabled (dcp.vp.enabled=false)");
            return null;
        }

        try {
            logger.debug("Generating Verifiable Presentation JWT for connector authentication");

            // Create PresentationQueryMessage using builder
            PresentationQueryMessage.Builder builder = PresentationQueryMessage.Builder.newInstance();

            // Set scope if configured
            if (vpScope != null && !vpScope.trim().isEmpty()) {
                List<String> scopeList = new ArrayList<>();
                for (String type : vpScope.split(",")) {
                    scopeList.add(type.trim());
                }
                builder.scope(scopeList);
                logger.debug("VP scope set to: {}", scopeList);
            }

            // Set empty presentation definition (use default)
            builder.presentationDefinition(new HashMap<>());

            PresentationQueryMessage queryMessage = builder.build();

            // Call PresentationService to create VP
            PresentationResponseMessage responseMessage = presentationService.createPresentation(queryMessage);

            // Extract presentations
            List<Object> presentations = responseMessage.getPresentation();

            if (presentations == null || presentations.isEmpty()) {
                logger.warn("No Verifiable Presentation generated - no credentials found in repository");
                return null;
            }

            // Get the first presentation (should be a JWT string)
            Object firstPresentation = presentations.get(0);
            if (firstPresentation instanceof String) {
                String vpJwt = (String) firstPresentation;
                logger.info("Successfully generated VP JWT for connector authentication (length: {})", vpJwt.length());
                logger.debug("VP JWT: {}", vpJwt);
                return vpJwt;
            } else {
                logger.warn("Presentation is not a JWT string: {}", firstPresentation.getClass().getName());
                return null;
            }

        } catch (Exception e) {
            logger.error("Failed to generate Verifiable Presentation JWT", e);
            return null;
        }
    }

    /**
     * Generate a Bearer token with VP JWT.
     * @return "Bearer {VP_JWT}" or null if generation fails
     */
    public String getBearerToken() {
        String vpJwt = getVerifiablePresentationJwt();
        if (vpJwt != null) {
            return "Bearer " + vpJwt;
        }
        return null;
    }
}

