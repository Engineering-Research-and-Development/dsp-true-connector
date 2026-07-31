package it.eng.connector.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import it.eng.connector.service.AuthService.AuthTokens;

/**
 * Unit tests for {@link ConnectorCredentialProviderImpl}.
 */
@ExtendWith(MockitoExtension.class)
class ConnectorCredentialProviderImplTest {

    @Mock
    private AuthService authService;

    private ConnectorCredentialProviderImpl provider;

    @BeforeEach
    void setUp() {
        provider = new ConnectorCredentialProviderImpl(authService);
    }

    @Test
    @DisplayName("Should authenticate as the seeded connector user and return the access token")
    void issuesTokenForSeededConnectorUser() {
        when(authService.login("connector@mail.com", "password"))
                .thenReturn(new AuthTokens("access-token", "refresh-token", 900L));

        String token = provider.issueConnectorToken();

        assertEquals("access-token", token);
    }

    @Test
    @DisplayName("Should return null when authentication fails")
    void returnsNullOnAuthenticationFailure() {
        when(authService.login("connector@mail.com", "password"))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        String token = provider.issueConnectorToken();

        assertNull(token);
    }
}
