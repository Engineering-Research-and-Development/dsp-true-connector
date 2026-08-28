package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InitialDataProfileConsistencyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("local consumer and provider profiles share the same seeded agreement identifiers")
    void localConsumerAndProviderProfilesShareTheSameSeededAgreementIdentifiers() throws IOException {
        JsonNode consumerProfile = readProfile("initial_data-consumer.json");
        JsonNode providerProfile = readProfile("initial_data-provider.json");

        Set<String> consumerAgreementIds = extractIds(consumerProfile.path("agreements"));
        Set<String> providerAgreementIds = extractIds(providerProfile.path("agreements"));

        assertEquals(consumerAgreementIds, providerAgreementIds);
        assertEquals(consumerAgreementIds, extractFieldValues(consumerProfile.path("transfer_process"), "agreementId"));
        assertEquals(providerAgreementIds, extractFieldValues(providerProfile.path("transfer_process"), "agreementId"));
        assertEquals(consumerAgreementIds,
                extractReferenceIds(consumerProfile.path("contract_negotiations"), "agreement"));
        assertEquals(providerAgreementIds,
                extractReferenceIds(providerProfile.path("contract_negotiations"), "agreement"));
        assertEquals(consumerAgreementIds,
                extractFieldValues(consumerProfile.path("policy_enforcements"), "agreementId"));
        assertEquals(providerAgreementIds,
                extractFieldValues(providerProfile.path("policy_enforcements"), "agreementId"));
    }

    private JsonNode readProfile(String fileName) throws IOException {
        try (InputStream inputStream = new ClassPathResource(fileName).getInputStream()) {
            return objectMapper.readTree(inputStream);
        }
    }

    private Set<String> extractIds(JsonNode nodes) {
        return extractFieldValues(nodes, "id");
    }

    private Set<String> extractFieldValues(JsonNode nodes, String fieldName) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode node : nodes) {
            values.add(node.path(fieldName).asText());
        }
        return values;
    }

    private Set<String> extractReferenceIds(JsonNode nodes, String fieldName) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode node : nodes) {
            values.add(node.path(fieldName).path("$id").asText());
        }
        return values;
    }
}
