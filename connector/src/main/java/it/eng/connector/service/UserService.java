package it.eng.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.exception.UserNotFoundException;
import it.eng.connector.model.*;
import it.eng.connector.repository.UserRepository;
import it.eng.tools.auth.condition.InternalOrDisabledAuthenticationModeCondition;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.ResourceNotFoundException;
import it.eng.tools.exception.TenantNotFoundException;
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

import java.util.*;
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
	 * Returns the authenticated user's own record by email.
	 *
	 * @param email the email of the authenticated principal
	 * @return the user as a serialized JSON node
	 * @throws UserNotFoundException if no user with the given email exists
	 */
	public UserCurrentUserResponse findCurrentUser(String email) {
		UserCurrentUserResponse user = userRepository.findByEmail(email)
				.map(u -> UserCurrentUserResponse.Builder.newInstance()
						.firstName(u.getFirstName())
						.lastName(u.getLastName())
						.email(u.getEmail())
						.tenantId(u.getTenantId())
						.role(u.getRole().name())
						.build())
				.orElseThrow(() -> new UserNotFoundException("User not found: " + email));
		return user;
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
	 * Always excludes users with Role.CONNECTOR.
	 *
	 * @param filters  Map of field names to filter values. All values are pre-validated and converted.
	 * @param pageable Pageable
	 * @return page of User
	 */
	public Page<User> findAll(Map<String, Object> filters, Pageable pageable) {
		Map<String, Object> filterMap = new HashMap<>(filters != null ? filters : Collections.emptyMap());

		Object roleFilter = filterMap.get("role");

		if (roleFilter == null || (roleFilter instanceof String str && str.isBlank())) {
			// "All Roles" selected: include all roles except CONNECTOR
			List<Role> allowedRoles = Arrays.stream(Role.values())
					.filter(role -> role != Role.CONNECTOR)
					.toList();
			filterMap.put("role", allowedRoles);
		} else if (Role.CONNECTOR.equals(roleFilter)
				|| Role.CONNECTOR.name().equalsIgnoreCase(String.valueOf(roleFilter))) {
			// CONNECTOR explicitly requested: return no results
			filterMap.put("role", Collections.emptyList());
		}

		return userRepository.findWithDynamicFilters(filterMap, User.class, pageable);
	}

	/**
	 * Creates a new user.
	 *
	 * <p>For non-SUPER_ADMIN users the {@code tenantId} in {@code request} must reference an
	 * existing, enabled tenant.  SUPER_ADMIN users are exempt from this check.
	 *
	 * @param request the user data; {@code tenantId} is required unless the computed role is
	 *                {@code SUPER_ADMIN}
	 * @return the created user as a serialized JSON node
	 * @throws BadRequestException                            if the e-mail already exists or the password is invalid
	 * @throws TenantNotFoundException if the referenced tenant does not exist
	 */
	public JsonNode createUser(UserCreateRequest request) {
		userRepository.findByEmail(request.getEmail())
				.ifPresent(u -> {
					throw new BadRequestException("User with email already exists");
				});
		PasswordValidationResult validationResult = passwordValidator.isValid(request.getPassword());
		if (validationResult.isValid()) {
			// SUPER_ADMIN users are not bound to a specific tenant.
			if (StringUtils.isNotBlank(request.getTenantId())) {
				tenantService.findEnabledTenantById(request.getTenantId());
			}
			User user = new User(createNewPid(),
					request.getFirstName(),
					request.getLastName(),
					request.getEmail(),
					encoder.encode(request.getPassword()),
					true, false, false,
					// If tenantId is provided, assign ADMIN role; otherwise, assign SUPER_ADMIN role.
					StringUtils.isNotBlank(request.getTenantId()) ? Role.ADMIN : Role.SUPER_ADMIN,
					request.getTenantId());
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
	 * @param id      the user identifier
	 * @param request the new field values
	 * @return the updated user as a serialized JSON node
	 * @throws UserNotFoundException if the user is not found
	 */
	public JsonNode updateUser(String id, UserUpdateRequest request) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + id));

		user.setFirstName(request.getFirstName() != null ? request.getFirstName() : user.getFirstName());
		user.setLastName(request.getLastName() != null ? request.getLastName() : user.getLastName());
		if (StringUtils.isNotBlank(request.getEmail()) && !request.getEmail().equals(user.getEmail())) {
			userRepository.findByEmail(request.getEmail())
					.ifPresent(u -> {
						throw new BadRequestException("User with email already exists");
					});
			user.setEmail(request.getEmail());
		}

		if (StringUtils.isNotBlank(request.getPassword())) {
			PasswordValidationResult validationResult = passwordValidator.isValid(request.getPassword());
			if (validationResult.isValid()) {
				user.setPassword(encoder.encode(request.getPassword()));
			} else {
				throw new BadRequestException(
						validationResult.getViolations().stream().collect(Collectors.joining(", ")));
			}
		}

		user.setEnabled(request.isEnabled());
//		enable when functionality is implemented
//		user.setExpired(request.isExpired());
//		user.setLocked(request.isLocked());

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
	 * @param request      the new field values
	 * @return the updated user as a serialized JSON node
	 * @throws UserNotFoundException if the user is not found
	 * @throws BadRequestException if the caller tries to update another user
	 */
	public JsonNode updateUserNames(String id, String loggedInUser, UserNamesUpdateRequest request) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + id));

		if (loggedInUser == null || user.getEmail().equals(loggedInUser)) {
			user.setFirstName(request.getFirstName() != null ? request.getFirstName() : user.getFirstName());
			user.setLastName(request.getLastName() != null ? request.getLastName() : user.getLastName());
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
	 * @param request      must contain the current password in {@code password} and the new password
	 *                     in {@code newPassword}
	 * @return the updated user as a serialized JSON node
	 * @throws UserNotFoundException if the user is not found
	 * @throws BadRequestException if the current password does not match or the new password fails strength validation
	 */
	public JsonNode updatePassword(String id, String loggedInUser, UserPasswordUpdateRequest request) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + id));

		if (loggedInUser == null || user.getEmail().equals(loggedInUser)) {
			if (encoder.matches(request.getPassword(), user.getPassword())) {
				PasswordValidationResult validationResult = passwordValidator.isValid(request.getNewPassword());
				if (validationResult.isValid()) {
					user.setPassword(encoder.encode(request.getNewPassword()));
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
