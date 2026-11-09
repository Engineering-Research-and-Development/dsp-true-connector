# Keycloak Authentication Internals

How a Keycloak bearer token is turned into a Spring Security `Authentication` in `KEYCLOAK` mode, and
which Spring Security and Nimbus JOSE+JWT pieces do the work underneath `KeycloakAuthenticationFilter`.

This is a code-level companion to [security.md](security.md) → *Keycloak Authentication Mode* and
*Trusted Issuers*. Those sections describe the rules. This page describes the machinery that enforces them.

Short answer to "does the filter only decode, or does it verify the signature?": `jwtDecoder.decode(token)`
**does verify the signature**. The injected `JwtDecoder` is not a parser. It selects a trusted issuer, loads
that issuer's public keys from Keycloak and verifies the signature with them. Only then does it validate the
claims. See [Step 3](#step-3--jwtdecoderdecode-what-actually-happens).

---

## Classes involved

| Class | Module | Role |
|---|---|---|
| `ConnectorSecurityConfig` | `connector` | Builds the four `SecurityFilterChain` beans and the two Keycloak filter beans |
| `KeycloakAuthenticationFilter` | `connector` | Reads the `Authorization` header, calls the decoder, populates the `SecurityContext` |
| `TrustedIssuerRegistry` | `tools` | Picks the issuer, caches one `NimbusJwtDecoder` per issuer, composes the per-chain decoder |
| `KeycloakDiscoveryClient` | `tools` | Fetches `<issuer>/.well-known/openid-configuration` |
| `JwkSetRetriever` | `tools` | Nimbus `ResourceRetriever` that fetches the JWK set (no redirects, bounded time and size) |
| `KeycloakHttpClientConfiguration` | `tools` | The JDK `HttpClient` shared by both: no redirects, host name verification, TLS trust from the connector SSL bundle with OCSP when `server.ssl.enabled=true`, JVM default truststore otherwise |
| `IssuerTokenValidators` | `tools` | Per-issuer-type checks: `aud`, `azp`, `tenant_id`, realm role rules |
| `KeycloakRealmRoleConverter` | `connector` | Maps `realm_access.roles` to exactly one Spring authority per issuer type |
| `KeycloakIssuerClassifier` | `connector` | Tells `TENANT` / `ADMIN` / `PLATFORM` from a **verified** `iss` |
| `RealmRoleViolationAuditor` | `connector` | Writes the `SECURITY_REALM_ROLE_VIOLATION` audit event |
| `ApiTenantContextFilter` | `connector` | Sets `TenantContextHolder` from `tenant_id` or `X-Tenant-Id` (admin chain only) |

All Keycloak beans are created only when `KeycloakAuthenticationModeCondition` matches, so in `INTERNAL` or
`DISABLED` mode none of them exist.

---

## End-to-end flow

```
HTTP request  Authorization: Bearer eyJ...
   │
   ▼
Servlet container ─► DelegatingFilterProxy("springSecurityFilterChain") ─► FilterChainProxy
   │
   │  FilterChainProxy picks the FIRST SecurityFilterChain whose securityMatcher matches:
   │    @Order(0) /internal/**                       internal chain (no Keycloak filter)
   │    @Order(1) /api/**, /actuator/**, /env        admin chain     ◄── keycloakAuthenticationFilter
   │    @Order(2) /*/catalog/**, /*/negotiations/**… protocol chain  ◄── keycloakProtocolAuthenticationFilter
   │    @Order(3) everything else                    default chain (permitAll)
   ▼
SecurityContextHolderFilter        (empty context per request; nothing is loaded from a session)
HeaderWriterFilter, CorsFilter, …
KeycloakAuthenticationFilter       ─┐ Step 1–4
   jwtDecoder.decode(token)         │   TrustedIssuerRegistry.issuerSelectingDecoder(...)
     ├─ parse unverified, read iss  │     ├─ resolve(iss) → TrustedIssuer (or BadJwtException)
     ├─ decoderFor(issuer)          │     ├─ cached NimbusJwtDecoder (discovery + JWKS on first use)
     │    └─ NimbusJwtDecoder       │     │    ├─ signature check against the realm's JWK set
     │         .decode(token)       │     │    └─ exp / nbf / exact iss
     └─ IssuerTokenValidators       │     └─ aud / azp / tenant_id / realm role rules
   authoritiesConverter.convert()   │   KeycloakRealmRoleConverter → ROLE_ADMIN | ROLE_SUPER_ADMIN | ROLE_CONNECTOR
   SecurityContext ← JwtAuthenticationToken
                                    ─┘
ApiTenantContextFilter             (admin chain only) Step 5
ExceptionTranslationFilter
AuthorizationFilter                Step 6: hasRole / hasAnyRole → 200, 401 or 403
   │
   ▼
DispatcherServlet → controller
```

---

## Step 0 – How the filter gets into the right chain

### Two filter instances, two decoders

`ConnectorSecurityConfig` creates **two beans of the same class**, differing only in the decoder:

```java
// admin chain: tenant, connector-admin and platform issuers
JwtDecoder decoder = registry.issuerSelectingDecoder(
        Set.of(IssuerType.TENANT, IssuerType.ADMIN, IssuerType.PLATFORM), validators::forIssuer);

// protocol chain: platform issuer only
JwtDecoder decoder = registry.issuerSelectingDecoder(Set.of(IssuerType.PLATFORM), validators::forIssuer);
```

The filter has no idea which issuers are trusted. The trust scope of a chain is entirely in the decoder
passed to it. That is why a tenant token sent to `/{tenantId}/catalog/request` gets 401: the protocol
decoder rejects the issuer type before any key is fetched.

The chains inject them by name with `@Qualifier("keycloakAuthenticationFilter")` and
`@Qualifier("keycloakProtocolAuthenticationFilter")` through an `ObjectProvider`, because in other modes
the beans don't exist.

### Placement inside the chain

```java
http.anonymous(AbstractHttpConfigurer::disable)
    .addFilterBefore(keycloakFilter.getObject(), UsernamePasswordAuthenticationFilter.class)
    .addFilterAfter(apiTenantContextFilter.getObject(), UsernamePasswordAuthenticationFilter.class)
```

- `UsernamePasswordAuthenticationFilter` is not actually in the chain (no form login). Spring Security uses
  its registered position only as an **anchor** in the filter order. "Before" and "after" the same anchor
  guarantee that the Keycloak filter runs first and the tenant filter sees its result.
- Both end up before `ExceptionTranslationFilter` and `AuthorizationFilter`, which always sit near the end
  of the chain.
- `anonymous(disable)` matters for Step 6: without it, Spring would put an `AnonymousAuthenticationToken`
  in the context and an unauthenticated request would get 403 instead of 401.

### Why the global servlet registration is disabled

Spring Boot registers **every** `Filter` bean with the servlet container, mapped to `/*`. The Keycloak
filters are `@Bean`s, so without intervention they would also run outside Spring Security, on every request,
including `/internal/**`. Two `FilterRegistrationBean`s with `setEnabled(false)` stop that:

```java
FilterRegistrationBean<KeycloakAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
registration.setEnabled(false);
```

The filter then runs only where `addFilterBefore` placed it. `shouldNotFilter` (skip `/internal/`) is a
second guard in case this ever regresses. `OncePerRequestFilter` additionally guarantees a single execution
per request even on forwards and error dispatches.

### Stateless by construction

`sessionManagement(disable)` is applied to every chain. In Spring Security 6 the `SecurityContextHolderFilter`
does not save the context automatically, and the filter only calls `securityContextHolderStrategy.setContext`.
Nothing is written to an `HttpSession`. Every request is authenticated from its own token, and
`SecurityContextHolderFilter` clears the thread-local context when the request ends.

---

## Step 1 – Extracting the token

`KeycloakAuthenticationFilter.doFilterInternal`:

1. Reads `Authorization`. If it is missing or doesn't start with `Bearer `, the filter does **nothing** and
   continues the chain. It never answers 401 itself.
2. Otherwise it takes the substring after `Bearer ` and calls `jwtDecoder.decode(tokenValue)`.

---

## Step 2 – Choosing the issuer from the unverified token

`issuerSelectingDecoder(...)` returns a lambda `JwtDecoder`. Its first part is `selectIssuer`:

1. `JWTParser.parse(token)` (Nimbus). This only base64-decodes the header and payload. Nothing is trusted yet.
2. Anything that isn't a `SignedJWT` is rejected: `PlainJWT` (`alg: none`) and `EncryptedJWT` included.
3. The **unverified** `iss` is read and passed to `resolve(iss)`:
   - exact string match with `issuerFor(platformRealm)` → `PLATFORM`;
   - exact string match with `issuerFor(adminRealm)` → `ADMIN`;
   - otherwise it must start with `<base-url>/realms/`. The remainder must match `^[A-Za-z0-9._-]+$`, must not
     be `.`/`..`, must not be `master`, the platform realm or the admin realm. Then Mongo is queried
     (`findFirstByRealmAndEnabledTrue`). Only an enabled tenant bound to exactly that realm makes it a
     `TENANT` issuer, carrying that tenant's `_id`.
4. The issuer type must be in the chain's `allowed` set.

Any failure here is a `BadJwtException` and **no network call happens**. A token from an unknown issuer
can't make the connector fetch anything.

The tenant lookup is not cached. Disabling or deleting a tenant, or changing its `realm`, takes effect on
the next request, and `resolve` also evicts that issuer's cached decoder.

Why the unverified `iss` is safe to use here: it only picks **which** of a fixed set of known issuers to try.
The URL that is then fetched is rebuilt from configuration (`issuerFor(realm)`), never copied from the token.
If an attacker writes another realm's `iss` into a token, that token is verified with that realm's keys and
fails the signature check.

---

## Step 3 – `jwtDecoder.decode()`: what actually happens

### 3a. Getting the issuer's decoder (`decoderFor`)

Decoders live in a Caffeine `AsyncCache<String, JwtDecoder>` keyed by issuer URL (max 1000 entries,
evicted after 1 hour without use). On a cache miss, `loadDecoder` runs on the registry's small executor:

1. `KeycloakDiscoveryClient.fetch(issuerUrl)`: `GET <issuer>/.well-known/openid-configuration`. The
   document's `issuer` must equal the expected issuer exactly, and it must contain `jwks_uri`.
2. A Nimbus `JWKSource` is built for `jwks_uri` as a stack of sources, outermost first:

   ```
   JWKSetBasedJWKSource
     └─ RefreshAheadCachingJWKSetSource   cache 5 min, background refresh 30 s before expiry
          └─ RateLimitedJWKSetSource      at most 2 fetches per 30 s per issuer
               └─ URLBasedJWKSetSource    HTTP via JwkSetRetriever (no redirects, 64 KiB cap)
   ```

3. The JWK set is fetched **once immediately**, so an unreachable JWKS fails the load instead of producing a
   decoder that fails on every request.
4. The decoder is built:

   ```java
   DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
   processor.setJWSKeySelector(new JWSVerificationKeySelector<>(SIGNATURE_ALGORITHMS, jwkSource));
   processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT, null));
   processor.setJWTClaimsSetVerifier((claims, context) -> { });   // claims checked by Spring below
   NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
   decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUrl));
   ```

The calling request thread waits for the load at most `2 × (connect + read timeout) + 500 ms`, which is
30.5 s with the defaults (5 s + 10 s). A failed or timed-out load is not cached, but the issuer is marked in
`recentFailures` for 5 seconds. During that window requests fail fast with a `JwtException` instead of
piling up on a dead Keycloak.

Concurrent first requests for the same issuer share one load. `AsyncCache` hands them the same
`CompletableFuture`.

### 3b. Inside `NimbusJwtDecoder.decode` (Spring Security)

`NimbusJwtDecoder` is Spring Security's adapter around Nimbus. For each token it:

1. **Parses** the token again (`JWTParser`).
2. **Runs `DefaultJWTProcessor.process`** (Nimbus):
   - **Header type check**: `typ` must be `JWT` or absent.
   - **Key selection**: `JWSVerificationKeySelector` takes the header's `alg` and `kid`. It rejects any `alg`
     outside `SIGNATURE_ALGORITHMS` (RS*, PS*, ES* only). It then asks the `JWKSource` for keys whose `kid`,
     key type and use match.
     If the `kid` isn't in the cached set (Keycloak rotated its keys), the source refetches the JWK set,
     limited by the rate limiter, so rotation is picked up without a restart.
   - **Signature verification**: a Nimbus `RSASSAVerifier` / `ECDSAVerifier` is created for each candidate
     public key and checks the signature over `base64(header).base64(payload)`. **This is the signature
     check.** No candidate verifies → `BadJOSEException("Signed JWT rejected: Invalid signature")`.
   - **Claims verifier**: deliberately a no-op here. Spring's validators below do the claim checks.
3. **Converts claims** with `MappedJwtClaimSetConverter`: `exp`/`iat`/`nbf` become `Instant`, `aud` becomes
   a list and `iss` a string. The result is a Spring `Jwt` object.
4. **Runs the validator** from `JwtValidators.createDefaultWithIssuer(issuerUrl)`:
   - `JwtTimestampValidator`: `exp` and `nbf`, with a 60 s clock-skew allowance;
   - `JwtIssuerValidator`: `iss` must equal the issuer URL exactly;
   - recent Spring Security versions also add `X509CertificateThumbprintValidator`, which only acts on
     certificate-bound tokens (`cnf` claim). Keycloak tokens here don't carry one.

How the failures map to exceptions:

| Failure | Exception thrown out of `decode` |
|---|---|
| Malformed, unsigned, unknown or disallowed issuer (Step 2) | `BadJwtException` |
| Bad signature, disallowed `alg`, no matching key | `BadJwtException` |
| Expired, not yet valid, wrong `iss` | `JwtValidationException` (subclass of `BadJwtException`) |
| JWKS / discovery unreachable, load timed out, issuer in retry back-off | `JwtException` (not `BadJwtException`) |

### 3c. Per-issuer validators (`IssuerTokenValidators`)

After the cached decoder returns a verified `Jwt`, the lambda applies the chain's extra validator for that
issuer. It is a `DelegatingOAuth2TokenValidator`, so all errors are collected:

| Issuer type | `aud` | `azp` | Privileged realm roles | `tenant_id` |
|---|---|---|---|---|
| `TENANT` | contains `application.keycloak.audience` | `ui-client-id` | only `ADMIN` allowed | required, equals the tenant `_id` bound to the realm |
| `ADMIN` | contains audience | `ui-client-id` | only `SUPER_ADMIN` allowed | must be absent |
| `PLATFORM` | contains audience | `platform-client-id` | only `CONNECTOR`, and it is required | must be absent |

The privileged roles are `ADMIN`, `SUPER_ADMIN`, `CONNECTOR` and `INTERNAL`, compared case-insensitively.
A token from a realm that grants a privileged role it is not allowed to grant (a tenant realm issuing
`SUPER_ADMIN`, say) is **rejected as a whole** with error code `realm_role_violation`, not silently stripped.
Errors become a `JwtValidationException`.

These validators are kept outside the cached decoder on purpose: the same issuer's decoder (and its JWK
cache) is shared by both chains, while the rules can differ per chain.

