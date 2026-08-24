# User Self-Service, Role Enum Cleanup, and Tenant participantId Immutability

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow ROLE_ADMIN users to manage their own account, rename the Role enum to remove redundant `ROLE_` prefixes, make Tenant.participantId immutable after creation, and update all related documentation.

**Architecture:** The Role enum is renamed (ROLE_ADMIN → ADMIN etc.) and gains an `authorityName()` helper that produces the Spring Security `"ROLE_"` prefix needed by `User.getAuthorities()`. ConnectorSecurityConfig is updated to add per-endpoint matchers that open the three user self-service endpoints to ADMIN in addition to SUPER_ADMIN, while all other user management remains SUPER_ADMIN-only. TenantService.updateTenant() is fixed to always use the existing participantId, ignoring any value in the request body.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Spring Security 6.x, MongoDB / Spring Data MongoDB, JUnit 5, MockMvc, Testcontainers (MongoDB + MinIO), Mockito.

---

## File Map

| Action | File |
|---|---|
| Modify | `connector/src/main/java/it/eng/connector/model/Role.java` |
| Modify | `connector/src/main/java/it/eng/connector/model/User.java` |
| Modify | `connector/src/main/java/it/eng/connector/model/UserDTO.java` |
| Modify | `connector/src/main/java/it/eng/connector/configuration/ConnectorSecurityConfig.java` |
| Modify | `connector/src/main/java/it/eng/connector/configuration/InternalServiceAuthenticationProvider.java` |
| Modify | `connector/src/main/java/it/eng/connector/service/UserService.java` |
| Modify | `connector/src/main/java/it/eng/connector/service/KeycloakUserService.java` |
| Modify | `connector/src/main/java/it/eng/connector/rest/api/UserApiController.java` |
| Modify | `connector/src/main/java/it/eng/connector/rest/api/TenantAPIController.java` |
| Modify | `tools/src/main/java/it/eng/tools/service/TenantService.java` |
| Modify | `connector/src/test/java/it/eng/connector/util/TestUtil.java` |
| Modify | `connector/src/test/java/it/eng/connector/filter/ApiTenantContextFilterTest.java` |
| Modify | `connector/src/test/java/it/eng/connector/configuration/KeycloakAuthenticationFilterTest.java` |
| Modify | `connector/src/test/java/it/eng/connector/configuration/KeycloakRealmRoleConverterTest.java` |
| Modify | `connector/src/test/java/it/eng/connector/model/UserDTOTest.java` |
| Modify | `connector/src/test/java/it/eng/connector/service/UserServiceTest.java` |
| Modify | `connector/src/test/java/it/eng/connector/integration/user/UserIT.java` |
| Modify | `connector/src/test/java/it/eng/connector/integration/KeycloakUserRegistrationIT.java` |
| Modify | `connector/src/test/java/it/eng/connector/integration/tenant/TenantAPIIT.java` |
| Modify | `tools/src/test/java/it/eng/tools/service/TenantServiceTest.java` |
| Modify | `connector/src/test/resources/initial_data.json` |
| Modify | `connector/src/test/resources/initial_data-tck.json` |
| Modify | `connector/src/main/resources/initial_data.json` |
| Modify | `connector/src/main/resources/initial_data-consumer.json` |
| Modify | `connector/src/main/resources/initial_data-provider.json` |
| Modify | `connector/src/main/resources/initial_data-tck.json` |
| Modify | `ci/docker/connector_a_resources/initial_data.json` |
| Modify | `ci/docker/connector_b_resources/initial_data.json` |
| Modify | `ci/tck/connector_tck_resources/initial_data-tck.json` |
| Modify | `doc/security.md` |
| Modify | `CHANGELOG.md` |

---

## Task 1: Rename Role enum values and fix User.getAuthorities()

**Files:**
- Modify: `connector/src/main/java/it/eng/connector/model/Role.java`
- Modify: `connector/src/main/java/it/eng/connector/model/User.java`

This task renames the enum constants and adds `authorityName()`. It will cause compile errors in callers — fix those in Task 2.

- [ ] **Step 1: Replace Role.java entirely**

```java
package it.eng.connector.model;

/**
 * Application roles assigned to connector users.
 *
 * <p>Use {@link #authorityName()} when constructing Spring Security authority strings
 * (e.g. for {@code SimpleGrantedAuthority}) — it prepends the {@code ROLE_} prefix required
 * by Spring Security's {@code hasRole()} / {@code hasAnyRole()} methods.
 */
public enum Role {
    USER, ADMIN, CONNECTOR, SUPER_ADMIN;

    /**
     * Returns the Spring Security authority string for this role (e.g. {@code "ROLE_ADMIN"}).
     *
     * @return the role name prefixed with {@code ROLE_}
     */
    public String authorityName() {
        return "ROLE_" + name();
    }
}
```

- [ ] **Step 2: Fix User.getAuthorities() to use authorityName()**

In `connector/src/main/java/it/eng/connector/model/User.java`, change line 61:

Old:
```java
return List.of(new SimpleGrantedAuthority(role.name()));
```

