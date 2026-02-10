package it.eng.dcp.holder.service;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.DCPConstants;
import it.eng.dcp.common.model.PresentationQueryMessage;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.holder.model.VerifiableCredential;
import it.eng.dcp.holder.model.VerifiablePresentation;
import it.eng.dcp.holder.repository.VerifiableCredentialRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service orchestrating presentation creation: fetch credentials, group by profile, build homogenous VPs and sign them.
 */
@Service
@Slf4j
public class PresentationService {

    private final VerifiableCredentialRepository credentialRepository;
    private final VerifiablePresentationSigner vpSigner;

    @Autowired
    public PresentationService(VerifiableCredentialRepository credentialRepository, VerifiablePresentationSigner vpSigner) {
        this.credentialRepository = credentialRepository;
        this.vpSigner = vpSigner;
    }

    /**
     * Create a PresentationResponseMessage with access token scope enforcement.
     *
     * <p>Per DCP Protocol v1.0 Section 5.1, the access token scopes define which
     * credentials the verifier is authorized to access. The verifier's requested
     * scopes must be a subset of (or equal to) the authorized scopes.
     *
     * <p>Scope Resolution Rules:
     * <ol>
     *   <li>If no authorized scopes → use requested scopes (no restriction)</li>
     *   <li>If no requested scopes → use authorized scopes (all authorized)</li>
     *   <li>If both exist → intersection (only scopes that are both requested AND authorized)</li>
     * </ol>
     *
     * <p>Scope Format: Per DCP spec Section 5.4.1.2, scopes use the format [alias]:[discriminator]:
     * <ul>
     *   <li>org.eclipse.dspace.dcp.vc.type:MembershipCredential - access by credential type</li>
     *   <li>org.eclipse.dspace.dcp.vc.id:uuid - access by credential ID</li>
     *   <li>MembershipCredential - simple format (backward compatibility)</li>
     * </ul>
     *
     * @param query the presentation query message containing requested scope
     * @param accessTokenClaims the validated access token claims with authorized scopes
     * @return the presentation response with only authorized credentials
     */
    public PresentationResponseMessage createPresentation(PresentationQueryMessage query,
                                                           JWTClaimsSet accessTokenClaims) {
        // Extract authorized scopes from access token
        List<String> authorizedScopes = extractScopesFromClaims(accessTokenClaims);
        List<String> requestedScopes = query.getScope();

        // Determine effective scopes (intersection of requested and authorized)
        List<String> effectiveScopes = determineEffectiveScopes(requestedScopes, authorizedScopes);

        log.info("Scope enforcement - requested: {}, authorized: {}, effective: {}",
                requestedScopes, authorizedScopes, effectiveScopes);

        // Fetch credentials based on effective scopes - now handling both type and id aliases
        List<VerifiableCredential> fetched;
        if (effectiveScopes == null || effectiveScopes.isEmpty()) {
            // No scope restrictions - fetch all (only if no authorized scopes were specified)
            if (authorizedScopes == null || authorizedScopes.isEmpty()) {
                log.debug("No scope restrictions - fetching all credentials");
                fetched = credentialRepository.findAll();
            } else {
                // Authorized scopes exist but effective is empty = no matching credentials
                log.warn("No credentials match the intersection of requested and authorized scopes");
                fetched = List.of();
            }
        } else {
            log.debug("Fetching credentials for scopes: {}", effectiveScopes);
            fetched = fetchCredentialsByScopes(effectiveScopes);
        }

        // Rest of presentation creation logic (grouping and signing)
        return buildPresentationResponse(fetched);
    }

