package it.eng.dcp.service;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.PresentationQueryMessage;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.model.VerifiableCredential;
import it.eng.dcp.model.VerifiablePresentation;
import it.eng.dcp.repository.VerifiableCredentialRepository;
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

        // Parse scopes to extract credential types/IDs for repository query
        List<String> credentialTypesOrIds = parseScopesToCredentialTypes(effectiveScopes);

        log.debug("Parsed credential types/IDs from scopes: {}", credentialTypesOrIds);

        // Fetch credentials based on effective scopes
        List<VerifiableCredential> fetched;
        if (credentialTypesOrIds == null || credentialTypesOrIds.isEmpty()) {
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
            log.debug("Fetching credentials for types/IDs: {}", credentialTypesOrIds);
            fetched = credentialRepository.findByCredentialTypeIn(credentialTypesOrIds);
        }

        // Rest of presentation creation logic (grouping and signing)
        return buildPresentationResponse(fetched);
    }

    /**
     * Create a PresentationResponseMessage without scope enforcement (backward compatibility).
     *
     * @param query the presentation query message containing scope and definition
     * @return the presentation response message containing signed presentations
     * @deprecated Use {@link #createPresentation(PresentationQueryMessage, JWTClaimsSet)} for scope enforcement
     */
    @Deprecated
    public PresentationResponseMessage createPresentation(PresentationQueryMessage query) {
        log.warn("Creating presentation WITHOUT scope enforcement - consider updating caller");
        List<String> requiredTypes = query.getScope();
        List<VerifiableCredential> fetched;
        if (requiredTypes == null || requiredTypes.isEmpty()) {
            // fetch all credentials
            fetched = credentialRepository.findAll();
        } else {
            fetched = credentialRepository.findByCredentialTypeIn(requiredTypes);
        }

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

        // Intersection - only scopes that are both requested AND authorized
        List<String> intersection = requested.stream()
                .filter(authorized::contains)
                .collect(Collectors.toList());

        if (intersection.isEmpty()) {
            log.warn("Scope mismatch: verifier requested {} but only {} authorized. Returning empty result.",
                    requested, authorized);
        }

        return intersection;
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
     *   <li><b>Action suffixes:</b> prefix:discriminator:action → extracts discriminator</li>
     *   <li><b>Simple format:</b> credential → returns as-is (backward compatibility)</li>
     * </ul>
     *
     * <p>Examples:
     * <ul>
     *   <li>org.eclipse.dspace.dcp.vc.type:MembershipCredential → MembershipCredential</li>
     *   <li>org.eclipse.dspace.dcp.vc.type:MembershipCredential:read → MembershipCredential</li>
     *   <li>org.eclipse.dspace.dcp.vc.id:uuid-123 → uuid-123</li>
     *   <li>custom.org.prefix:CustomType:write → CustomType</li>
     *   <li>mycompany.credentials:EmployeeCredential → EmployeeCredential</li>
     *   <li>MembershipCredential → MembershipCredential (simple)</li>
     * </ul>
     *
     * @param scopes The scopes to parse (may be null)
     * @return List of credential types or IDs, or null if input is null
     */
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
     * Parse a single scope string to extract the credential type or ID.
     *
     * <p>Per DCP spec Section 5.4.1.2, scope format is [alias]:[discriminator].
     * The spec states (line 907): "The [alias] value MAY be implementation-specific."
     *
     * <p>This method uses generic parsing to handle:
     * <ul>
     *   <li><b>Standard DCP aliases (MUST be supported per spec):</b></li>
     *   <ul>
     *     <li>org.eclipse.dspace.dcp.vc.type:MembershipCredential → MembershipCredential</li>
     *     <li>org.eclipse.dspace.dcp.vc.id:uuid-1234 → uuid-1234</li>
     *   </ul>
     *   <li><b>Custom aliases (implementation-specific):</b></li>
     *   <ul>
     *     <li>custom.prefix:SomeCredential → SomeCredential</li>
     *     <li>myorg.credentials:Type → Type</li>
     *   </ul>
     *   <li><b>With action/permission suffix (non-standard but common):</b></li>
     *   <ul>
     *     <li>prefix:credential:read → credential</li>
     *     <li>prefix:credential:write → credential</li>
     *   </ul>
     *   <li><b>Simple format (no alias):</b></li>
     *   <ul>
     *     <li>MembershipCredential → MembershipCredential</li>
     *   </ul>
     * </ul>
     *
     * <p>Parsing strategy:
     * <ol>
     *   <li>If contains colon: treat as [alias]:[discriminator] or [alias]:[discriminator]:[action]</li>
     *   <li>Extract discriminator (second segment)</li>
     *   <li>Strip known permission suffixes from discriminator</li>
     *   <li>If no colon: treat as simple format, strip permission suffixes</li>
     * </ol>
     *
     * @param scope The scope string to parse
     * @return The extracted credential type/ID, or the original scope if not parseable
     */
    private String parseSingleScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return null;
        }

        // Check if scope contains colon (aliased format)
        int firstColon = scope.indexOf(':');
        if (firstColon == -1) {
            // Simple format (no alias) - return as-is, but strip permission suffixes
            return stripPermissionSuffix(scope);
        }

        // Aliased format: [alias]:[discriminator] or [alias]:[discriminator]:[action]
        // Extract the discriminator (everything after first colon)
        String afterFirstColon = scope.substring(firstColon + 1);

        // Strip any action/permission suffix from discriminator
        return stripPermissionSuffix(afterFirstColon);
    }

    /**
     * Strip permission suffixes like :read, :write, :delete from a scope.
     * These are not part of the DCP spec but may be present in some implementations.
     *
     * <p>Examples:
     * <ul>
     *   <li>MembershipCredential:read → MembershipCredential</li>
     *   <li>MembershipCredential:write → MembershipCredential</li>
     *   <li>MembershipCredential → MembershipCredential (no change)</li>
     * </ul>
     *
     * @param value The value that may have a permission suffix
     * @return The value with permission suffix removed
     */
    private String stripPermissionSuffix(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        // Common permission suffixes to strip
        String[] permissionSuffixes = {":read", ":write", ":delete", ":admin", ":execute"};

        for (String suffix : permissionSuffixes) {
            if (value.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length());
            }
        }

        return value;
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
