package it.eng.connector.integration.s3;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class S3PresignedUrlReader {
    private static final int DEFAULT_TIMEOUT = 10000; // 10 seconds

    public static InputStream getInputStreamFromPresignedUrl(String presignedUrl) throws IOException {
        URL url = new URL(presignedUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Configure connection
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(DEFAULT_TIMEOUT);
        connection.setReadTimeout(DEFAULT_TIMEOUT);

        // Check if the request was successful
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to get stream. HTTP response code: " + responseCode);
        }

        return new BufferedInputStream(connection.getInputStream());
    }
}
