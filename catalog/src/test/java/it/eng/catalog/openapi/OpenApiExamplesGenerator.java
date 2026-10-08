package it.eng.catalog.openapi;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates plain JSON examples for the catalog management API.
 */
public final class OpenApiExamplesGenerator {

    private OpenApiExamplesGenerator() {
    }

    /**
     * Writes fixture-backed management API examples into the catalog module output directory.
     *
     * @param args command-line arguments, which are not used
     * @throws IOException if an example cannot be written
     * @throws URISyntaxException if the test classpath location is not a valid URI
     */
    public static void main(final String[] args) throws IOException, URISyntaxException {
        Files.createDirectories(examplesDirectory());
        writeExample("catalog", CatalogMockObjectUtil.CATALOG);
        writeExample("dataset", CatalogMockObjectUtil.DATASET_WITH_ARTIFACT);
        writeExample("distribution", CatalogMockObjectUtil.DISTRIBUTION);
        writeExample("data-service", CatalogMockObjectUtil.DATA_SERVICE);
        writeExample("offer", CatalogMockObjectUtil.OFFER);
        writeExample("forwarded-catalog", forwardedRequest());
        writeExample("forwarded-formats", forwardedRequest());
    }

    private static Path examplesDirectory() throws URISyntaxException {
        Path testClassesDirectory = Path.of(
                OpenApiExamplesGenerator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClassesDirectory.getParent().resolve("classes/openapi/examples");
    }

    private static void writeExample(final String name, final Object payload)
            throws IOException, URISyntaxException {
        Path example = examplesDirectory().resolve(name + ".plain.json");
        Files.writeString(example, CatalogSerializer.serializePlain(payload), StandardCharsets.UTF_8);
    }

    private static ObjectNode forwardedRequest() {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("Forward-To", CatalogMockObjectUtil.ENDPOINT_URL);
        return request;
    }
}
