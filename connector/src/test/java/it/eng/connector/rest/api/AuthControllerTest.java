package it.eng.connector.rest.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.exception.AuthExceptionAdvice;
import it.eng.connector.service.AuthService;
import it.eng.connector.service.AuthService.AuthTokens;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.exception.ExceptionAPIAdvice;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.TreeMap;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link AuthController} and {@link AuthExceptionAdvice}.
 *
 * <p>Registers both {@link AuthExceptionAdvice} and the shared {@link ExceptionAPIAdvice} in the
 * standalone {@link MockMvc} setup (matching the real application context, where both advices are
 * active for controllers under {@code it.eng.connector.rest.api}), so these tests exercise the real
 * advice-resolution interaction rather than only the narrower advice in isolation.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String LOGIN_PATH = ApiEndpoints.AUTH_V1 + "/login";
    private static final String REFRESH_PATH = ApiEndpoints.AUTH_V1 + "/refresh";
    private static final String LOGOUT_PATH = ApiEndpoints.AUTH_V1 + "/logout";

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        AuthController controller = new AuthController(authService, validator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ExceptionAPIAdvice(), new AuthExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("Login with valid credentials returns 200 with flat token-only body")
    void loginSuccessful() throws Exception {
        when(authService.login("user@test.com", "password"))
                .thenReturn(new AuthTokens("access-token", "refresh-token", 3600L));

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@test.com",
                                "password", "password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access-token"))
                .andExpect(jsonPath("$.refresh_token").value("refresh-token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.sub").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.tenantId").doesNotExist());
    }

    @Test
    @DisplayName("Login with bad password, disabled, locked, expired, or credentials-expired accounts "
            + "return identical 401 bodies")
    void loginFailuresReturnIdentical401Body() throws Exception {
        when(authService.login("bad@test.com", "wrong")).thenThrow(new BadCredentialsException("bad credentials"));
        when(authService.login("disabled@test.com", "password")).thenThrow(new DisabledException("disabled"));
        when(authService.login("locked@test.com", "password")).thenThrow(new LockedException("locked"));
        when(authService.login("expired@test.com", "password")).thenThrow(new AccountExpiredException("expired"));
        when(authService.login("credexpired@test.com", "password"))
                .thenThrow(new CredentialsExpiredException("credentials expired"));

        MvcResult badCredentials = performLogin("bad@test.com", "wrong")
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult disabled = performLogin("disabled@test.com", "password")
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult locked = performLogin("locked@test.com", "password")
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult expired = performLogin("expired@test.com", "password")
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult credentialsExpired = performLogin("credexpired@test.com", "password")
                .andExpect(status().isUnauthorized())
                .andReturn();

        String bodyWithoutTimestamp1 = stripTimestamp(badCredentials.getResponse().getContentAsString());
        String bodyWithoutTimestamp2 = stripTimestamp(disabled.getResponse().getContentAsString());
        String bodyWithoutTimestamp3 = stripTimestamp(locked.getResponse().getContentAsString());
        String bodyWithoutTimestamp4 = stripTimestamp(expired.getResponse().getContentAsString());
        String bodyWithoutTimestamp5 = stripTimestamp(credentialsExpired.getResponse().getContentAsString());

        assertEquals(bodyWithoutTimestamp1, bodyWithoutTimestamp2);
        assertEquals(bodyWithoutTimestamp2, bodyWithoutTimestamp3);
        assertEquals(bodyWithoutTimestamp3, bodyWithoutTimestamp4);
        assertEquals(bodyWithoutTimestamp4, bodyWithoutTimestamp5);
    }

    @Test
    @DisplayName("Login with blank email returns 400 with the dedicated AuthErrorResponse shape")
    void loginBlankEmailReturns400() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "",
                                "password", "password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.success").doesNotExist());

        verify(authService, never()).login(anyString(), anyString());
    }

    @Test
    @DisplayName("Login with malformed email returns 400 with the dedicated AuthErrorResponse shape")
    void loginMalformedEmailReturns400() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "not-an-email",
                                "password", "password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.success").doesNotExist());

        verify(authService, never()).login(anyString(), anyString());
    }

    @Test
    @DisplayName("Refresh with a valid refresh token returns 200 with a new token pair")
    void refreshSuccessful() throws Exception {
        when(authService.refresh("old-refresh-token"))
                .thenReturn(new AuthTokens("new-access-token", "new-refresh-token", 3600L));

        mockMvc.perform(post(REFRESH_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refresh_token", "old-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("new-access-token"))
                .andExpect(jsonPath("$.refresh_token").value("new-refresh-token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    @DisplayName("Refresh with an invalid/expired/rotated token returns 401")
    void refreshInvalidTokenReturns401() throws Exception {
        when(authService.refresh("bad-refresh-token")).thenThrow(new BadCredentialsException("invalid token"));

        mockMvc.perform(post(REFRESH_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refresh_token", "bad-refresh-token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Logout always returns 200, including for an already-revoked/unknown token")
    void logoutAlwaysReturns200() throws Exception {
        mockMvc.perform(post(LOGOUT_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refresh_token", "unknown-token"))))
                .andExpect(status().isOk());

        verify(authService).logout("unknown-token");
    }

    @Test
    @DisplayName("Unexpected internal exception produces a masked 500 with no internal detail")
    void unexpectedExceptionReturnsMasked500() throws Exception {
        doThrow(new IllegalStateException("connection refused: internal db host db-primary.internal down"))
                .when(authService)
                .login(anyString(), anyString());

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@test.com",
                                "password", "password"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message", not(blankOrNullString())));
    }

    private ResultActions performLogin(String email, String password)
            throws Exception {
        return mockMvc.perform(post(LOGIN_PATH)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String stripTimestamp(String json) throws Exception {
        Map<String, Object> map = new TreeMap<>(objectMapper.readValue(json, Map.class));
        map.remove("timestamp");
        return objectMapper.writeValueAsString(map);
    }
}
