package it.eng.catalog.model;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class DatasetTest {

    @Test
    @DisplayName("Verify valid plain object serialization")
    public void testPlain() {
        String result = CatalogSerializer.serializePlain(CatalogMockObjectUtil.DATASET);
        assertNotNull(result);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.ID));
        assertTrue(result.contains(DSpaceConstants.KEYWORD));
        assertTrue(result.contains(DSpaceConstants.THEME));
        assertTrue(result.contains(DSpaceConstants.CONFORMSTO));

        assertTrue(result.contains(DSpaceConstants.CREATOR));
        assertTrue(result.contains(DSpaceConstants.DESCRIPTION));
        assertTrue(result.contains(DSpaceConstants.IDENTIFIER));
        assertTrue(result.contains(DSpaceConstants.ISSUED));
        assertTrue(result.contains(DSpaceConstants.MODIFIED));
        assertTrue(result.contains(DSpaceConstants.MODIFIED));
        assertTrue(result.contains(DSpaceConstants.DISTRIBUTION));

        Dataset javaObj = CatalogSerializer.deserializePlain(result, Dataset.class);
        validateDataset(javaObj);
    }

    @Test
    @DisplayName("Verify valid protocol object serialization")
    public void testProtocol() {
        JsonNode result = CatalogSerializer.serializeProtocolJsonNode(CatalogMockObjectUtil.DATASET);
        assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
        assertNotNull(result.get(DSpaceConstants.TYPE).asText());
        assertNotNull(result.get(DSpaceConstants.DCAT_KEYWORD).asText());
        assertNotNull(result.get(DSpaceConstants.DCAT_THEME).asText());
        assertNotNull(result.get(DSpaceConstants.DCT_CONFORMSTO).asText());

        assertNotNull(result.get(DSpaceConstants.DCT_CREATOR).asText());
        assertNotNull(result.get(DSpaceConstants.DCT_DESCRIPTION).asText());
        assertNotNull(result.get(DSpaceConstants.DCT_IDENTIFIER).asText());
        assertNotNull(result.get(DSpaceConstants.DCT_ISSUED).asText());
        assertNotNull(result.get(DSpaceConstants.DCT_MODIFIED).asText());
        assertNotNull(result.get(DSpaceConstants.DCT_MODIFIED).asText());
        assertNotNull(result.get(DSpaceConstants.DCAT_DISTRIBUTION).asText());

        Dataset javaObj = CatalogSerializer.deserializeProtocol(result, Dataset.class);
        validateDataset(javaObj);
    }

    @Test
    @DisplayName("Missing @context and @type")
    public void missingContextAndType() {
        JsonNode result = CatalogSerializer.serializePlainJsonNode(CatalogMockObjectUtil.DATASET);
        assertThrows(ValidationException.class, () -> CatalogSerializer.deserializeProtocol(result, Dataset.class));
    }

    @Test
    @DisplayName("No required fields")
    public void validateInvalid() {
        assertThrows(ValidationException.class,
                () -> Dataset.Builder.newInstance()
                        .build());
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        Dataset dataset = CatalogMockObjectUtil.DATASET;
        String ss = CatalogSerializer.serializePlain(dataset);
        Dataset dataset2 = CatalogSerializer.deserializePlain(ss, Dataset.class);
        assertThat(dataset).usingRecursiveComparison().isEqualTo(dataset2);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        Dataset dataset = CatalogMockObjectUtil.DATASET;
        String ss = CatalogSerializer.serializeProtocol(dataset);
        Dataset dataset2 = CatalogSerializer.deserializeProtocol(ss, Dataset.class);
        assertThat(dataset).usingRecursiveComparison().isEqualTo(dataset2);
    }

    private void validateDataset(Dataset javaObj) {
        assertNotNull(javaObj);
        assertNotNull(javaObj.getConformsTo());
        assertNotNull(javaObj.getCreator());
        assertNotNull(javaObj.getDescription().iterator().next());
        assertNotNull(javaObj.getDistribution().iterator().next());
        assertNotNull(javaObj.getIdentifier());
        assertNotNull(javaObj.getIssued());
        assertNotNull(javaObj.getKeyword());
        assertEquals(2, javaObj.getKeyword().size());
        assertEquals(3, javaObj.getTheme().size());
        assertNotNull(javaObj.getModified());
        assertNotNull(javaObj.getTheme());
        assertNotNull(javaObj.getTitle());
    }

    @Test
    @DisplayName("Validate protocol - valid dataset")
    public void validateProtocolValid() {
        Dataset dataset = CatalogMockObjectUtil.DATASET;
        // This should not throw an exception
        dataset.validateProtocol();
    }

    @Test
    @DisplayName("Validate protocol - empty hasPolicy")
    public void validateProtocolEmptyHasPolicy() {
        Dataset dataset = Dataset.Builder.newInstance()
                .id(CatalogMockObjectUtil.DATASET_ID)
                .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
                .creator(CatalogMockObjectUtil.CREATOR)
                .distribution(CatalogMockObjectUtil.DATASET.getDistribution())
                .description(CatalogMockObjectUtil.DATASET.getDescription())
                .issued(CatalogMockObjectUtil.ISSUED)
                .keyword(CatalogMockObjectUtil.DATASET.getKeyword())
                .identifier(CatalogMockObjectUtil.IDENTIFIER)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .theme(CatalogMockObjectUtil.DATASET.getTheme())
                .title(CatalogMockObjectUtil.TITLE)
                .hasPolicy(Collections.emptySet()) // Empty hasPolicy
                .build();

        ValidationException exception = assertThrows(ValidationException.class,
                dataset::validateProtocol);
        assertEquals("Dataset must have at least one Offer", exception.getMessage());
    }

    @Test
    @DisplayName("Validate protocol - null Offer in hasPolicy")
    public void validateProtocolNullPolicy() {
        // Create a set with a single null element using Collections.singleton(null)
        Set<Offer> policies = Collections.singleton(null);

        Dataset dataset = Dataset.Builder.newInstance()
                .id(CatalogMockObjectUtil.DATASET_ID)
                .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
                .creator(CatalogMockObjectUtil.CREATOR)
                .distribution(CatalogMockObjectUtil.DATASET.getDistribution())
                .description(CatalogMockObjectUtil.DATASET.getDescription())
                .issued(CatalogMockObjectUtil.ISSUED)
                .keyword(CatalogMockObjectUtil.DATASET.getKeyword())
                .identifier(CatalogMockObjectUtil.IDENTIFIER)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .theme(CatalogMockObjectUtil.DATASET.getTheme())
                .title(CatalogMockObjectUtil.TITLE)
                .hasPolicy(policies) // Contains null Offer
                .build();

        ValidationException exception = assertThrows(ValidationException.class,
                dataset::validateProtocol);
        assertEquals("Dataset must have at least one non-null Offer", exception.getMessage());
    }

    @Test
    @DisplayName("Validate protocol - missing distribution")
    public void validateProtocolMissingDistribution() {
        Dataset dataset = Dataset.Builder.newInstance()
                .id(CatalogMockObjectUtil.DATASET_ID)
                .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
                .creator(CatalogMockObjectUtil.CREATOR)
                .description(CatalogMockObjectUtil.DATASET.getDescription())
                .issued(CatalogMockObjectUtil.ISSUED)
                .keyword(CatalogMockObjectUtil.DATASET.getKeyword())
                .identifier(CatalogMockObjectUtil.IDENTIFIER)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .theme(CatalogMockObjectUtil.DATASET.getTheme())
                .title(CatalogMockObjectUtil.TITLE)
                .hasPolicy(CatalogMockObjectUtil.DATASET.getHasPolicy())
                // distribution is missing
                .build();

        ValidationException exception = assertThrows(ValidationException.class,
                dataset::validateProtocol);
        assertEquals("Dataset must have at least one Distribution", exception.getMessage());
    }

    @Test
    @DisplayName("Validate protocol - empty distribution")
    public void validateProtocolEmptyDistribution() {
        Dataset dataset = Dataset.Builder.newInstance()
                .id(CatalogMockObjectUtil.DATASET_ID)
                .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
                .creator(CatalogMockObjectUtil.CREATOR)
                .distribution(Collections.emptySet()) // Empty distribution
                .description(CatalogMockObjectUtil.DATASET.getDescription())
                .issued(CatalogMockObjectUtil.ISSUED)
                .keyword(CatalogMockObjectUtil.DATASET.getKeyword())
                .identifier(CatalogMockObjectUtil.IDENTIFIER)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .theme(CatalogMockObjectUtil.DATASET.getTheme())
                .title(CatalogMockObjectUtil.TITLE)
                .hasPolicy(CatalogMockObjectUtil.DATASET.getHasPolicy())
                .build();

        ValidationException exception = assertThrows(ValidationException.class,
                dataset::validateProtocol);
        assertEquals("Dataset must have at least one Distribution", exception.getMessage());
    }

    @Test
    @DisplayName("Validate protocol - null distribution in distribution")
    public void validateProtocolNullDistribution() {
        // Create a set with a single null element using Collections.singleton(null)
        Set<Distribution> distributions = Collections.singleton(null);

        Dataset dataset = Dataset.Builder.newInstance()
                .id(CatalogMockObjectUtil.DATASET_ID)
                .conformsTo(CatalogMockObjectUtil.CONFORMSTO)
                .creator(CatalogMockObjectUtil.CREATOR)
                .distribution(distributions) // Contains null distribution
                .description(CatalogMockObjectUtil.DATASET.getDescription())
                .issued(CatalogMockObjectUtil.ISSUED)
                .keyword(CatalogMockObjectUtil.DATASET.getKeyword())
                .identifier(CatalogMockObjectUtil.IDENTIFIER)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .theme(CatalogMockObjectUtil.DATASET.getTheme())
                .title(CatalogMockObjectUtil.TITLE)
                .hasPolicy(CatalogMockObjectUtil.DATASET.getHasPolicy())
                .build();

        ValidationException exception = assertThrows(ValidationException.class,
                dataset::validateProtocol);
        assertEquals("Dataset must have at least one non-null Distribution", exception.getMessage());
    }
}
