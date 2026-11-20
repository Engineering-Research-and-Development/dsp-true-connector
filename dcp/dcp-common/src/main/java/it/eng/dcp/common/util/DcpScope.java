package it.eng.dcp.common.util;

import it.eng.dcp.common.model.DCPConstants;

import java.util.List;
import java.util.Set;

/**
 * Parsed form of a DCP scope string.
 *
 * <p>Per DCP v1.0.1 section 5.4.1.2 a scope has the form {@code [alias]:[discriminator]}. The two
 * standard aliases are {@link DCPConstants#SCOPE_ALIAS_VC_TYPE} (credential type) and
 * {@link DCPConstants#SCOPE_ALIAS_VC_ID} (credential id); other aliases are implementation specific and are
 * treated as type scopes. A string without any colon (for example {@code MembershipCredential}) is an
 * <em>unqualified</em> scope. It is a deprecated compatibility form: it is read as a type scope, and callers
 * should log a deprecation warning (see {@link #isUnqualified()}).
 *
 * <p>An optional action suffix ({@code :read}, {@code :write}, {@code :delete}, {@code :admin},
 * {@code :execute}) is split off from the discriminator and kept separately; it is not part of the DCP
 * specification.
 *
 * @param alias         the alias, or {@code null} for an unqualified scope
 * @param discriminator the credential type or id, without alias and action suffix
 * @param action        the action suffix without the leading colon, or {@code null}
 */
public record DcpScope(String alias, String discriminator, String action) {

    private static final List<String> ACTION_SUFFIXES = List.of(":read", ":write", ":delete", ":admin", ":execute");
    private static final Set<String> STANDARD_ALIASES = Set.of(DCPConstants.SCOPE_ALIAS_VC_TYPE, DCPConstants.SCOPE_ALIAS_VC_ID);

    /**
     * Parses a scope string.
     *
     * @param scope the scope string, may be {@code null} or blank
     * @return the parsed scope; a {@code null} or blank input yields an unqualified scope with an empty discriminator
     */
    public static DcpScope parse(String scope) {
        if (scope == null || scope.isBlank()) {
            return new DcpScope(null, "", null);
        }
        String trimmed = scope.trim();
        int firstColon = trimmed.indexOf(':');
        if (firstColon == -1) {
            return new DcpScope(null, trimmed, null);
        }
        String alias = trimmed.substring(0, firstColon);
        String rest = trimmed.substring(firstColon + 1);
        for (String suffix : ACTION_SUFFIXES) {
            if (rest.endsWith(suffix)) {
                return new DcpScope(alias, rest.substring(0, rest.length() - suffix.length()), suffix.substring(1));
            }
        }
        return new DcpScope(alias, rest, null);
    }

    /**
     * Builds the standard qualified type scope {@code org.eclipse.dspace.dcp.vc.type:<type>} for a credential type.
     * A value that already carries an alias is returned unchanged (trimmed).
     *
     * @param credentialType a credential type, or an already qualified scope
     * @return the qualified scope; blank input is returned unchanged
     */
    public static String qualifyType(String credentialType) {
        if (credentialType == null || credentialType.isBlank()) {
            return credentialType;
        }
        DcpScope parsed = parse(credentialType);
        return parsed.isUnqualified()
                ? DCPConstants.SCOPE_ALIAS_VC_TYPE + ":" + parsed.discriminator()
                : credentialType.trim();
    }

    /**
     * Qualifies every entry with {@link #qualifyType(String)}, dropping blank entries and duplicates.
     *
     * @param credentialTypes credential types or qualified scopes, may be {@code null}
     * @return the qualified scopes, never {@code null}
     */
    public static List<String> qualifyTypes(List<String> credentialTypes) {
        if (credentialTypes == null) {
            return List.of();
        }
        return credentialTypes.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(DcpScope::qualifyType)
                .distinct()
                .toList();
    }

    /**
     * Whether the scope has no alias (deprecated compatibility form such as {@code MembershipCredential}).
     *
     * @return {@code true} for an unqualified scope
     */
    public boolean isUnqualified() {
        return alias == null;
    }

    /**
     * Whether the alias is {@link DCPConstants#SCOPE_ALIAS_VC_ID}.
     *
     * @return {@code true} for a credential id scope
     */
    public boolean isIdAlias() {
        return DCPConstants.SCOPE_ALIAS_VC_ID.equals(alias);
    }

    /**
     * Whether the alias is one of the two standard aliases.
     *
     * @return {@code true} for a standard alias
     */
    public boolean isStandardAlias() {
        return alias != null && STANDARD_ALIASES.contains(alias);
    }

    /**
     * Whether an action suffix is present.
     *
     * @return {@code true} if an action was parsed
     */
    public boolean hasAction() {
        return action != null && !action.isBlank();
    }
}
