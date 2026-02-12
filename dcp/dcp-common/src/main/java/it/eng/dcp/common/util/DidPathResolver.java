package it.eng.dcp.common.util;

import it.eng.dcp.common.model.DCPConstants;
import lombok.extern.slf4j.Slf4j;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for resolving DID path segments to controller endpoints.
 * Implements W3C DID:web specification for path-based resolution.
 *
 * <p>According to W3C DID:web specification:
 * <ul>
 *   <li>{@code did:web:localhost%3A8080} maps to {@code https://localhost:8080/.well-known/did.json}</li>
 *   <li>{@code did:web:localhost%3A8080:issuer} maps to {@code https://localhost:8080/issuer/.well-known/did.json}</li>
 *   <li>{@code did:web:localhost%3A8080:api:v1:issuer} maps to {@code https://localhost:8080/api/v1/issuer/.well-known/did.json}</li>
 * </ul>
 */
@Slf4j
public final class DidPathResolver {

    private DidPathResolver() {
        // Utility class
    }

    /**
     * Extracts path segments from a DID.
     *
     * <p>Examples:
     * <ul>
     *   <li>"did:web:localhost%3A8080" → []</li>
     *   <li>"did:web:localhost%3A8083:issuer" → ["issuer"]</li>
     *   <li>"did:web:localhost%3A8083:api:v1:issuer" → ["api", "v1", "issuer"]</li>
     * </ul>
     *
     * @param did the DID string
     * @return list of path segments (empty if no path)
     */
    public static List<String> extractPathSegments(String did) {
        if (did == null || did.isBlank()) {
            log.warn("DID is null or blank");
            return Collections.emptyList();
        }

        if (!did.startsWith(DCPConstants.DID_WEB_PREFIX)) {
            log.warn("DID does not start with 'did:web:' prefix: {}", did);
            return Collections.emptyList();
        }

        // Remove "did:web:" prefix
        String remainder = did.substring(DCPConstants.DID_WEB_PREFIX.length());

        // Split by ":"
        String[] parts = remainder.split(":");

        if (parts.length <= 1) {
            // Only host, no path segments
            return Collections.emptyList();
        }

        // First part is host (potentially URL-encoded), rest are path segments
        List<String> pathSegments = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String segment = parts[i];
            if (segment != null && !segment.isBlank()) {
                // Decode URL-encoded segments if needed
                String decoded = URLDecoder.decode(segment, StandardCharsets.UTF_8);
                pathSegments.add(decoded);
            }
        }

        log.debug("Extracted {} path segments from DID: {}", pathSegments.size(), did);
        return pathSegments;
    }

    /**
     * Builds W3C-compliant endpoint path from path segments.
     *
     * <p>Examples:
     * <ul>
     *   <li>[] → "/.well-known/did.json"</li>
     *   <li>["issuer"] → "/issuer/.well-known/did.json"</li>
     *   <li>["api", "v1", "issuer"] → "/api/v1/issuer/.well-known/did.json"</li>
     * </ul>
     *
     * @param pathSegments list of path segments
     * @return W3C-compliant endpoint path
     */
    public static String buildWellKnownEndpointPath(List<String> pathSegments) {
        if (pathSegments == null || pathSegments.isEmpty()) {
            return DCPConstants.WELL_KNOWN_DID_PATH;
        }

        // Join segments with "/"
        String pathPrefix = "/" + String.join("/", pathSegments);
        return pathPrefix + DCPConstants.WELL_KNOWN_DID_PATH;
    }

    /**
     * Builds legacy convenience endpoint path from path segments.
     * Uses only the last segment (typically the role name).
     *
     * <p>Examples:
     * <ul>
     *   <li>["issuer"] → "/issuer/did.json"</li>
     *   <li>["api", "v1", "issuer"] → "/issuer/did.json"</li>
     * </ul>
     *
     * @param pathSegments list of path segments
     * @return legacy endpoint path, or null if no segments
     */
    public static String buildLegacyEndpointPath(List<String> pathSegments) {
        if (pathSegments == null || pathSegments.isEmpty()) {
            return null;
        }

        // Use last segment (typically role name)
        String roleName = pathSegments.get(pathSegments.size() - 1);
        return "/" + roleName + DCPConstants.LEGACY_DID_FILENAME;
    }

    /**
     * Gets all endpoint paths for a DID (W3C standard, legacy, and fallback).
     *
     * <p>Example for "did:web:localhost:8080:issuer":
     * <ul>
     *   <li>/issuer/.well-known/did.json (W3C with path)</li>
     *   <li>/issuer/did.json (legacy convenience)</li>
     *   <li>/.well-known/did.json (fallback)</li>
     * </ul>
     *
     * @param did the DID string
     * @return list of endpoint paths (ordered by priority)
     */
    public static List<String> getAllEndpointPaths(String did) {
        List<String> endpoints = new ArrayList<>();
        List<String> pathSegments = extractPathSegments(did);

        if (!pathSegments.isEmpty()) {
            // W3C standard with path
            endpoints.add(buildWellKnownEndpointPath(pathSegments));

            // Legacy convenience endpoint
            String legacyPath = buildLegacyEndpointPath(pathSegments);
            if (legacyPath != null) {
                endpoints.add(legacyPath);
            }
        }

        // Always include standard well-known as fallback
        endpoints.add(DCPConstants.WELL_KNOWN_DID_PATH);

        return endpoints;
    }

    /**
     * Extracts the role name from path segments (last segment).
     *
     * @param pathSegments list of path segments
     * @return role name, or "connector" if no segments
     */
    public static String extractRoleName(List<String> pathSegments) {
        if (pathSegments == null || pathSegments.isEmpty()) {
            return "connector";
        }
        return pathSegments.get(pathSegments.size() - 1);
    }
}
