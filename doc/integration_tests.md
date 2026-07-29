# Integration tests


## @WithUserDetails annotation

Currently IT tests are anootated with @WithUserDetails annotation, which is used to specify the user details for the test. 
This allows for testing different user roles and permissions within the application.

The @WithUserDetails annotation does not simulate HTTP-level transport authentication (such as Basic Auth or Bearer Auth).
Under the hood, @WithUserDetails functions like this:
- Before the test runs, Spring Security’s test framework executes your UserDetailsService to look up TestUtil.API_USER.
- It constructs a UsernamePasswordAuthenticationToken representing that user.
- It programmatically injects this authentication object directly into the thread's SecurityContextHolder before MockMvc even triggers the request.
- When mockMvc.perform(...) is called, the security context is already fully authenticated. Because an authenticated principal is already present, HTTP-level credential parsing filters (like those parsing Basic or Bearer headers) are bypassed.
- The request proceeds straight to authorization (e.g., checking role requirements), sees that your pre-authenticated test user has the correct role, and returns 200 OK.

Because of this, @WithUserDetails completely bypasses both Bearer and Basic Auth validation. Tests are passing because they only verify your authorization rules (roles/permissions), not your authentication mechanisms.

If we want to test the authentication mechanisms themselves, we need to use a different approach. For example, we can use 
MockMvc to perform requests with actual HTTP headers for Bearer authentication, and then verify the responses accordingly. 
This way, we can ensure that our authentication logic is functioning correctly in addition to our authorization rules.

For example:

```java
@Autowired
private JwtService jwtService; // Inject your JwtService

@Test
public void testGetCatalogArtifactWithValidBearerToken() throws Exception {
    // 1. Programmatically generate a valid token
    String accessToken = jwtService.issueTokenPair(
        "userId", "test@email.com", List.of("ROLE_ADMIN"), "tenantId", Map.of()
    ).accessToken();

    // 2. Perform the request using the Bearer token header
    mockMvc.perform(get(ApiEndpoints.CATALOG_ARTIFACT_V1)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
}


```

## New custom annotation for JWT user

Create custom jwtUser annotation, and generate the token in the annotation itself, and then use it in the test. 

```java
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = JwtUserSecurityContextFactory.class)
public @interface WithJwtUser {
    String userId() default "userId";
    String email() default "test@email.com";
    String[] roles() default {"ROLE_ADMIN"};
    String tenantId() default "tenantId";
    String[] claims() default {};
}

```

SecurityContextFactory implementation to generate the token and set the authentication in the security context.

(use this code with caution, as it may not be fully functional without additional context and dependencies)
```java 

@TestComponent
class JwtUserSecurityContextFactory implements WithSecurityContextFactory<WithJwtUser> {

    @Autowired
    private JwtService jwtService;

    @Override
    public SecurityContext createSecurityContext(WithJwtUser annotation) {
        String accessToken = jwtService.issueTokenPair(
                annotation.userId(),
                annotation.email(),
                List.of(annotation.roles()),
                annotation.tenantId(),
                Map.of() // You can add claims here if needed
        ).accessToken();

        // Parse the raw string into a Nimbus JWT object
        var nimbusJwt = JWTParser.parse(accessToken);

        // Extract headers and claims maps
        Map<String, Object> headers = nimbusJwt.getHeader().toJSONObject();
        Map<String, Object> claims = nimbusJwt.getJWTClaimsSet().getClaims();

        // Map timestamp instances safely
        Instant issuedAt = nimbusJwt.getJWTClaimsSet().getIssueTime() != null
                ? nimbusJwt.getJWTClaimsSet().getIssueTime().toInstant() : null;
        Instant expiresAt = nimbusJwt.getJWTClaimsSet().getExpirationTime() != null
                ? nimbusJwt.getJWTClaimsSet().getExpirationTime().toInstant() : null;

        // Build the org.springframework.security.oauth2.jwt.Jwt instance
        Jwt springJwt = new Jwt(accessToken, issuedAt, expiresAt, headers, claims);
        
        // Construct Spring Security Jwt and use it to instantiate JwtAuthenticationToken
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(springJwt, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}
```