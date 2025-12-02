package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HttpPushTransferStrategy implements DataTransferStrategy {

    private final S3Properties s3Properties;
    private final S3ClientService s3ClientService;
    private static final int DEFAULT_TIMEOUT = 10000; // 10 seconds
    private final boolean serverSslEnabled;

    public HttpPushTransferStrategy(S3Properties s3Properties,
                                    S3ClientService s3ClientService,
                                    @Value("${server.ssl.enabled:false}") boolean serverSslEnabled) {
        this.s3Properties = s3Properties;
        this.s3ClientService = s3ClientService;
        this.serverSslEnabled = serverSslEnabled;
    }

    @Override
    public CompletableFuture<Void> transfer(TransferProcess transferProcess) {
        // Convert endpoint properties to a map for easier access
        Map<String, String> destinationS3Properties = transferProcess.getDataAddress().getEndpointProperties()
                .stream()
                .collect(Collectors.toMap(EndpointProperty::getName, EndpointProperty::getValue));
        String presignedUrl = s3ClientService.generateGetPresignedUrl(s3Properties.getBucketName(), transferProcess.getDatasetId(), Duration.ofDays(1L));
        return transfer(presignedUrl, destinationS3Properties)
                .thenAccept(key ->
                        log.info("Pushed transfer process id - {} data!", key));
    }

    private CompletableFuture<String> transfer(String presignedUrl, Map<String, String> destinationS3Properties) {
        HttpURLConnection connection;
        try {
            URL url = new URL(presignedUrl);
            connection = (HttpURLConnection) url.openConnection();

            if (!serverSslEnabled && connection instanceof HttpsURLConnection httpsURLConnection) {
                log.warn("server.ssl.enabled=false - trusting all certificates for presigned URL download");
                configureTrustAllCertificates(httpsURLConnection);
            }

            // Configure connection
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DEFAULT_TIMEOUT);
            connection.setReadTimeout(DEFAULT_TIMEOUT);

            // Check if the request was successful
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to get stream. HTTP response code: " + responseCode);
            }

            log.info("Presigned URL: {}", presignedUrl);
            log.info("HTTP response code: {}", responseCode);

            // Use S3ClientService's uploadFile method
            return s3ClientService.uploadFile(
                    connection.getInputStream(),
                    destinationS3Properties,
                    connection.getContentType(),
                    connection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION));
        } catch (IOException | GeneralSecurityException e) {
            log.error("Failed to download stream", e);
            throw new DataTransferAPIException(e.getMessage());
        }
    }

    private void configureTrustAllCertificates(HttpsURLConnection httpsURLConnection) throws GeneralSecurityException {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // Trust all client certificates
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // Trust all server certificates
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());

        HostnameVerifier trustAllHostnames = (hostname, session) -> true;
        httpsURLConnection.setSSLSocketFactory(sslContext.getSocketFactory());
        httpsURLConnection.setHostnameVerifier(trustAllHostnames);
    }
}
