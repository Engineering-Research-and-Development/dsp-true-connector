package it.eng.dcp.rest.api;

import it.eng.dcp.common.service.KeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/key")
public class KeyRotationAPIController {
    private final KeyService keyService;

    public KeyRotationAPIController(KeyService keyService) {
        this.keyService = keyService;
    }

    @PostMapping("/rotate")
    public ResponseEntity<String> rotateKey() {
        String alias = keyService.rotateKeyAndUpdateMetadata("eckey.p12", "password");
        return ResponseEntity.ok("Key rotated. New alias: " + alias);
    }
}

