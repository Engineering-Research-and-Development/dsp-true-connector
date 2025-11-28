package it.eng.connector.tck;

import it.eng.connector.integration.BaseIntegrationTest;
import org.eclipse.dataspacetck.core.system.ConsoleMonitor;
import org.eclipse.dataspacetck.runtime.TckRuntime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ActiveProfiles("tck")
@Profile("tck")
//@Disabled("Disabled until WE can run it in CI/CD")
public class TCKCompliance extends BaseIntegrationTest {

    @Test
    void assertDspCompatibility() throws IOException {
        var monitor = new ConsoleMonitor(true, true);

        Map<String, String> properties = createProperties();

        var result = TckRuntime.Builder.newInstance()
                .properties(properties) // Add any additional properties if needed
                .addPackage("org.eclipse.dataspacetck.dsp.verification")
                .monitor(monitor)
                .build()
                .execute();

        if (!result.getFailures().isEmpty()) {
            var failures = result.getFailures().stream()
                    .map(f -> "- " + f.getTestIdentifier().getDisplayName() + " (" + f.getException() + ")")
                    .collect(Collectors.joining("\n"));
            Assertions.fail(result.getTotalFailureCount() + " TCK test cases failed:\n" + failures);
        }
    }

    private Map<String, String> createProperties() {
        Map<String, String> properties = new HashMap<>();
        // Basic configuration
        properties.put("dataspacetck.debug", "true");
        properties.put("dataspacetck.launcher", "org.eclipse.dataspacetck.dsp.system.DspSystemLauncher");
        properties.put("dataspacetck.dsp.local.connector", "false");
        properties.put("dataspacetck.dsp.connector.agent.id", "CONNECTOR_UNDER_TEST");
        properties.put("dataspacetck.dsp.connector.http.url", "http://localhost:8080");
        properties.put("dataspacetck.dsp.connector.http.base.url", "http://localhost:8080");
        properties.put("dataspacetck.dsp.connector.http.headers.authorization", "Basic Y29ubmVjdG9yQG1haWwuY29tOnBhc3N3b3Jk");
        properties.put("dataspacetck.dsp.connector.negotiation.initiate.url", "http://localhost:8080/consumer/negotiations/tck");
        properties.put("dataspacetck.dsp.connector.transfer.initiate.url", "http://localhost:8080/consumer/transfers/tck");


        // Sets the dataset and offer ids to use for contract negotiation scenarios
        // contract negotiation provider
        // per user's mapping: CN_01_01..04, CN_02_01..07, CN_03_01..04
        int[] cnProviderMax = {4, 7, 4};
        for (int i = 1; i <= 3; i++) {
            int max = cnProviderMax[i - 1];
            for (int j = 1; j <= max; j++) {
                String prefix = String.format("CN_%02d_%02d_", i, j);
                String datasetId = String.format("ACN%02d%02d", i, j);
                String offerId = String.format("CD123:%s:456", datasetId);
                properties.put(prefix + "DATASETID", datasetId);
                properties.put(prefix + "OFFERID", offerId);
            }
        }

        // contract negotiation consumer
        // per user's mapping: CN_C_01_01..04, CN_C_02_01..06, CN_C_03_01..06
        int[] cnConsumerMax = {4, 6, 6};
        for (int i = 1; i <= 3; i++) {
            int max = cnConsumerMax[i - 1];
            for (int j = 1; j <= max; j++) {
                properties.put(String.format("CN_C_%02d_%02d_DATASETID", i, j), String.format("ACNC%02d%02d", i, j));
            }
        }

        // Transfer process provider properties
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 6; j++) {
                String prefix = String.format("TP_%02d_%02d_", i, j);
                properties.put(prefix + "AGREEMENTID", "ATP0" + i + "0" + j);
                properties.put(prefix + "FORMAT", "HttpData-PULL");
            }
        }

        // Transfer process consumer properties
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 6; j++) {
                String prefix = String.format("TP_C_%02d_%02d_", i, j);
                properties.put(prefix + "AGREEMENTID", "ATPC0" + i + "0" + j);
                properties.put(prefix + "FORMAT", "HttpData-PULL");
            }
        }

        // Catalog properties
        for (int i = 1; i <= 2; i++) {
            // datasetId from initial_data_tck.json
            properties.put(String.format("CAT_01_%02d_DATASETID", i), "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5");
        }
        properties.put(String.format("CAT_01_%02d_DATASETID", 3), "dataset_not_found");
        return properties;
    }

}
