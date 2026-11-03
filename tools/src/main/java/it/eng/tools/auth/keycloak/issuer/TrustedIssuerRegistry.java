package it.eng.tools.auth.keycloak.issuer;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import it.eng.tools.auth.keycloak.DiscoveryDocument;
import it.eng.tools.auth.keycloak.KeycloakDiscoveryClient;
import it.eng.tools.auth.keycloak.KeycloakTrustProperties;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Registry of the Keycloak issuers the connector trusts, with one cached, rotation-aware
 * {@link JwtDecoder} per issuer.
 * <p>
 * Trusted issuers are the static {@code platform} and {@code connector-admin} realms and the realm of
 * every enabled tenant. The unverified {@code iss} of a token is only used to select a registered issuer:
 * it is compared exactly against the static issuers or, after a strict pattern check, used as a realm name
 * in a tenant lookup. It is never used to build a URL. Every fetched URL is either produced by
 * {@link KeycloakTrustProperties#issuerFor(String)} (the discovery document) or taken from that issuer's
 * verified discovery document ({@code jwks_uri}).
 * <p>
 * Tenant issuers are dynamic: {@link #resolve(String)} queries the tenant repository on every call, so a
 * tenant that is created, enabled, disabled, deleted or moved to another realm is trusted or untrusted on
 * the next request. Only decoders are cached, keyed by issuer URL, bounded in size and evicted after a
 * period without use.
 * <p>
 * Extra validation hook: the cached decoders only check the signature, {@code exp}, {@code nbf} and the
 * exact {@code iss}. Callers that need more checks (for example {@code aud} or {@code azp}) pass a
 * per-issuer validator to {@link #issuerSelectingDecoder(Set, Function)}. The validator runs after the
 * cached decoder, so different routes can apply different rules to the same issuer without rebuilding
 * or duplicating the decoder cache.
 */
@Slf4j
@Component
@Conditional(KeycloakAuthenticationModeCondition.class)
public class TrustedIssuerRegistry {

    /**
     * Accepted signature algorithms: asymmetric only, so a public key can never be used as an HMAC secret.
     */
    static final Set<JWSAlgorithm> SIGNATURE_ALGORITHMS = Set.of(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512);

    private static final String KEYCLOAK_MASTER_REALM = "master";
    private static final int EXECUTOR_THREADS = 4;
    private static final int MAX_LOGGED_ISSUER_LENGTH = 200;
    private static final int REQUESTS_PER_LOAD = 2;
    private static final Duration LOAD_TIMEOUT_MARGIN = Duration.ofMillis(500);
    private static final OAuth2TokenValidator<Jwt> NO_ADDITIONAL_VALIDATION = jwt -> OAuth2TokenValidatorResult.success();

    private final KeycloakTrustProperties properties;
    private final KeycloakDiscoveryClient discoveryClient;
    private final TenantRepository tenantRepository;
    private final IssuerRegistrySettings settings;
    private final Duration loadTimeout;
    private final String realmsPrefix;
    private final TrustedIssuer platformIssuer;
    private final TrustedIssuer adminIssuer;
    private final Set<String> reservedRealms;
    private final JwkSetRetriever jwkSetRetriever;
    private final ExecutorService executor;
    private final AsyncCache<String, JwtDecoder> decoders;
    private final Cache<String, Boolean> recentFailures;

    /**
     * Creates the registry with the default cache and refresh settings.
     *
     * @param properties the Keycloak trust configuration
     * @param discoveryClient loads discovery documents of registered issuers
     * @param tenantRepository looks up the tenant bound to a realm
     */
    @Autowired
    public TrustedIssuerRegistry(KeycloakTrustProperties properties, KeycloakDiscoveryClient discoveryClient,
            TenantRepository tenantRepository) {
        this(properties, discoveryClient, tenantRepository, IssuerRegistrySettings.defaults(), Ticker.systemTicker());
    }

    /**
     * Creates the registry with explicit settings and a ticker for the decoder and failure caches.
     *
     * @param properties the Keycloak trust configuration
     * @param discoveryClient loads discovery documents of registered issuers
     * @param tenantRepository looks up the tenant bound to a realm
     * @param settings the cache and refresh settings
     * @param ticker the time source of the decoder and failure caches
     */
    TrustedIssuerRegistry(KeycloakTrustProperties properties, KeycloakDiscoveryClient discoveryClient,
            TenantRepository tenantRepository, IssuerRegistrySettings settings, Ticker ticker) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.discoveryClient = Objects.requireNonNull(discoveryClient, "discoveryClient");
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository");
        this.settings = Objects.requireNonNull(settings, "settings");
        // A load makes two requests (discovery, JWK set), each bounded by connect plus read timeout.
        this.loadTimeout = properties.getDiscoveryConnectTimeout().plus(properties.getDiscoveryReadTimeout())
                .multipliedBy(REQUESTS_PER_LOAD)
                .plus(LOAD_TIMEOUT_MARGIN);
        this.realmsPrefix = properties.getBaseUrl() + "/realms/";
        this.platformIssuer = new TrustedIssuer(properties.issuerFor(properties.getPlatformRealm()),
                properties.getPlatformRealm(), IssuerType.PLATFORM, null);
        this.adminIssuer = new TrustedIssuer(properties.issuerFor(properties.getAdminRealm()),
                properties.getAdminRealm(), IssuerType.ADMIN, null);
        this.reservedRealms = Set.of(KEYCLOAK_MASTER_REALM,
                properties.getPlatformRealm().toLowerCase(Locale.ROOT),
                properties.getAdminRealm().toLowerCase(Locale.ROOT));
        this.jwkSetRetriever = new JwkSetRetriever(properties.getDiscoveryConnectTimeout(),
                properties.getDiscoveryReadTimeout(), settings.jwksSizeLimit());
        this.executor = newExecutor();
        this.decoders = Caffeine.newBuilder()
                .maximumSize(settings.decoderCacheMaximumSize())
                .expireAfterAccess(settings.decoderCacheExpireAfterAccess())
                .ticker(ticker)
                .executor(executor)
                .buildAsync();
        this.recentFailures = Caffeine.newBuilder()
                .maximumSize(settings.decoderCacheMaximumSize())
                .expireAfterWrite(settings.failureRetryInterval())
                .ticker(ticker)
                .build();
    }

    /**
     * Selects the trusted issuer named by an unverified {@code iss}. Makes no network request.
     * <p>
     * The platform and admin issuers match by exact string comparison. Any other value must be
     * {@code <base-url>/realms/<realm>}, where {@code <realm>} matches {@link KeycloakTrustProperties#REALM_PATTERN},
     * is not {@code .} or {@code ..} and is not a reserved realm name ({@code master}, the platform or the
     * admin realm, compared case-insensitively). It then resolves only when an enabled tenant is bound to
     * exactly that realm. The tenant repository is queried on every call, there is no resolution cache.
     * When a tenant realm no longer resolves, its cached decoder is evicted.
     *
     * @param unverifiedIss the {@code iss} claim of a token whose signature has not been verified yet
     * @return the trusted issuer, or empty when the value names no trusted issuer
     */
    public Optional<TrustedIssuer> resolve(String unverifiedIss) {
        if (unverifiedIss == null) {
            return Optional.empty();
        }
        if (unverifiedIss.equals(platformIssuer.issuer())) {
            return Optional.of(platformIssuer);
        }
        if (unverifiedIss.equals(adminIssuer.issuer())) {
            return Optional.of(adminIssuer);
        }
        if (!unverifiedIss.startsWith(realmsPrefix)) {
            return Optional.empty();
        }
        String realm = unverifiedIss.substring(realmsPrefix.length());
        if (!isTenantRealmCandidate(realm)) {
            return Optional.empty();
        }
        String issuer = properties.issuerFor(realm);
        Optional<Tenant> tenant = tenantRepository.findFirstByRealmAndEnabledTrue(realm)
                .filter(t -> t.isEnabled() && realm.equals(t.getRealm()));
        if (tenant.isEmpty()) {
            decoders.synchronous().invalidate(issuer);
            return Optional.empty();
        }
        return Optional.of(new TrustedIssuer(issuer, realm, IssuerType.TENANT, tenant.get().getId()));
    }

    /**
     * Returns the cached decoder of a trusted issuer, loading it on first use.
     * <p>
     * Loading fetches the discovery document of the registered issuer URL, then the JWK set from its
     * {@code jwks_uri}. The decoder verifies the signature with that JWK set only and validates
     * {@code exp}, {@code nbf} and the exact {@code iss}. An unknown {@code kid} triggers a rate-limited
     * JWK set refetch, so rotated keys are picked up without a restart. A failed load is not cached: the
     * issuer is retried on a later call, but at most once per {@code failureRetryInterval}. The wait for a
     * load is bounded by twice the discovery connect plus read timeout (one discovery and one JWK set
     * request), plus a small margin; a load that takes longer counts as failed.
     *
     * @param issuer a trusted issuer, normally from {@link #resolve(String)}
     * @return the issuer's decoder
     * @throws JwtException when the discovery document or the JWK set cannot be loaded in time, or the
     *         issuer failed recently and is not retried yet
     * @throws IllegalArgumentException when the issuer URL is not the issuer URL of its realm
     */
    public JwtDecoder decoderFor(TrustedIssuer issuer) {
        Objects.requireNonNull(issuer, "issuer");
        String issuerUrl = properties.issuerFor(issuer.realm());
        if (!issuerUrl.equals(issuer.issuer())) {
            throw new IllegalArgumentException("Issuer URL does not match its realm");
        }
        throwIfRecentlyFailed(issuerUrl);
        CompletableFuture<JwtDecoder> decoder = decoders.get(issuerUrl, this::loadDecoder);
        try {
            return decoder.get(loadTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JwtException("Interrupted while loading the signing keys of issuer " + issuerUrl, e);
        } catch (TimeoutException e) {
            recentFailures.put(issuerUrl, Boolean.TRUE);
            log.warn("Loading signing keys of issuer {} did not complete within {} ms", issuerUrl, loadTimeout.toMillis());
            removeFailedLoad(issuerUrl, decoder);
            decoder.cancel(true);
            throw new JwtException("Signing keys of issuer " + issuerUrl + " could not be loaded in time");
        } catch (CancellationException e) {
            removeFailedLoad(issuerUrl, decoder);
            throw new JwtException("Signing keys of issuer " + issuerUrl + " could not be loaded", e);
        } catch (ExecutionException e) {
            removeFailedLoad(issuerUrl, decoder);
            if (e.getCause() instanceof JwtException jwtException) {
                throw jwtException;
            }
            throw new JwtException("Signing keys of issuer " + issuerUrl + " could not be loaded", e.getCause());
        }
    }

    /**
     * Returns a decoder that accepts tokens of the allowed issuer types only, without extra validation.
     *
     * @param allowed the accepted issuer types
     * @return a decoder that selects the issuer's cached decoder from the unverified {@code iss}
     * @see #issuerSelectingDecoder(Set, Function)
     */
    public JwtDecoder issuerSelectingDecoder(Set<IssuerType> allowed) {
        return issuerSelectingDecoder(allowed, issuer -> NO_ADDITIONAL_VALIDATION);
    }

    /**
     * Returns a decoder that accepts tokens of the allowed issuer types only and applies additional,
     * per-issuer validation.
     * <p>
     * The decoder parses the token without verifying it and reads {@code iss}. A malformed or unsigned
     * token (including {@code alg: none}), a missing {@code iss}, an issuer that does not resolve or an
     * issuer type outside {@code allowed} is rejected with a {@link BadJwtException} before any network
     * request. Otherwise the token is decoded by the issuer's cached decoder (see {@link #decoderFor}) and
     * then checked by the validator that {@code additionalValidators} returns for the issuer; a failure
     * there is a {@link JwtValidationException}.
     *
     * @param allowed the accepted issuer types
     * @param additionalValidators returns the extra validator for a resolved issuer, must not return {@code null}
     * @return a decoder that selects the issuer's cached decoder from the unverified {@code iss}
     */
    public JwtDecoder issuerSelectingDecoder(Set<IssuerType> allowed,
            Function<TrustedIssuer, OAuth2TokenValidator<Jwt>> additionalValidators) {
        Set<IssuerType> allowedTypes = Set.copyOf(allowed);
        Objects.requireNonNull(additionalValidators, "additionalValidators");
        return token -> {
            TrustedIssuer issuer = selectIssuer(token, allowedTypes);
            Jwt jwt = decoderFor(issuer).decode(token);
            OAuth2TokenValidator<Jwt> validator = Objects.requireNonNull(additionalValidators.apply(issuer),
                    "additional validator");
            OAuth2TokenValidatorResult result = validator.validate(jwt);
            if (result.hasErrors()) {
                throw new JwtValidationException("Token rejected by issuer validation", result.getErrors());
            }
            return jwt;
        };
    }

    /**
     * Stops the background threads used for decoder loading and JWK set refresh-ahead.
     */
    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }

    private TrustedIssuer selectIssuer(String token, Set<IssuerType> allowed) {
        if (token == null || token.isBlank()) {
            throw new BadJwtException("Missing token");
        }
        JWT parsed;
        try {
            parsed = JWTParser.parse(token);
        } catch (ParseException | RuntimeException e) {
            throw new BadJwtException("Malformed token");
        }
        if (!(parsed instanceof SignedJWT)) {
            throw new BadJwtException("Unsupported token type");
        }
        String unverifiedIss;
        try {
            unverifiedIss = parsed.getJWTClaimsSet().getIssuer();
        } catch (ParseException | RuntimeException e) {
            throw new BadJwtException("Malformed token");
        }
        if (unverifiedIss == null) {
            throw new BadJwtException("Token has no issuer");
        }
        Optional<TrustedIssuer> issuer;
        try {
            issuer = resolve(unverifiedIss);
        } catch (RuntimeException e) {
            log.warn("Issuer resolution failed: {}", e.getClass().getSimpleName());
            throw new JwtException("Issuer resolution failed", e);
        }
        if (issuer.isEmpty()) {
            log.debug("Rejected token from untrusted issuer {}", sanitize(unverifiedIss));
            throw new BadJwtException("Untrusted issuer");
        }
        if (!allowed.contains(issuer.get().type())) {
            log.debug("Rejected token from issuer {} of type {}", issuer.get().issuer(), issuer.get().type());
            throw new BadJwtException("Issuer not accepted");
        }
        return issuer.get();
    }

    private boolean isTenantRealmCandidate(String realm) {
        return KeycloakTrustProperties.REALM_PATTERN.matcher(realm).matches()
                && !".".equals(realm)
                && !"..".equals(realm)
                && !reservedRealms.contains(realm.toLowerCase(Locale.ROOT));
    }

    private void removeFailedLoad(String issuerUrl, CompletableFuture<JwtDecoder> decoder) {
        // Caffeine also drops failed loads, but asynchronously: remove it now so that the next call after
        // the retry interval loads again instead of seeing this failure.
        decoders.asMap().remove(issuerUrl, decoder);
    }

    private void throwIfRecentlyFailed(String issuerUrl) {
        if (recentFailures.getIfPresent(issuerUrl) != null) {
            throw new JwtException("Signing keys of issuer " + issuerUrl + " are unavailable, retry later");
        }
    }

    private JwtDecoder loadDecoder(String issuerUrl) {
        // The cache runs this at most once per issuer at a time. Checked again here: another load may have
        // failed while this caller was waiting.
        throwIfRecentlyFailed(issuerUrl);
        try {
            DiscoveryDocument document = discoveryClient.fetch(issuerUrl);
            JWKSource<SecurityContext> jwkSource = jwkSource(jwksUrl(document.jwksUri()));
            // Load the JWK set now, so that an unreachable JWKS fails the load instead of a cached decoder.
            jwkSource.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
            log.info("Loaded signing keys of issuer {}", issuerUrl);
            return decoder(issuerUrl, jwkSource);
        } catch (KeySourceException | RuntimeException e) {
            recentFailures.put(issuerUrl, Boolean.TRUE);
            log.warn("Loading signing keys of issuer {} failed: {}", issuerUrl, e.getMessage());
            throw new JwtException("Signing keys of issuer " + issuerUrl + " could not be loaded", e);
        }
    }

    private JWKSource<SecurityContext> jwkSource(URL jwksUrl) {
        // Built by hand instead of with JWKSourceBuilder, so that refresh-ahead runs on the registry's shared
        // executor instead of one new thread per issuer. Outermost first: refresh-ahead cache, rate limiter
        // (which also bounds the refetches triggered by an unknown kid), HTTP source.
        JWKSetSource<SecurityContext> source = new URLBasedJWKSetSource<>(jwksUrl, jwkSetRetriever);
        source = new RateLimitedJWKSetSource<>(source, settings.jwksRateLimitInterval().toMillis(), null);
        source = new RefreshAheadCachingJWKSetSource<>(source,
                settings.jwksCacheTimeToLive().toMillis(),
                settings.jwksCacheRefreshTimeout().toMillis(),
                settings.jwksRefreshAheadTime().toMillis(),
                false,
                executor,
                false,
                null);
        return new JWKSetBasedJWKSource<>(source);
    }

    private static NimbusJwtDecoder decoder(String issuerUrl, JWKSource<SecurityContext> jwkSource) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(SIGNATURE_ALGORITHMS, jwkSource));
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT, null));
        // Claims are validated by the Spring validators below, as NimbusJwtDecoder's own builders do.
        processor.setJWTClaimsSetVerifier((claims, context) -> {
        });
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUrl));
        return decoder;
    }

    private static URL jwksUrl(String jwksUri) {
        try {
            URI uri = URI.create(jwksUri);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("jwks_uri must be an absolute http or https URL");
            }
            return uri.toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("jwks_uri is not a valid URL", e);
        }
    }

    private static ExecutorService newExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "trusted-issuer-registry-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(EXECUTOR_THREADS, EXECUTOR_THREADS, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), threadFactory);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static String sanitize(String value) {
        String truncated = value.length() > MAX_LOGGED_ISSUER_LENGTH ? value.substring(0, MAX_LOGGED_ISSUER_LENGTH) : value;
        return truncated.replaceAll("\\p{Cntrl}", "_");
    }
}
