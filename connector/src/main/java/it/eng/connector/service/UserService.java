package it.eng.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.exception.UserNotFoundException;
import it.eng.connector.model.PasswordValidationResult;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;
import it.eng.connector.model.UserDTO;
import it.eng.connector.repository.UserRepository;
import it.eng.tools.auth.condition.InternalOrDisabledAuthenticationModeCondition;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.ResourceNotFoundException;
import it.eng.tools.serializer.ToolsSerializer;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
	private final AuditEventPublisher auditEventPublisher;

	/**
	 * Creates the service with its required dependencies.
	 *
	 * @param userRepository      the user repository
	 * @param encoder             the password encoder
	 * @param passwordValidator   the password-strength validator
	 * @param tenantService       the tenant service used to validate tenant existence
	 * @param auditEventPublisher publisher used to record user CRUD audit events
	 */
	public UserService(UserRepository userRepository, PasswordEncoder encoder,
			PasswordCheckValidator passwordValidator, TenantService tenantService,
			AuditEventPublisher auditEventPublisher) {
		super();
		this.userRepository = userRepository;
		this.encoder = encoder;
		this.passwordValidator = passwordValidator;
		this.tenantService = tenantService;
		this.auditEventPublisher = auditEventPublisher;
	}

	/**
	 * Finds a user by ID regardless of its enabled state.
	 *
	 * @param userId the user identifier
	 * @return the user
	 * @throws UserNotFoundException if the user does not exist
	 */
	public User findById(String userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
	}

	/**
	 * Returns the authenticated user's own record by e-mail.
	 *
	 * @param email the e-mail of the authenticated principal
	 * @return the user as a serialized JSON node
	 * @throws UserNotFoundException if no user with the given e-mail exists
	 */
	public JsonNode findCurrentUser(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + email));
		return ToolsSerializer.serializePlainJsonNode(user);
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
	 * Find users based on generic filter criteria.
	 * Supports any field with automatic type detection and conversion.
	 *
	 * @param filters  Map of field names to filter values. All values are pre-validated and converted.
	 * @param pageable Pageable
	 * @return page of User
	 */
	public Page<User> findAll(Map<String, Object> filters, Pageable pageable) {
		return userRepository.findWithDynamicFilters(filters, User.class, pageable);
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
			if (StringUtils.isNotBlank(userDTO.getTenantId())) {
				tenantService.findEnabledTenantById(userDTO.getTenantId());
			}
			User user = new User(createNewPid(),
					userDTO.getFirstName(),
					userDTO.getLastName(),
					userDTO.getEmail(),
					encoder.encode(userDTO.getPassword()),
					true, false, false,
					// If tenantId is provided, assign ADMIN role; otherwise, assign SUPER_ADMIN role.
					StringUtils.isNotBlank(userDTO.getTenantId()) ? Role.ADMIN : Role.SUPER_ADMIN,
					userDTO.getTenantId());
			User saved = userRepository.save(user);
			auditEventPublisher.publishEvent(
					AuditEventType.USER_CREATED, "User created", Map.of("email", user.getEmail()));
			return ToolsSerializer.serializePlainJsonNode(saved);
		} else {
			throw new BadRequestException(
					validationResult.getViolations().stream().collect(Collectors.joining(", ")));
		}
	}

	/**
	 * Updates the user data.
	 * @param id           the user identifier
	 * @param userDTO      the new field values
	 * @return the updated user as a serialized JSON node
	 * @throws UserNotFoundException if the user is not found
	 */
	public JsonNode updateUser(String id, UserDTO userDTO) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + id));

		user.setFirstName(userDTO.getFirstName() != null ? userDTO.getFirstName() : user.getFirstName());
		user.setLastName(userDTO.getLastName() != null ? userDTO.getLastName() : user.getLastName());
		if (StringUtils.isNotBlank(userDTO.getEmail()) && !userDTO.getEmail().equals(user.getEmail())) {
			userRepository.findByEmail(userDTO.getEmail())
					.ifPresent(u -> {
						throw new BadRequestException("User with email already exists");
					});
			user.setEmail(userDTO.getEmail());
		}

		if (StringUtils.isNotBlank(userDTO.getPassword())) {
			PasswordValidationResult validationResult = passwordValidator.isValid(userDTO.getPassword());
			if (validationResult.isValid()) {
				user.setPassword(encoder.encode(userDTO.getPassword()));
			} else {
				throw new BadRequestException(
						validationResult.getViolations().stream().collect(Collectors.joining(", ")));
			}
		}

		user.setEnabled(userDTO.isEnabled());
		user.setExpired(userDTO.isExpired());
		user.setLocked(userDTO.isLocked());

		userRepository.save(user);
		auditEventPublisher.publishEvent(
				AuditEventType.USER_UPDATED, "User updated", Map.of("email", user.getEmail()));
		return ToolsSerializer.serializePlainJsonNode(user);
	}

	/**
	 * Updates the first name and last name of the given user.
	 *
	 * @param id           the user identifier
	 * @param loggedInUser the e-mail of the authenticated principal, or {@code null} in disabled mode
	 * @param userDTO      the new field values
	 * @return the updated user as a serialized JSON node
	 * @throws UserNotFoundException if the user is not found
	 * @throws BadRequestException if the caller tries to update another user
	 */
	public JsonNode updateUserNames(String id, String loggedInUser, UserDTO userDTO) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + id));

		if (loggedInUser == null || user.getEmail().equals(loggedInUser)) {
			user.setFirstName(userDTO.getFirstName() != null ? userDTO.getFirstName() : user.getFirstName());
			user.setLastName(userDTO.getLastName() != null ? userDTO.getLastName() : user.getLastName());
			userRepository.save(user);
			auditEventPublisher.publishEvent(
					AuditEventType.USER_UPDATED, "User updated", Map.of("email", user.getEmail()));
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
	 * @throws UserNotFoundException if the user is not found
	 * @throws BadRequestException if the current password does not match or the new password fails strength validation
	 */
	public JsonNode updatePassword(String id, String loggedInUser, UserDTO userDTO) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + id));

		if (loggedInUser == null || user.getEmail().equals(loggedInUser)) {
			if (encoder.matches(userDTO.getPassword(), user.getPassword())) {
				PasswordValidationResult validationResult = passwordValidator.isValid(userDTO.getNewPassword());
				if (validationResult.isValid()) {
					user.setPassword(encoder.encode(userDTO.getNewPassword()));
					userRepository.save(user);
					auditEventPublisher.publishEvent(
							AuditEventType.USER_PASSWORD_CHANGED, "User password changed",
							Map.of("email", user.getEmail()));
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
