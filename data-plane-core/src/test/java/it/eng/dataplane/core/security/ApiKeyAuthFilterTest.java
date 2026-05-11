package it.eng.dataplane.core.security;

import it.eng.dataplane.core.config.DataPlaneProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ApiKeyAuthFilter}.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthFilter filter;
    private DataPlaneProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DataPlaneProperties();
        properties.setApiKey("test-api-key-12345");
        filter = new ApiKeyAuthFilter(properties);
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWithValidApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Api-Key", "test-api-key-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("control-plane", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void doesNotAuthenticateWithInvalidApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Api-Key", "wrong-api-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doesNotAuthenticateWithMissingApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doesNotAuthenticateWithBlankApiKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Api-Key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