---

## Step 4 – Building the `Authentication`

On success the filter:

1. Calls `KeycloakRealmRoleConverter.convert(jwt)`. It classifies the now-**verified** `iss` with
   `KeycloakIssuerClassifier` and grants **at most one** authority:
   - `TENANT` + realm role `ADMIN` → `ROLE_ADMIN`
   - `ADMIN` + realm role `SUPER_ADMIN` → `ROLE_SUPER_ADMIN`
   - `PLATFORM` + realm role `CONNECTOR` → `ROLE_CONNECTOR`

   There is no generic `ROLE_<x>` mapping, so creating a new role in a realm grants nothing. No Keycloak token
   ever gets `ROLE_INTERNAL`. This is defence in depth behind the validators of Step 3c.
2. Creates `new JwtAuthenticationToken(jwt, authorities)`. That constructor marks it authenticated. Its
   name is the `sub` claim and the `Jwt` stays available as the principal and credentials.
3. Puts it into a fresh `SecurityContext` via the `SecurityContextHolderStrategy`.

On a `JwtException` the filter:

1. **Clears** the context.
2. Logs and audits by type:
   - realm role violation → WARN plus a `SECURITY_REALM_ROLE_VIOLATION` audit event (realm, issuer type, `sub`,
     path; never the token);
   - any other `BadJwtException` → DEBUG with the exception type and error codes only;
   - plain `JwtException` (keys unavailable) → WARN, because it is an infrastructure problem.
