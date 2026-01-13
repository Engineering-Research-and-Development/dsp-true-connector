package it.eng.dcp.rest.api;

import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.config.DcpProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/key")
public class KeyRotationAPIController {

    private final KeyService keyService;
    private final DcpProperties holderProperties;

    public KeyRotationAPIController(KeyService keyService, DcpProperties holderProperties) {
        this.keyService = keyService;
        this.holderProperties = holderProperties;
    }

    @PostMapping("/rotate")
    public ResponseEntity<String> rotateKey() {
        String alias = keyService.rotateKeyAndUpdateMetadata(holderProperties.getKeystore().getPath(),
                holderProperties.getKeystore().getPassword(),
                holderProperties.getKeystore().getAlias());
        return ResponseEntity.ok("Key rotated. New alias: " + alias);
    }
}

