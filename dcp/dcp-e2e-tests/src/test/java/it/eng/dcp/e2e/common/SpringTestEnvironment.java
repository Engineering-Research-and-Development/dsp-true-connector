package it.eng.dcp.e2e.common;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;

/**
 * DCP test environment implementation for Spring Boot applications.
 */
public class SpringTestEnvironment implements DcpTestEnvironment {

    private final ConfigurableApplicationContext holderVerifierContext;
    private final ConfigurableApplicationContext issuerContext;
    private final int holderVerifierPort;
    private final int issuerPort;

    private RestTemplate issuerClient;
    private RestTemplate holderClient;
    private RestTemplate verifierClient;

    public SpringTestEnvironment(
            ConfigurableApplicationContext holderVerifierContext,
            ConfigurableApplicationContext issuerContext,
            int holderVerifierPort,
            int issuerPort) {
        this.holderVerifierContext = holderVerifierContext;
        this.issuerContext = issuerContext;
        this.holderVerifierPort = holderVerifierPort;
        this.issuerPort = issuerPort;

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
        return "http://localhost:" + issuerPort;
    }

    @Override
    public String getHolderBaseUrl() {
        return "http://localhost:" + holderVerifierPort;
    }

    @Override
    public String getVerifierBaseUrl() {
        return "http://localhost:" + holderVerifierPort;
    }

    @Override
    public String getIssuerDid() {
        return "did:web:localhost:" + issuerPort + ":issuer";
    }

    @Override
    public String getHolderDid() {
        return "did:web:localhost:" + holderVerifierPort + ":holder";
    }

    @Override
    public String getVerifierDid() {
        return "did:web:localhost:" + holderVerifierPort + ":verifier";
    }

    @Override
    public String getEnvironmentName() {
        return "Spring Boot";
    }
}
