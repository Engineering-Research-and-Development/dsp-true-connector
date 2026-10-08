package it.eng.datatransfer.openapi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiExamplesGeneratorTest {

    private static final String GENERATOR_CLASS_NAME = "it.eng.datatransfer.openapi.OpenApiExamplesGenerator";
    private static final String REQUEST_EXAMPLE_FILE = "transfer-request.plain.json";

    @Test
    @DisplayName("Generator writes a plain JSON example for transfer management requests")
    void writesPlainJsonExample() throws Exception {
        assertDoesNotThrow(() -> {
            Class<?> generator = Class.forName(GENERATOR_CLASS_NAME);
            generator.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        });

        Path requestExample = examplesDirectory().resolve(REQUEST_EXAMPLE_FILE);
        assertTrue(Files.isRegularFile(requestExample) && Files.size(requestExample) > 0,
                () -> "Expected a non-empty generated example at " + requestExample);
        String requestJson = Files.readString(requestExample);
        assertTrue(requestJson.contains("\"transferProcessId\""));
        assertTrue(requestJson.contains("\"format\""));
        assertTrue(requestJson.contains(DataTransferFormat.HTTP_PULL.format()));
        assertTrue(requestJson.contains(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED.getId()));
    }

    private static Path examplesDirectory() throws URISyntaxException {
        Path testClassesDirectory = Path.of(
                OpenApiExamplesGeneratorTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClassesDirectory.getParent().resolve("classes/openapi/examples");
    }
}
