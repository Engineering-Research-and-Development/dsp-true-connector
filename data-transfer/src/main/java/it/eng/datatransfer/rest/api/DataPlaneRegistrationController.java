package it.eng.datatransfer.rest.api;

import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST controller for managing Data Plane registrations.
 */
@RestController
@RequestMapping(ApiEndpoints.DATA_PLANES)
@Slf4j
@RequiredArgsConstructor
public class DataPlaneRegistrationController {

    private final DataPlaneRegistrationService service;

    /**
     * Registers a new Data Plane with the Control Plane.
     *
     * @param registration the Data Plane registration payload
     * @return 201 Created with the saved registration
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericApiResponse<DataPlaneRegistration>> register(
            @RequestBody DataPlaneRegistration registration) {
        log.info("Registering Data Plane at endpoint {}", registration.getEndpoint());
        DataPlaneRegistration saved = service.register(registration);
        return ResponseEntity.status(201)
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(saved, "Data Plane registered"));
    }

    /**
     * Deregisters a Data Plane by its id.
     *
     * @param id the id of the Data Plane registration to remove
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deregister(@PathVariable("id") String id) {
        log.info("Deregistering Data Plane with id {}", id);
        service.deregister(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all registered Data Plane instances.
     *
     * @return 200 OK with list of all registrations
     */
    @GetMapping
    public ResponseEntity<GenericApiResponse<List<DataPlaneRegistration>>> findAll() {
        log.debug("Listing all registered Data Planes");
        List<DataPlaneRegistration> registrations = service.findAll();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(registrations, "Data Planes fetched"));
    }
}
