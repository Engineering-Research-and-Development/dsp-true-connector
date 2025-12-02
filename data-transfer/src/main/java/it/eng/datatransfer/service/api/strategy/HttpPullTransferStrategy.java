package it.eng.datatransfer.service.api.strategy;

import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.service.api.DataTransferStrategy;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class HttpPullTransferStrategy implements DataTransferStrategy {

    private final S3ClientService s3ClientService;
    private final S3Properties s3Properties;
    private static final int DEFAULT_TIMEOUT = 10000; // 10 seconds
    private final boolean serverSslEnabled;

    public HttpPullTransferStrategy(S3ClientService s3ClientService,
                                    S3Properties s3Properties,
                                    @Value("${server.ssl.enabled:false}") boolean serverSslEnabled) {
        this.s3ClientService = s3ClientService;
        this.s3Properties = s3Properties;
        this.serverSslEnabled = serverSslEnabled;
    }

    @Override
    public CompletableFuture<Void> transfer(TransferProcess transferProcess) {
        log.info("Executing HTTP PULL transfer for process {}", transferProcess.getId());

        // get authorization information from Data Address if present
        String authorization = extractAuthorization(transferProcess);

        return downloadAndUploadToS3(
                transferProcess.getDataAddress().getEndpoint(),
                authorization,
                transferProcess.getId()
        ).thenAccept(key ->
                log.info("Stored transfer process id - {} data!", key));
    }

    private CompletableFuture<String> downloadAndUploadToS3(String presignedUrl,
                                                            String authorization,
                                                            String key) {
        HttpURLConnection connection;
        try {
            URL url = new URL(presignedUrl);
            connection = (HttpURLConnection) url.openConnection();

            if (!serverSslEnabled && connection instanceof HttpsURLConnection httpsURLConnection) {
                log.warn("server.ssl.enabled=false - trusting all certificates for HTTP pull download");
                configureTrustAllCertificates(httpsURLConnection);
            }

            // Configure connection
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DEFAULT_TIMEOUT);
            connection.setReadTimeout(DEFAULT_TIMEOUT);
            if (StringUtils.isNotBlank(authorization)) {
                connection.setRequestProperty(HttpHeaders.AUTHORIZATION, authorization);
            }

            // Check if the request was successful
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to get stream. HTTP response code: " + responseCode);
            }

            log.info("Presigned URL: {}", presignedUrl);
            log.info("HTTP response code: {}", responseCode);

            String contentType = connection.getContentType();
            String contentDisposition = connection.getHeaderField(HttpHeaders.CONTENT_DISPOSITION);

            Map<String, String> destinationS3Properties = Map.of(
                    S3Utils.OBJECT_KEY, key,
                    S3Utils.BUCKET_NAME, s3Properties.getBucketName(),
                    S3Utils.ENDPOINT_OVERRIDE, s3Properties.getEndpoint(),
                    S3Utils.REGION, s3Properties.getRegion(),
                    S3Utils.ACCESS_KEY, s3Properties.getAccessKey(),
                    S3Utils.SECRET_KEY, s3Properties.getSecretKey()
            );
            // Use S3ClientService's uploadFile method
            return s3ClientService.uploadFile(
                    connection.getInputStream(),
                    destinationS3Properties,
                    contentType,
                    contentDisposition
            );
        } catch (IOException | GeneralSecurityException e) {
            log.error("Failed to download stream", e);
            throw new DataTransferAPIException(e.getMessage());
        }
    }

    private void configureTrustAllCertificates(HttpsURLConnection httpsURLConnection) throws GeneralSecurityException {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        // Trust all client certificates
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        // Trust all server certificates
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
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

    private String extractAuthorization(TransferProcess transferProcess) {
        if (transferProcess.getDataAddress().getEndpointProperties() != null) {
            List<EndpointProperty> properties = transferProcess.getDataAddress().getEndpointProperties();
            String authType = properties.stream()
                    .filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTH_TYPE))
                    .findFirst()
                    .map(EndpointProperty::getValue)
                    .orElse(null);
            String token = properties.stream()
                    .filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTHORIZATION))
                    .findFirst()
                    .map(EndpointProperty::getValue)
                    .orElse(null);

            if (authType != null && token != null) {
                return authType + " " + token;
            }
        }
        return null;
    }
}
