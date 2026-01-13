package it.eng.dcp.issuer.rest.api;

import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.issuer.config.IssuerProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/issuer/key")
public class KeyRotationAPIController {
    private final KeyService keyService;
    private final IssuerProperties issuerProperties;

    public KeyRotationAPIController(KeyService keyService, IssuerProperties issuerProperties) {
        this.keyService = keyService;
        this.issuerProperties = issuerProperties;
    }

    @PostMapping("/rotate")
    public ResponseEntity<String> rotateKey() {
        String keystorePath = issuerProperties.getKeystore().getPath();
        String keystorePassword = issuerProperties.getKeystore().getPassword();
        String alias = keyService.rotateKeyAndUpdateMetadata(keystorePath, keystorePassword, issuerProperties.getKeystore().getAlias());
        return ResponseEntity.ok("Key rotated. New alias: " + alias);
    }
}