3. **Continues the chain anyway.** The filter only authenticates. The decision is left to Step 6, so a bad
   token on a `permitAll` route such as `/actuator/health` doesn't break that route.

---

## Step 5 – Tenant context (admin chain only)

`ApiTenantContextFilter` reads the `JwtAuthenticationToken` that Step 4 stored:

- a tenant token → `tenant_id` claim → `TenantContextHolder`;
- a super-admin token (no `tenant_id`) → the tenant named by `X-Tenant-Id`. It must exist and be enabled,
  otherwise 400. Each use is audited as `TENANT_DELEGATION`. Without the header the tenant stays `null`;
- anyone else sending `X-Tenant-Id` → 403;
- a platform token → no tenant.

It clears `TenantContextHolder` in a `finally` block.

---

## Step 6 – Authorization and the HTTP status

`AuthorizationFilter` evaluates the rules declared in `authorizeHttpRequests(...)`. For example,
`/api/v1/tenants/**` requires `hasRole("SUPER_ADMIN")`, which checks the authority `ROLE_SUPER_ADMIN`.

| Situation after Step 4 | What Spring does | Status |
|---|---|---|
| No token, or the token was rejected (context empty, anonymous disabled) | `AuthorizationFilter` throws `AuthenticationCredentialsNotFoundException` → `ExceptionTranslationFilter` → `delegatedAuthenticationEntryPoint` → MVC `HandlerExceptionResolver` | **401** |
| Valid token, wrong authority (a tenant `ROLE_ADMIN` on `/api/v1/tenants`, a platform token on `/api/**`) | `AccessDeniedException` → access denied handler | **403** |
| Valid token, matching authority | request reaches the controller | 2xx |

