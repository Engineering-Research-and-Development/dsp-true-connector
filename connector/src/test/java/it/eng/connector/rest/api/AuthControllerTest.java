package it.eng.connector.rest.api;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.connector.exception.AuthExceptionAdvice;
import it.eng.connector.service.AuthService;
import it.eng.connector.service.AuthService.AuthTokens;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for {@link AuthController} and {@link AuthExceptionAdvice}.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthExceptionAdvice())
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
    @DisplayName("Login with bad password, disabled account, and locked account return identical 401 bodies")
    void loginFailuresReturnIdentical401Body() throws Exception {
        when(authService.login("bad@test.com", "wrong")).thenThrow(new BadCredentialsException("bad credentials"));
        when(authService.login("disabled@test.com", "password")).thenThrow(new DisabledException("disabled"));
        when(authService.login("locked@test.com", "password")).thenThrow(new LockedException("locked"));

        MvcResult badCredentials = performLogin("bad@test.com", "wrong")
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult disabled = performLogin("disabled@test.com", "password")
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult locked = performLogin("locked@test.com", "password")
                .andExpect(status().isUnauthorized())
                .andReturn();

        String bodyWithoutTimestamp1 = stripTimestamp(badCredentials.getResponse().getContentAsString());
        String bodyWithoutTimestamp2 = stripTimestamp(disabled.getResponse().getContentAsString());
        String bodyWithoutTimestamp3 = stripTimestamp(locked.getResponse().getContentAsString());

        assertEquals(bodyWithoutTimestamp1, bodyWithoutTimestamp2);
        assertEquals(bodyWithoutTimestamp2, bodyWithoutTimestamp3);
    }

    @Test
    @DisplayName("Login with blank email returns 400")
    void loginBlankEmailReturns400() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "",
                                "password", "password"))))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(anyString(), anyString());
    }

    @Test
    @DisplayName("Login with malformed email returns 400")
    void loginMalformedEmailReturns400() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "not-an-email",
                                "password", "password"))))
                .andExpect(status().isBadRequest());

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
