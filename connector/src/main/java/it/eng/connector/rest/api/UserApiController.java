package it.eng.connector.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.model.UserDTO;
import it.eng.connector.service.UserService;
import it.eng.tools.auth.condition.BasicOrDisabledAuthenticationModeCondition;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Collection;

/**
 * REST controller for managing MongoDB-based users.
 * This controller is active whenever Keycloak mode is not selected.
 * When Keycloak mode is active, user management happens in Keycloak Admin Console.
 */
@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, 
path = ApiEndpoints.USERS_V1)
@Slf4j
@Conditional(BasicOrDisabledAuthenticationModeCondition.class)
public class UserApiController {
	
	private final UserService userService;

	public UserApiController(UserService userService) {
		this.userService = userService;
	}
	
	/**
	 * Returns the currently authenticated user's own profile.
	 *
	 * <p>Returns {@code 400 Bad Request} when there is no authenticated principal (disabled-auth
	 * mode), since there is no user identity to resolve.
	 *
	 * @param principal the authenticated principal injected by Spring Security
	 * @return the current user as a {@link GenericApiResponse}
	 */
	@GetMapping(path = "/me")
	public ResponseEntity<GenericApiResponse<JsonNode>> getCurrentUser(Principal principal) {
		if (principal == null) {
			throw new BadRequestException("No authenticated user in current context");
		}
		log.info("Fetching current user profile for principal '{}'", principal.getName());
		JsonNode user = userService.findCurrentUser(principal.getName());
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(user, "Current user"));
	}
	
	/**
	 * Find user.<br>
	 * By email or all 
	 * @param email
	 * @return GenericApiResponse
	 */
	@GetMapping(path = { "", "/{email}" })
	public ResponseEntity<GenericApiResponse<Collection<JsonNode>>> getUsers(
			@PathVariable(required = false) String email) {
		log.info("Fetching users, email {}", email);
		Collection<JsonNode> response = userService.findUsers(email);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(response, "Fetching users"));
	}
	
	/**
	 * Create new user.
	 * @param userDTO
	 * @return GenericApiResponse
	 */
	@PostMapping
	public ResponseEntity<GenericApiResponse<JsonNode>> createUser(@RequestBody UserDTO userDTO) {
		JsonNode newUser = userService.createUser(userDTO);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(newUser, "New user created"));
	}
	
	/**
	 * Update first name, last name and role.
	 * @param id
	 * @param userDTO
	 * @param principal
	 * @return GenericApiResponse
	 */
	@PutMapping(path = "/{id}/update")
	public ResponseEntity<GenericApiResponse<JsonNode>> updateUser(@PathVariable String id, @RequestBody UserDTO userDTO, Principal principal) {
		JsonNode updatedUser = userService.updateUser(id, resolvePrincipalName(principal), userDTO);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(updatedUser, "User updated"));
	}
	
	/**
	 * Update password for privided user.
	 * @param id
	 * @param userDTO
	 * @param principal
	 * @return GenericApiResponse
	 */
	@PutMapping(path = "/{id}/password")
	public ResponseEntity<GenericApiResponse<JsonNode>> updatePassword(@PathVariable String id, @RequestBody UserDTO userDTO, Principal principal) {
		JsonNode updatedUser = userService.updatePassword(id, resolvePrincipalName(principal), userDTO);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(updatedUser, "Password updated"));
	}

	private String resolvePrincipalName(Principal principal) {
		return principal != null ? principal.getName() : null;
	}
}
