package it.eng.tools.auth.keycloak;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import it.eng.tools.configuration.SharedOkHttpClients;
import it.eng.tools.ssl.ocsp.OcspTrustManagerFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.UnknownServiceException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakHttpClientConfigurationTest {

    private static final char[] PASSWORD = "password".toCharArray();
    private static final String DISCOVERY_PATH = "/realms/platform/.well-known/openid-configuration";

    @Mock
    private OcspTrustManagerFactory ocspTrustManagerFactory;

    private final KeycloakHttpClientConfiguration configuration = new KeycloakHttpClientConfiguration();
    private HttpsServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("With SSL enabled, trusts a Keycloak certificate issued by a CA in the connector truststore")
    void sslEnabled_trustsConnectorTruststore() throws Exception {
        KeyStore serverKeyStore = startServer("127.0.0.1");
        when(ocspTrustManagerFactory.createTrustManagers()).thenReturn(trustManagersFor(serverKeyStore));

        OkHttpClient client = keycloakHttpClient(properties(), true);

        try (Response response = client.newCall(request(DISCOVERY_PATH)).execute()) {
            assertEquals(200, response.code());
            assertEquals("{}", response.body().string());
        }
    }

    @Test
    @DisplayName("With SSL enabled, discovery succeeds against an HTTPS Keycloak behind a private CA")
    void sslEnabled_discoveryOverPrivateCa() throws Exception {
        KeyStore serverKeyStore = startServer("127.0.0.1");
        when(ocspTrustManagerFactory.createTrustManagers()).thenReturn(trustManagersFor(serverKeyStore));
        KeycloakTrustProperties properties = properties();
        String issuer = properties.issuerFor("platform");
        server.removeContext(DISCOVERY_PATH);
        server.createContext(DISCOVERY_PATH, exchange -> {
            byte[] body = ("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/certs\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        KeycloakDiscoveryClient discoveryClient = new KeycloakDiscoveryClient(properties,
                keycloakHttpClient(properties, true));

        DiscoveryDocument document = discoveryClient.fetch(issuer);
        assertEquals(issuer, document.issuer());
    }

    @Test
    @DisplayName("With SSL enabled, rejects a trusted certificate whose host name does not match")
    void sslEnabled_verifiesHostName() throws Exception {
        KeyStore serverKeyStore = startServer("keycloak.example.org");
        when(ocspTrustManagerFactory.createTrustManagers()).thenReturn(trustManagersFor(serverKeyStore));

        OkHttpClient client = keycloakHttpClient(properties(), true);

        assertThrows(SSLPeerUnverifiedException.class, () -> client.newCall(request(DISCOVERY_PATH)).execute());
    }

    @Test
    @DisplayName("With SSL disabled, trusts a certificate that is in no truststore")
    void sslDisabled_trustsUntrustedCertificate() throws Exception {
        startServer("127.0.0.1");

        OkHttpClient client = keycloakHttpClient(properties(), false);

        assertEquals(200, status(client, DISCOVERY_PATH));
        verifyNoInteractions(ocspTrustManagerFactory);
    }

    @Test
    @DisplayName("With SSL disabled, does not verify the host name")
    void sslDisabled_doesNotVerifyHostName() throws Exception {
        startServer("keycloak.example.org");

        OkHttpClient client = keycloakHttpClient(properties(), false);

        assertEquals(200, status(client, DISCOVERY_PATH));
    }

    @Test
    @DisplayName("Does not follow redirects")
    void doesNotFollowRedirects() throws Exception {
        KeyStore serverKeyStore = startServer("127.0.0.1");
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", DISCOVERY_PATH);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        when(ocspTrustManagerFactory.createTrustManagers()).thenReturn(trustManagersFor(serverKeyStore));

        OkHttpClient client = keycloakHttpClient(properties(), true);

        assertEquals(302, status(client, "/redirect"));
    }

    @Test
    @DisplayName("With SSL enabled, refuses a plain http Keycloak")
    void sslEnabled_refusesCleartext() throws Exception {
        KeyStore serverKeyStore = startServer("127.0.0.1");
        when(ocspTrustManagerFactory.createTrustManagers()).thenReturn(trustManagersFor(serverKeyStore));
        HttpServer plainServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        plainServer.createContext(DISCOVERY_PATH, exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        plainServer.start();
        try {
            OkHttpClient client = keycloakHttpClient(properties(), true);
            Request plainRequest = new Request.Builder()
                    .url("http://127.0.0.1:" + plainServer.getAddress().getPort() + DISCOVERY_PATH)
                    .get()
                    .build();

            assertThrows(UnknownServiceException.class, () -> client.newCall(plainRequest).execute());
        } finally {
            plainServer.stop(0);
        }
    }

    @Test
    @DisplayName("Never follows redirects and bounds the whole call by connect plus read timeout")
    void variantSettings() throws Exception {
        startServer("127.0.0.1");
        KeycloakTrustProperties properties = properties();
        properties.setDiscoveryConnectTimeout(Duration.ofSeconds(3));
        properties.setDiscoveryReadTimeout(Duration.ofSeconds(7));

        OkHttpClient client = keycloakHttpClient(properties, false);

        assertFalse(client.followRedirects());
        assertFalse(client.followSslRedirects());
        assertEquals(3_000, client.connectTimeoutMillis());
        assertEquals(7_000, client.readTimeoutMillis());
        assertEquals(10_000, client.callTimeoutMillis());
    }

    // --- helpers ----------------------------------------------------------------------------------------

    private KeycloakTrustProperties properties() {
        KeycloakTrustProperties properties = new KeycloakTrustProperties();
        properties.setBaseUrl("https://127.0.0.1:" + server.getAddress().getPort());
        properties.setDiscoveryConnectTimeout(Duration.ofSeconds(2));
        properties.setDiscoveryReadTimeout(Duration.ofSeconds(2));
        properties.init();
        return properties;
    }

    private OkHttpClient keycloakHttpClient(KeycloakTrustProperties properties, boolean sslEnabled)
            throws GeneralSecurityException {
        OkHttpClient shared = SharedOkHttpClients.sharedClient(ocspTrustManagerFactory, sslEnabled);
        return configuration.keycloakHttpClient(shared, properties);
    }

    private Request request(String path) {
        return new Request.Builder()
                .url("https://127.0.0.1:" + server.getAddress().getPort() + path)
                .get()
                .build();
    }

    private int status(OkHttpClient client, String path) throws IOException {
        try (Response response = client.newCall(request(path)).execute()) {
            return response.code();
        }
    }

    /**
     * Starts an HTTPS server with a self-signed certificate, which stands in for a private CA.
     */
    private KeyStore startServer(String subjectAlternativeName) throws Exception {
        KeyStore keyStore = selfSignedKeyStore(subjectAlternativeName);
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, PASSWORD);
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagerFactory.getKeyManagers(), null, null);

        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        server.createContext(DISCOVERY_PATH, exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return keyStore;
    }

    private static TrustManager[] trustManagersFor(KeyStore serverKeyStore) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("keycloak", serverKeyStore.getCertificate("server"));
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory.getTrustManagers();
    }

    private static KeyStore selfSignedKeyStore(String subjectAlternativeName) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=" + subjectAlternativeName);
        Instant now = Instant.now();
        GeneralName name = subjectAlternativeName.matches("[0-9.]+")
                ? new GeneralName(GeneralName.iPAddress, subjectAlternativeName)
                : new GeneralName(GeneralName.dNSName, subjectAlternativeName);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(1, ChronoUnit.DAYS)), subject,
                keyPair.getPublic());
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(name));
        X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", keyPair.getPrivate(), PASSWORD, new X509Certificate[] {certificate});
        return keyStore;
    }
}
