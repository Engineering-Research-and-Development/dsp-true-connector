package it.eng.catalog.openapi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.eng.catalog.util.CatalogMockObjectUtil;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiExamplesGeneratorTest {

    private static final String GENERATOR_CLASS_NAME = "it.eng.catalog.openapi.OpenApiExamplesGenerator";
    private static final List<String> EXAMPLE_FILES = List.of(
            "catalog.plain.json",
            "dataset.plain.json",
            "distribution.plain.json",
            "data-service.plain.json",
            "offer.plain.json",
            "forwarded-catalog.plain.json",
            "forwarded-formats.plain.json");

    @Test
    @DisplayName("Generator writes plain JSON examples for catalog API payloads")
    void writesPlainJsonExamples() throws Exception {
        assertDoesNotThrow(() -> {
            Class<?> generator = Class.forName(GENERATOR_CLASS_NAME);
            Method main = generator.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
        });

        for (String exampleFile : EXAMPLE_FILES) {
            Path example = examplesDirectory().resolve(exampleFile);
            assertTrue(Files.isRegularFile(example) && Files.size(example) > 0,
                    () -> "Expected a non-empty generated example at " + example);
        }
        String datasetExample = Files.readString(examplesDirectory().resolve("dataset.plain.json"));
        assertTrue(datasetExample.contains(CatalogMockObjectUtil.DATASET_ID),
                "Dataset example should use the seeded dataset ID");
    }

    private static Path examplesDirectory() throws URISyntaxException {
        Path testClassesDirectory = Path.of(
                OpenApiExamplesGeneratorTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClassesDirectory.getParent().resolve("classes/openapi/examples");
    }
}
