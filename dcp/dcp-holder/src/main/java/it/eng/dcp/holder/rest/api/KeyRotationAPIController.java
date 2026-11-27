package it.eng.dcp.holder.rest.api;

import it.eng.dcp.common.participant.ParticipantKeyProvider;
import it.eng.dcp.common.participant.RequestParticipantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * REST API controller to rotate the holder signing key.
 */
@RestController
@RequestMapping("/api/key")
public class KeyRotationAPIController {

    private final ParticipantKeyProvider keyProvider;
    private final RequestParticipantContext requestParticipantContext;

    /**
     * Constructor.
     *
     * @param keyProvider provider that rotates the participant key
     * @param requestParticipantContext resolves the participant an administration request acts on
     */
    public KeyRotationAPIController(ParticipantKeyProvider keyProvider,
                                    RequestParticipantContext requestParticipantContext) {
        this.keyProvider = keyProvider;
        this.requestParticipantContext = requestParticipantContext;
    }

    /**
     * Rotates the signing key of the participant the request acts on.
     *
     * @param request   the inbound request
     * @param principal the authenticated administration principal
     * @return the alias of the new key
     */
    @PostMapping("/rotate")
    public ResponseEntity<String> rotateKey(HttpServletRequest request, Principal principal) {
        String alias = keyProvider.rotate(requestParticipantContext.requireForAdmin(request, principal));
        return ResponseEntity.ok("Key rotated. New alias: " + alias);
    }
}
