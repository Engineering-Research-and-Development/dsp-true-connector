package it.eng.dataplane.core.controller;

import it.eng.dataplane.core.config.DataPlaneProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Receives Control Plane registration acknowledgement.
 * Allows the Control Plane to notify this Data Plane of its endpoint.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/controlplanes")
public class ControlPlaneRegistrationController {

    private final DataPlaneProperties properties;

    /**
     * Accepts Control Plane registration confirmation.
     *
     * @param payload registration payload from CP containing endpoint information
     * @return 200 OK
     */
    @PutMapping
    public ResponseEntity<Void> registerControlPlane(@RequestBody Map<String, String> payload) {
        log.info("Control Plane registered: endpoint={}", payload.get("endpoint"));
        properties.setControlPlaneEndpoint(payload.get("endpoint"));
        return ResponseEntity.ok().build();
    }
}