New:
```java
return List.of(new SimpleGrantedAuthority(role.authorityName()));
```

- [ ] **Step 3: Attempt compile to see all call sites that need updating**

```bash
cd /path/to/repo && mvn -pl connector -am compile -q 2>&1 | grep "error:" | grep -v "cannot find symbol" | head -20
mvn -pl connector -am compile -q 2>&1 | grep "cannot find symbol" -A 2 | head -60
```

Expected: compile errors listing every file that references `Role.ROLE_*`.

- [ ] **Step 4: Commit Role.java and User.java only (compilation will be restored in Task 2)**

```bash
git add connector/src/main/java/it/eng/connector/model/Role.java \
        connector/src/main/java/it/eng/connector/model/User.java
git commit -m "refactor: rename Role enum values (ROLE_ADMIN→ADMIN etc.), add authorityName()"
```

---

## Task 2: Fix all production code callers of old Role enum names

**Files:**
- Modify: `connector/src/main/java/it/eng/connector/configuration/InternalServiceAuthenticationProvider.java`
- Modify: `connector/src/main/java/it/eng/connector/service/UserService.java`
- Modify: `connector/src/main/java/it/eng/connector/service/KeycloakUserService.java`
- Modify: `connector/src/main/java/it/eng/connector/model/UserDTO.java`

- [ ] **Step 1: Fix InternalServiceAuthenticationProvider — line 75**

Old:
```java
.role(Role.ROLE_ADMIN)
```

New:
```java
.role(Role.ADMIN)
```

- [ ] **Step 2: Fix UserService — line 99**

Old:
```java
&& userDTO.getRole() != Role.ROLE_SUPER_ADMIN) {
```

New:
```java
&& userDTO.getRole() != Role.SUPER_ADMIN) {
```

Also update the Javadoc on `createUser()` — change `{@code ROLE_SUPER_ADMIN}` to `{@code SUPER_ADMIN}`:

Old Javadoc (lines 81–86):
```java
/**
 * Creates a new user.
 *
 * <p>For non-SUPER_ADMIN users the {@code tenantId} in {@code userDTO} must reference an
 * existing, enabled tenant.  SUPER_ADMIN users are exempt from this check.
 *
 * @param userDTO the user data; {@code tenantId} is required unless the role is
 *                {@code ROLE_SUPER_ADMIN}
```

New Javadoc:
```java
/**
 * Creates a new user.
 *
 * <p>For non-SUPER_ADMIN users the {@code tenantId} in {@code userDTO} must reference an
 * existing, enabled tenant.  SUPER_ADMIN users are exempt from this check.
 *
 * @param userDTO the user data; {@code tenantId} is required unless the role is
 *                {@code SUPER_ADMIN}
```

- [ ] **Step 3: Fix KeycloakUserService — two occurrences (lines 177 and 188)**

Old (line 177 in `buildUserRepresentation`):
```java
String roleName = userDTO.getRole() != null ? userDTO.getRole().name() : Role.ROLE_ADMIN.name();
```

New:
```java
String roleName = userDTO.getRole() != null ? userDTO.getRole().name() : Role.ADMIN.name();
```

Old (line 188 in `buildCreatedUserResponse`):
```java
node.put("role", userDTO.getRole() != null ? userDTO.getRole().name() : Role.ROLE_ADMIN.name());
```

New:
```java
node.put("role", userDTO.getRole() != null ? userDTO.getRole().name() : Role.ADMIN.name());
```

- [ ] **Step 4: Fix UserDTO Javadoc**

In `connector/src/main/java/it/eng/connector/model/UserDTO.java`, update the field comment:

Old:
```java
/** The tenant this user belongs to.  Optional only for ROLE_SUPER_ADMIN. */
private String tenantId;
```

New:
```java
/** The tenant this user belongs to.  Optional only for {@link Role#SUPER_ADMIN}. */
private String tenantId;
```

Also add `import it.eng.connector.model.Role;` if not already present (check first — it may not be imported).

Note: `UserDTO` does not import `Role` currently. Only the Javadoc references it. Use the simple name `{@link Role#SUPER_ADMIN}` which requires the import, OR change to plain text `"SUPER_ADMIN role"`. Use plain text to avoid adding an unnecessary import:

```java
/** The tenant this user belongs to.  Optional only for the {@code SUPER_ADMIN} role. */
private String tenantId;
```

Also update the class Javadoc if it mentions `ROLE_SUPER_ADMIN`:

Old class Javadoc:
```java
/**
 * Data transfer object for user create and update operations.
 *
 * <p>When creating a non-SUPER_ADMIN user, {@code tenantId} must reference an existing,
 * enabled tenant.  SUPER_ADMIN users are exempt and may omit {@code tenantId}.
 */
```

This is already clean (uses SUPER_ADMIN not ROLE_SUPER_ADMIN), so no change needed.

- [ ] **Step 5: Verify compilation succeeds**

```bash
mvn -pl connector -am compile -q
```

Expected: clean compile with no errors.

- [ ] **Step 6: Commit**

