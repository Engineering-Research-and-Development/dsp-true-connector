package it.eng.negotiation.openapi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.eng.negotiation.model.NegotiationMockObjectUtil;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiExamplesGeneratorTest {

    private static final String GENERATOR_CLASS_NAME = "it.eng.negotiation.openapi.OpenApiExamplesGenerator";
    private static final List<String> EXAMPLE_FILES = List.of(
            "contract-request.plain.json",
            "contract-counteroffer.plain.json",
            "contract-offer.plain.json",
            "offer-counteroffer.plain.json");

    @Test
    @DisplayName("Generator writes plain JSON examples for negotiation management requests")
    void writesPlainJsonExamples() throws Exception {
        assertDoesNotThrow(() -> {
            Class<?> generator = Class.forName(GENERATOR_CLASS_NAME);
            generator.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        });

        for (String exampleFile : EXAMPLE_FILES) {
            Path example = examplesDirectory().resolve(exampleFile);
            assertTrue(Files.isRegularFile(example) && Files.size(example) > 0,
                    () -> "Expected a non-empty generated example at " + example);
        }
        String requestExample = Files.readString(examplesDirectory().resolve("contract-request.plain.json"));
        assertTrue(requestExample.contains(NegotiationMockObjectUtil.TARGET),
                "Contract request example should use the negotiation offer fixture");
    }

    private static Path examplesDirectory() throws URISyntaxException {
        Path testClassesDirectory = Path.of(
                OpenApiExamplesGeneratorTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClassesDirectory.getParent().resolve("classes/openapi/examples");
    }
}
