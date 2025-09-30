package it.eng.connector.tck;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.repository.CatalogRepository;
import it.eng.catalog.repository.DataServiceRepository;
import it.eng.catalog.repository.DatasetRepository;
import it.eng.catalog.repository.DistributionRepository;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.tools.repository.ArtifactRepository;
import it.eng.tools.s3.service.S3ClientService;
import org.eclipse.dataspacetck.core.system.ConsoleMonitor;
import org.eclipse.dataspacetck.runtime.TckRuntime;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

//@Profile("tck")
@ActiveProfiles("tck")
@Disabled("Disabled until the TCK issues are resolved")
public class TransferTCKComplianceTest extends BaseIntegrationTest {

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
    @Autowired
    private AgreementRepository agreementRepository;
    @Autowired
    private TransferProcessRepository transferProcessRepository;


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
    public void cleanUp() {
        agreementRepository.deleteAll();
        transferProcessRepository.deleteAll();
    }

    @Test
    public void testTransfer() throws Exception {
        assertFalse(TransferState.REQUESTED.canTransitTo(TransferState.SUSPENDED));
    }

    @Test
    void assertDspCompatibility() throws IOException {
        var monitor = new ConsoleMonitor(true, true);
        var ct = okhttp3.MediaType.get("application/json");


        Map<String, String> properties = createProperties(catalog);

        var result = TckRuntime.Builder.newInstance()
                .properties(properties) // Add any additional properties if needed
                .addPackage("org.eclipse.dataspacetck.dsp.verification.tp")
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
//        properties.put("dataspacetck.debug", "true");
        properties.put("dataspacetck.launcher", "org.eclipse.dataspacetck.dsp.system.DspSystemLauncher");
        properties.put("dataspacetck.dsp.local.connector", "false");
        properties.put("dataspacetck.dsp.connector.agent.id", "CONNECTOR_UNDER_TEST");
        properties.put("dataspacetck.dsp.connector.http.url", "http://localhost:8090");
        properties.put("dataspacetck.dsp.connector.http.base.url", "http://localhost:8090");
        properties.put("dataspacetck.dsp.connector.http.headers.authorization", "Basic Y29ubmVjdG9yQG1haWwuY29tOnBhc3N3b3Jk");
        properties.put("dataspacetck.dsp.connector.negotiation.initiate.url", "http://localhost:8090/negotiations/initiate");
        properties.put("dataspacetck.dsp.connector.transfer.initiate.url", "http://localhost:8090/transfers/initiate");
//        properties.put("dataspacetck.dsp.default.wait", "5000");

        // Contract negotiation provider properties
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 4; j++) {
                String prefix = String.format("CN_%02d_%02d_", i, j);
                properties.put(prefix + "DATASETID", "12345678-aaaa-bbbb-cccc-123456789012");
                properties.put(prefix + "OFFERID", "1234-offerid-5678");
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
                properties.put(prefix + "AGREEMENTID", "ATP0" + i + "0" + j);
                properties.put(prefix + "FORMAT", "HttpData-PULL");

//                Agreement agreement = Agreement.Builder.newInstance()
//                        .id("ATP0" + i + "0" + j)
//                        .target(dataset.getId())
//                        .assignee("tck")
//                        .assigner("provider")
//                        .permission(List.of(NegotiationMockObjectUtil.PERMISSION))
//                        .build();
//                agreementRepository.save(agreement);
//                TransferProcess transferProcess = TransferProcess.Builder.newInstance()
//                        .state(TransferState.INITIALIZED)
//                        .consumerPid(UUID.randomUUID().toString())
//                        .agreementId(agreement.getId())
//                        .datasetId(dataset.getId())
//                        .role(IConstants.ROLE_PROVIDER)
//                        .build();
//                transferProcessRepository.save(transferProcess);
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
            properties.put(String.format("CAT_01_%02d_DATASETID", i), "12345678-aaaa-bbbb-cccc-123456789012");
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
