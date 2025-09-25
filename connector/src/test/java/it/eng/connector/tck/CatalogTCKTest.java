package it.eng.connector.tck;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Offer;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.s3.service.S3ClientService;
import org.eclipse.dataspacetck.core.system.ConsoleMonitor;
import org.eclipse.dataspacetck.runtime.TckRuntime;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Disabled
public class CatalogTCKTest extends BaseIntegrationTest {

    @Autowired
    private CatalogRepository catalogRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DataServiceRepository dataServiceRepository;
    @Autowired
    private DistributionRepository distributionRepository;
    @Autowired
    private ArtifactRepository artifactRepository;
    @Autowired
    private S3ClientService s3ClientService;

    private Catalog catalog;
    private Dataset dataset;

    @BeforeEach
    public void populateCatalog() throws Exception {
        catalog = CatalogMockObjectUtil.createNewCatalog();
        dataset = catalog.getDataset().stream().findFirst().orElse(null);

        uploadFile();

        catalogRepository.save(catalog);
        datasetRepository.saveAll(catalog.getDataset());
        dataServiceRepository.saveAll(catalog.getService());
        distributionRepository.saveAll(catalog.getDistribution());
        artifactRepository.save(dataset.getArtifact());
    }

    @AfterEach
    public void cleanup() {
        datasetRepository.deleteAll();
        catalogRepository.deleteAll();
        dataServiceRepository.deleteAll();
        distributionRepository.deleteAll();
        artifactRepository.deleteAll();
    }

    @Test
    void assertDspCompatibility() throws IOException {
        var monitor = new ConsoleMonitor(true, true);

        Map<String, String> properties = createProperties(catalog);

        var result = TckRuntime.Builder.newInstance()
                .properties(properties) // Add any additional properties if needed
                .addPackage("org.eclipse.dataspacetck.dsp.verification")
                .addPackage("org.eclipse.dataspacetck.dsp.verification.catalog")
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

    private Map<String, String> createProperties(Catalog catalog) {
        Map<String, String> properties = new HashMap<>();
        // Basic configuration
        properties.put("dataspacetck.debug", "true");
        properties.put("dataspacetck.launcher", "org.eclipse.dataspacetck.dsp.system.DspSystemLauncher");
        properties.put("dataspacetck.dsp.local.connector", "false");
        properties.put("dataspacetck.dsp.connector.agent.id", "CONNECTOR_UNDER_TEST");
        properties.put("dataspacetck.dsp.connector.http.url", "http://localhost:8090");
        properties.put("dataspacetck.dsp.connector.http.base.url", "http://localhost:8090");
        properties.put("dataspacetck.dsp.connector.http.headers.authorization", "Basic Y29ubmVjdG9yQG1haWwuY29tOnBhc3N3b3Jk");
        properties.put("dataspacetck.dsp.connector.negotiation.initiate.url", "http://localhost:8090/negotiations");
        properties.put("dataspacetck.dsp.connector.transfer.initiate.url", "http://localhost:8090/transfers");
        properties.put("dataspacetck.dsp.default.wait", "10000000");

        Offer offer = dataset.getHasPolicy().stream().findFirst().orElse(null);
        // Contract negotiation provider properties
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 4; j++) {
                String prefix = String.format("CN_%02d_%02d_", i, j);
                properties.put(prefix + "DATASETID", dataset.getId());
                properties.put(prefix + "OFFERID", offer.getId());
            }
        }

        // Contract negotiation consumer properties
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 6; j++) {
                properties.put(String.format("CN_C_%02d_%02d_DATASETID", i, j), "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5");
            }
        }

        // Transfer process provider properties
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 6; j++) {
                String prefix = String.format("TP_%02d_%02d_", i, j);
                properties.put(prefix + "AGREEMENTID", "urn:uuid:AGREEMENT_ID_OK");
                properties.put(prefix + "FORMAT", "HttpData-PULL");
            }
        }

        // Transfer process consumer properties
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 6; j++) {
                String prefix = String.format("TP_C_%02d_%02d_", i, j);
                properties.put(prefix + "AGREEMENTID", "urn:uuid:AGREEMENT_ID_OK");
                properties.put(prefix + "FORMAT", "HttpData-PULL");
            }
        }

        // Catalog properties
        for (int i = 1; i <= 2; i++) {
            properties.put(String.format("CAT_01_%02d_DATASETID", i), dataset.getId());
        }
        properties.put(String.format("CAT_01_%02d_DATASETID", 3), "dataset_not_found");
        return properties;
    }

    private void uploadFile() throws Exception {
        String fileContent = "Hello, World!";

        MockMultipartFile file
                = new MockMultipartFile(
                "file",
                "hello.txt",
                MediaType.TEXT_PLAIN_VALUE,
                fileContent.getBytes()
        );

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(file.getOriginalFilename())
                .build();

        Map<String, String> destinationS3Properties = createS3EndpointProperties(dataset.getId());

        try {
            s3ClientService.uploadFile(file.getInputStream(), destinationS3Properties,
                            file.getContentType(), contentDisposition.toString())
                    .get();
        } catch (Exception e) {
            throw new Exception("File storing aborted, " + e.getLocalizedMessage());
        }

        Thread.sleep(2000); // wait for the file to be uploaded to S3
    }
}
