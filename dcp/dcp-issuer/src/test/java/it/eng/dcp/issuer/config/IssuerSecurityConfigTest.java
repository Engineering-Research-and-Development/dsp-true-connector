package it.eng.dcp.issuer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the administration user validation and the DID document matcher of {@link IssuerSecurityConfig}.
 */
class IssuerSecurityConfigTest {

    private static final String ADMIN_HASH =
            "{bcrypt}$2a$10$aZXjLqJPPXsBq7zSJRnID.6fAO9oPNoB.fNQf8.XMH8k43qBEOnB6";

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    @DisplayName("No configured user gives an empty store")
    void buildAdminUserDetailsManager_noUsers_isEmpty() {
        InMemoryUserDetailsManager manager = IssuerSecurityConfig.buildAdminUserDetailsManager(List.of(), passwordEncoder);

        assertFalse(manager.userExists("issuer-admin"));
    }

    @Test
    @DisplayName("Configured users are loaded with their trimmed authorities")
    void buildAdminUserDetailsManager_validUsers_loaded() {
        IssuerProperties.AdminUser admin = user("issuer-admin", ADMIN_HASH,
                Arrays.asList(" DCP_ISSUER_ADMIN ", "", null));
        IssuerProperties.AdminUser viewer = user("viewer", "{noop}viewer-password", null);

        InMemoryUserDetailsManager manager = IssuerSecurityConfig.buildAdminUserDetailsManager(
                List.of(admin, viewer), passwordEncoder);

        UserDetails adminDetails = manager.loadUserByUsername("issuer-admin");
        assertEquals(List.of(IssuerSecurityConfig.ADMIN_AUTHORITY),
                adminDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        assertTrue(passwordEncoder.matches("issuer-admin-test-password", adminDetails.getPassword()));
        assertTrue(manager.loadUserByUsername("viewer").getAuthorities().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("A blank username fails startup")
    void buildAdminUserDetailsManager_blankUsername_throws(String username) {
        List<IssuerProperties.AdminUser> users = List.of(user(username, ADMIN_HASH, List.of()));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> IssuerSecurityConfig.buildAdminUserDetailsManager(users, passwordEncoder));
        assertEquals("dcp.admin.users[0].username must not be blank", e.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("A blank password fails startup")
    void buildAdminUserDetailsManager_blankPassword_throws(String password) {
        List<IssuerProperties.AdminUser> users = List.of(user("issuer-admin", password, List.of()));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> IssuerSecurityConfig.buildAdminUserDetailsManager(users, passwordEncoder));
        assertEquals("dcp.admin.users[0].password must not be blank", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain-text-password", "{unknown}abc"})
    @DisplayName("A password outside the DelegatingPasswordEncoder format fails startup without echoing it")
    void buildAdminUserDetailsManager_passwordWithoutKnownId_throws(String password) {
        List<IssuerProperties.AdminUser> users = List.of(user("issuer-admin", password, List.of()));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> IssuerSecurityConfig.buildAdminUserDetailsManager(users, passwordEncoder));
        assertTrue(e.getMessage().startsWith("dcp.admin.users[0].password must be in DelegatingPasswordEncoder format"));
        assertFalse(e.getMessage().contains(password));
    }

    @Test
    @DisplayName("A repeated username (ignoring case) fails startup")
    void buildAdminUserDetailsManager_duplicateUsername_throws() {
        List<IssuerProperties.AdminUser> users = List.of(
                user("issuer-admin", ADMIN_HASH, List.of()),
                user("Issuer-Admin", ADMIN_HASH, List.of()));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> IssuerSecurityConfig.buildAdminUserDetailsManager(users, passwordEncoder));
        assertTrue(e.getMessage().startsWith("dcp.admin.users[1].username is a duplicate"));
    }

    @Test
    @DisplayName("A null user entry fails startup")
    void buildAdminUserDetailsManager_nullEntry_throws() {
        List<IssuerProperties.AdminUser> users = Arrays.asList((IssuerProperties.AdminUser) null);

        assertThrows(IllegalStateException.class,
                () -> IssuerSecurityConfig.buildAdminUserDetailsManager(users, passwordEncoder));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/did.json", "/issuer/did.json", "/a/b/c/did.json"})
    @DisplayName("GET requests for a did.json at any depth match")
    void didDocumentRequestMatcher_getDidJson_matches(String path) {
        RequestMatcher matcher = IssuerSecurityConfig.didDocumentRequestMatcher();

        assertTrue(matcher.matches(new MockHttpServletRequest("GET", path)));
    }

    @Test
    @DisplayName("Other methods and paths do not match the DID document matcher")
    void didDocumentRequestMatcher_otherRequests_doNotMatch() {
        RequestMatcher matcher = IssuerSecurityConfig.didDocumentRequestMatcher();

        assertFalse(matcher.matches(new MockHttpServletRequest("POST", "/issuer/did.json")));
        assertFalse(matcher.matches(new MockHttpServletRequest("GET", "/issuer/did.json.bak")));
        assertFalse(matcher.matches(new MockHttpServletRequest("GET", "/issuer/notdid.json")));
    }

    @Test
    @DisplayName("The DID document matcher strips the context path")
    void didDocumentRequestMatcher_contextPath_stripped() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ctx/did.json");
        request.setContextPath("/ctx");

        assertTrue(IssuerSecurityConfig.didDocumentRequestMatcher().matches(request));
    }

    private static IssuerProperties.AdminUser user(String username, String password, List<String> authorities) {
        IssuerProperties.AdminUser user = new IssuerProperties.AdminUser();
        user.setUsername(username);
        user.setPassword(password);
        user.setAuthorities(authorities);
        return user;
    }
}
