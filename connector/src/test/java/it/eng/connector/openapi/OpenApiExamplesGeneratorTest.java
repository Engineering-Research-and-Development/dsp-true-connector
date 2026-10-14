package it.eng.connector.openapi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiExamplesGeneratorTest {

    private static final String GENERATOR_CLASS_NAME = "it.eng.connector.openapi.OpenApiExamplesGenerator";
    private static final List<String> EXAMPLE_FILES = List.of(
            "login-admin.plain.json",
            "login-connector.plain.json");

    @Test
    @DisplayName("Generator writes login examples for seeded admin and connector accounts")
    void writesSeededLoginExamples() throws Exception {
        assertDoesNotThrow(() -> {
            Class<?> generator = Class.forName(GENERATOR_CLASS_NAME);
            Method main = generator.getMethod("main", String[].class);
            main.invoke(null, (Object) new String[0]);
        });

        ObjectMapper objectMapper = new ObjectMapper();
        List<JsonNode> examples = new ArrayList<>();
        for (String exampleFile : EXAMPLE_FILES) {
            Path example = examplesDirectory().resolve(exampleFile);
            assertTrue(Files.isRegularFile(example) && Files.size(example) > 0,
                    () -> "Expected a non-empty generated example at " + example);
            examples.add(objectMapper.readTree(example.toFile()));
        }

        assertEquals(List.of("admin@mail.com", "connector@mail.com"),
                examples.stream().map(example -> example.path("email").asText()).toList());
        assertTrue(examples.stream().allMatch(example -> "password".equals(example.path("password").asText())));
    }

    private static Path examplesDirectory() throws URISyntaxException {
        Path testClassesDirectory = Path.of(
                OpenApiExamplesGeneratorTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClassesDirectory.getParent().resolve("classes/openapi/examples");
    }
}
