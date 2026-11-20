package it.eng.negotiation.openapi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.serializer.NegotiationSerializer;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

    @Test
    @DisplayName("Generator writes protocol JSON-LD examples for negotiation messages")
    void writesProtocolJsonExamples() throws Exception {
        Class<?> generator = Class.forName(GENERATOR_CLASS_NAME);
        generator.getMethod("main", String[].class).invoke(null, (Object) new String[0]);

        Map<String, String> expectedExamples = Map.of(
                "contract-request-initial.protocol.json",
                NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_INITIAL),
                "contract-request-counteroffer.protocol.json",
                NegotiationSerializer.serializeProtocol(
                        NegotiationMockObjectUtil.CONTRACT_REQUEST_MESSAGE_COUNTEROFFER),
                "contract-offer-initial.protocol.json",
                NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE_INITIAL),
                "contract-offer-counteroffer.protocol.json",
                NegotiationSerializer.serializeProtocol(
                        NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE_COUNTEROFFER),
                "contract-agreement.protocol.json",
                NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_AGREEMENT_MESSAGE),
                "contract-agreement-verification.protocol.json",
                NegotiationSerializer.serializeProtocol(
                        NegotiationMockObjectUtil.CONTRACT_AGREEMENT_VERIFICATION_MESSAGE),
                "contract-negotiation-event-accepted.protocol.json",
                NegotiationSerializer.serializeProtocol(
                        NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE_ACCEPTED),
                "contract-negotiation-event-finalized.protocol.json",
                NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_EVENT_MESSAGE),
                "contract-negotiation-termination.protocol.json",
                NegotiationSerializer.serializeProtocol(NegotiationMockObjectUtil.TERMINATION_MESSAGE));

        for (Map.Entry<String, String> expectedExample : expectedExamples.entrySet()) {
            Path example = examplesDirectory().resolve(expectedExample.getKey());
            assertTrue(Files.isRegularFile(example) && Files.size(example) > 0,
                    () -> "Expected a non-empty generated example at " + example);
            assertEquals(expectedExample.getValue(), Files.readString(example), expectedExample.getKey());
        }
    }

    private static Path examplesDirectory() throws URISyntaxException {
        Path testClassesDirectory = Path.of(
                OpenApiExamplesGeneratorTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return testClassesDirectory.getParent().resolve("classes/openapi/examples");
    }
}
