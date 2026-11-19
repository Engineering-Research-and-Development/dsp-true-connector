package it.eng.dcp.issuer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.issuer.config.CredentialMetadataConfig;
import it.eng.dcp.issuer.config.CredentialMetadataConfigLoader;
import it.eng.dcp.issuer.config.IssuerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Validates the issuer metadata response against the published DCP v1.0 IssuerMetadata JSON Schema.
 *
 * <p>The published schemas and the schemas they reference are copied to {@code src/test/resources/dcp-schemas}
 * so the test runs without network access. JSON Schema 2019-09 treats {@code format} as an annotation, so
 * format assertions stay off (see the gap inventory note on {@code CredentialObject.id}).
 */
class IssuerMetadataSchemaTest {

    private static final String ISSUER_METADATA_SCHEMA =
            "https://w3id.org/dspace-dcp/v1.0/issuance/issuer-metadata-schema.json";

    private final ObjectMapper mapper = new ObjectMapper();

    private static JsonSchema issuerMetadataSchema() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909,
                builder -> builder.schemaMappers(mappers -> mappers
                        .mapPrefix("https://w3id.org/dspace-dcp/v1.0/issuance/", "classpath:dcp-schemas/")
                        .mapPrefix("https://w3id.org/dspace-dcp/v1.0/common/", "classpath:dcp-schemas/")
                        .mapPrefix("https://identity.foundation/presentation-exchange/schemas/",
                                "classpath:dcp-schemas/")
                        .mapPrefix("https://identity.foundation/claim-format-registry/schemas/"
                                        + "presentation-definition-claim-format-designations.json",
                                "classpath:dcp-schemas/pd-claim-format.json")));
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(false).build();
        return factory.getSchema(SchemaLocation.of(ISSUER_METADATA_SCHEMA), config);
    }

    private static CredentialMetadataService serviceFor(CredentialMetadataConfig config) {
        IssuerProperties issuerProperties = mock(IssuerProperties.class);
        when(issuerProperties.getConnectorDid()).thenReturn("did:web:localhost%3A8084:issuer");
        when(issuerProperties.getSupportedProfiles()).thenReturn(List.of("vc20-bssl/jwt"));
        return new CredentialMetadataService(issuerProperties, config);
    }

    private Set<ValidationMessage> validate(IssuerMetadata metadata) throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(metadata));
        return issuerMetadataSchema().validate(json);
    }

    @Test
    @DisplayName("Metadata built from the shipped configuration is valid against the published schema")
    void shippedConfigurationIsSchemaValid() throws Exception {
        CredentialMetadataService service = serviceFor(new CredentialMetadataConfigLoader().credentialMetadataConfig());
        assertDoesNotThrow(service::validateSupportedCredentials);

        Set<ValidationMessage> errors = validate(service.buildIssuerMetadata());

        assertTrue(errors.isEmpty(), () -> "Schema violations: " + errors);
    }

    @Test
    @DisplayName("Metadata with no supported credentials is valid against the published schema")
    void emptyConfigurationIsSchemaValid() throws Exception {
        Set<ValidationMessage> errors = validate(serviceFor(new CredentialMetadataConfig()).buildIssuerMetadata());

        assertTrue(errors.isEmpty(), () -> "Schema violations: " + errors);
    }

    @Test
    @DisplayName("An entry without offerReason violates the published schema")
    void entryWithoutOfferReasonIsSchemaInvalid() throws Exception {
        CredentialMetadataConfig config = new CredentialMetadataConfig();
        CredentialMetadataConfig.CredentialConfig entry = new CredentialMetadataConfig.CredentialConfig();
        entry.setId("id-1");
        entry.setCredentialType("MembershipCredential");
        entry.setCredentialSchema("https://example.com/schemas/membership.json");
        entry.setProfile("vc20-bssl/jwt");
        config.getSupported().add(entry);

        Set<ValidationMessage> errors = validate(serviceFor(config).buildIssuerMetadata());

        assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("offerReason")), () -> "Got: " + errors);
    }
}
