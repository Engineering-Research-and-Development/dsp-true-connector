package it.eng.dcp.common.config;

import it.eng.dcp.common.exception.InsecureEndpointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseDidDocumentConfigurationTest {

    private DcpProperties props;

    @BeforeEach
    void setUp() {
        props = new DcpProperties();
        props.setConnectorDid("did:web:example.com:connector");
        props.setHost("example.com");
        DcpProperties.ServiceEntry entry = new DcpProperties.ServiceEntry();
        entry.setId("cs");
        entry.setType("CredentialService");
        entry.setEndpointPath("/dcp");
        props.getServiceEntries().add(entry);
    }

    private BaseDidDocumentConfiguration configuration(boolean ssl) {
        BaseDidDocumentConfiguration c = new BaseDidDocumentConfiguration(props);
        ReflectionTestUtils.setField(c, "sslEnabled", ssl);
        ReflectionTestUtils.setField(c, "port", "8080");
        return c;
    }

    @Test
    @DisplayName("advertised endpoints are https by default, even without server TLS")
    void defaultsToHttps() {
        DidDocumentConfig config = configuration(false).didDocumentConfig();
        assertEquals("https://example.com:8080", config.getEffectiveBaseUrl());
    }

    @Test
    @DisplayName("http is derived when insecure http is allowed and TLS is off")
    void httpWhenAllowed() {
        props.setAllowInsecureHttp(true);
        assertEquals("http://example.com:8080", configuration(false).didDocumentConfig().getEffectiveBaseUrl());
        assertEquals("https://example.com:8080", configuration(true).didDocumentConfig().getEffectiveBaseUrl());
    }

    @Test
    @DisplayName("an http base URL is rejected unless insecure http is allowed")
    void httpBaseUrlRejected() {
        props.setBaseUrl("http://example.com:8080");
        BaseDidDocumentConfiguration c = configuration(false);
        assertThrows(InsecureEndpointException.class, c::didDocumentConfig);
        props.setAllowInsecureHttp(true);
        assertEquals("http://example.com:8080", c.didDocumentConfig().getEffectiveBaseUrl());
    }

    @Test
    @DisplayName("an http issuer location is rejected unless insecure http is allowed")
    void httpIssuerLocationRejected() {
        props.getServiceEntries().get(0).setIssuerLocation("http://example.com:8080/issuer/");
        BaseDidDocumentConfiguration c = configuration(true);
        assertThrows(InsecureEndpointException.class, c::didDocumentConfig);
        props.setAllowInsecureHttp(true);
        c.didDocumentConfig();
    }

    @Test
    @DisplayName("DidDocumentConfig builder defaults to https")
    void builderDefaultsToHttps() {
        assertEquals("https://localhost:1", DidDocumentConfig.builder().port("1").build().getEffectiveBaseUrl());
    }
}
