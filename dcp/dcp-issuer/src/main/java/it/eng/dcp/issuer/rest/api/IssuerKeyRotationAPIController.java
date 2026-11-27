package it.eng.dcp.issuer.rest.api;

import it.eng.dcp.issuer.service.IssuerIdentity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller to rotate the signing key of the Issuer.
 */
@RestController
@RequestMapping("/api/issuer/key")
public class IssuerKeyRotationAPIController {
    private final IssuerIdentity issuerIdentity;

    /**
     * Constructor.
     *
     * @param issuerIdentity the single Issuer identity, which rotates its key
     */
    public IssuerKeyRotationAPIController(IssuerIdentity issuerIdentity) {
        this.issuerIdentity = issuerIdentity;
    }

    /**
     * Rotates the signing key of the Issuer.
     *
     * @return the alias of the new key
     */
    @PostMapping("/rotate")
    public ResponseEntity<String> rotateKey() {
        String alias = issuerIdentity.rotateKey();
        return ResponseEntity.ok("Key rotated. New alias: " + alias);
    }
}
