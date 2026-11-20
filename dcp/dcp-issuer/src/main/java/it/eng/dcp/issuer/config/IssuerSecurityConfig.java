package it.eng.dcp.issuer.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Spring Security configuration of the standalone Issuer application.
 *
 * <p>Two filter chains:
 * <ul>
 *   <li>The administration chain ({@code /api/issuer/**}, every path below it) requires HTTP Basic
 *       authentication of a user from {@code dcp.admin.users[n]} with the authority {@value #ADMIN_AUTHORITY}.
 *       Without credentials, or with wrong ones, the answer is 401; an authenticated user without the
 *       authority gets 403. With no user configured every administration request gets 401 (fail closed).</li>
 *   <li>The public chain permits the DCP protocol endpoints ({@code /issuer/**}, including the status list
 *       path), the DID documents ({@code /.well-known/**} and any {@code GET .../did.json}), {@code /actuator/health}
 *       and the {@code /error} dispatch. These endpoints keep their own DCP token validation in the controllers.
 *       Every other path is denied.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class IssuerSecurityConfig {

    /** Authority required on every administration API endpoint. */
    public static final String ADMIN_AUTHORITY = "DCP_ISSUER_ADMIN";

    /** Path pattern of the administration API. */
    public static final String ADMIN_API_PATTERN = "/api/issuer/**";

    private static final String BASIC_REALM = "dcp-issuer-admin";

    private static final String NOOP_PREFIX = "{noop}";

    private static final String PASSWORD_PROBE = "issuer-admin-password-format-probe";

    /**
     * Password encoder for the administration users, accepting every {@code DelegatingPasswordEncoder} format.
     *
     * @return the delegating password encoder
     */
    @Bean
    public PasswordEncoder issuerAdminPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * In-memory store of the administration users from {@code dcp.admin.users[n]}.
     *
     * <p>Defining this bean also stops Spring Boot from creating its default user with a generated password.
     *
     * @param issuerProperties issuer configuration holding the administration users
     * @param issuerAdminPasswordEncoder encoder used to check the configured password format
     * @return the user details service of the administration chain
     */
    @Bean
    public UserDetailsService issuerAdminUserDetailsService(IssuerProperties issuerProperties,
                                                            PasswordEncoder issuerAdminPasswordEncoder) {
        List<IssuerProperties.AdminUser> users = issuerProperties.getAdmin() == null
                ? List.of()
                : Objects.requireNonNullElse(issuerProperties.getAdmin().getUsers(), List.of());
        InMemoryUserDetailsManager manager = buildAdminUserDetailsManager(users, issuerAdminPasswordEncoder);
        if (users.isEmpty()) {
            log.warn("No Issuer administration user configured (dcp.admin.users): every {} request is denied",
                    ADMIN_API_PATTERN);
        } else {
            log.info("Issuer administration API: {} user(s) configured", users.size());
        }
        return manager;
    }

    /**
     * Filter chain of the administration API: HTTP Basic, authority {@value #ADMIN_AUTHORITY}, stateless.
     *
     * @param http the HTTP security builder
     * @param issuerAdminUserDetailsService the administration users
     * @param issuerAdminPasswordEncoder the password encoder of the administration users
     * @return the administration filter chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    @Order(1)
    public SecurityFilterChain issuerAdminSecurityFilterChain(HttpSecurity http,
                                                              UserDetailsService issuerAdminUserDetailsService,
                                                              PasswordEncoder issuerAdminPasswordEncoder) throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(issuerAdminUserDetailsService);
        provider.setPasswordEncoder(issuerAdminPasswordEncoder);
        AuthenticationManager authenticationManager = new ProviderManager(provider);

        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName(BASIC_REALM);
        entryPoint.afterPropertiesSet();

        http.securityMatcher(ADMIN_API_PATTERN)
                .authenticationManager(authenticationManager)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasAuthority(ADMIN_AUTHORITY))
                .httpBasic(basic -> basic.realmName(BASIC_REALM).authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * Filter chain of every other path: DCP protocol endpoints, DID documents and health are permitted,
     * everything else is denied.
     *
     * @param http the HTTP security builder
     * @return the public filter chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    @Order(2)
    public SecurityFilterChain issuerPublicSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/issuer/**", "/.well-known/**", "/actuator/health", "/actuator/health/**",
                                "/error").permitAll()
                        .requestMatchers(didDocumentRequestMatcher()).permitAll()
                        .anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * Matches {@code GET} requests for a DID document at any depth ({@code /<path>/did.json}).
     *
     * @return the DID document request matcher
     */
    static RequestMatcher didDocumentRequestMatcher() {
        return request -> HttpMethod.GET.matches(request.getMethod()) && pathWithinApplication(request).endsWith("/did.json");
    }

    /**
     * Validates the configured administration users and loads them into an in-memory store.
     *
     * @param users the configured users, possibly empty
     * @param passwordEncoder the delegating password encoder used to check each password format
     * @return the in-memory user store
     * @throws IllegalStateException when a user has no username or password, a password is not in
     *                               {@code DelegatingPasswordEncoder} format, or a username is repeated
     */
    static InMemoryUserDetailsManager buildAdminUserDetailsManager(List<IssuerProperties.AdminUser> users,
                                                                   PasswordEncoder passwordEncoder) {
        List<UserDetails> userDetails = new ArrayList<>();
        Set<String> usernames = new HashSet<>();
        for (int i = 0; i < users.size(); i++) {
            IssuerProperties.AdminUser user = users.get(i);
            String property = "dcp.admin.users[" + i + "]";
            if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
                throw new IllegalStateException(property + ".username must not be blank");
            }
            if (!usernames.add(user.getUsername().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException(property + ".username is a duplicate: " + user.getUsername());
            }
            String password = user.getPassword();
            if (password == null || password.isBlank()) {
                throw new IllegalStateException(property + ".password must not be blank");
            }
            requireDelegatingFormat(property, password, passwordEncoder);
            if (password.startsWith(NOOP_PREFIX)) {
                log.warn("{} ({}) has a {} plain-text password; store a hash such as {bcrypt} instead",
                        property, user.getUsername(), NOOP_PREFIX);
            }
            String[] authorities = Objects.requireNonNullElse(user.getAuthorities(), List.<String>of()).stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(authority -> !authority.isEmpty())
                    .toArray(String[]::new);
            userDetails.add(User.withUsername(user.getUsername())
                    .password(password)
                    .authorities(authorities)
                    .build());
        }
        return new InMemoryUserDetailsManager(userDetails);
    }

    private static void requireDelegatingFormat(String property, String password, PasswordEncoder passwordEncoder) {
        try {
            passwordEncoder.matches(PASSWORD_PROBE, password);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(property + ".password must be in DelegatingPasswordEncoder format "
                    + "with a known id, for example {bcrypt}<hash>");
        }
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = Objects.requireNonNullElse(request.getRequestURI(), "");
        String contextPath = Objects.requireNonNullElse(request.getContextPath(), "");
        return uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
    }
}