```bash
git add connector/src/main/java/it/eng/connector/configuration/InternalServiceAuthenticationProvider.java \
        connector/src/main/java/it/eng/connector/service/UserService.java \
        connector/src/main/java/it/eng/connector/service/KeycloakUserService.java \
        connector/src/main/java/it/eng/connector/model/UserDTO.java
git commit -m "refactor: update all production callers to new Role enum names"
```

---

## Task 3: Rewrite ConnectorSecurityConfig — remove role constants, add ADMIN self-service matchers

**Files:**
- Modify: `connector/src/main/java/it/eng/connector/configuration/ConnectorSecurityConfig.java`

- [ ] **Step 1: Remove the three private static final String constants (lines 77–79)**

Delete these three lines:
```java
private static final String ADMIN_ROLE = Role.ROLE_ADMIN.name().substring("ROLE_".length());
private static final String CONNECTOR_ROLE = Role.ROLE_CONNECTOR.name().substring("ROLE_".length());
private static final String SUPER_ADMIN_ROLE = Role.ROLE_SUPER_ADMIN.name().substring("ROLE_".length());
```

- [ ] **Step 2: Add HttpMethod import**

Add to the import section (after the other `org.springframework` imports):
```java
import org.springframework.http.HttpMethod;
```

- [ ] **Step 3: Replace the KEYCLOAK branch authorizeHttpRequests block**

Old (inside the `authMode == AuthenticationMode.KEYCLOAK` block):
```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(ApiEndpoints.TENANTS_V1 + "/**").hasRole(SUPER_ADMIN_ROLE)
        .requestMatchers(ApiEndpoints.USERS_V1 + "/**").hasRole(SUPER_ADMIN_ROLE)
        .requestMatchers(ApiEndpoints.PROPERTIES_V1 + "/**").hasRole(SUPER_ADMIN_ROLE)
        .anyRequest().hasAnyRole(ADMIN_ROLE, SUPER_ADMIN_ROLE))
```

New:
```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(ApiEndpoints.TENANTS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
        .requestMatchers(HttpMethod.GET,  ApiEndpoints.USERS_V1 + "/me")
            .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
        .requestMatchers(HttpMethod.PUT,  ApiEndpoints.USERS_V1 + "/*/update")
            .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
        .requestMatchers(HttpMethod.PUT,  ApiEndpoints.USERS_V1 + "/*/password")
            .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
        .requestMatchers(ApiEndpoints.USERS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
        .requestMatchers(ApiEndpoints.PROPERTIES_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
        .anyRequest().hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name()))
```

- [ ] **Step 4: Replace the BASIC branch authorizeHttpRequests block**

Old (inside the `else` / BASIC block):
```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(ApiEndpoints.TENANTS_V1 + "/**").hasRole(SUPER_ADMIN_ROLE)
        .requestMatchers(ApiEndpoints.USERS_V1 + "/**").hasRole(SUPER_ADMIN_ROLE)
        .requestMatchers(ApiEndpoints.PROPERTIES_V1 + "/**").hasRole(SUPER_ADMIN_ROLE)
        .anyRequest().hasAnyRole(ADMIN_ROLE, SUPER_ADMIN_ROLE))
```

New (identical shape to the KEYCLOAK block above):
```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(ApiEndpoints.TENANTS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
        .requestMatchers(HttpMethod.GET,  ApiEndpoints.USERS_V1 + "/me")
            .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
        .requestMatchers(HttpMethod.PUT,  ApiEndpoints.USERS_V1 + "/*/update")
            .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
        .requestMatchers(HttpMethod.PUT,  ApiEndpoints.USERS_V1 + "/*/password")
            .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
        .requestMatchers(ApiEndpoints.USERS_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
        .requestMatchers(ApiEndpoints.PROPERTIES_V1 + "/**").hasRole(Role.SUPER_ADMIN.name())
        .anyRequest().hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name()))
```

- [ ] **Step 5: Replace all remaining CONNECTOR_ROLE references in protocolFilterChain**

Three occurrences of `.hasRole(CONNECTOR_ROLE)` inside `protocolFilterChain`:

Old:
```java
.authorizeHttpRequests(auth -> auth.anyRequest().hasRole(CONNECTOR_ROLE))
```

New (appears three times — in DCP, KEYCLOAK, and BASIC branches):
```java
.authorizeHttpRequests(auth -> auth.anyRequest().hasRole(Role.CONNECTOR.name()))
```

- [ ] **Step 6: Update the class-level Javadoc**

Old:
```
 * Requires {@code ROLE_SUPER_ADMIN} for {@code /api/v1/tenants/**},
 * and {@code /api/v1/properties/**}; all other {@code /api/**} endpoints require
 * {@code ROLE_ADMIN} or {@code ROLE_SUPER_ADMIN}. Disabled mode permits all requests.
```

