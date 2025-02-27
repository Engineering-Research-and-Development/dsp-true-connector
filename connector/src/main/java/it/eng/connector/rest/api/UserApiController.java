package it.eng.connector.rest.api;

import java.security.Principal;
import java.util.Collection;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.connector.model.UserDTO;
import it.eng.connector.service.UserService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, 
path = ApiEndpoints.USERS_V1)
@Slf4j
public class UserApiController {
	
	private final UserService userService;

	public UserApiController(UserService userService) {
		this.userService = userService;
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
		JsonNode updatedUser = userService.updateUser(id, principal.getName(), userDTO);
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
		JsonNode updatedUser = userService.updatePassword(id, principal.getName(), userDTO);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(GenericApiResponse.success(updatedUser, "Password updated"));
	}
}
