package it.eng.connector.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.connector.exception.UserNotFoundException;
import it.eng.connector.model.*;
import it.eng.connector.repository.UserRepository;
import it.eng.connector.util.TestUtil;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.exception.BadRequestException;
import it.eng.tools.exception.ResourceNotFoundException;
import it.eng.tools.exception.TenantNotFoundException;
import it.eng.tools.model.Tenant;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final String USER_ID = "1234";
	private static final String USER = "user@test.com";
	private static final String TENANT_ID = "engineering";

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordEncoder encoder;
	@Mock
	private PasswordCheckValidator passwordValidator;
	@Mock
	private TenantService tenantService;
	@Mock
	private AuditEventPublisher auditEventPublisher;

	@InjectMocks
	private UserService userService;

	@Mock
	private PasswordValidationResult passwordValidationResult;

	@Test
	@DisplayName("Find users")
	void testFindUsers() {
		when(userRepository.findAll()).thenReturn(Arrays.asList(TestUtil.USER));
		Collection<JsonNode> response = userService.findUsers(null);
		assertNotNull(response);
		assertFalse(response.stream().allMatch(jn -> jn.toPrettyString().contains("password")));

		// found by email
		when(userRepository.findByEmail(TestUtil.USER.getEmail())).thenReturn(Optional.of(TestUtil.USER));
		response = userService.findUsers(TestUtil.USER.getEmail());
		assertNotNull(response);
		assertFalse(response.stream().allMatch(jn -> jn.toPrettyString().contains("password")));

		// not found by email
		when(userRepository.findByEmail("not found")).thenReturn(Optional.empty());
		assertThrows(ResourceNotFoundException.class, () -> userService.findUsers("not found"));
	}

	@Test
	@DisplayName("findCurrentUser returns user for known email")
	void findCurrentUser_returnsUser() {
		when(userRepository.findByEmail(TestUtil.USER.getEmail()))
				.thenReturn(Optional.of(TestUtil.USER));

		JsonNode result = userService.findCurrentUser(TestUtil.USER.getEmail());

		assertNotNull(result);
		assertEquals(TestUtil.USER.getEmail(), result.get("email").asText());
		verify(userRepository).findByEmail(TestUtil.USER.getEmail());
	}

	@Test
	@DisplayName("findCurrentUser throws UserNotFoundException for unknown email")
	void findCurrentUser_notFound_throws() {
		when(userRepository.findByEmail("unknown@mail.com")).thenReturn(Optional.empty());

		assertThrows(UserNotFoundException.class,
				() -> userService.findCurrentUser("unknown@mail.com"));
	}

	private UserCreateRequest createRequest(String email, String password, String tenantId) {
		return UserCreateRequest.Builder.newInstance()
				.firstName("First")
				.lastName("Last")
				.email(email)
				.password(password)
				.tenantId(tenantId)
				.build();
	}

	@Test
	@DisplayName("Create user with valid tenantId - user saved with tenantId set")
	void createUser_withValidTenantId() {
		UserCreateRequest request = createRequest(USER, "StrongPassword1!", TENANT_ID);
		when(userRepository.findByEmail(USER)).thenReturn(Optional.empty());
		when(passwordValidator.isValid(request.getPassword())).thenReturn(passwordValidationResult);
		when(passwordValidationResult.isValid()).thenReturn(true);
		when(tenantService.findEnabledTenantById(TENANT_ID)).thenReturn(
				Tenant.Builder.newInstance().id(TENANT_ID).name("Engineering")
						.participantId("urn:connector:engineering")
						.enabled(true).build());

		userService.createUser(request);

		verify(tenantService).findEnabledTenantById(TENANT_ID);
		verify(userRepository).save(any(User.class));
		verify(auditEventPublisher).publishEvent(eq(AuditEventType.USER_CREATED), anyString(), any());
	}

	@Test
	@DisplayName("Create user with non-existent tenantId - TenantNotFoundException thrown")
	void createUser_withNonExistentTenantId() {
		UserCreateRequest request = createRequest(USER, "StrongPassword1!", "non-existent-tenant");
		when(userRepository.findByEmail(USER)).thenReturn(Optional.empty());
		when(passwordValidator.isValid(request.getPassword())).thenReturn(passwordValidationResult);
		when(passwordValidationResult.isValid()).thenReturn(true);
		when(tenantService.findEnabledTenantById("non-existent-tenant"))
				.thenThrow(new TenantNotFoundException("Tenant not found"));

		assertThrows(TenantNotFoundException.class, () -> userService.createUser(request));

		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	@DisplayName("Create ROLE_SUPER_ADMIN user without tenantId - succeeds without tenant lookup")
	void createUser_superAdmin_noTenantId() {
		UserCreateRequest request = createRequest(USER, "StrongPassword1!", null);
		when(userRepository.findByEmail(USER)).thenReturn(Optional.empty());
		when(passwordValidator.isValid(request.getPassword())).thenReturn(passwordValidationResult);
		when(passwordValidationResult.isValid()).thenReturn(true);

		userService.createUser(request);

		// SUPER_ADMIN must never trigger a tenant lookup
		verify(tenantService, never()).findEnabledTenantById(anyString());
		verify(userRepository).save(any(User.class));
	}

	@Test
	@DisplayName("Create user - user email already exists")
	void createUser_not_found() {
		UserCreateRequest request = createRequest(USER, "StrongPassword1!", null);
		when(userRepository.findByEmail(USER)).thenReturn(Optional.of(TestUtil.USER));
		assertThrows(BadRequestException.class, () -> userService.createUser(request));

		verify(userRepository, times(0)).save(any(User.class));
	}

	@Test
	@DisplayName("Create user - password not valid")
	void createUser_weak_password() {
		UserCreateRequest request = createRequest(USER, "weak", null);
		when(userRepository.findByEmail(USER)).thenReturn(Optional.empty());
		when(passwordValidator.isValid(request.getPassword())).thenReturn(passwordValidationResult);
		when(passwordValidationResult.isValid()).thenReturn(false);

		assertThrows(BadRequestException.class, () -> userService.createUser(request));

		verify(userRepository, times(0)).save(any(User.class));
	}

	@Test
	@DisplayName("Update user")
	void updateUser() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));
		UserUpdateRequest request = UserUpdateRequest.Builder.newInstance()
				.firstName("First Name update")
				.lastName("Last Name update")
				.build();

		userService.updateUser(USER_ID, request);

		verify(userRepository).save(any(User.class));
		verify(auditEventPublisher).publishEvent(eq(AuditEventType.USER_UPDATED), anyString(), any());
	}

	@Test
	@DisplayName("Update user - user not found")
	void updateUser_not_found() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
		UserUpdateRequest request = UserUpdateRequest.Builder.newInstance().build();

		assertThrows(UserNotFoundException.class,
				() -> userService.updateUser(USER_ID, request));

		verify(userRepository, times(0)).save(any(User.class));
	}

	@Test
	@DisplayName("Update user - updating other with existing email")
	void updateUser_other_with_existing_email() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));
		UserUpdateRequest request = UserUpdateRequest.Builder.newInstance()
				.email("existingemail@mail.com")
				.build();
		when(userRepository.findByEmail("existingemail@mail.com")).thenReturn(Optional.of(TestUtil.USER));

		assertThrows(BadRequestException.class,
				() -> userService.updateUser(USER_ID, request));

		verify(userRepository, times(0)).save(any(User.class));
	}

	@Test
	@DisplayName("Update user names")
	void updateUserNames() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));
		UserNamesUpdateRequest request = UserNamesUpdateRequest.Builder.newInstance()
				.firstName("First Name update")
				.lastName("Last Name update")
				.build();

		userService.updateUserNames(USER_ID, TestUtil.USER.getEmail(), request);

		verify(userRepository).save(any(User.class));
		verify(auditEventPublisher).publishEvent(eq(AuditEventType.USER_UPDATED), anyString(), any());
	}

	@Test
	@DisplayName("Update user names without logged in principal in disabled mode")
	void updateUser_withoutLoggedInUserNames() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));
		UserNamesUpdateRequest request = UserNamesUpdateRequest.Builder.newInstance()
				.firstName("First Name update")
				.lastName("Last Name update")
				.build();

		userService.updateUserNames(USER_ID, null, request);

		verify(userRepository).save(any(User.class));
	}

	@Test
	@DisplayName("Update user names - user not found")
	void updateUser_Names_not_found() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
		UserNamesUpdateRequest request = UserNamesUpdateRequest.Builder.newInstance().build();

		assertThrows(UserNotFoundException.class,
				() -> userService.updateUserNames(USER_ID, TestUtil.USER.getEmail(), request));

		verify(userRepository, times(0)).save(any(User.class));
	}

	@Test
	@DisplayName("Update user names - updating other user than own")
	void updateUser_other_userNames() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));
		UserNamesUpdateRequest request = UserNamesUpdateRequest.Builder.newInstance().build();

		assertThrows(BadRequestException.class,
				() -> userService.updateUserNames(USER_ID, "otheruser@mail.com", request));

		verify(userRepository, times(0)).save(any(User.class));
	}

	@Test
	@DisplayName("Update user password")
	void updatePassword() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));
		when(encoder.matches(anyString(), anyString())).thenReturn(true);
		UserPasswordUpdateRequest request = UserPasswordUpdateRequest.Builder.newInstance()
				.password("aaa")
				.newPassword("newPassword")
				.build();
		when(passwordValidator.isValid(anyString())).thenReturn(passwordValidationResult);
		when(passwordValidationResult.isValid()).thenReturn(true);
		when(encoder.encode(anyString())).thenReturn("passwordEncoded");

		userService.updatePassword(USER_ID, TestUtil.USER.getEmail(), request);

		verify(userRepository).save(any(User.class));
		verify(auditEventPublisher).publishEvent(eq(AuditEventType.USER_PASSWORD_CHANGED), anyString(), any());
	}

	@Test
	@DisplayName("Update user password without logged in principal in disabled mode")
	void updatePassword_withoutLoggedInUser() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));
		when(encoder.matches(anyString(), anyString())).thenReturn(true);
		UserPasswordUpdateRequest request = UserPasswordUpdateRequest.Builder.newInstance()
				.password("aaa")
				.newPassword("newPassword")
				.build();
		when(passwordValidator.isValid(anyString())).thenReturn(passwordValidationResult);
		when(passwordValidationResult.isValid()).thenReturn(true);
		when(encoder.encode(anyString())).thenReturn("passwordEncoded");

		userService.updatePassword(USER_ID, null, request);

		verify(userRepository).save(any(User.class));
	}

	@Test
	@DisplayName("Update user password - user not found")
	void updatePassword_not_found() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
		UserPasswordUpdateRequest request = UserPasswordUpdateRequest.Builder.newInstance()
				.password("aaa")
				.newPassword("newPassword")
				.build();

		assertThrows(UserNotFoundException.class,
				() -> userService.updatePassword(USER_ID, USER, request));

		verify(userRepository, times(0)).save(any(User.class));
	}

	@Test
	@DisplayName("Update user password - old password not match")
	void updatePassword_not_match() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(TestUtil.USER));
		UserPasswordUpdateRequest request = UserPasswordUpdateRequest.Builder.newInstance()
				.password("aaa")
				.newPassword("newPassword")
				.build();
		when(encoder.matches(anyString(), anyString())).thenReturn(false);

		assertThrows(BadRequestException.class,
				() -> userService.updatePassword(USER_ID, TestUtil.USER.getEmail(), request));

		verify(userRepository, times(0)).save(any(User.class));
	}

}