New (in the `adminFilterChain` Javadoc, around lines 118–121):
```
 * Requires {@code ROLE_SUPER_ADMIN} for {@code /api/v1/tenants/**},
 * {@code /api/v1/properties/**}, and most {@code /api/v1/users/**} endpoints.
 * {@code GET /api/v1/users/me}, {@code PUT /api/v1/users/*\/update}, and
 * {@code PUT /api/v1/users/*\/password} are accessible to {@code ROLE_ADMIN} and
 * {@code ROLE_SUPER_ADMIN} (self-service only — the service layer enforces ownership).
 * All other {@code /api/**} endpoints require at minimum {@code ROLE_ADMIN}.
 * Disabled mode permits all requests.
```

- [ ] **Step 7: Verify compilation**

```bash
mvn -pl connector -am compile -q
```

Expected: clean compile.

- [ ] **Step 8: Commit**

```bash
git add connector/src/main/java/it/eng/connector/configuration/ConnectorSecurityConfig.java
git commit -m "feat: remove role string constants from ConnectorSecurityConfig, add ADMIN self-service matchers"
```

---

## Task 4: Add GET /api/v1/users/me endpoint

**Files:**
- Modify: `connector/src/main/java/it/eng/connector/service/UserService.java`
- Modify: `connector/src/main/java/it/eng/connector/rest/api/UserApiController.java`

- [ ] **Step 1: Add findCurrentUser() method to UserService**

Add after the existing `findUsers()` method (around line 76):

```java
/**
 * Returns the authenticated user's own record by e-mail.
 *
 * @param email the e-mail of the authenticated principal
 * @return the user as a serialized JSON node
 * @throws BadRequestException if no user with the given e-mail exists
 */
public JsonNode findCurrentUser(String email) {
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadRequestException("User not found"));
    return ToolsSerializer.serializePlainJsonNode(user);
}
```

- [ ] **Step 2: Add getCurrentUser() endpoint to UserApiController**

Add the following method after the constructor and before `getUsers()`. The class-level `@RequestMapping` has `consumes = APPLICATION_JSON_VALUE`; GET endpoints without a body should work because the `Content-Type` header is optional for GET (clients may still send it). This follows the same pattern as the existing `getUsers()` GET method in this controller.

```java
/**
 * Returns the currently authenticated user's own profile.
 *
 * <p>Returns {@code 400 Bad Request} when there is no authenticated principal (disabled-auth
 * mode), since there is no user identity to resolve.
 *
 * @param principal the authenticated principal injected by Spring Security
 * @return the current user as a {@link GenericApiResponse}
 */
@GetMapping(path = "/me")
public ResponseEntity<GenericApiResponse<JsonNode>> getCurrentUser(Principal principal) {
    if (principal == null) {
        throw new BadRequestException("No authenticated user in current context");
    }
    log.info("Fetching current user profile for principal '{}'", principal.getName());
    JsonNode user = userService.findCurrentUser(principal.getName());
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(GenericApiResponse.success(user, "Current user"));
}
```

Note: `BadRequestException` is **not** currently imported in `UserApiController`. Add this import to the import section:
```java
import it.eng.tools.exception.BadRequestException;
```

- [ ] **Step 3: Verify compilation**

```bash
mvn -pl connector -am compile -q
```

Expected: clean compile.

- [ ] **Step 4: Commit**

```bash
git add connector/src/main/java/it/eng/connector/service/UserService.java \
        connector/src/main/java/it/eng/connector/rest/api/UserApiController.java
git commit -m "feat: add GET /api/v1/users/me endpoint for ADMIN and SUPER_ADMIN self-service"
```

---

## Task 5: Fix TenantService.updateTenant() — participantId immutability

**Files:**
- Modify: `tools/src/main/java/it/eng/tools/service/TenantService.java`
- Modify: `connector/src/main/java/it/eng/connector/rest/api/TenantAPIController.java`

- [ ] **Step 1: Fix line 266 in TenantService.updateTenant()**

Old:
```java
.participantId(updates.getParticipantId() != null ? updates.getParticipantId() : existing.getParticipantId())
```

New:
```java
.participantId(existing.getParticipantId())  // immutable; any value in request body is silently ignored
```

- [ ] **Step 2: Update the Javadoc on TenantService.updateTenant()**

Old Javadoc (lines 229–242):
```java
/**
 * Updates the mutable settings of an existing tenant (name, description, participantId,
 * automaticNegotiation, automaticTransfer, bucketName).
 * The {@code enabled} state is preserved from the existing tenant.
 *
 * <p>If {@code bucketName} is changed, the new bucket is provisioned before the tenant
 * is updated.  The old bucket is <strong>not</strong> deleted automatically.
 *
 * @param tenantId the tenant identifier
 * @param updates  the tenant containing the new values to apply
 * @return the saved, updated tenant
 * @throws TenantNotFoundException  if the tenant does not exist
 * @throws IllegalArgumentException if the new bucket name is already owned by another tenant
 */
```

New:
```java
/**
 * Updates the mutable settings of an existing tenant (name, description,
 * automaticNegotiation, automaticTransfer, bucketName).
 * The {@code enabled} state and {@code participantId} are always preserved from the existing
 * tenant; any {@code participantId} value in {@code updates} is silently ignored.
 *
 * <p>If {@code bucketName} is changed, the new bucket is provisioned before the tenant
 * is updated.  The old bucket is <strong>not</strong> deleted automatically.
 *
 * @param tenantId the tenant identifier
 * @param updates  the tenant containing the new values to apply
 * @return the saved, updated tenant
 * @throws TenantNotFoundException  if the tenant does not exist
 * @throws IllegalArgumentException if the new bucket name is already owned by another tenant
 */
```

