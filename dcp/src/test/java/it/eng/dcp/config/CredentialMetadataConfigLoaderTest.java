package it.eng.dcp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for CredentialMetadataConfigLoader to verify it loads configuration
 * from credential-metadata-configuration.properties on the classpath.
 */
@SpringBootTest
@ContextConfiguration(classes = CredentialMetadataConfigLoader.class)
class CredentialMetadataConfigLoaderTest {

    @Autowired
    private CredentialMetadataConfig credentialMetadataConfig;

    @Test
    @DisplayName("Should load credential metadata from properties file on classpath")
    void shouldLoadCredentialMetadataFromPropertiesFile() {
        // Verify that configuration is loaded
        assertNotNull(credentialMetadataConfig, "CredentialMetadataConfig should be loaded");
        assertNotNull(credentialMetadataConfig.getSupported(), "Supported credentials list should not be null");

        // Verify at least one credential is loaded (from the example file)
        assertFalse(credentialMetadataConfig.getSupported().isEmpty(),
                "Should load at least one credential from the properties file");
    }

    @Test
    @DisplayName("Should parse MembershipCredential configuration correctly")
    void shouldParseMembershipCredentialConfiguration() {
        // Find MembershipCredential in the loaded configuration
        CredentialMetadataConfig.CredentialConfig membershipCred = credentialMetadataConfig.getSupported().stream()
                .filter(c -> "MembershipCredential".equals(c.getCredentialType()))
                .findFirst()
                .orElse(null);

        assertNotNull(membershipCred, "MembershipCredential should be loaded");
        assertEquals("550e8400-e29b-41d4-a716-446655440000", membershipCred.getId());
        assertEquals("CredentialObject", membershipCred.getType());
        assertEquals("https://example.com/schemas/membership-credential.json",
                membershipCred.getCredentialSchema());
        assertEquals("vc11-sl2021/jwt", membershipCred.getProfile());

        // Verify binding methods
        assertNotNull(membershipCred.getBindingMethods());
        assertEquals(2, membershipCred.getBindingMethods().size());
        assertTrue(membershipCred.getBindingMethods().contains("did:web"));
        assertTrue(membershipCred.getBindingMethods().contains("did:key"));
    }

    @Test
    @DisplayName("Should parse CompanyCredential with issuance policy")
    void shouldParseCompanyCredentialWithIssuancePolicy() {
        // Find CompanyCredential in the loaded configuration
        CredentialMetadataConfig.CredentialConfig companyCred = credentialMetadataConfig.getSupported().stream()
                .filter(c -> "CompanyCredential".equals(c.getCredentialType()))
                .findFirst()
                .orElse(null);

        assertNotNull(companyCred, "CompanyCredential should be loaded");
        assertEquals("d5c77b0e-7f4e-4fd5-8c5f-28b5fc3f96d1", companyCred.getId());
        assertEquals("reissue", companyCred.getOfferReason());

        // Verify issuance policy is loaded
        assertNotNull(companyCred.getIssuancePolicy(), "Issuance policy should be loaded");
        assertEquals("Scalable trust example", companyCred.getIssuancePolicy().get("id"));

        // Verify input_descriptors is present
        assertTrue(companyCred.getIssuancePolicy().containsKey("input_descriptors"));
    }

    @Test
    @DisplayName("Should parse OrganizationCredential with minimal configuration")
    void shouldParseOrganizationCredentialWithMinimalConfiguration() {
        // Find OrganizationCredential in the loaded configuration
        CredentialMetadataConfig.CredentialConfig orgCred = credentialMetadataConfig.getSupported().stream()
                .filter(c -> "OrganizationCredential".equals(c.getCredentialType()))
                .findFirst()
                .orElse(null);

        assertNotNull(orgCred, "OrganizationCredential should be loaded");
        assertEquals("https://example.com/schemas/organization-credential.json",
                orgCred.getCredentialSchema());

        // ID should be null or empty (will be auto-generated)
        // Type and other fields may use defaults
    }

    @Test
    @DisplayName("Should load all three example credentials")
    void shouldLoadAllThreeExampleCredentials() {
        // Verify we have all three credentials from the example file
        long count = credentialMetadataConfig.getSupported().stream()
                .filter(c -> c.getCredentialType() != null)
                .count();

        assertTrue(count >= 3, "Should load at least 3 credentials from the example file");
    }
}

