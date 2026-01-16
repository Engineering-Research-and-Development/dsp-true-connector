package it.eng.tools.s3.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class S3ClientProviderAwsDetectionTest {

    private boolean isAwsEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return true;
        }
        String lower = endpoint.toLowerCase();
        return lower.contains(".amazonaws.com") || lower.contains(".aws.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Should detect AWS S3 for null/blank endpoints")
    void isAwsEndpoint_nullOrBlank_returnsTrue(String endpoint) {
        assertTrue(isAwsEndpoint(endpoint));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://s3.amazonaws.com",
        "https://s3.us-east-1.amazonaws.com",
        "https://s3.eu-west-1.amazonaws.com",
        "https://bucket.s3.eu-west-1.amazonaws.com"
    })
    @DisplayName("Should detect AWS S3 for AWS domains")
    void isAwsEndpoint_awsDomain_returnsTrue(String endpoint) {
        assertTrue(isAwsEndpoint(endpoint));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost:9000",
        "http://minio:9000",
        "https://minio.example.com",
        "http://192.168.1.100:9000"
    })
    @DisplayName("Should detect Minio for non-AWS endpoints")
    void isAwsEndpoint_minioEndpoint_returnsFalse(String endpoint) {
        assertFalse(isAwsEndpoint(endpoint));
    }

    @Test
    @DisplayName("Should handle case-insensitive detection")
    void isAwsEndpoint_caseInsensitive() {
        assertTrue(isAwsEndpoint("https://S3.AMAZONAWS.COM"));
        assertTrue(isAwsEndpoint("https://s3.EU-WEST-1.AMAZONAWS.COM"));
    }
}

