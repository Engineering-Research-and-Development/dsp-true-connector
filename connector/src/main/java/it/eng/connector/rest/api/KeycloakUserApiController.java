package it.eng.connector.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.model.UserDTO;
import it.eng.connector.service.KeycloakUserService;
import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/**
 * REST controller for user management in Keycloak authentication mode.
 *
 * <p>This controller is active only when {@code application.auth.provider=KEYCLOAK}.
 * It mirrors the API surface of {@link UserApiController} but delegates to
 * {@link KeycloakUserService} to register users in the Keycloak realm.
 */
@RestController
@RequestMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.USERS_V1)
@Slf4j
@Conditional(KeycloakAuthenticationModeCondition.class)
public class KeycloakUserApiController {

    private final KeycloakUserService keycloakUserService;

    /**
     * Creates the controller with its required service dependency.
     *
     * @param keycloakUserService the Keycloak user management service
     */
    public KeycloakUserApiController(KeycloakUserService keycloakUserService) {
        this.keycloakUserService = keycloakUserService;
    }

    /**
     * Returns users from Keycloak, optionally filtered by e-mail.
     *
     * @param email optional e-mail path variable; may be {@code null}
     * @return 200 OK with the list of matching users
     */
    @GetMapping(path = {"", "/{email}"})
    public ResponseEntity<GenericApiResponse<Collection<JsonNode>>> getUsers(
            @PathVariable(required = false) String email) {
        log.info("Fetching users from Keycloak, email {}", email);
        Collection<JsonNode> response = keycloakUserService.findUsers(email);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(response, "Fetching users"));
    }

    /**
     * Creates a new user in the Keycloak realm.
     *
     * @param userDTO the user to create
     * @return 200 OK with the created user JSON
     */
    @PostMapping
    public ResponseEntity<GenericApiResponse<JsonNode>> createUser(@RequestBody UserDTO userDTO) {
        log.info("Creating user in Keycloak: {}", userDTO.getEmail());
        JsonNode newUser = keycloakUserService.createUser(userDTO);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(GenericApiResponse.success(newUser, "New user created"));
    }
}
