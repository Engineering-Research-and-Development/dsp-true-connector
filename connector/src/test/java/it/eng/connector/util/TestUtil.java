package it.eng.connector.util;

import it.eng.connector.configuration.ApiUserPrincipal;
import it.eng.connector.dto.*;
import it.eng.connector.model.Role;
import it.eng.connector.model.User;

public class TestUtil {
	
	// === SEED DATA (from initial_data.json) ===
	public static final String ADMIN_USER = "admin@mail.com";
	public static final String ADMIN_USER_ID = "59d54e6d-a3f3-4a03-a093-d276e3068eef";
	public static final String CONNECTOR_USER = "connector@mail.com";
	public static final String CONNECTOR_USER_ID = "cab7b27b-f810-457d-b900-368994f6a640";
	public static final String REGULAR_USER = "user@mail.com";
	public static final String REGULAR_USER_ID = "f9e8d7c6-b5a4-4321-9876-543210abcdef";
	
	// === COMMON TEST DATA ===
	public static final String TEST_EMAIL = "test@example.com";
	public static final String TEST_PASSWORD = "TestPassword123!";
	public static final String TEST_FIRST_NAME = "Test";
	public static final String TEST_LAST_NAME = "User";
    public static final String TEST_USER_ID = "mock-user-id-12345";
	public static final String TEST_ACCESS_TOKEN = "test-access-token";
	public static final String TEST_REFRESH_TOKEN = "test-refresh-token";
	
	// === OTHER TEST DATA ===
	public static final String NON_EXISTENT_USER_ID = "urn:uuid:00000000-0000-0000-0000-000000000000";
	public static final String DATASET_ID = "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5";
	public static final String PROVIDER_PID = "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab";
	public static final String CONSUMER_PID = "urn:uuid:32541fe6-c580-409e-85a8-8e9a32fbe833";
	
	// (removed legacy backward-compatibility constants and User object)

	// === USER CREATION METHODS ===
	
	/**
	 * Creates a User object for the specified role
	 */
	public static User createUser(Role role) {
		return createUser(role, "mock-" + role.name().toLowerCase() + "@example.com");
	}
	
	/**
	 * Creates a User object for the specified role with custom email
	 */
	public static User createUser(Role role, String email) {
		String rolePrefix = role.name().toLowerCase().replace("role_", "");
		return User.builder()
				.id("mock-" + rolePrefix + "-id-" + System.currentTimeMillis())
				.firstName("Mock")
				.lastName(rolePrefix.substring(0, 1).toUpperCase() + rolePrefix.substring(1))
				.email(email)
				.password("$2a$10$encoded.password.for." + rolePrefix + ".test")
				.role(role)
				.enabled(true)
				.expired(false)
				.locked(false)
				.build();
	}
	
	/**
	 * Creates an ApiUserPrincipal for the specified role
	 */
	public static ApiUserPrincipal createApiUserPrincipal(Role role) {
		ApiUserPrincipal principal = new ApiUserPrincipal();
		principal.setUserId("mock-" + role.name().toLowerCase() + "-id");
		principal.setEmail("mock." + role.name().toLowerCase() + "@example.com");
		principal.setFirstName("Mock");
		principal.setLastName(role.name().replace("ROLE_", ""));
		principal.setRole(role);
		return principal;
	}

	// === AUTHENTICATION DTO CREATION METHODS ===
	
	/**
	 * Creates a LoginRequest with default test data
	 */
	public static LoginRequest createLoginRequest() {
		return createLoginRequest(TEST_EMAIL, TEST_PASSWORD);
	}
	
	/**
	 * Creates a LoginRequest with custom email and password
	 */
	public static LoginRequest createLoginRequest(String email, String password) {
		return LoginRequest.builder()
				.email(email)
				.password(password)
				.build();
	}

	/**
	 * Creates a RegisterRequest with default test data
	 */
	public static RegisterRequest createRegisterRequest() {
		return createRegisterRequest(TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL, TEST_PASSWORD);
	}
	
	/**
	 * Creates a RegisterRequest with custom data
	 */
	public static RegisterRequest createRegisterRequest(String firstName, String lastName, String email, String password) {
		return RegisterRequest.builder()
				.firstName(firstName)
				.lastName(lastName)
				.email(email)
				.password(password)
				.build();
	}