`DataspaceProtocolEndpointsAuthenticationEntryPoint` forwards authentication errors to Spring MVC's
`HandlerExceptionResolver`, so the 401 body is produced by the application's normal exception handling
instead of the bare servlet error page.

---

## Comparison with Spring's built-in resource server

Spring Security provides `http.oauth2ResourceServer(o -> o.jwt(...))`, which wires
`BearerTokenAuthenticationFilter` → `JwtAuthenticationProvider` → `JwtDecoder` →
`JwtAuthenticationConverter`, and offers `JwtIssuerAuthenticationManagerResolver` for multiple issuers.

The connector reuses the same building blocks: `JwtDecoder`, `NimbusJwtDecoder`, `OAuth2TokenValidator`,
`JwtAuthenticationToken`. It replaces only the outer layer:

| | Built-in resource server | Connector |
|---|---|---|
| Invalid token | 401 immediately from the filter, with a `WWW-Authenticate` header, even on `permitAll` routes | context left empty, the authorization rules decide |
| Multi-issuer | `JwtIssuerAuthenticationManagerResolver` with a static issuer list or a predicate | `TrustedIssuerRegistry`: static issuers plus tenant realms looked up per request, with per-type rules |
| Key loading | `JwtDecoders.fromIssuerLocation`, default Nimbus retriever (follows redirects) | own discovery client and `JwkSetRetriever` (no redirects, bounded time and size), shared executor, failure back-off |
| Role mapping | `scope`/`scp` → `SCOPE_x` by default | per-realm allow-list on `realm_access.roles` |
| Audit hook | none | realm role violation audit event |

