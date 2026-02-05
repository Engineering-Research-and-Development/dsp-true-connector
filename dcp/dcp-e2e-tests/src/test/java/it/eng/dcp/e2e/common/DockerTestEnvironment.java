package it.eng.dcp.e2e.common;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * DCP test environment implementation for Docker containers.
 *
 * <p>This environment runs applications in separate Docker containers
 * via Testcontainers, providing full isolation and production-like testing.
 */
public class DockerTestEnvironment implements DcpTestEnvironment {

    private final GenericContainer<?> issuerContainer;
    private final GenericContainer<?> holderVerifierContainer;

    private RestTemplate issuerClient;
    private RestTemplate holderClient;
    private RestTemplate verifierClient;

    /**
     * Creates a Docker-based test environment.
     *
     * @param issuerContainer the Docker container running the Issuer app
     * @param holderVerifierContainer the Docker container running Holder+Verifier app
     */
    public DockerTestEnvironment(
            GenericContainer<?> issuerContainer,
            GenericContainer<?> holderVerifierContainer) {
        this.issuerContainer = issuerContainer;
        this.holderVerifierContainer = holderVerifierContainer;

        initializeClients();
    }

    private void initializeClients() {
        this.issuerClient = new RestTemplateBuilder()
            .rootUri(getIssuerBaseUrl())
            .build();

        this.holderClient = new RestTemplateBuilder()
            .rootUri(getHolderBaseUrl())
            .build();

        this.verifierClient = new RestTemplateBuilder()
            .rootUri(getVerifierBaseUrl())
            .build();
    }

    @Override
    public RestTemplate getIssuerClient() {
        return issuerClient;
    }

    @Override
    public RestTemplate getHolderClient() {
        return holderClient;
    }

    @Override
    public RestTemplate getVerifierClient() {
        return verifierClient;
    }

    @Override
    public String getIssuerBaseUrl() {
        return "http://localhost:" + issuerContainer.getMappedPort(8084);
    }

    @Override
    public String getHolderBaseUrl() {
        return "http://localhost:" + holderVerifierContainer.getMappedPort(8087);
    }

    @Override
    public String getVerifierBaseUrl() {
        return "http://localhost:" + holderVerifierContainer.getMappedPort(8087);
    }

    @Override
    public String getIssuerDid() {
        return "did:web:localhost:" + issuerContainer.getMappedPort(8084) + ":issuer";
    }

    @Override
    public String getHolderDid() {
        return "did:web:localhost:" + holderVerifierContainer.getMappedPort(8087) + ":holder";
    }

    @Override
    public String getVerifierDid() {
        return "did:web:localhost:" + holderVerifierContainer.getMappedPort(8087) + ":verifier";
    }

    @Override
    public String getEnvironmentName() {
        return "Docker (Testcontainers)";
    }
}
