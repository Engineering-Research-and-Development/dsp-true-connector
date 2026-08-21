package it.eng.connector.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.model.*;
import it.eng.connector.service.UserService;
import it.eng.tools.auth.condition.InternalOrDisabledAuthenticationModeCondition;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.rest.api.PagedAPIResponse;
import it.eng.tools.service.GenericFilterBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for managing MongoDB-based users.
 * This controller is active whenever Keycloak mode is not selected.
 * When Keycloak mode is active, user management happens in Keycloak Admin Console.
 */
@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, 
path = ApiEndpoints.USERS_V1)
@Slf4j
@Conditional(InternalOrDisabledAuthenticationModeCondition.class)
public class UserAPIController {

	private final GenericFilterBuilder filterBuilder;
	private final PagedResourcesAssembler<User> pagedResourcesAssembler;
	private final PlainUserAssembler plainAssembler;
	
	private final UserService userService;

	/**
	 * Constructs the controller with its service dependency.
	 *
	 * @param filterBuilder the filter builder
	 * @param pagedResourcesAssembler the paged resources assembler
	 * @param plainAssembler the plain user assembler
	 * @param userService the user service
	 * */
	public UserAPIController(GenericFilterBuilder filterBuilder,
	                         PagedResourcesAssembler<User> pagedResourcesAssembler,
	                         PlainUserAssembler plainAssembler,
	                         UserService userService) {
        this.filterBuilder = filterBuilder;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
        this.plainAssembler = plainAssembler;
        this.userService = userService;
	}

	/**
	 * Returns the user with the given ID.
	 *
	 * @param id the user identifier
	 * @return 200 OK with the user, or 404 if not found
	 */
	@GetMapping(path = "/{id}", consumes = MediaType.ALL_VALUE)
	public ResponseEntity<GenericApiResponse<User>> getUserById(@PathVariable String id) {
		log.info("Fetching user: {}", id);
		User user = userService.findById(id);
		return ResponseEntity.ok(GenericApiResponse.success(user, "User found"));
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
	 * Returns all users.
	 *
	 * @param request HttpServletRequest containing all filter parameters
	 * @param page    pagination page number (default 0)
	 * @param size    pagination parameters
	 * @param sort    sorting parameters in the format "field,direction"
	 *
	 * @return GenericApiResponse with matching users
	 */
	@GetMapping(consumes = MediaType.ALL_VALUE)
	public ResponseEntity<PagedAPIResponse> getAllUsers(HttpServletRequest request,
	                                                      @RequestParam(defaultValue = "0") int page,
	                                                      @RequestParam(defaultValue = "20") int size,
	                                                      @RequestParam(defaultValue = "timestamp,desc") String[] sort) {

		log.info("Fetching all users");

		Sort.Direction direction = (sort.length > 1 && sort[1].equalsIgnoreCase("desc")) ?
				Sort.Direction.DESC : Sort.Direction.ASC;
		Sort sorting = Sort.by(direction, sort[0]);
		Pageable pageable = PageRequest.of(page, size, sorting);
		// Build filter map automatically from ALL request parameters
		Map<String, Object> filters = filterBuilder.buildFromRequest(request);

		log.debug("Generated filters: {}", filters);

		Page<User> users = userService.findAll(filters, pageable);
		PagedModel<EntityModel<Object>> pagedModel = pagedResourcesAssembler.toModel(users, plainAssembler);

		String filterString = filters.entrySet().stream()
				.map(entry -> entry.getKey() + ":" + entry.getValue())
				.collect(Collectors.joining(", "));

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_JSON)
				.body(PagedAPIResponse.of(pagedModel,
						"Users - Page " + page + " of " + users.getTotalPages() + ", Size: " + size +
								", Sort: " + sorting + ", Filters: [" + filterString + "]"));
	}

	/**
	 * Create new user.
	 * @param request the user create request
	 * @return GenericApiResponse
	 */
	@PostMapping
	public ResponseEntity<GenericApiResponse<JsonNode>> createUser(@RequestBody UserCreateRequest request) {
		JsonNode newUser = userService.createUser(request);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(newUser, "New user created"));
	}

	/**
	 * Update user data.
	 * @param id      the user identifier
	 * @param request the user update request
	 * @return GenericApiResponse
	 */
	@PutMapping(path = "/{id}")
	public ResponseEntity<GenericApiResponse<JsonNode>> updateUser(@PathVariable String id, @RequestBody UserUpdateRequest request) {
		JsonNode updatedUser = userService.updateUser(id, request);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(updatedUser, "User updated"));
	}
	
	/**
	 * Update first name and last name.
	 * @param id        the user identifier
	 * @param request   the names update request
	 * @param principal the authenticated principal
	 * @return GenericApiResponse
	 */
	@PutMapping(path = "/{id}/updateNames")
	public ResponseEntity<GenericApiResponse<JsonNode>> updateUserNames(@PathVariable String id, @RequestBody UserNamesUpdateRequest request, Principal principal) {
		JsonNode updatedUser = userService.updateUserNames(id, resolvePrincipalName(principal), request);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(updatedUser, "User updated"));
	}
	
	/**
	 * Update password for provided user.
	 * @param id        the user identifier
	 * @param request   the password update request
	 * @param principal the authenticated principal
	 * @return GenericApiResponse
	 */
	@PutMapping(path = "/{id}/password")
	public ResponseEntity<GenericApiResponse<JsonNode>> updatePassword(@PathVariable String id, @RequestBody UserPasswordUpdateRequest request, Principal principal) {
		JsonNode updatedUser = userService.updatePassword(id, resolvePrincipalName(principal), request);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(updatedUser, "Password updated"));
	}

	private String resolvePrincipalName(Principal principal) {
		return principal != null ? principal.getName() : null;
	}
}