---

## Debugging

Useful log levels:

```properties
# Filter, converter, auditor: decode success/failure, extracted authorities
logging.level.it.eng.connector.configuration=DEBUG
# Issuer resolution, decoder loading, discovery and JWKS fetches
logging.level.it.eng.tools.auth.keycloak=DEBUG
# Which SecurityFilterChain matched and the filter-by-filter trace
logging.level.org.springframework.security=TRACE
```

Typical symptoms:

| Log | Meaning |
|---|---|
| `Bearer token rejected: BadJwtException` | untrusted issuer, wrong chain, bad signature or malformed token. Check `iss` against `<base-url>/realms/<realm>` (see [security.md](security.md) → *Hostname / `iss` alignment*) |
| `Bearer token rejected: JwtValidationException [invalid_token]` | expired, or failed `aud`/`azp`/`tenant_id` |
| `Bearer token rejected on …: privileged realm role` | realm misconfiguration. See the audit event |
| `Bearer token could not be verified … signing keys or decoder unavailable` | Keycloak discovery or JWKS unreachable or slow. TLS trust is a common cause: with `server.ssl.enabled=true` the Keycloak CA must be in the `connector` SSL bundle truststore (and pass OCSP if enabled); with `server.ssl.enabled=false` it must be in the JVM default truststore |
| `Loaded signing keys of issuer …` (INFO) | first successful use of an issuer, logged once per cache load |

