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

public class DistributionTest {

    @Test
    @DisplayName("Verify valid plain object serialization")
    public void testPlain() {
        String result = CatalogSerializer.serializePlain(CatalogMockObjectUtil.DISTRIBUTION);
        assertNotNull(result);
        assertFalse(result.contains(DSpaceConstants.CONTEXT));
        assertFalse(result.contains(DSpaceConstants.TYPE));
        assertTrue(result.contains(DSpaceConstants.TITLE));
        assertTrue(result.contains(DSpaceConstants.DESCRIPTION));
        assertTrue(result.contains(DSpaceConstants.ISSUED));
        assertTrue(result.contains(DSpaceConstants.FORMAT));
        assertTrue(result.contains(DSpaceConstants.MODIFIED));
        assertTrue(result.contains(DSpaceConstants.ACCESS_SERVICE));

        Distribution javaObj = CatalogSerializer.deserializePlain(result, Distribution.class);
        assertNotNull(javaObj);
        validateDistribution(javaObj);
    }

    @Test
    @DisplayName("Verify valid protocol object serialization")
    public void testProtocol() {
        JsonNode result = CatalogSerializer.serializeProtocolJsonNode(CatalogMockObjectUtil.DISTRIBUTION);
        assertNull(result.get(DSpaceConstants.CONTEXT), "Not root element to have context");
        assertNotNull(result.get(DSpaceConstants.TYPE).asText());
        assertNotNull(result.get(DSpaceConstants.DCT_TITLE).asText());
        assertNotNull(result.get(DSpaceConstants.DCT_DESCRIPTION).asText());
        assertNotNull(result.get(DSpaceConstants.DCT_MODIFIED).asText());
        assertNotNull(result.get(DSpaceConstants.DCT_ISSUED).asText());
        assertNotNull(result.get(DSpaceConstants.DCT_FORMAT));
        assertNotNull(result.get(DSpaceConstants.DCAT_ACCESS_SERVICE).asText());

        Distribution javaObj = CatalogSerializer.deserializeProtocol(result, Distribution.class);
        validateDistribution(javaObj);
    }

    @Test
    @DisplayName("Missing @context and @type")
    public void missingContextAndType() {
        JsonNode result = CatalogSerializer.serializePlainJsonNode(CatalogMockObjectUtil.DISTRIBUTION);
        assertThrows(ValidationException.class, () -> CatalogSerializer.deserializeProtocol(result, Distribution.class));
    }

    @Test
    @DisplayName("No required fields")
    public void validateInvalid() {
        assertThrows(ValidationException.class,
                () -> Distribution.Builder.newInstance()
                        .build());
    }

    @Test
    @DisplayName("Plain serialize/deserialize")
    public void equalsTestPlain() {
        Distribution distribution = CatalogMockObjectUtil.DISTRIBUTION;
        String ss = CatalogSerializer.serializePlain(distribution);
        Distribution distribution2 = CatalogSerializer.deserializePlain(ss, Distribution.class);
        assertThat(distribution).usingRecursiveComparison().isEqualTo(distribution2);
    }

    @Test
    @DisplayName("Protocol serialize/deserialize")
    public void equalsTestProtocol() {
        Distribution distribution = CatalogMockObjectUtil.DISTRIBUTION;
        String ss = CatalogSerializer.serializeProtocol(distribution);
        Distribution distribution2 = CatalogSerializer.deserializeProtocol(ss, Distribution.class);
        assertThat(distribution).usingRecursiveComparison().isEqualTo(distribution2);
    }

    private void validateDistribution(Distribution distribution) {
        assertNotNull(distribution.getTitle());
        assertNotNull(distribution.getAccessService());
        assertNotNull(distribution.getDescription());
        assertNotNull(distribution.getHasPolicy());
        assertNotNull(distribution.getIssued());
        assertNotNull(distribution.getModified());
        assertNotNull(distribution.getFormat().getId());
    }

    @Test
    @DisplayName("Validate protocol - valid distribution")
    public void validateProtocolValid() {
        Distribution distribution = CatalogMockObjectUtil.DISTRIBUTION;
        // This should not throw an exception
        distribution.validateProtocol();
    }

    @Test
    @DisplayName("Validate - missing accessService")
    public void validateMissingAccessService() {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> Distribution.Builder.newInstance()
                        .title(CatalogMockObjectUtil.TITLE)
                        .description(CatalogMockObjectUtil.DISTRIBUTION.getDescription())
                        .issued(CatalogMockObjectUtil.ISSUED)
                        .modified(CatalogMockObjectUtil.MODIFIED)
                        .format(CatalogMockObjectUtil.DISTRIBUTION.getFormat())
                        .hasPolicy(CatalogMockObjectUtil.DISTRIBUTION.getHasPolicy())
                        // accessService is missing
                        .build());
        assertEquals("Distribution - accessService must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Validate protocol - empty accessService")
    public void validateProtocolEmptyAccessService() {
        Distribution distribution = Distribution.Builder.newInstance()
                .title(CatalogMockObjectUtil.TITLE)
                .description(CatalogMockObjectUtil.DISTRIBUTION.getDescription())
                .issued(CatalogMockObjectUtil.ISSUED)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .format(CatalogMockObjectUtil.DISTRIBUTION.getFormat())
                .hasPolicy(CatalogMockObjectUtil.DISTRIBUTION.getHasPolicy())
                .accessService(Collections.emptySet()) // Empty accessService
                .build();

        ValidationException exception = assertThrows(ValidationException.class,
                distribution::validateProtocol);
        assertEquals("Distribution must have at least one AccessService", exception.getMessage());
    }

    @Test
    @DisplayName("Validate protocol - null AccessService in accessService")
    public void validateProtocolNullAccessService() {
        // Create a set with a single null element
        Set<DataService> accessServices = Collections.singleton(null);

        Distribution distribution = Distribution.Builder.newInstance()
                .title(CatalogMockObjectUtil.TITLE)
                .description(CatalogMockObjectUtil.DISTRIBUTION.getDescription())
                .issued(CatalogMockObjectUtil.ISSUED)
                .modified(CatalogMockObjectUtil.MODIFIED)
                .format(CatalogMockObjectUtil.DISTRIBUTION.getFormat())
                .hasPolicy(CatalogMockObjectUtil.DISTRIBUTION.getHasPolicy())
                .accessService(accessServices) // Contains null AccessService
                .build();

        ValidationException exception = assertThrows(ValidationException.class,
                distribution::validateProtocol);
        assertEquals("Distribution must have at least one non-null AccessService", exception.getMessage());
    }
}
