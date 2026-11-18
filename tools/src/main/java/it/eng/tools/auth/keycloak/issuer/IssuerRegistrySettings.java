package it.eng.tools.auth.keycloak.issuer;

import java.time.Duration;
import java.util.Objects;

/**
 * Cache and refresh settings of {@link TrustedIssuerRegistry}. Production code uses {@link #defaults()};
 * tests pass shorter values.
 *
 * @param decoderCacheMaximumSize maximum number of cached issuer decoders
 * @param decoderCacheExpireAfterAccess a decoder that is not used for this long is evicted
 * @param failureRetryInterval after a failed discovery or JWKS load, the issuer is not retried for this long
 * @param jwksCacheTimeToLive how long a loaded JWK set is used before it is loaded again
 * @param jwksCacheRefreshTimeout how long concurrent requests wait for a JWK set refresh in progress
 * @param jwksRefreshAheadTime how long before expiry a request triggers a background refresh of the JWK set
 * @param jwksRateLimitInterval at most two JWK set requests per issuer within this interval, which also bounds
 *        refetches triggered by an unknown {@code kid}
 * @param jwksSizeLimit maximum size of a JWK set response, in bytes
 */
record IssuerRegistrySettings(
        int decoderCacheMaximumSize,
        Duration decoderCacheExpireAfterAccess,
        Duration failureRetryInterval,
        Duration jwksCacheTimeToLive,
        Duration jwksCacheRefreshTimeout,
        Duration jwksRefreshAheadTime,
        Duration jwksRateLimitInterval,
        int jwksSizeLimit) {

    /**
     * Validates the settings, including the constraints the Nimbus JWK set sources impose.
     *
     * @throws IllegalArgumentException when a value is not positive or the JWK set timings are inconsistent
     */
    IssuerRegistrySettings {
        requirePositive(decoderCacheExpireAfterAccess, "decoderCacheExpireAfterAccess");
        requirePositive(failureRetryInterval, "failureRetryInterval");
        requirePositive(jwksCacheTimeToLive, "jwksCacheTimeToLive");
        requirePositive(jwksCacheRefreshTimeout, "jwksCacheRefreshTimeout");
        requirePositive(jwksRefreshAheadTime, "jwksRefreshAheadTime");
        requirePositive(jwksRateLimitInterval, "jwksRateLimitInterval");
        if (decoderCacheMaximumSize <= 0 || jwksSizeLimit <= 0) {
            throw new IllegalArgumentException("decoderCacheMaximumSize and jwksSizeLimit must be positive");
        }
        if (jwksRefreshAheadTime.plus(jwksCacheRefreshTimeout).compareTo(jwksCacheTimeToLive) > 0) {
            throw new IllegalArgumentException(
                    "jwksRefreshAheadTime plus jwksCacheRefreshTimeout must not exceed jwksCacheTimeToLive");
        }
        if (jwksRateLimitInterval.compareTo(jwksCacheTimeToLive) >= 0) {
            throw new IllegalArgumentException("jwksRateLimitInterval must be shorter than jwksCacheTimeToLive");
        }
    }

    /**
     * Returns the production settings: up to 1000 decoders evicted after 1 hour without use, a 5 second
     * failure retry interval, a 5 minute JWK set cache refreshed 30 seconds ahead of expiry (15 second
     * refresh timeout), at most two JWK set requests per 30 seconds and a 64 KiB response limit.
     *
     * @return the default settings
     */
    static IssuerRegistrySettings defaults() {
        return new IssuerRegistrySettings(
                1000,
                Duration.ofHours(1),
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                Duration.ofSeconds(15),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                64 * 1024);
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