	/**
	 * Creates a RefreshTokenRequest with default test token
	 */
	public static RefreshTokenRequest createRefreshTokenRequest() {
		return createRefreshTokenRequest(TEST_REFRESH_TOKEN);
	}
	
	/**
	 * Creates a RefreshTokenRequest with custom token
	 */
	public static RefreshTokenRequest createRefreshTokenRequest(String refreshToken) {
		return RefreshTokenRequest.builder()
				.refreshToken(refreshToken)
				.build();
	}

	/**
	 * Creates a LogoutRequest with default test tokens
	 */
	public static LogoutRequest createLogoutRequest() {
		return createLogoutRequest(TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN);
	}
	
	/**
	 * Creates a LogoutRequest with custom tokens
	 */
	public static LogoutRequest createLogoutRequest(String accessToken, String refreshToken) {
		return LogoutRequest.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.build();
	}

	/**
	 * Creates a LoginResponse with default test data
	 */
	public static LoginResponse createLoginResponse() {
		return createLoginResponse(TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN, TEST_USER_ID, 
				TEST_FIRST_NAME, TEST_LAST_NAME, TEST_EMAIL, Role.ROLE_USER);
	}
	
	/**
	 * Creates a LoginResponse with custom data
	 */
	public static LoginResponse createLoginResponse(String accessToken, String refreshToken, String userId, 
			String firstName, String lastName, String email, Role role) {
		return LoginResponse.builder()
				.accessToken(accessToken)
				.refreshToken(refreshToken)
				.userId(userId)
				.firstName(firstName)
				.lastName(lastName)
				.email(email)
				.role(role)
				.build();
	}

	// === USER MANAGEMENT DTO CREATION METHODS ===
	
	/**
	 * Creates an AdminUserCreateRequest with default test data
	 */
	public static AdminUserCreateRequest createUserCreateRequest() {
		return AdminUserCreateRequest.builder()
				.firstName(TEST_FIRST_NAME)
				.lastName(TEST_LAST_NAME)
				.email(TEST_EMAIL)
				.password(TEST_PASSWORD)
				.role(Role.ROLE_USER)
				.enabled(true)
				.build();
	}

	/**
	 * Creates an AdminUserUpdateRequest with default test data
	 */
	public static AdminUserUpdateRequest createUserUpdateRequest() {
		return AdminUserUpdateRequest.builder()
				.firstName(TEST_FIRST_NAME)
				.lastName(TEST_LAST_NAME)
				.email(TEST_EMAIL)
				.role(Role.ROLE_USER)
				.enabled(true)
				.build();
	}

	/**
	 * Creates a UserProfileResponse with default test data
	 */
	public static UserProfileResponse createUserProfileResponse() {
		return UserProfileResponse.builder()
				.userId(TEST_USER_ID)
				.firstName(TEST_FIRST_NAME)
				.lastName(TEST_LAST_NAME)
				.email(TEST_EMAIL)
				.role(Role.ROLE_USER)
				.enabled(true)
				.build();
	}

	/**
	 * Creates a ChangePasswordRequest with default test data
	 */
	public static ChangePasswordRequest createChangePasswordRequest() {
		return ChangePasswordRequest.builder()
				.currentPassword("oldPassword123")
				.newPassword("newPassword456")
				.build();
	}

	/**
	 * Creates an UpdateProfileRequest with default test data
	 */
	public static UpdateProfileRequest createUpdateProfileRequest() {
		return UpdateProfileRequest.builder()
				.firstName("Updated")
				.lastName("Name")
				.email("updated@example.com")
				.build();
	}

	/**
	 * Creates an AdminUserCreateRequest with default test data
	 */
	public static AdminUserCreateRequest createAdminUserCreateRequest() {
		return AdminUserCreateRequest.builder()
				.firstName("New")
				.lastName("Admin")
				.email("new.admin@example.com")
				.password("NewAdminPass123!")
				.role(Role.ROLE_ADMIN)
				.enabled(true)
				.build();
	}

	/**
	 * Creates an AdminUserUpdateRequest with default test data
	 */
	public static AdminUserUpdateRequest createAdminUserUpdateRequest() {
		return AdminUserUpdateRequest.builder()
				.firstName("Updated")
				.lastName("Admin")
				.email("updated.admin@example.com")
				.role(Role.ROLE_ADMIN)
				.enabled(true)
				.build();
	}

}