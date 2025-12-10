package it.eng.dcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class VerifyTypeFieldPresent {
    public static void main(String[] args) {
        try {
            PresentationResponseMessage msg = PresentationResponseMessage.Builder.newInstance()
                    .presentation(List.of(Map.of("vpId", "vp-1")))
                    .presentationSubmission(Map.of("descriptor_map", List.of(Map.of("id", "desc-1"))))
                    .build();

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(msg);

            System.out.println("=== Serialized PresentationResponseMessage ===");
            System.out.println(json);
            System.out.println("==============================================");

            // Check if @type is present
            boolean hasType = json.contains("\"@type\"");
            boolean hasContext = json.contains("\"@context\"");

            System.out.println("Contains @type: " + hasType);
            System.out.println("Contains @context: " + hasContext);

            if (hasType && hasContext) {
                System.out.println("\n✓ SUCCESS: Both @type and @context are present in JSON!");
            } else {
                System.out.println("\n✗ FAILURE: Missing fields in JSON!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

