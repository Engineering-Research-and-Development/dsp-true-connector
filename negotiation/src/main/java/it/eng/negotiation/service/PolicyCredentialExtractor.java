package it.eng.negotiation.service;

import it.eng.negotiation.exception.PolicyParseException;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Permission;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class PolicyCredentialExtractor {

    /**
     * Extract credential types from an Offer's permissions by inspecting constraint.rightOperand values.
     * This is conservative: if constraints contain recognizable credential type strings we return them.
     * @param offer the Offer to inspect
     * @param consumerPid consumer PID for error reporting
     * @param providerPid provider PID for error reporting
     * @return set of credential type strings found (never empty)
     * @throws PolicyParseException if no credential types can be extracted
     */
    public Set<String> extractCredentialTypes(Offer offer, String consumerPid, String providerPid) {
        Set<String> out = new HashSet<>();
        if (offer == null) {
            log.warn("Offer is null, cannot extract credential types");
            throw new PolicyParseException("Offer is null", consumerPid, providerPid);
        }

        List<Permission> perms = offer.getPermission();
        if (perms == null || perms.isEmpty()) {
            log.warn("Offer has no permissions, cannot extract credential types");
            throw new PolicyParseException("Offer has no permissions", consumerPid, providerPid);
        }

        for (Permission p : perms) {
            if (p.getConstraint() == null) continue;
            for (Constraint c : p.getConstraint()) {
                // Check leftOperand for credentialType
                if (c.getLeftOperand() != null) {
                    String leftOpStr = c.getLeftOperand().toString().toLowerCase();
                    if (leftOpStr.contains("credentialtype") || leftOpStr.contains("credential")) {
                        if (c.getRightOperand() != null) {
                            String v = c.getRightOperand().trim();
                            if (!v.isEmpty()) {
                                out.add(v);
                                log.debug("Extracted credential type from constraint: {}", v);
                            }
                        }
                    }
                }

                // Fallback: check rightOperand for credential-like strings
                if (c.getRightOperand() != null) {
                    String v = c.getRightOperand().trim();
                    // heuristics: if operand looks like a type name (contains 'Credential' or contains '/'), include it
                    if (v.contains("Credential") || v.contains("/") || v.contains("#")) {
                        out.add(v);
                        log.debug("Extracted credential type from rightOperand: {}", v);
                    }
                }
            }
        }

        if (out.isEmpty()) {
            log.warn("No credential types could be extracted from offer");
            throw new PolicyParseException("No credential types found in offer policy", consumerPid, providerPid);
        }

        log.info("Extracted {} credential types from offer: {}", out.size(), out);
        return out;
    }

    /**
     * Extract credential types from an Offer's permissions (backwards compatible version without PIDs).
     * @param offer the Offer to inspect
     * @return set of credential type strings found (may be empty for backwards compatibility)
     * @deprecated Use {@link #extractCredentialTypes(Offer, String, String)} instead
     */
    @Deprecated
    public Set<String> extractCredentialTypes(Offer offer) {
        Set<String> out = new HashSet<>();
        if (offer == null) return out;
        List<Permission> perms = offer.getPermission();
        if (perms == null) return out;
        for (Permission p : perms) {
            if (p.getConstraint() == null) continue;
            for (Constraint c : p.getConstraint()) {
                if (c.getRightOperand() != null) {
                    String v = c.getRightOperand().trim();
                    // heuristics: if operand looks like a type name (contains 'Credential' or contains '/'), include it
                    if (v.contains("Credential") || v.contains("/") || v.contains("#")) {
                        out.add(v);
                    }
                }
            }
        }
        return out;
    }
}

