package it.eng.connector.service;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.connector.model.PasswordValidationResult;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.model.UserDTO;
import it.eng.connector.repository.UserRepository;
import it.eng.tools.auth.condition.InternalOrDisabledAuthenticationModeCondition;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.ResourceNotFoundException;
import it.eng.tools.serializer.ToolsSerializer;
import it.eng.tools.service.TenantService;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing MongoDB-based users.
 *
 * <p>This service is active whenever Keycloak mode is not selected.
 * When Keycloak mode is active, user management is delegated to {@code KeycloakUserService}.
 */
@Service
@Slf4j
@Conditional(InternalOrDisabledAuthenticationModeCondition.class)
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder encoder;
	private final PasswordCheckValidator passwordValidator;
	private final TenantService tenantService;

	/**
	 * Creates the service with its required dependencies.
	 *
	 * @param userRepository    the user repository
	 * @param encoder           the password encoder
	 * @param passwordValidator the password-strength validator
	 * @param tenantService     the tenant service used to validate tenant existence
	 */
	public UserService(UserRepository userRepository, PasswordEncoder encoder,
			PasswordCheckValidator passwordValidator, TenantService tenantService) {
		super();
		this.userRepository = userRepository;
		this.encoder = encoder;
		this.passwordValidator = passwordValidator;
		this.tenantService = tenantService;
	}

	/**
	 * Returns all users, or only the user matching the given e-mail when {@code email} is non-blank.
	 *
	 * @param email optional e-mail filter; may be {@code null} or blank
	 * @return the matching user(s) as serialized JSON nodes
	 * @throws ResourceNotFoundException if an e-mail filter is given but no user matches
	 */
	public Collection<JsonNode> findUsers(String email) throws ResourceNotFoundException {
		if (StringUtils.isNotBlank(email)) {
			User user = userRepository.findByEmail(email).orElseThrow(ResourceNotFoundException::new);
			return Collections.singletonList(ToolsSerializer.serializePlainJsonNode(user));
		}
		return userRepository.findAll()
				.stream()
				.map(u -> ToolsSerializer.serializePlainJsonNode(u))
				.collect(Collectors.toList());
	}

	/**
	 * Returns the authenticated user's own record by e-mail.
	 *
	 * @param email the e-mail of the authenticated principal
	 * @return the user as a serialized JSON node
	 * @throws BadRequestException if no user with the given e-mail exists
	 */
	public JsonNode findCurrentUser(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new BadRequestException("User not found"));
		return ToolsSerializer.serializePlainJsonNode(user);
	}

	/**
	 * Creates a new user.
	 *
	 * <p>For non-SUPER_ADMIN users the {@code tenantId} in {@code userDTO} must reference an
	 * existing, enabled tenant.  SUPER_ADMIN users are exempt from this check.
	 *
	 * @param userDTO the user data; {@code tenantId} is required unless the role is
	 *                {@code SUPER_ADMIN}
	 * @return the created user as a serialized JSON node
	 * @throws BadRequestException                            if the e-mail already exists or the password is invalid
	 * @throws it.eng.tools.exception.TenantNotFoundException if the referenced tenant does not exist
	 */
	public JsonNode createUser(UserDTO userDTO) {
		userRepository.findByEmail(userDTO.getEmail())
				.ifPresent(u -> {
					throw new BadRequestException("User with email already exists");
				});
		PasswordValidationResult validationResult = passwordValidator.isValid(userDTO.getPassword());
		if (validationResult.isValid()) {
			// SUPER_ADMIN users are not bound to a specific tenant.
			if (StringUtils.isNotBlank(userDTO.getTenantId())
					&& userDTO.getRole() != Role.SUPER_ADMIN) {
				tenantService.findEnabledTenantById(userDTO.getTenantId());
			}
			User user = new User(createNewPid(), userDTO.getFirstName(), userDTO.getLastName(),
					userDTO.getEmail(), encoder.encode(userDTO.getPassword()),
					true, false, false, userDTO.getRole());
			user.setTenantId(userDTO.getTenantId());
			User saved = userRepository.save(user);
			return ToolsSerializer.serializePlainJsonNode(saved);
		} else {
			throw new BadRequestException(
					validationResult.getViolations().stream().collect(Collectors.joining(", ")));
		}
	}

	/**
	 * Updates the first name and last name of the given user.
	 *
	 * @param id           the user identifier
	 * @param loggedInUser the e-mail of the authenticated principal, or {@code null} in disabled mode
	 * @param userDTO      the new field values
	 * @return the updated user as a serialized JSON node
	 * @throws BadRequestException if the user is not found or the caller tries to update another user
	 */
	public JsonNode updateUser(String id, String loggedInUser, UserDTO userDTO) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new BadRequestException("User not found"));

		if (loggedInUser == null || user.getEmail().equals(loggedInUser)) {
			user.setFirstName(userDTO.getFirstName() != null ? userDTO.getFirstName() : user.getFirstName());
			user.setLastName(userDTO.getLastName() != null ? userDTO.getLastName() : user.getLastName());
			userRepository.save(user);
			return ToolsSerializer.serializePlainJsonNode(user);
		} else {
			log.error("Not allowed to change other user email");
			throw new BadRequestException("Not allowed to change other user email");
		}
	}

	/**
	 * Updates the password of the given user after verifying the current password.
	 *
	 * @param id           the user identifier
	 * @param loggedInUser the e-mail of the authenticated principal, or {@code null} in disabled mode
	 * @param userDTO      must contain the current password in {@code password} and the new password
	 *                     in {@code newPassword}
	 * @return the updated user as a serialized JSON node
	 * @throws BadRequestException if the user is not found, the current password does not match,
	 *                             or the new password fails strength validation
	 */
	public JsonNode updatePassword(String id, String loggedInUser, UserDTO userDTO) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new BadRequestException("User not found"));

		if (loggedInUser == null || user.getEmail().equals(loggedInUser)) {
			if (encoder.matches(userDTO.getPassword(), user.getPassword())) {
				PasswordValidationResult validationResult = passwordValidator.isValid(userDTO.getNewPassword());
				if (validationResult.isValid()) {
					user.setPassword(encoder.encode(userDTO.getNewPassword()));
					userRepository.save(user);
					return ToolsSerializer.serializePlainJsonNode(user);
				} else {
					log.warn("Password not valid with strength check");
					throw new BadRequestException(
							validationResult.getViolations().stream().collect(Collectors.joining(", ")));
				}
			} else {
				throw new BadRequestException("Old password does not match");
			}
		} else {
			log.error("Not allowed to change other user email");
			throw new BadRequestException("Not allowed to change other user email");
		}
	}

	private String createNewPid() {
		return "urn:uuid:" + UUID.randomUUID();
	}
}