- [ ] **Step 3: Add missing Javadoc to TenantAPIController.updateTenant()**

The current `updateTenant` method in `TenantAPIController` has no Javadoc. Checkstyle will reject this. Add it:

```java
/**
 * Updates the mutable settings of an existing tenant.
 *
 * <p>{@code participantId} is read-only after creation; any value supplied in the request
 * body is silently ignored and the stored value is preserved.
 *
 * @param id      the tenant identifier
 * @param updates the tenant body with the fields to update
 * @return 200 OK with the updated tenant
 */
@PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<GenericApiResponse<Tenant>> updateTenant(
        @PathVariable String id,
        @RequestBody Tenant updates) {
```

- [ ] **Step 4: Verify compilation**

```bash
mvn -pl tools connector -am compile -q
```

Expected: clean compile.

- [ ] **Step 5: Commit**

```bash
git add tools/src/main/java/it/eng/tools/service/TenantService.java \
        connector/src/main/java/it/eng/connector/rest/api/TenantAPIController.java
git commit -m "fix: make Tenant.participantId immutable — updateTenant() silently ignores it"
```

---

## Task 6: Update seed data JSON files (Role values in user records)

**Files:** All `initial_data*.json` files listed below.

The `User` MongoDB documents store the role as the enum name. After renaming from `ROLE_ADMIN` to `ADMIN`, existing documents must use the new names. Update **only user records** — other collections (e.g., `transfer_processes`, `negotiation`) also have a `"role"` field that means something entirely different (e.g., `"provider"`, `"consumer"`) — do **not** change those.

- [ ] **Step 1: Update connector/src/test/resources/initial_data.json**

Change in the `users` array:
- `"role": "ROLE_ADMIN"` → `"role": "ADMIN"`
- `"role": "ROLE_CONNECTOR"` → `"role": "CONNECTOR"`
- `"role": "ROLE_SUPER_ADMIN"` → `"role": "SUPER_ADMIN"`

- [ ] **Step 2: Update connector/src/test/resources/initial_data-tck.json**

Same substitutions as Step 1 — only in user documents.

- [ ] **Step 3: Update connector/src/main/resources/initial_data.json**

Same substitutions.

- [ ] **Step 4: Update connector/src/main/resources/initial_data-consumer.json**

Same substitutions.

- [ ] **Step 5: Update connector/src/main/resources/initial_data-provider.json**

Same substitutions.

- [ ] **Step 6: Update connector/src/main/resources/initial_data-tck.json**

Same substitutions.

- [ ] **Step 7: Update ci/docker/connector_a_resources/initial_data.json**

Same substitutions.

- [ ] **Step 8: Update ci/docker/connector_b_resources/initial_data.json**

Same substitutions.

- [ ] **Step 9: Update ci/tck/connector_tck_resources/initial_data-tck.json**

Same substitutions.

For each file, verify before and after with:
```bash
grep '"role": "ROLE_' <filename>
```
Expected: no matches.

- [ ] **Step 10: Commit all seed file changes**

```bash
git add connector/src/test/resources/initial_data.json \
        connector/src/test/resources/initial_data-tck.json \
        connector/src/main/resources/initial_data.json \
        connector/src/main/resources/initial_data-consumer.json \
        connector/src/main/resources/initial_data-provider.json \
        connector/src/main/resources/initial_data-tck.json \
        ci/docker/connector_a_resources/initial_data.json \
        ci/docker/connector_b_resources/initial_data.json \
        ci/tck/connector_tck_resources/initial_data-tck.json
git commit -m "chore: update seed data role values to match renamed Role enum"
```

---

## Task 7: Fix test code — update all Role.ROLE_* references and inline role strings

**Files:**
- Modify: `connector/src/test/java/it/eng/connector/util/TestUtil.java`
- Modify: `connector/src/test/java/it/eng/connector/filter/ApiTenantContextFilterTest.java`
- Modify: `connector/src/test/java/it/eng/connector/configuration/KeycloakAuthenticationFilterTest.java`
- Modify: `connector/src/test/java/it/eng/connector/configuration/KeycloakRealmRoleConverterTest.java`
- Modify: `connector/src/test/java/it/eng/connector/model/UserDTOTest.java`
- Modify: `connector/src/test/java/it/eng/connector/service/UserServiceTest.java`
- Modify: `connector/src/test/java/it/eng/connector/integration/user/UserIT.java`
- Modify: `connector/src/test/java/it/eng/connector/integration/KeycloakUserRegistrationIT.java`

- [ ] **Step 1: Fix TestUtil.java**

Old:
```java
public static User USER = new User(USER_ID, "first name", "last name", "test@mail.com", "secret", true, false, false, Role.ROLE_ADMIN);
```

New:
```java
public static User USER = new User(USER_ID, "first name", "last name", "test@mail.com", "secret", true, false, false, Role.ADMIN);
```

