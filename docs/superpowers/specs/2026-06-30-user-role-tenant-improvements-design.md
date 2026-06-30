# Design: User Self-Service, Role Enum Cleanup, and Tenant participantId Immutability

**Date:** 2026-06-30  
**Status:** Approved

## Overview

This design covers four related improvements to the connector's user management, role handling, and tenant model:

1. Allow `ROLE_ADMIN` users to manage their own account (new `/me` endpoint + PUT access).
2. Rename `Role` enum values to remove the redundant `ROLE_` prefix and eliminate all inline role strings.
3. Make `Tenant.participantId` immutable (cannot be changed after creation).
4. Update documentation to reflect all three changes.

---

## 1. ROLE_ADMIN Self-Service User Management

### Problem

The security config currently routes all of `/api/v1/users/**` to `ROLE_SUPER_ADMIN`. This prevents `ROLE_ADMIN` users from viewing or updating their own account details, even though the service layer already enforces ownership (only the authenticated user's own record can be modified).

### Design

**New endpoint:** `GET /api/v1/users/me`

- Available to `ROLE_ADMIN` and `ROLE_SUPER_ADMIN`.
- Reads the `Principal` from the request, resolves the current user by email, and returns the user as a `JsonNode`.
- If `Principal` is `null` (disabled-auth mode), the controller returns a `400 Bad Request` with a clear message, consistent with how the existing PUT methods handle it via the service's `loggedInUser == null` branch (which permits the action in disabled mode — the `/me` endpoint returns 400 in disabled mode since there is no authenticated user to identify).
- A new `UserService.findCurrentUser(String email)` method is added (delegates to the existing repository lookup).
- A new `UserApiController.getCurrentUser(Principal)` method handles the endpoint.

**Existing PUT endpoints** (`/{id}/update`, `/{id}/password`):

- Both opened to `ROLE_ADMIN` in addition to `ROLE_SUPER_ADMIN`.
- No service changes needed; the existing ownership check (`user.getEmail().equals(loggedInUser)`) already prevents cross-user writes.

**Security config change (`ConnectorSecurityConfig.adminFilterChain`):**

More specific matchers are added before the existing catch-all rule. Order matters; Spring Security evaluates rules top-to-bottom:

```java
.requestMatchers(HttpMethod.GET,  ApiEndpoints.USERS_V1 + "/me")
    .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
.requestMatchers(HttpMethod.PUT,  ApiEndpoints.USERS_V1 + "/*/update")
    .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
.requestMatchers(HttpMethod.PUT,  ApiEndpoints.USERS_V1 + "/*/password")
    .hasAnyRole(Role.ADMIN.name(), Role.SUPER_ADMIN.name())
.requestMatchers(ApiEndpoints.USERS_V1 + "/**")
    .hasRole(Role.SUPER_ADMIN.name())   // all other user endpoints: SUPER_ADMIN only
```

This change applies to both the KEYCLOAK and BASIC branches of the admin filter chain.

### Files Affected

- `connector/src/main/java/it/eng/connector/configuration/ConnectorSecurityConfig.java`
- `connector/src/main/java/it/eng/connector/rest/api/UserApiController.java`
- `connector/src/main/java/it/eng/connector/service/UserService.java`

---

## 2. Role Enum Cleanup

### Problem

The `Role` enum values carry a redundant `ROLE_` prefix (`ROLE_ADMIN`, `ROLE_SUPER_ADMIN`, etc.). This causes awkward Spring Security integration — `hasRole()` strips `ROLE_` from the authority but the enum values have it baked in, requiring `.name().substring("ROLE_".length())` in the security config. It also means inline string literals in tests duplicate the enum value.

### Design

**Rename `Role` enum values:**

| Old name         | New name      |
|------------------|---------------|
| `ROLE_USER`      | `USER`        |
| `ROLE_ADMIN`     | `ADMIN`       |
| `ROLE_CONNECTOR` | `CONNECTOR`   |
| `ROLE_SUPER_ADMIN` | `SUPER_ADMIN` |

**Add `authorityName()` helper:**

```java
public enum Role {
    USER, ADMIN, CONNECTOR, SUPER_ADMIN;

    /**
     * Returns the Spring Security authority string for this role (e.g. {@code "ROLE_ADMIN"}).
     *
     * @return the prefixed authority name
     */
    public String authorityName() {
        return "ROLE_" + name();
    }
}
```

**`User.getAuthorities()`** — use the helper so the authority string is correct:

```java
return List.of(new SimpleGrantedAuthority(role.authorityName()));
```

**`ConnectorSecurityConfig`** — remove the three `private static final String` role constants (`ADMIN_ROLE`, `CONNECTOR_ROLE`, `SUPER_ADMIN_ROLE`). Replace every `hasRole(ADMIN_ROLE)` / `hasAnyRole(ADMIN_ROLE, ...)` call with `hasRole(Role.ADMIN.name())` / `hasAnyRole(Role.ADMIN.name(), ...)` inline. No intermediate variables.

**All other callers** — replace every `Role.ROLE_ADMIN` (etc.) reference with `Role.ADMIN` throughout production code and tests. Replace test string literals `"ROLE_ADMIN"` with `Role.ADMIN.authorityName()`.

**`KeycloakRealmRoleConverter`** — no change. It already produces `"ROLE_" + jwtRole.toUpperCase()` (e.g. `"ROLE_ADMIN"` from a JWT claim of `"ADMIN"`), which matches `Role.ADMIN.authorityName()`.

**No backward-compatibility annotations required** — there is no existing deployed data to migrate.

### Files Affected

- `connector/src/main/java/it/eng/connector/model/Role.java`
- `connector/src/main/java/it/eng/connector/model/User.java`
- `connector/src/main/java/it/eng/connector/configuration/ConnectorSecurityConfig.java`
- `connector/src/main/java/it/eng/connector/configuration/InternalServiceAuthenticationProvider.java`
- `connector/src/main/java/it/eng/connector/service/UserService.java`
- `connector/src/main/java/it/eng/connector/service/KeycloakUserService.java`
- `connector/src/main/java/it/eng/connector/model/UserDTO.java` (Javadoc references)
- All test files that reference `Role.ROLE_*` or inline `"ROLE_*"` strings

---

## 3. Tenant.participantId Immutability

### Problem

`TenantService.updateTenant()` rebuilds the tenant using `updates.getParticipantId()`, which allows the `participantId` to be changed after creation. The `participantId` is a stable DSP participant identity and should be set once at creation time only.

### Design

In `TenantService.updateTenant()`, always take `participantId` from the existing tenant:

```java
.participantId(existing.getParticipantId())  // immutable; request body value silently ignored
```

The `rebuildWithEnabled()` private helper already uses `source.getParticipantId()` — no change needed there.

**Client behavior:** If a client sends a `participantId` value in the update request body, the request succeeds and the value is silently ignored. The stored `participantId` remains unchanged.

**No model changes:** `@NotNull` on `Tenant.participantId` is preserved (clients may include it in the request body without breaking anything).

**Javadoc updates:**
- `TenantService.updateTenant()` — remove `participantId` from the list of mutable fields in the doc comment.
- `TenantAPIController.updateTenant()` — add a note clarifying that `participantId` is read-only.

### Files Affected

- `tools/src/main/java/it/eng/tools/service/TenantService.java`
- `connector/src/main/java/it/eng/connector/rest/api/TenantAPIController.java` (Javadoc only)

---

## 4. Documentation Updates

### `doc/security.md`

Add a section or note clarifying:
- `ROLE_ADMIN` users may call `GET /api/v1/users/me`, `PUT /api/v1/users/{id}/update`, and `PUT /api/v1/users/{id}/password` for their own account only.
- All other user-management endpoints (list all users, create user) remain restricted to `ROLE_SUPER_ADMIN`.

### Tenant docs

Update any tenant API documentation to state that `participantId` is set at creation and is immutable thereafter.

### `CHANGELOG.md`

Add entries under the next unreleased version:

```markdown
### Changed
- ROLE_ADMIN users can now update their own name, password, and view their own profile via `/api/v1/users/me`.
- `Role` enum values renamed (ROLE_ADMIN → ADMIN, etc.); `authorityName()` helper added for Spring Security integration.
- `Tenant.participantId` is now treated as immutable; update requests that include a different `participantId` succeed but the value is silently ignored.
```

---

## Testing

- **`UserIT`** — add test cases for `GET /api/v1/users/me` as ADMIN and SUPER_ADMIN; verify ADMIN cannot call `GET /api/v1/users` (returns 403).
- **`UserServiceTest`** — add unit test for `findCurrentUser()`.
- **`TenantServiceTest` / `TenantIT`** — add a test that sends a different `participantId` in an update body and verifies the stored value is unchanged.
- Existing tests referencing `Role.ROLE_ADMIN` will be updated to `Role.ADMIN` (compile-time check).
