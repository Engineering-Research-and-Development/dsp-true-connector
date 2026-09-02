package it.eng.datatransfer.rest.api;

import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Admin REST controller for managing Data Plane registrations.
 *
 * <p>This controller uses a two-tier authentication model: {@code POST /api/v1/dataplanes}
 * requires a Control Plane-wide bootstrap key presented in the {@code X-Registration-Key}
 * header, while {@code DELETE /api/v1/dataplanes/{id}} requires the specific Data Plane's
 * ownership key presented in the {@code X-Api-Key} header and verified by the service against
 * the stored hash. {@code GET /api/v1/dataplanes} intentionally has no bespoke header-based
 * authentication here and relies on the default {@code /api/**} {@code ROLE_ADMIN} security
 * rule. The Dataplane Signaling Protocol (DPS) specification leaves registration-endpoint
 * authentication as implementation-specific ("MAY require an authorization mechanism such as
 * OAuth 2.0 or API Key") and models any such mechanism as an optional {@code authorization}
 * object inside the registration request body, not as a bespoke HTTP header.
 * {@code X-Registration-Key} and {@code X-Api-Key} are therefore a TRUE Connector
 * implementation choice made under that latitude — not a DPS-mandated or DPS-recommended
 * header shape. Do not describe this mechanism as "DPS-compliant" in code comments or docs.
 */
@RestController
@RequestMapping(ApiEndpoints.DATA_PLANES)
@Slf4j
public class DataPlaneRegistrationController {

    private static final String REGISTRATION_KEY_HEADER = "X-Registration-Key";
    private static final String API_KEY_HEADER = "X-Api-Key";

    private final DataPlaneRegistrationService service;
    private final String bootstrapKey;

    /**
     * Creates the controller with the registration bootstrap key.
     *
     * @param service the registration service
     * @param bootstrapKey the Control Plane bootstrap key used for registration
     */
    public DataPlaneRegistrationController(DataPlaneRegistrationService service,
                                           @Value("${dataplane.registration.bootstrap-key}") String bootstrapKey) {
        this.service = service;
        this.bootstrapKey = bootstrapKey;
    }

    /**
     * Registers a new Data Plane with the Control Plane.
     *
     * @param registrationKey the bootstrap key presented in the registration header
     * @param registration the Data Plane registration payload
     * @return 201 Created with the saved registration when authenticated, otherwise 401 Unauthorized
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericApiResponse<DataPlaneRegistration>> register(
            @RequestHeader(value = REGISTRATION_KEY_HEADER, required = false) String registrationKey,
            @Valid @RequestBody DataPlaneRegistration registration) {
        if (!isValidRegistrationKey(registrationKey)) {
            log.warn("Rejected Data Plane registration due to invalid bootstrap key");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("Registering Data Plane at endpoint {}", registration.getEndpoint());
        DataPlaneRegistration saved = service.register(registration);
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(saved, "Data Plane registered"));
    }

    /**
     * Deregisters a Data Plane by its id.
     *
     * @param id the id of the Data Plane registration to remove
     * @param apiKey the ownership key presented in the deregistration header
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deregister(@PathVariable String id,
                                           @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        log.info("Deregistering Data Plane with id {}", id);
        service.deregister(id, apiKey);
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

    private boolean isValidRegistrationKey(String presentedKey) {
        if (presentedKey == null || bootstrapKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                bootstrapKey.getBytes(StandardCharsets.UTF_8),
                presentedKey.getBytes(StandardCharsets.UTF_8));
    }
}
