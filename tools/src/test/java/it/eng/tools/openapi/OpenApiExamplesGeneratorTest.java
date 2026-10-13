package it.eng.tools.openapi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiExamplesGeneratorTest {

    private static final String GENERATOR_CLASS_NAME = "it.eng.tools.openapi.OpenApiExamplesGenerator";
    private static final String EXAMPLE_FILE = "application-properties.plain.json";

    @Test
    @DisplayName("Generator writes a plain JSON example for application property updates")
    void writesPlainJsonExample() throws Exception {
        assertDoesNotThrow(() -> {
            Class<?> generator = Class.forName(GENERATOR_CLASS_NAME);
            Method main = generator.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
        });

        Path example = examplesDirectory().resolve(EXAMPLE_FILE);
        assertTrue(Files.isRegularFile(example) && Files.size(example) > 0,
                () -> "Expected a non-empty generated example at " + example);

        JsonNode properties = new ObjectMapper().readTree(example.toFile());
        assertTrue(properties.isArray());
        assertEquals(1, properties.size());
        assertEquals("Sample key", properties.get(0).path("key").asText());
        assertEquals("Sample value", properties.get(0).path("value").asText());
    }

    private static Path examplesDirectory() throws URISyntaxException {
        Path testClassesDirectory = Path.of(
                OpenApiExamplesGeneratorTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClassesDirectory.getParent().resolve("classes/openapi/examples");
    }
}