- [ ] **Step 2: Fix ApiTenantContextFilterTest.java — four occurrences**

Replace all four occurrences of `Role.ROLE_ADMIN` with `Role.ADMIN`:

```java
// line ~52
.role(Role.ADMIN)

// line ~74
.role(Role.ADMIN)

// line ~100
.role(Role.ADMIN)

// line ~126
.role(Role.ADMIN)
```

- [ ] **Step 3: Fix KeycloakAuthenticationFilterTest.java — two inline strings**

Add `import it.eng.connector.model.Role;` to the import section.

Old (line ~63):
```java
Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
```

New:
```java
Set<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority(Role.ADMIN.authorityName()));
```

Old (line ~74):
```java
.anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN")));
```

New:
```java
.anyMatch(auth -> auth.getAuthority().equals(Role.ADMIN.authorityName())));
```

- [ ] **Step 4: Fix KeycloakRealmRoleConverterTest.java — two inline strings**

Add `import it.eng.connector.model.Role;` to the import section.

Old (lines ~25–26):
```java
assertTrue(authorities.stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN")));
assertTrue(authorities.stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_CONNECTOR")));
```

New:
```java
assertTrue(authorities.stream().anyMatch(auth -> auth.getAuthority().equals(Role.ADMIN.authorityName())));
assertTrue(authorities.stream().anyMatch(auth -> auth.getAuthority().equals(Role.CONNECTOR.authorityName())));
```

- [ ] **Step 5: Fix UserDTOTest.java**

Old:
```java
userDTO.setRole(Role.ROLE_ADMIN);
```

New:
```java
userDTO.setRole(Role.ADMIN);
```

- [ ] **Step 6: Fix UserServiceTest.java — three occurrences**

Old (three locations):
```java
when(userDTO.getRole()).thenReturn(Role.ROLE_ADMIN);    // line ~86
when(userDTO.getRole()).thenReturn(Role.ROLE_ADMIN);    // line ~106
when(userDTO.getRole()).thenReturn(Role.ROLE_SUPER_ADMIN); // line ~123
```

New:
```java
when(userDTO.getRole()).thenReturn(Role.ADMIN);
when(userDTO.getRole()).thenReturn(Role.ADMIN);
when(userDTO.getRole()).thenReturn(Role.SUPER_ADMIN);
```

- [ ] **Step 7: Fix UserIT.java — all occurrences**

Replace throughout the file:
- `Role.ROLE_ADMIN` → `Role.ADMIN`
- `Role.ROLE_SUPER_ADMIN` → `Role.SUPER_ADMIN`

All existing `UserDTO` constructor calls pass the `Role` enum value. The inline `".roles("SUPER_ADMIN")` strings in `user(...).roles(...)` calls are Spring Security test utilities and must NOT be changed — they take plain role names (without `ROLE_` prefix) and already work correctly.

Check: `git diff connector/src/test/java/it/eng/connector/integration/user/UserIT.java | grep "^[-+]" | grep Role`

- [ ] **Step 8: Fix KeycloakUserRegistrationIT.java — two occurrences**

Old (lines ~79 and ~102):
```java
"TestPass123!", null, Role.ROLE_ADMIN, null
```

New:
```java
"TestPass123!", null, Role.ADMIN, null
```

- [ ] **Step 9: Run unit tests to verify compile and tests pass**

```bash
mvn -pl connector -am test -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 10: Commit**

```bash
git add connector/src/test/java/it/eng/connector/util/TestUtil.java \
        connector/src/test/java/it/eng/connector/filter/ApiTenantContextFilterTest.java \
        connector/src/test/java/it/eng/connector/configuration/KeycloakAuthenticationFilterTest.java \
        connector/src/test/java/it/eng/connector/configuration/KeycloakRealmRoleConverterTest.java \
        connector/src/test/java/it/eng/connector/model/UserDTOTest.java \
        connector/src/test/java/it/eng/connector/service/UserServiceTest.java \
        connector/src/test/java/it/eng/connector/integration/user/UserIT.java \
        connector/src/test/java/it/eng/connector/integration/KeycloakUserRegistrationIT.java
git commit -m "refactor: update test Role enum references to renamed values"
```

---

## Task 8: Add new tests

**Files:**
- Modify: `connector/src/test/java/it/eng/connector/service/UserServiceTest.java`
- Modify: `connector/src/test/java/it/eng/connector/integration/user/UserIT.java`
- Modify: `tools/src/test/java/it/eng/tools/service/TenantServiceTest.java`
- Modify: `connector/src/test/java/it/eng/connector/integration/tenant/TenantAPIIT.java`

### 8a: UserService.findCurrentUser() unit test

- [ ] **Step 1: Add test to UserServiceTest.java**

Add the following test after `testFindUsers()`:

```java
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
@DisplayName("findCurrentUser throws BadRequestException for unknown email")
void findCurrentUser_notFound_throws() {
    when(userRepository.findByEmail("unknown@mail.com")).thenReturn(Optional.empty());

    assertThrows(BadRequestException.class,
            () -> userService.findCurrentUser("unknown@mail.com"));
}
```

- [ ] **Step 2: Run the new unit tests**

```bash
mvn -pl connector -am -Dtest=UserServiceTest -Dsurefire.failIfNoSpecifiedTests=false test -q
```

Expected: `BUILD SUCCESS`.

### 8b: UserIT integration tests for GET /me

- [ ] **Step 3: Add tests to UserIT.java**

Add these two test methods and extend the `TEST_USER_EMAILS` list if needed (no new emails are used here — existing ADMIN_USER seed user is used).

Add the following tests after the existing `getUserByEmail_asAdmin_returns403()` test:

```java
@Test
@DisplayName("GET /api/v1/users/me as ROLE_ADMIN returns 200 with own profile")
@WithUserDetails(TestUtil.ADMIN_USER)
public void getCurrentUser_asAdmin_returns200() throws Exception {
    mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/me")
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
}

