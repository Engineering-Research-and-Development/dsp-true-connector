package it.eng.negotiation.openapi;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.serializer.NegotiationSerializer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates plain JSON examples for the negotiation management API.
 */
public final class OpenApiExamplesGenerator {

    private OpenApiExamplesGenerator() {
    }

    /**
     * Writes fixture-backed management API examples into the negotiation module output directory.
     *
     * @param args command-line arguments, which are not used
     * @throws IOException if an example cannot be written
     * @throws URISyntaxException if the test classpath location is not a valid URI
     */
    public static void main(final String[] args) throws IOException, URISyntaxException {
        Files.createDirectories(examplesDirectory());
        writeExample("contract-request", requestWithOffer());
        writeExample("contract-counteroffer", NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID);
        writeExample("contract-offer", requestWithOffer());
        writeExample("offer-counteroffer", NegotiationMockObjectUtil.OFFER_WITH_ORIGINAL_ID);
    }

    private static Path examplesDirectory() throws URISyntaxException {
        Path testClassesDirectory = Path.of(
                OpenApiExamplesGenerator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClassesDirectory.getParent().resolve("classes/openapi/examples");
    }

    private static void writeExample(final String name, final Object payload)
            throws IOException, URISyntaxException {
        Path example = examplesDirectory().resolve(name + ".plain.json");
        Files.writeString(example, NegotiationSerializer.serializePlain(payload), StandardCharsets.UTF_8);
    }

    private static ObjectNode requestWithOffer() {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        request.set("offer", NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER));
        return request;
    }
}