    /**
     * Extract scope claim from JWT claims.
     * Handles both space-delimited string and array formats per OAuth 2.0 conventions.
     *
     * @param claims The JWT claims containing scope claim
     * @return List of scope strings, or null if no scope claim present
     */
    private List<String> extractScopesFromClaims(JWTClaimsSet claims) {
        if (claims == null) {
            return null;
        }

        try {
            Object scopeClaim = claims.getClaim("scope");
            if (scopeClaim == null) {
                return null;
            }

            if (scopeClaim instanceof String) {
                // Space-delimited string format (OAuth 2.0 style)
                String scopeStr = (String) scopeClaim;
                if (scopeStr.isBlank()) {
                    return null;
                }
                return Arrays.asList(scopeStr.split("\\s+"));
            } else if (scopeClaim instanceof List) {
                // Array format
                @SuppressWarnings("unchecked")
                List<String> scopeList = (List<String>) scopeClaim;
                return scopeList.isEmpty() ? null : scopeList;
            }
        } catch (Exception e) {
            log.warn("Failed to extract scopes from token claims: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Determine effective scopes based on requested and authorized scopes.
     *
     * <p>Rules:
     * <ol>
     *   <li>If no authorized scopes → use requested scopes (no restriction)</li>
     *   <li>If no requested scopes → use authorized scopes (all authorized)</li>
     *   <li>If both exist → intersection (only scopes that are both requested AND authorized)</li>
     * </ol>
     *
     * <p>Scope matching is done by normalizing scopes to their discriminators:
     * <ul>
     *   <li>org.eclipse.dspace.dcp.vc.type:MembershipCredential → MembershipCredential</li>
     *   <li>org.eclipse.dspace.dcp.vc.id:uuid-123 → uuid-123</li>
     *   <li>MembershipCredential → MembershipCredential</li>
     * </ul>
     * This ensures that scopes with different formats but same discriminator are matched.
     *
     * @param requested The scopes requested by the verifier in the query
     * @param authorized The scopes authorized in the access token
     * @return The effective scopes to use for credential fetching
     */
    private List<String> determineEffectiveScopes(List<String> requested, List<String> authorized) {
        // No authorization restriction
        if (authorized == null || authorized.isEmpty()) {
            return requested;
        }

        // No specific request - use all authorized
        if (requested == null || requested.isEmpty()) {
            return authorized;
        }

        // Normalize authorized scopes to discriminators for comparison
        // Create a map: normalized discriminator → original scope
        Map<String, String> authorizedNormalized = authorized.stream()
                .collect(Collectors.toMap(
                        scope -> parseScope(scope).getDiscriminator(),  // key: discriminator
                        scope -> scope,                                  // value: original scope
                        (existing, duplicate) -> existing                // keep first if duplicates
                ));

        // Find intersection by normalizing requested scopes and checking if discriminator is in authorized
        List<String> intersection = requested.stream()
                .filter(requestedScope -> {
                    String normalizedRequested = parseScope(requestedScope).getDiscriminator();
                    return authorizedNormalized.containsKey(normalizedRequested);
                })
                .collect(Collectors.toList());

        if (intersection.isEmpty()) {
            log.warn("Scope mismatch: verifier requested {} but only {} authorized. Returning empty result.",
                    requested, authorized);
        } else {
            log.debug("Effective scopes after intersection: {} (from requested: {}, authorized: {})",
                    intersection, requested, authorized);
        }

        return intersection;
    }

    /**
     * Fetch credentials based on scopes, handling both type and id aliases.
     *
     * <p>Per DCP Protocol v1.0 Section 5.4.1.2, two standard aliases MUST be supported:
     * <ul>
     *   <li>{@link DCPConstants#SCOPE_ALIAS_VC_TYPE} - query by credential type</li>
     *   <li>{@link DCPConstants#SCOPE_ALIAS_VC_ID} - query by credential ID</li>
     * </ul>
     *
     * <p>This method separates scopes into type-based and id-based queries,
     * executes both queries if needed, and combines the results.
     *
     * @param scopes The effective scopes to query
     * @return Combined list of credentials matching the scopes
     */
    private List<VerifiableCredential> fetchCredentialsByScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }

        List<String> credentialTypes = new ArrayList<>();
        List<String> credentialIds = new ArrayList<>();

        // Separate scopes by alias type
        for (String scope : scopes) {
            ParsedScope parsed = parseScope(scope);
            if (parsed.isIdAlias()) {
                credentialIds.add(parsed.getDiscriminator());
            } else {
                // Default to type-based query (includes simple format and vc.type alias)
                credentialTypes.add(parsed.getDiscriminator());
            }
        }

        List<VerifiableCredential> results = new ArrayList<>();

        // Query by types if present
        if (!credentialTypes.isEmpty()) {
            log.debug("Querying credentials by types: {}", credentialTypes);
            results.addAll(credentialRepository.findByCredentialTypeIn(credentialTypes));
        }

        // Query by IDs if present
        if (!credentialIds.isEmpty()) {
            log.debug("Querying credentials by IDs: {}", credentialIds);
            results.addAll(credentialRepository.findByIdIn(credentialIds));
        }

        return results;
    }

    /**
     * Parse DCP-formatted scopes to extract credential types or IDs.
     *
     * <p>Per DCP Protocol v1.0 Section 5.4.1.2:
     * <ul>
     *   <li>Scope format: [alias]:[discriminator]</li>
     *   <li>"The [alias] value MAY be implementation-specific" (spec line 907)</li>
     *   <li>Standard aliases that MUST be supported: org.eclipse.dspace.dcp.vc.type and org.eclipse.dspace.dcp.vc.id</li>
     * </ul>
     *
     * <p>This implementation uses <b>generic parsing</b> to support:
     * <ul>
     *   <li><b>Any alias format:</b> prefix:discriminator → extracts discriminator</li>
     *   <li><b>Simple format:</b> credential → returns as-is (backward compatibility)</li>
     * </ul>
     *
     * <p>Examples:
     * <ul>
     *   <li>org.eclipse.dspace.dcp.vc.type:MembershipCredential → MembershipCredential</li>
     *   <li>org.eclipse.dspace.dcp.vc.id:uuid-123 → uuid-123</li>
     *   <li>custom.org.prefix:CustomType → CustomType</li>
     *   <li>MembershipCredential → MembershipCredential (simple)</li>
     * </ul>
     *
     * @param scopes The scopes to parse (may be null)
     * @return List of credential types or IDs, or null if input is null
     * @deprecated Use {@link #fetchCredentialsByScopes(List)} which properly handles type vs id aliases
     */
    @Deprecated
    private List<String> parseScopesToCredentialTypes(List<String> scopes) {
        if (scopes == null) {
            return null;
        }

        return scopes.stream()
                .map(this::parseSingleScope)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Parse a single scope string to extract alias and discriminator.
     *
     * <p>Per DCP spec Section 5.4.1.2, scope format is [alias]:[discriminator].
     * Two standard aliases MUST be supported:
     * <ul>
     *   <li>{@link DCPConstants#SCOPE_ALIAS_VC_TYPE} - search by credential type</li>
     *   <li>{@link DCPConstants#SCOPE_ALIAS_VC_ID} - search by credential ID</li>
     * </ul>
     *
     * <p><b>Action Suffix Handling:</b>
     * Action suffixes (e.g., :read, :write) are NOT part of the DCP spec but are
     * supported for downstream policy evaluation (e.g., Permission/Action constraints).
     * <ul>
     *   <li>For <b>querying</b>: action suffixes are stripped (query by base type/id)</li>
     *   <li>For <b>authorization</b>: action suffixes should be preserved for policy evaluation</li>
     *   <li>If <b>no action</b> specified: all actions are implicitly allowed</li>
     * </ul>
     *
     * <p>Examples:
     * <ul>
     *   <li>org.eclipse.dspace.dcp.vc.type:MembershipCredential → query "MembershipCredential"</li>
     *   <li>org.eclipse.dspace.dcp.vc.type:MembershipCredential:read → query "MembershipCredential" (strip :read for query)</li>
     *   <li>org.eclipse.dspace.dcp.vc.id:uuid-1234 → query "uuid-1234"</li>
     *   <li>org.eclipse.dspace.dcp.vc.id:uuid-1234:write → query "uuid-1234" (strip :write for query)</li>
     * </ul>
     *
     * @param scope The scope string to parse
     * @return ParsedScope object containing alias type, base discriminator, and optional action
     */
    private ParsedScope parseScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return new ParsedScope(false, "", null);
        }

        // Check if scope contains colon (aliased format)
        int firstColon = scope.indexOf(':');
        if (firstColon == -1) {
            // Simple format (no alias) - treat as type query
            return new ParsedScope(false, scope, null);
        }

        // Extract alias and discriminator
        String alias = scope.substring(0, firstColon);
        String discriminator = scope.substring(firstColon + 1);

        // Check if this is an ID alias
        boolean isIdAlias = DCPConstants.SCOPE_ALIAS_VC_ID.equals(alias);

        // Extract action suffix if present
        String action = extractActionSuffix(discriminator);
        String baseDiscriminator = action != null ? stripActionSuffix(discriminator) : discriminator;

        return new ParsedScope(isIdAlias, baseDiscriminator, action);
    }

    /**
     * Extract action suffix from a discriminator if present.
     *
     * @param discriminator The discriminator that may have an action suffix
     * @return The action suffix (e.g., "read", "write") or null if not present
     */
    private String extractActionSuffix(String discriminator) {
        if (discriminator == null || discriminator.isBlank()) {
            return null;
        }

        // Common action suffixes (for downstream policy evaluation)
        String[] actionSuffixes = {":read", ":write", ":delete", ":admin", ":execute"};

        for (String suffix : actionSuffixes) {
            if (discriminator.endsWith(suffix)) {
                return suffix.substring(1); // Remove leading colon
            }
        }

        return null;
    }

    /**
     * Strip action suffix from a discriminator.
     * Used to get the base type/id for repository queries.
     *
     * @param discriminator The discriminator with action suffix
     * @return The discriminator without action suffix
     */
    private String stripActionSuffix(String discriminator) {
        if (discriminator == null || discriminator.isBlank()) {
            return discriminator;
        }

        // Common action suffixes
        String[] actionSuffixes = {":read", ":write", ":delete", ":admin", ":execute"};

        for (String suffix : actionSuffixes) {
            if (discriminator.endsWith(suffix)) {
                return discriminator.substring(0, discriminator.length() - suffix.length());
            }
        }

        return discriminator;
    }

    /**
     * Legacy method for backward compatibility.
     * Parses a single scope to extract discriminator, stripping action suffixes.
     *
     * @param scope The scope string to parse
     * @return The extracted discriminator without action suffix
     * @deprecated Use {@link #parseScope(String)} which properly distinguishes between type and id aliases
     */
    @Deprecated
    private String parseSingleScope(String scope) {
        ParsedScope parsed = parseScope(scope);
        return parsed.getDiscriminator();
    }

    /**
     * Parsed scope representation.
     * Contains information about whether this is an ID-based or type-based query,
     * the base discriminator (without action suffix) for querying, and the optional
     * action for downstream policy evaluation.
     */
    private static class ParsedScope {
        private final boolean isIdAlias;
        private final String discriminator; // Base discriminator without action suffix
        private final String action;        // Optional action suffix (e.g., "read", "write")

        public ParsedScope(boolean isIdAlias, String discriminator, String action) {
            this.isIdAlias = isIdAlias;
            this.discriminator = discriminator;
            this.action = action;
        }

        public boolean isIdAlias() {
            return isIdAlias;
        }

        public String getDiscriminator() {
            return discriminator;
        }

        public String getAction() {
            return action;
        }

        public boolean hasAction() {
            return action != null && !action.isBlank();
        }
    }

    /**
     * Build presentation response from fetched credentials.
     * Groups credentials by profile and signs each group.
     *
     * @param fetched The credentials to include in presentations
     * @return The presentation response message
     */
    private PresentationResponseMessage buildPresentationResponse(List<VerifiableCredential> fetched) {

        // Group by profileId (null profile grouped under default VC20_BSSL_JWT)
        Map<ProfileId, List<VerifiableCredential>> groups = fetched.stream()
                .collect(Collectors.groupingBy(vc ->
                    vc.getProfileId() != null ? vc.getProfileId() : ProfileId.VC20_BSSL_JWT));

        List<Object> signedPresentations = new ArrayList<>();

        for (Map.Entry<ProfileId, List<VerifiableCredential>> e : groups.entrySet()) {
            List<VerifiableCredential> groupCreds = e.getValue();
            if (groupCreds.isEmpty()) continue;

            // Collect credential IDs for reference
            List<String> credentialIds = groupCreds.stream()
                    .map(VerifiableCredential::getId)
                    .collect(Collectors.toList());

            // Collect full credentials for embedding in VP per DCP spec Section 5.4.2
            // This allows the verifier to validate VC signatures without additional fetch
            List<Object> fullCredentials = groupCreds.stream()
                    .map(vc -> {
                        // If VC already has JWT representation, use it
                        if (vc.getJwtRepresentation() != null && !vc.getJwtRepresentation().isBlank()) {
                            return vc.getJwtRepresentation();
                        }
                        // Otherwise, if credential JSON is available, use it
                        if (vc.getCredential() != null) {
                            return vc.getCredential();
                        }
                        // Fallback to credential ID
                        return vc.getId();
                    })
                    .collect(Collectors.toList());

            ProfileId profile = e.getKey();
            VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                    .holderDid(groupCreds.get(0).getHolderDid())
                    .credentialIds(credentialIds)  // Keep IDs for reference/tracking
                    .credentials(fullCredentials)   // Embed full VCs per DCP spec
                    .profileId(profile)
                    .build();

            // All official DCP profiles use JWT format
            String format = profile.getFormat();
            Object signed = vpSigner.sign(vp, format);
            signedPresentations.add(signed);
        }

        PresentationResponseMessage.Builder respBuilder = PresentationResponseMessage.Builder.newInstance();
        respBuilder.presentation(signedPresentations);
        return respBuilder.build();
    }

}
