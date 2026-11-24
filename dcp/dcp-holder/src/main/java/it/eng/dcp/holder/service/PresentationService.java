package it.eng.dcp.holder.service;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.DCPConstants;
import it.eng.dcp.common.model.PresentationQueryMessage;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.util.DcpScope;
import it.eng.dcp.common.util.DidUrlConverter;
import it.eng.dcp.holder.model.VerifiableCredential;
import it.eng.dcp.holder.model.VerifiablePresentation;
import it.eng.dcp.holder.repository.VerifiableCredentialRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service orchestrating presentation creation: fetch credentials, group by profile, build homogenous VPs and sign them.
 */
@Service
@Slf4j
public class PresentationService {

    /** Host and port of a decoded {@code did:web} DID, for building the percent-encoded form. */
    private static final Pattern ENCODED_PORT = Pattern.compile("^(did:web:[^:]+):(\\d+)(?=:|$)");

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
     * <p>Per DCP v1.0.1 §5.3.1 and §5.4.1.2, the scopes of the validated access token define which
     * credentials the verifier is authorized to access. The effective scopes are the requested scopes
     * that the access token authorizes; a requested scope it does not authorize is dropped, and the
     * response simply contains fewer presentations (DCP v1.0.1 §5.4.1.2).
     *
     * <p>Scope Resolution Rules:
     * <ol>
     *   <li>No authorized scopes → no credentials (an access token without scopes authorizes nothing)</li>
     *   <li>No requested scopes → all authorized scopes</li>
     *   <li>Otherwise → the requested scopes that are authorized. Scopes match on their alias kind
     *       (credential ID or credential type) and their discriminator.</li>
     * </ol>
     *
     * <p>Only credentials of {@code holderDid}, the holder that owns this Credential Service, are returned.
     *
     * <p>Scope Format: Per DCP spec Section 5.4.1.2, scopes use the format [alias]:[discriminator]:
     * <ul>
     *   <li>org.eclipse.dspace.dcp.vc.type:MembershipCredential - access by credential type</li>
     *   <li>org.eclipse.dspace.dcp.vc.id:uuid - access by credential ID</li>
     *   <li>MembershipCredential - unqualified format (deprecated, accepted with a warning)</li>
     * </ul>
     *
     * @param query the presentation query message containing requested scope
     * @param holderDid the DID of the holder that owns this Credential Service
     * @param accessTokenClaims the validated access token claims with authorized scopes
     * @param participant the participant that owns this Credential Service and signs the presentations
     * @return the presentation response with only authorized credentials of the holder
     */
    public PresentationResponseMessage createPresentation(PresentationQueryMessage query,
                                                           String holderDid,
                                                           JWTClaimsSet accessTokenClaims,
                                                           ParticipantContext participant) {
        List<String> authorizedScopes = extractScopesFromClaims(accessTokenClaims);
        List<String> requestedScopes = query.getScope();
        warnOnUnqualifiedScopes(requestedScopes);
        warnOnUnqualifiedScopes(authorizedScopes);

        List<String> effectiveScopes = determineEffectiveScopes(requestedScopes, authorizedScopes);

        log.info("Scope enforcement - requested: {}, authorized: {}, effective: {}",
                requestedScopes, authorizedScopes, effectiveScopes);

        List<VerifiableCredential> fetched = fetchCredentialsByScopes(holderDid, effectiveScopes);
        return buildPresentationResponse(fetched, participant);
    }

    /**
     * Extract scope claim from JWT claims.
     * Handles both space-delimited string and array formats per OAuth 2.0 conventions.
     *
     * @param claims The JWT claims containing scope claim
     * @return List of scope strings, empty if no scope claim present
     */
    private List<String> extractScopesFromClaims(JWTClaimsSet claims) {
        if (claims == null) {
            return List.of();
        }

        Object scopeClaim = claims.getClaim("scope");
        if (scopeClaim instanceof String scopeStr) {
            // Space-delimited string format (OAuth 2.0 style)
            return scopeStr.isBlank() ? List.of() : List.of(scopeStr.trim().split("\\s+"));
        }
        if (scopeClaim instanceof List<?> scopeList) {
            return scopeList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(scope -> !scope.isBlank())
                    .toList();
        }
        if (scopeClaim != null) {
            log.warn("Ignoring access token scope claim of unexpected type: {}", scopeClaim.getClass().getSimpleName());
        }
        return List.of();
    }

