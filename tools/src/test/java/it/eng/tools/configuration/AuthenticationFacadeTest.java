package it.eng.tools.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthenticationFacadeTest {

    @InjectMocks
    private AuthenticationFacade authenticationFacade;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("Should return authentication from security context")
    void testGetAuthentication() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);

        // Act
        Authentication result = authenticationFacade.getAuthentication();

        // Assert
        assertNotNull(result);
        assertEquals(authentication, result);
    }

    @Test
    @DisplayName("Should handle null authentication")
    void testGetAuthenticationWhenNull() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(null);

        // Act
        Authentication result = authenticationFacade.getAuthentication();

        // Assert
        assertEquals(null, result);
    }
}