Breakpoints that show the whole path: `KeycloakAuthenticationFilter.doFilterInternal`,
`TrustedIssuerRegistry.selectIssuer`, `TrustedIssuerRegistry.loadDecoder`, `NimbusJwtDecoder.decode`,
`IssuerTokenValidators.forIssuer`.

---

## Tests

| Test | Covers |
|---|---|
| `KeycloakAuthenticationFilterTest` | filter behaviour: no header, success, each failure type, `/internal` skip |
| `TrustedIssuerRegistryTest` | issuer resolution, caching, failure back-off, key rotation |
| `TenantTokenMatrixTest`, `IssuerTokenValidatorsTest` | per-issuer claim rules |
| `KeycloakRealmRoleConverterTest` | role allow-list |
| `KeycloakIssuerTrustIT`, `KeycloakIssuerRegistryIT`, `KeycloakMultiIssuerApiIT` | the full chain against a real Keycloak (Testcontainers) |
| `KeycloakInternalCallSecurityIT` | Keycloak tokens never open `/internal/**` |

## Related

- [security.md](security.md): rules, properties, troubleshooting
- [diagrams/auth_provider_architecture.md](diagrams/auth_provider_architecture.md): chain diagrams
- [D-ARC-006](decisions/architecture/D-ARC-006-per-realm-role-allow-list-and-per-tenant-realm-credentials.md): per-realm role allow-list
- [D-ARC-005](decisions/architecture/D-ARC-005-internal-call-zone-with-in-memory-key.md): internal call zone
