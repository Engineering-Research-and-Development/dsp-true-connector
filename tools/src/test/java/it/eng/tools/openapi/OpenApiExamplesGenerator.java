package it.eng.tools.openapi;

import it.eng.tools.serializer.ToolsSerializer;
import it.eng.tools.util.ToolsMockObjectUtil;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates plain JSON examples for tools administration APIs.
 */
public final class OpenApiExamplesGenerator {

    private OpenApiExamplesGenerator() {
    }

    /**
     * Writes fixture-backed administration API examples into the tools module output directory.
     *
     * @param args command-line arguments, which are not used
     * @throws IOException if an example cannot be written
     * @throws URISyntaxException if the test classpath location is not a valid URI
     */
    public static void main(final String[] args) throws IOException, URISyntaxException {
        Files.createDirectories(examplesDirectory());
        writeExample("application-properties", List.of(ToolsMockObjectUtil.APPLICATION_PROPERTY_FOR_UPDATE));
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
