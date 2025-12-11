# Credential Generation Patterns and Constraint-Based Verification

## Overview

This guide provides patterns for extending `CredentialIssuanceService` to support new credential types with constraint-based verification (similar to the negotiation module's `Constraint` class).

## Architecture Pattern

### 1. Credential Type Registry

Create a credential type registry to manage credential definitions and their constraints:

```java
// New class: it.eng.dcp.model.CredentialDefinition.java
package it.eng.dcp.model;

import java.util.Map;
import java.util.HashMap;

/**
 * Defines metadata and constraints for a specific credential type.
 */
public class CredentialDefinition {
    private String credentialType;
    private String format; // "jwt" or "json"
    private Map<String, ConstraintRule> allowedClaims; // claim name -> constraint rules
    
    // Builder pattern similar to existing models
}
```

### 2. Constraint Rule System

Create a constraint verification system modeled after the negotiation module:

```java
// New class: it.eng.dcp.model.ConstraintRule.java
package it.eng.dcp.model;

/**
 * Defines constraint rules for credential claims.
 * Similar to negotiation.model.Constraint but tailored for credentials.
 */
public class ConstraintRule {
    private String claimName;      // e.g., "location", "organizationType"
    private Operator operator;      // EQ, NEQ, IN, GT, LT, etc.
    private Object value;          // The expected value or constraint
    
    public enum Operator {
        EQ,      // equals
        NEQ,     // not equals
        IN,      // in list
        NOT_IN,  // not in list
        GT,      // greater than
        LT,      // less than
        GTE,     // greater than or equal
        LTE,     // less than or equal
        MATCHES  // regex pattern match
    }
    
    /**
     * Verify if a claim value satisfies this constraint.
     */
    public boolean verify(Object claimValue) {
        // Implementation details
    }
}
```

### 3. Enhanced Credential Request

Extend `CredentialRequest` to include claim specifications:

```java
// Enhancement to existing it.eng.dcp.model.CredentialRequest.java
// Add new fields:

/**
 * Claims to include in credentials (key-value pairs).
 * Example: {"location": "US", "organizationType": "Corporation"}
 */
private Map<String, Object> requestedClaims = new HashMap<>();

/**
 * Constraints that must be verified for issuance.
 * These can come from presentation query or approval logic.
 */
private List<ConstraintRule> constraints = new ArrayList<>();
```

## Implementation Examples

### Example 1: Location-Based Credential

Create a new method in `CredentialIssuanceService`:

```java
/**
 * Generate a LocationCredential with country code and verification.
 *
 * This credential includes location claims that can be verified against constraints.
 * Common use cases:
 * - Geofencing: Restrict access based on location
 * - Regulatory compliance: Verify jurisdiction requirements
 * - Trade compliance: Export control verification
 *
 * @param request The credential request containing location claims
 * @return A credential container with the signed location credential
 */
private CredentialMessage.CredentialContainer generateLocationCredential(CredentialRequest request) {
    // Extract location claims from request
    Map<String, Object> locationClaims = extractLocationClaims(request);
    
    // Validate against constraints (if present)
    if (request.getConstraints() != null && !request.getConstraints().isEmpty()) {
        validateClaims(locationClaims, request.getConstraints());
    }
    
    // Add location-specific claims
    Map<String, String> claims = new HashMap<>();
    claims.put("country_code", (String) locationClaims.getOrDefault("country_code", "US"));
    claims.put("region", (String) locationClaims.getOrDefault("region", "Unknown"));
    claims.put("verification_method", "GeoIP");
    claims.put("verification_timestamp", Instant.now().toString());
    
    // Generate signed JWT
    String signedJwt = generateSignedJWT(
        request.getHolderPid(), 
        "LocationCredential", 
        claims
    );
    
    return CredentialMessage.CredentialContainer.Builder.newInstance()
        .credentialType("LocationCredential")
        .format("jwt")
        .payload(signedJwt)
        .build();
}

/**
 * Extract location-related claims from a credential request.
 */
private Map<String, Object> extractLocationClaims(CredentialRequest request) {
    Map<String, Object> locationClaims = new HashMap<>();
    
    if (request.getRequestedClaims() != null) {
        // Extract location-specific claims
        request.getRequestedClaims().forEach((key, value) -> {
            if (key.startsWith("location") || key.equals("country_code") || key.equals("region")) {
                locationClaims.put(key, value);
            }
        });
    }
    
    // Apply defaults if not specified
    locationClaims.putIfAbsent("country_code", "US");
    
    return locationClaims;
}

/**
 * Validate claims against constraint rules.
 * Throws exception if validation fails.
 */
private void validateClaims(Map<String, Object> claims, List<ConstraintRule> constraints) {
    for (ConstraintRule constraint : constraints) {
        Object claimValue = claims.get(constraint.getClaimName());
        
        if (!constraint.verify(claimValue)) {
            throw new IllegalArgumentException(
                String.format("Claim '%s' with value '%s' does not satisfy constraint: %s %s",
                    constraint.getClaimName(), 
                    claimValue,
                    constraint.getOperator(),
                    constraint.getValue())
            );
        }
    }
    
    LOG.info("All constraints validated successfully for {} claims", claims.size());
}
```

### Example 2: Role-Based Credential with Constraints

```java
/**
 * Generate a RoleCredential with organizational role and permissions.
 *
 * Constraints might include:
 * - role IN ["admin", "manager", "viewer"]
 * - clearance_level GTE 3
 * - department EQ "Engineering"
 *
 * @param request The credential request
 * @return A credential container with the signed role credential
 */
private CredentialMessage.CredentialContainer generateRoleCredential(CredentialRequest request) {
    Map<String, Object> roleClaims = extractRoleClaims(request);
    
    // Validate against constraints
    if (request.getConstraints() != null) {
        validateClaims(roleClaims, request.getConstraints());
    }
    
    Map<String, String> claims = new HashMap<>();
    claims.put("role", (String) roleClaims.getOrDefault("role", "viewer"));
    claims.put("department", (String) roleClaims.getOrDefault("department", "General"));
    claims.put("clearance_level", roleClaims.getOrDefault("clearance_level", "1").toString());
    claims.put("effective_date", Instant.now().toString());
    claims.put("expiration_date", Instant.now().plusSeconds(90 * 24 * 60 * 60).toString()); // 90 days
    
    String signedJwt = generateSignedJWT(request.getHolderPid(), "RoleCredential", claims);
    
    return CredentialMessage.CredentialContainer.Builder.newInstance()
        .credentialType("RoleCredential")
        .format("jwt")
        .payload(signedJwt)
        .build();
}

private Map<String, Object> extractRoleClaims(CredentialRequest request) {
    Map<String, Object> roleClaims = new HashMap<>();
    
    if (request.getRequestedClaims() != null) {
        request.getRequestedClaims().forEach((key, value) -> {
            if (key.equals("role") || key.equals("department") || key.equals("clearance_level")) {
                roleClaims.put(key, value);
            }
        });
    }
    
    return roleClaims;
}
```

### Example 3: Compliance Credential with Complex Constraints

```java
/**
 * Generate a ComplianceCredential for regulatory requirements.
 *
 * Example constraints:
 * - certification_type IN ["ISO27001", "SOC2", "GDPR"]
 * - compliance_level EQ "full"
 * - audit_date GT "2024-01-01"
 * - region MATCHES "^(EU|US|APAC)$"
 *
 * @param request The credential request
 * @return A credential container with the signed compliance credential
 */
private CredentialMessage.CredentialContainer generateComplianceCredential(CredentialRequest request) {
    Map<String, Object> complianceClaims = extractComplianceClaims(request);
    
    // Validate against constraints
    if (request.getConstraints() != null) {
        validateClaims(complianceClaims, request.getConstraints());
    }
    
    Map<String, String> claims = new HashMap<>();
    claims.put("certification_type", (String) complianceClaims.getOrDefault("certification_type", "ISO27001"));
    claims.put("compliance_level", (String) complianceClaims.getOrDefault("compliance_level", "full"));
    claims.put("audit_date", complianceClaims.getOrDefault("audit_date", Instant.now().toString()).toString());
    claims.put("region", (String) complianceClaims.getOrDefault("region", "EU"));
    claims.put("auditor", "Independent Auditor Inc.");
    claims.put("certificate_id", "CERT-" + UUID.randomUUID().toString().substring(0, 12));
    
    String signedJwt = generateSignedJWT(request.getHolderPid(), "ComplianceCredential", claims);
    
    return CredentialMessage.CredentialContainer.Builder.newInstance()
        .credentialType("ComplianceCredential")
        .format("jwt")
        .payload(signedJwt)
        .build();
}

private Map<String, Object> extractComplianceClaims(CredentialRequest request) {
    Map<String, Object> complianceClaims = new HashMap<>();
    
    if (request.getRequestedClaims() != null) {
        request.getRequestedClaims().forEach((key, value) -> {
            if (key.startsWith("certification") || 
                key.startsWith("compliance") || 
                key.equals("audit_date") || 
                key.equals("region")) {
                complianceClaims.put(key, value);
            }
        });
    }
    
    return complianceClaims;
}
```

## Enhanced generateCredentialForType Method

Update the switch statement to handle new credential types:

```java
private CredentialMessage.CredentialContainer generateCredentialForType(
        String credentialId, 
        CredentialRequest request) {
    
    String credentialType = extractCredentialType(credentialId);
    
    switch (credentialType) {
        case "MembershipCredential":
            return generateMembershipCredential(request);
        case "OrganizationCredential":
            return generateOrganizationCredential(request);
        case "LocationCredential":
            return generateLocationCredential(request);
        case "RoleCredential":
            return generateRoleCredential(request);
        case "ComplianceCredential":
            return generateComplianceCredential(request);
        case "EducationCredential":
            return generateEducationCredential(request);
        case "EmploymentCredential":
            return generateEmploymentCredential(request);
        default:
            LOG.warn("Unknown credential type '{}', generating generic credential", credentialType);
            return generateGenericCredential(credentialType, request);
    }
}
```

## Constraint Verification Service

Create a dedicated service for constraint verification:

```java
// New class: it.eng.dcp.service.ConstraintVerificationService.java
package it.eng.dcp.service;

import it.eng.dcp.model.ConstraintRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ConstraintVerificationService {
    
    private static final Logger LOG = LoggerFactory.getLogger(ConstraintVerificationService.class);
    
    /**
     * Verify that all constraints are satisfied by the provided claims.
     *
     * @param claims The claims to verify
     * @param constraints The constraints to apply
     * @return true if all constraints are satisfied
     * @throws IllegalArgumentException if any constraint fails
     */
    public boolean verifyConstraints(Map<String, Object> claims, List<ConstraintRule> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return true;
        }
        
        for (ConstraintRule constraint : constraints) {
            if (!verifyConstraint(claims, constraint)) {
                String message = String.format(
                    "Constraint verification failed: %s %s %s",
                    constraint.getClaimName(),
                    constraint.getOperator(),
                    constraint.getValue()
                );
                LOG.error(message);
                throw new IllegalArgumentException(message);
            }
        }
        
        LOG.info("All {} constraints verified successfully", constraints.size());
        return true;
    }
    
    /**
     * Verify a single constraint against claims.
     */
    private boolean verifyConstraint(Map<String, Object> claims, ConstraintRule constraint) {
        Object claimValue = claims.get(constraint.getClaimName());
        
        if (claimValue == null) {
            LOG.warn("Claim '{}' not found in claims map", constraint.getClaimName());
            return false;
        }
        
        return evaluateConstraint(claimValue, constraint.getOperator(), constraint.getValue());
    }
    
    /**
     * Evaluate a constraint based on operator type.
     */
    private boolean evaluateConstraint(Object claimValue, ConstraintRule.Operator operator, Object expectedValue) {
        switch (operator) {
            case EQ:
                return claimValue.equals(expectedValue);
                
            case NEQ:
                return !claimValue.equals(expectedValue);
                
            case IN:
                if (expectedValue instanceof List) {
                    return ((List<?>) expectedValue).contains(claimValue);
                }
                return false;
                
            case NOT_IN:
                if (expectedValue instanceof List) {
                    return !((List<?>) expectedValue).contains(claimValue);
                }
                return true;
                
            case GT:
                return compareValues(claimValue, expectedValue) > 0;
                
            case LT:
                return compareValues(claimValue, expectedValue) < 0;
                
            case GTE:
                return compareValues(claimValue, expectedValue) >= 0;
                
            case LTE:
                return compareValues(claimValue, expectedValue) <= 0;
                
            case MATCHES:
                if (expectedValue instanceof String && claimValue instanceof String) {
                    Pattern pattern = Pattern.compile((String) expectedValue);
                    return pattern.matcher((String) claimValue).matches();
                }
                return false;
                
            default:
                LOG.warn("Unknown operator: {}", operator);
                return false;
        }
    }
    
    /**
     * Compare values for GT/LT operations.
     */
    @SuppressWarnings("unchecked")
    private int compareValues(Object value1, Object value2) {
        if (value1 instanceof Comparable && value2 instanceof Comparable) {
            return ((Comparable) value1).compareTo(value2);
        }
        throw new IllegalArgumentException("Values are not comparable");
    }
}
```

## Usage Example

### From PresentationQueryMessage to Credential Issuance

```java
// Example: Holder requests credential with specific location constraint
PresentationQueryMessage query = PresentationQueryMessage.Builder.newInstance()
    .presentationDefinition(Map.of(
        "input_descriptors", List.of(
            Map.of(
                "id", "location_requirement",
                "constraints", Map.of(
                    "fields", List.of(
                        Map.of(
                            "path", List.of("$.credentialSubject.country_code"),
                            "filter", Map.of(
                                "type", "string",
                                "pattern", "^(US|CA|MX)$"  // North America only
                            )
                        )
                    )
                )
            )
        )
    ))
    .build();

// In the issuance flow, convert presentation definition constraints to ConstraintRules:
List<ConstraintRule> constraints = extractConstraintsFromPresentationDefinition(query.getPresentationDefinition());

CredentialRequest request = CredentialRequest.Builder.newInstance()
    .holderPid("did:example:holder123")
    .credentialIds(List.of("LocationCredential"))
    .requestedClaims(Map.of("country_code", "US", "region", "California"))
    .constraints(constraints)
    .build();

// Generate credential - validation happens automatically
List<CredentialMessage.CredentialContainer> credentials = 
    credentialIssuanceService.generateCredentials(request);
```

## Integration with Presentation Exchange

The constraint system should integrate with DIF Presentation Exchange v2.0 spec:

```java
/**
 * Extract constraint rules from a Presentation Exchange presentation definition.
 */
private List<ConstraintRule> extractConstraintsFromPresentationDefinition(
        Map<String, Object> presentationDefinition) {
    
    List<ConstraintRule> constraints = new ArrayList<>();
    
    if (presentationDefinition == null) {
        return constraints;
    }
    
    Object descriptorsObj = presentationDefinition.get("input_descriptors");
    if (!(descriptorsObj instanceof List)) {
        return constraints;
    }
    
    List<?> descriptors = (List<?>) descriptorsObj;
    for (Object descriptorObj : descriptors) {
        if (!(descriptorObj instanceof Map)) continue;
        
        Map<?, ?> descriptor = (Map<?, ?>) descriptorObj;
        Object constraintsObj = descriptor.get("constraints");
        
        if (!(constraintsObj instanceof Map)) continue;
        
        Map<?, ?> constraintsMap = (Map<?, ?>) constraintsObj;
        Object fieldsObj = constraintsMap.get("fields");
        
        if (!(fieldsObj instanceof List)) continue;
        
        List<?> fields = (List<?>) fieldsObj;
        for (Object fieldObj : fields) {
            if (!(fieldObj instanceof Map)) continue;
            
            Map<?, ?> field = (Map<?, ?>) fieldObj;
            ConstraintRule rule = parseFieldConstraint(field);
            if (rule != null) {
                constraints.add(rule);
            }
        }
    }
    
    return constraints;
}

private ConstraintRule parseFieldConstraint(Map<?, ?> field) {
    // Extract path (JSONPath to claim)
    Object pathObj = field.get("path");
    if (!(pathObj instanceof List) || ((List<?>) pathObj).isEmpty()) {
        return null;
    }
    
    String path = (String) ((List<?>) pathObj).get(0);
    String claimName = extractClaimNameFromPath(path); // e.g., "$.credentialSubject.country_code" -> "country_code"
    
    // Extract filter
    Object filterObj = field.get("filter");
    if (!(filterObj instanceof Map)) {
        return null;
    }
    
    Map<?, ?> filter = (Map<?, ?>) filterObj;
    
    // Parse operator and value from filter
    // Example: {"type": "string", "pattern": "^US$"} -> MATCHES operator
    // Example: {"type": "string", "const": "US"} -> EQ operator
    // Example: {"type": "number", "minimum": 18} -> GTE operator
    
    return ConstraintRule.Builder.newInstance()
        .claimName(claimName)
        .operator(determineOperatorFromFilter(filter))
        .value(extractValueFromFilter(filter))
        .build();
}
```

## Best Practices

1. **Type Safety**: Always validate claim types before comparisons
2. **Logging**: Log all constraint verifications for audit trails
3. **Error Messages**: Provide clear error messages when constraints fail
4. **Default Values**: Define sensible defaults for optional claims
5. **Schema Validation**: Consider JSON Schema for additional validation
6. **Performance**: Cache credential definitions for frequently issued types
7. **Security**: Never expose sensitive verification logic in error messages

## Testing

Create comprehensive tests for each credential type:

```java
@Test
void testLocationCredentialWithConstraints() {
    // Arrange
    List<ConstraintRule> constraints = List.of(
        ConstraintRule.Builder.newInstance()
            .claimName("country_code")
            .operator(ConstraintRule.Operator.IN)
            .value(List.of("US", "CA", "MX"))
            .build()
    );
    
    CredentialRequest request = CredentialRequest.Builder.newInstance()
        .holderPid("did:example:holder")
        .credentialIds(List.of("LocationCredential"))
        .requestedClaims(Map.of("country_code", "US"))
        .constraints(constraints)
        .build();
    
    // Act
    List<CredentialMessage.CredentialContainer> credentials = 
        service.generateCredentials(request);
    
    // Assert
    assertThat(credentials).hasSize(1);
    assertThat(credentials.get(0).getCredentialType()).isEqualTo("LocationCredential");
    
    // Verify JWT contains correct claims
    String jwt = credentials.get(0).getPayload();
    SignedJWT signedJWT = SignedJWT.parse(jwt);
    Map<String, Object> vc = (Map<String, Object>) signedJWT.getJWTClaimsSet().getClaim("vc");
    Map<String, Object> subject = (Map<String, Object>) vc.get("credentialSubject");
    
    assertThat(subject.get("country_code")).isEqualTo("US");
}

@Test
void testLocationCredentialConstraintViolation() {
    // Arrange - request for non-allowed country
    List<ConstraintRule> constraints = List.of(
        ConstraintRule.Builder.newInstance()
            .claimName("country_code")
            .operator(ConstraintRule.Operator.IN)
            .value(List.of("US", "CA", "MX"))
            .build()
    );
    
    CredentialRequest request = CredentialRequest.Builder.newInstance()
        .holderPid("did:example:holder")
        .credentialIds(List.of("LocationCredential"))
        .requestedClaims(Map.of("country_code", "DE")) // Germany - not allowed
        .constraints(constraints)
        .build();
    
    // Act & Assert
    assertThatThrownBy(() -> service.generateCredentials(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("country_code")
        .hasMessageContaining("does not satisfy constraint");
}
```

## Conclusion

This pattern provides a flexible, extensible system for generating various credential types with constraint-based verification. The approach:

- Mirrors the negotiation module's constraint system
- Supports complex business rules
- Integrates with Presentation Exchange standard
- Maintains separation of concerns
- Enables easy addition of new credential types