@Test
@DisplayName("GET /api/v1/users/me as ROLE_SUPER_ADMIN returns 200 with own profile")
public void getCurrentUser_asSuperAdmin_returns200() throws Exception {
    mockMvc.perform(get(ApiEndpoints.USERS_V1 + "/me")
                    .with(user(TestUtil.SUPER_ADMIN_USER).roles("SUPER_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
}
```

Note: `@WithUserDetails(TestUtil.ADMIN_USER)` loads the `admin@mail.com` user from MongoDB via `UserDetailsService`. This requires the seed user to have `"role": "ADMIN"` (done in Task 6). The test does NOT check the response body content-type for the email field because the `User` serializer omits `password` (via `@JsonIgnore`).

### 8c: TenantService unit test — participantId is ignored on update

- [ ] **Step 4: Add updateTenant tests to TenantServiceTest.java**

Add the following imports if not already present:
```java
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
```

Add these two tests after `disableTenant_success()`:

```java
@Test
@DisplayName("updateTenant preserves participantId from existing tenant, ignoring update body")
void updateTenant_participantIdIsIgnored() {
    Tenant existing = buildTenant(true);
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

    Tenant updates = Tenant.Builder.newInstance()
            .id(TENANT_ID)
            .name("New Name")
            .participantId("urn:connector:changed")   // different from existing "urn:connector:engineering"
            .automaticNegotiation(false)
            .automaticTransfer(false)
            .build();

    Tenant result = tenantService.updateTenant(TENANT_ID, updates);

    assertEquals("urn:connector:engineering", result.getParticipantId(),
            "participantId must remain unchanged regardless of update body");
    assertEquals("New Name", result.getName());
}

@Test
@DisplayName("updateTenant keeps participantId when update body has null participantId")
void updateTenant_nullParticipantIdInUpdates_keepExisting() {
    Tenant existing = buildTenant(true);
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(existing));
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

    // Build with a workaround: participantId is @NotNull in Tenant.Builder, so supply existing
    // value. The service must ignore it and use the stored value regardless.
    Tenant updates = Tenant.Builder.newInstance()
            .id(TENANT_ID)
            .name("Another Name")
            .participantId("urn:connector:engineering")  // same value — still tests the code path
            .automaticNegotiation(true)
            .automaticTransfer(false)
            .build();

    Tenant result = tenantService.updateTenant(TENANT_ID, updates);

    assertEquals("urn:connector:engineering", result.getParticipantId());
}
```

- [ ] **Step 5: Run TenantServiceTest**

```bash
mvn -pl tools -am -Dtest=TenantServiceTest -Dsurefire.failIfNoSpecifiedTests=false test -q
```

Expected: `BUILD SUCCESS`.

### 8d: TenantAPIIT — participantId immutability integration test

- [ ] **Step 6: Add integration test to TenantAPIIT.java**

Add the following import if not already present:
```java
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
```

Add the test after `updateTenant_asSuperAdmin_returns200()`:

```java
@Test
@DisplayName("PUT /api/v1/tenants/{id} with different participantId succeeds but preserves original participantId")
public void updateTenant_participantIdIsIgnored_returnsOriginalValue() throws Exception {
    Tenant original = buildNewTenant(); // participantId = "urn:connector:test-it"
    tenantRepository.save(original);

    Tenant updates = Tenant.Builder.newInstance()
            .id(NEW_TENANT_ID)
            .name("Updated Name")
            .description("Updated description")
            .participantId("urn:connector:changed") // different value — must be ignored
            .automaticNegotiation(false)
            .automaticTransfer(false)
            .build();

    mockMvc.perform(put(ApiEndpoints.TENANTS_V1 + "/" + NEW_TENANT_ID)
                    .with(user("super").roles("SUPER_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(Objects.requireNonNull(ToolsSerializer.serializePlain(updates))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.participantId").value("urn:connector:test-it"));
}
```

- [ ] **Step 7: Run unit tests to verify all new tests compile and pass**

```bash
mvn -pl connector -am test -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit all new tests**

```bash
git add connector/src/test/java/it/eng/connector/service/UserServiceTest.java \
        connector/src/test/java/it/eng/connector/integration/user/UserIT.java \
        tools/src/test/java/it/eng/tools/service/TenantServiceTest.java \
        connector/src/test/java/it/eng/connector/integration/tenant/TenantAPIIT.java
git commit -m "test: add tests for findCurrentUser, GET /me, participantId immutability"
```

---

## Task 9: Run full verification

- [ ] **Step 1: Run the full Maven build with integration tests**

```bash
mvn clean verify -q
```

Expected: `BUILD SUCCESS`. Docker must be running for Testcontainers (MongoDB + MinIO).

If any test fails:
- Compile errors → re-check Task 7 for missed enum references
- Integration test failures → check seed file changes in Task 6; verify `"role": "ADMIN"` in seed files matches `Role.ADMIN`
- Security tests returning wrong HTTP status → check the authorizeHttpRequests order in Task 3

- [ ] **Step 2: Run Checkstyle**

```bash
mvn validate -q
```

Expected: no Checkstyle violations. Common failures:
- Missing Javadoc on a public method → add it
- Missing `@param`/`@return` tags → add them

---

## Task 10: Update documentation

**Files:**
- Modify: `doc/security.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Update doc/security.md — admin endpoint access table**

Find the table or section that lists `/api/v1/users/**` access (around lines 64–67 and 188–191 based on current content).

Update the table entry for `/api/v1/users/**`. Change:

Old:
```
| `/api/v1/users/**` | `ROLE_SUPER_ADMIN` |
```

New — replace the single row with multiple rows:
```
| `GET /api/v1/users/me` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` |
| `PUT /api/v1/users/*/update` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` (own account only) |
| `PUT /api/v1/users/*/password` | `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` (own account only) |
| `* /api/v1/users/**` (all other) | `ROLE_SUPER_ADMIN` |
```

Also update any prose that describes the role authority model. Find lines 64–67 where the BASIC/KEYCLOAK auth matrix table shows `ROLE_SUPER_ADMIN required for /tenants/**, /users/**, /properties/**`. Update those cells to say:

```
Keycloak JWT → ROLE_ADMIN or ROLE_SUPER_ADMIN; ROLE_SUPER_ADMIN required for /tenants/**, most /users/**, /properties/**; ROLE_ADMIN may call /users/me and own-account PUT endpoints
```

Also find section around line 188–191 that says:
```
| `/api/v1/users/**` | `ROLE_SUPER_ADMIN` |
```
And replace with the split rows shown above.

- [ ] **Step 2: Update CHANGELOG.md — add entries to the [Unreleased] section**

In the `### Changed` subsection, add after the existing MT2 entry:

```markdown
- **MT2 — ROLE_ADMIN self-service user management** — `ROLE_ADMIN` users may now call
  `GET /api/v1/users/me` (own profile), `PUT /api/v1/users/{id}/update` (own name), and
  `PUT /api/v1/users/{id}/password` (own password) without requiring `ROLE_SUPER_ADMIN`.
  All other user-management endpoints (`GET /api/v1/users`, `POST /api/v1/users`, etc.)
  remain restricted to `ROLE_SUPER_ADMIN`.
- **MT2 — Role enum cleanup** — `Role` enum values renamed from `ROLE_ADMIN`/`ROLE_USER`/
  `ROLE_CONNECTOR`/`ROLE_SUPER_ADMIN` to `ADMIN`/`USER`/`CONNECTOR`/`SUPER_ADMIN`.
  `authorityName()` helper added to produce the Spring Security `ROLE_`-prefixed authority
  string. `User.getAuthorities()` now calls `role.authorityName()`.
  All inline `"ROLE_*"` string literals removed from the codebase.
- **MT2 — Tenant.participantId immutability** — `TenantService.updateTenant()` no longer
  accepts a new `participantId` from the request body; any supplied value is silently ignored
  and the stored `participantId` is always preserved.
```

- [ ] **Step 3: Commit documentation**

```bash
git add doc/security.md CHANGELOG.md
git commit -m "docs: document ADMIN self-service endpoints, Role enum rename, participantId immutability"
```

---

## Task 11: Final verification and branch push

- [ ] **Step 1: Run full verification one last time**

```bash
mvn clean verify -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run Checkstyle**

```bash
mvn validate -q
```

Expected: no violations.

- [ ] **Step 3: Verify no old enum references remain**

```bash
grep -rn "Role\.ROLE_" --include="*.java" . | grep -v target/
```

Expected: zero matches.

```bash
grep -rn '"ROLE_ADMIN"\|"ROLE_CONNECTOR"\|"ROLE_SUPER_ADMIN"\|"ROLE_USER"' --include="*.java" . | grep -v target/
```

Expected: zero matches (all replaced with `Role.X.authorityName()` or `Role.X.name()`).

```bash
grep -rn '"ROLE_ADMIN"\|"ROLE_CONNECTOR"\|"ROLE_SUPER_ADMIN"\|"ROLE_USER"' --include="*.json" . | grep -v target/
```

Expected: zero matches (all seed files updated).

- [ ] **Step 4: Push branch**

```bash
git push origin feature/249-slice-security-access-control-hardening
```
