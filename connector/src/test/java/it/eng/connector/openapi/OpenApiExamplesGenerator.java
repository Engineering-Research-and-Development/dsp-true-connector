package it.eng.connector.openapi;

import it.eng.connector.model.LoginRequest;
import it.eng.tools.serializer.ToolsSerializer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates plain JSON examples for connector administration APIs.
 */
public final class OpenApiExamplesGenerator {

    private OpenApiExamplesGenerator() {
    }

    /**
     * Writes login examples for the seeded admin and connector accounts into the module output directory.
     *
     * @param args command-line arguments, which are not used
     * @throws IOException if an example cannot be written
     * @throws URISyntaxException if the test classpath location is not a valid URI
     */
    public static void main(final String[] args) throws IOException, URISyntaxException {
        Files.createDirectories(examplesDirectory());
        writeExample("login-admin", new LoginRequest("admin@mail.com", "password"));
        writeExample("login-connector", new LoginRequest("connector@mail.com", "password"));
    }

    private static Path examplesDirectory() throws URISyntaxException {
        Path testClassesDirectory = Path.of(
                OpenApiExamplesGenerator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClassesDirectory.getParent().resolve("classes/openapi/examples");
    }

    private static void writeExample(final String name, final Object payload)
            throws IOException, URISyntaxException {
        Path example = examplesDirectory().resolve(name + ".plain.json");
        Files.writeString(example, ToolsSerializer.serializePlain(payload), StandardCharsets.UTF_8);
    }
}