    /**
     * Determine effective scopes based on requested and authorized scopes.
     *
     * <p>Rules:
     * <ol>
     *   <li>If no authorized scopes → no effective scopes</li>
     *   <li>If no requested scopes → use authorized scopes (all authorized)</li>
     *   <li>If both exist → intersection (only scopes that are both requested AND authorized)</li>
     * </ol>
     *
     * <p>Scope matching normalizes scopes to their alias kind and discriminator:
     * <ul>
     *   <li>org.eclipse.dspace.dcp.vc.type:MembershipCredential → type MembershipCredential</li>
     *   <li>org.eclipse.dspace.dcp.vc.id:uuid-123 → ID uuid-123</li>
     *   <li>MembershipCredential → type MembershipCredential</li>
     * </ul>
     * A type scope never authorizes an ID scope with the same discriminator, or the other way round.
     *
     * @param requested The scopes requested by the verifier in the query
     * @param authorized The scopes authorized in the access token
     * @return The effective scopes to use for credential fetching
     */
    private List<String> determineEffectiveScopes(List<String> requested, List<String> authorized) {
        if (authorized.isEmpty()) {
            log.warn("Access token authorizes no scopes - returning no credentials");
            return List.of();
        }

        if (requested == null || requested.isEmpty()) {
            return authorized;
        }

        Set<ScopeKey> authorizedKeys = authorized.stream()
                .map(scope -> ScopeKey.of(parseScope(scope)))
                .collect(Collectors.toSet());

        List<String> intersection = requested.stream()
                .filter(requestedScope -> authorizedKeys.contains(ScopeKey.of(parseScope(requestedScope))))
                .toList();

        if (intersection.size() < requested.size()) {
            log.warn("Dropping requested scopes the access token does not authorize: requested {}, authorized {}",
                    requested, authorized);
        }

        return intersection;
    }

    /**
     * Normalized identity of a scope for authorization checks: its alias kind and discriminator.
     *
     * @param idAlias true for a credential ID scope, false for a credential type scope
     * @param discriminator the base discriminator without action suffix
     */
    private record ScopeKey(boolean idAlias, String discriminator) {
        static ScopeKey of(ParsedScope parsed) {
            return new ScopeKey(parsed.isIdAlias(), parsed.getDiscriminator());
        }
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
     * executes both queries if needed, and combines the results. Every query is constrained to
     * {@code holderDid}, so an ID scope cannot reach another holder's credential.
     *
     * @param holderDid The DID of the holder whose credentials may be returned
     * @param scopes The effective scopes to query
     * @return Combined list of credentials matching the scopes
     */
    private List<VerifiableCredential> fetchCredentialsByScopes(String holderDid, List<String> scopes) {
        if (scopes == null || scopes.isEmpty() || holderDid == null || holderDid.isBlank()) {
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

        Set<String> holderDids = holderDidForms(holderDid);
        List<VerifiableCredential> results = new ArrayList<>();

        // Query by types if present
        if (!credentialTypes.isEmpty()) {
            log.debug("Querying credentials by types: {}", credentialTypes);
            results.addAll(credentialRepository.findByHolderDidInAndCredentialTypeIn(holderDids, credentialTypes));
        }

        // Query by IDs if present
        if (!credentialIds.isEmpty()) {
            log.debug("Querying credentials by IDs: {}", credentialIds);
            results.addAll(credentialRepository.findByHolderDidInAndIdIn(holderDids, credentialIds));
        }

        return results;
    }

    /**
     * Returns the written forms of a {@code did:web} DID that name the same holder: as given, with the port
     * colon decoded ({@code host:8080}) and with it percent-encoded ({@code host%3A8080}). Issuers may write
     * either form into the credential subject, and the stored {@code holderDid} is matched exactly.
     *
     * @param holderDid the holder DID
     * @return the equivalent written forms, including {@code holderDid} itself
     */
    private static Set<String> holderDidForms(String holderDid) {
        Set<String> forms = new LinkedHashSet<>();
        forms.add(holderDid);
        if (holderDid.startsWith("did:web:")) {
            String decoded = DidUrlConverter.normalizeDid(holderDid);
            forms.add(decoded);
            forms.add(ENCODED_PORT.matcher(decoded).replaceFirst("$1%3A$2"));
        }
        return forms;
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
     *   <li><b>Unqualified format:</b> credential → returned as-is (deprecated; a warning is logged by the caller)</li>
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
        DcpScope parsed = DcpScope.parse(scope);
        return new ParsedScope(parsed.isIdAlias(), parsed.discriminator(), parsed.action());
    }

    /**
     * Logs a deprecation warning once per unqualified scope (for example {@code MembershipCredential}).
     * Unqualified scopes are still accepted and read as type scopes.
     *
     * @param scopes the scopes to check, may be null
     */
    private void warnOnUnqualifiedScopes(List<String> scopes) {
        if (scopes == null) {
            return;
        }
        scopes.stream()
                .filter(scope -> scope != null && !scope.isBlank())
                .filter(scope -> DcpScope.parse(scope).isUnqualified())
                .distinct()
                .forEach(scope -> log.warn("Deprecated unqualified scope '{}' accepted as a credential type; "
                        + "use '{}' (DCP section 5.4.1.2)", scope, DcpScope.qualifyType(scope)));
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
     * @param participant The participant whose key signs the presentations
     * @return The presentation response message
     */
    private PresentationResponseMessage buildPresentationResponse(List<VerifiableCredential> fetched, ParticipantContext participant) {

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
            Object signed = vpSigner.sign(vp, format, participant);
            signedPresentations.add(signed);
        }

        PresentationResponseMessage.Builder respBuilder = PresentationResponseMessage.Builder.newInstance();
        respBuilder.presentation(signedPresentations);
        return respBuilder.build();
    }

}
