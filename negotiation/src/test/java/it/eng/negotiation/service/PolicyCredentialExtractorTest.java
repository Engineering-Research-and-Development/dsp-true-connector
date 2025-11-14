package it.eng.negotiation.service;

import it.eng.negotiation.exception.PolicyParseException;
import it.eng.negotiation.model.Action;
import it.eng.negotiation.model.Constraint;
import it.eng.negotiation.model.LeftOperand;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Operator;
import it.eng.negotiation.model.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PolicyCredentialExtractor covering:
 * - Valid policy returns expected credential types
 * - Missing credentialType field throws PolicyParseException
 * - Multiple constraints return distinct set
 * - Null offer throws PolicyParseException
 * - Empty permissions throws PolicyParseException
 */
class PolicyCredentialExtractorTest {

    private PolicyCredentialExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new PolicyCredentialExtractor();
    }

    @Test
    @DisplayName("Extract credential types from valid policy with rightOperand")
    void testExtractCredentialTypes_ValidPolicy() {
        Constraint constraint = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.PURPOSE)
                .operator(Operator.EQ)
                .rightOperand("VerifiableCredential")
                .build();

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(List.of(constraint))
                .build();

        Offer offer = Offer.Builder.newInstance()
                .id("offer-1")
                .target("dataset-1")
                .assigner("did:web:provider.example")
                .permission(List.of(permission))
                .build();

        Set<String> types = extractor.extractCredentialTypes(offer, "consumer-1", "provider-1");

        assertEquals(1, types.size());
        assertTrue(types.contains("VerifiableCredential"));
    }

    @Test
    @DisplayName("Extract multiple credential types from policy with multiple constraints")
    void testExtractCredentialTypes_MultipleConstraints() {
        Constraint constraint1 = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.PURPOSE)
                .operator(Operator.EQ)
                .rightOperand("DriversLicenseCredential")
                .build();

        Constraint constraint2 = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.PURPOSE)
                .operator(Operator.EQ)
                .rightOperand("PassportCredential")
                .build();

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(List.of(constraint1, constraint2))
                .build();

        Offer offer = Offer.Builder.newInstance()
                .id("offer-1")
                .target("dataset-1")
                .assigner("did:web:provider.example")
                .permission(List.of(permission))
                .build();

        Set<String> types = extractor.extractCredentialTypes(offer, "consumer-1", "provider-1");

        assertEquals(2, types.size());
        assertTrue(types.contains("DriversLicenseCredential"));
        assertTrue(types.contains("PassportCredential"));
    }

    @Test
    @DisplayName("Extract credential types using fallback heuristics")
    void testExtractCredentialTypes_FallbackHeuristics() {
        Constraint constraint = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.COUNT)
                .operator(Operator.EQ)
                .rightOperand("https://example.com/credentials/MyCredential")
                .build();

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(List.of(constraint))
                .build();

        Offer offer = Offer.Builder.newInstance()
                .id("offer-1")
                .target("dataset-1")
                .assigner("did:web:provider.example")
                .permission(List.of(permission))
                .build();

        Set<String> types = extractor.extractCredentialTypes(offer, "consumer-1", "provider-1");

        assertEquals(1, types.size());
        assertTrue(types.contains("https://example.com/credentials/MyCredential"));
    }

    @Test
    @DisplayName("Returns distinct set when duplicate credential types exist")
    void testExtractCredentialTypes_Duplicates() {
        Constraint constraint1 = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.PURPOSE)
                .operator(Operator.EQ)
                .rightOperand("VerifiableCredential")
                .build();

        Constraint constraint2 = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.PURPOSE)
                .operator(Operator.EQ)
                .rightOperand("VerifiableCredential")
                .build();

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(List.of(constraint1, constraint2))
                .build();

        Offer offer = Offer.Builder.newInstance()
                .id("offer-1")
                .target("dataset-1")
                .assigner("did:web:provider.example")
                .permission(List.of(permission))
                .build();

        Set<String> types = extractor.extractCredentialTypes(offer, "consumer-1", "provider-1");

        assertEquals(1, types.size());
        assertTrue(types.contains("VerifiableCredential"));
    }

    @Test
    @DisplayName("Throw PolicyParseException when offer is null")
    void testExtractCredentialTypes_NullOffer() {
        PolicyParseException exception = assertThrows(PolicyParseException.class,
            () -> extractor.extractCredentialTypes(null, "consumer-1", "provider-1"));

        assertEquals("Offer is null", exception.getMessage());
        assertEquals("consumer-1", exception.getConsumerPid());
        assertEquals("provider-1", exception.getProviderPid());
    }

    @Test
    @DisplayName("Throw PolicyParseException when permissions are null")
    void testExtractCredentialTypes_NullPermissions() {
        Offer offer = Offer.Builder.newInstance()
                .id("offer-1")
                .target("dataset-1")
                .assigner("did:web:provider.example")
                .permission(null)
                .build();

        PolicyParseException exception = assertThrows(PolicyParseException.class,
            () -> extractor.extractCredentialTypes(offer, "consumer-1", "provider-1"));

        assertEquals("Offer has no permissions", exception.getMessage());
    }

    @Test
    @DisplayName("Throw PolicyParseException when permissions are empty")
    void testExtractCredentialTypes_EmptyPermissions() {
        Offer offer = Offer.Builder.newInstance()
                .id("offer-1")
                .target("dataset-1")
                .assigner("did:web:provider.example")
                .permission(List.of())
                .build();

        PolicyParseException exception = assertThrows(PolicyParseException.class,
            () -> extractor.extractCredentialTypes(offer, "consumer-1", "provider-1"));

        assertEquals("Offer has no permissions", exception.getMessage());
    }

    @Test
    @DisplayName("Throw PolicyParseException when no credential types found")
    void testExtractCredentialTypes_NoCredentialTypes() {
        Constraint constraint = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.DATE_TIME)
                .operator(Operator.GT)
                .rightOperand("someValue")
                .build();

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(List.of(constraint))
                .build();

        Offer offer = Offer.Builder.newInstance()
                .id("offer-1")
                .target("dataset-1")
                .assigner("did:web:provider.example")
                .permission(List.of(permission))
                .build();

        PolicyParseException exception = assertThrows(PolicyParseException.class,
            () -> extractor.extractCredentialTypes(offer, "consumer-1", "provider-1"));

        assertEquals("No credential types found in offer policy", exception.getMessage());
    }

    @Test
    @DisplayName("Backwards compatible method returns empty set for invalid offer")
    void testExtractCredentialTypes_BackwardsCompatible_NullOffer() {
        @SuppressWarnings("deprecation")
        Set<String> types = extractor.extractCredentialTypes(null);

        assertTrue(types.isEmpty());
    }

    @Test
    @DisplayName("Backwards compatible method returns credential types for valid offer")
    void testExtractCredentialTypes_BackwardsCompatible_ValidOffer() {
        Constraint constraint = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.COUNT)
                .operator(Operator.EQ)
                .rightOperand("SomeCredential")
                .build();

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(List.of(constraint))
                .build();

        Offer offer = Offer.Builder.newInstance()
                .id("offer-1")
                .target("dataset-1")
                .assigner("did:web:provider.example")
                .permission(List.of(permission))
                .build();

        @SuppressWarnings("deprecation")
        Set<String> types = extractor.extractCredentialTypes(offer);

        assertEquals(1, types.size());
        assertTrue(types.contains("SomeCredential"));
    }

    @Test
    @DisplayName("Extract credential types with hash symbol in rightOperand")
    void testExtractCredentialTypes_HashSymbol() {
        Constraint constraint = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.SPATIAL)
                .operator(Operator.EQ)
                .rightOperand("https://example.com/vocab#DriverLicense")
                .build();

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(List.of(constraint))
                .build();

        Offer offer = Offer.Builder.newInstance()
                .id("offer-1")
                .target("dataset-1")
                .assigner("did:web:provider.example")
                .permission(List.of(permission))
                .build();

        Set<String> types = extractor.extractCredentialTypes(offer, "consumer-1", "provider-1");

        assertEquals(1, types.size());
        assertTrue(types.contains("https://example.com/vocab#DriverLicense"));
    }

    @Test
    @DisplayName("Skip constraints with null rightOperand")
    void testExtractCredentialTypes_NullRightOperand() {
        Constraint constraint1 = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.PURPOSE)
                .operator(Operator.EQ)
                .rightOperand(null)
                .build();

        Constraint constraint2 = Constraint.Builder.newInstance()
                .leftOperand(LeftOperand.PURPOSE)
                .operator(Operator.EQ)
                .rightOperand("ValidCredential")
                .build();

        Permission permission = Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(List.of(constraint1, constraint2))
                .build();

        Offer offer = Offer.Builder.newInstance()
                .id("offer-1")
                .target("dataset-1")
                .assigner("did:web:provider.example")
                .permission(List.of(permission))
                .build();

        Set<String> types = extractor.extractCredentialTypes(offer, "consumer-1", "provider-1");

        assertEquals(1, types.size());
        assertTrue(types.contains("ValidCredential"));
    }
}

