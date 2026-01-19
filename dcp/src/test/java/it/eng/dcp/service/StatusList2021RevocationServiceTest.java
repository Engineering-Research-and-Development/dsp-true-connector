package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.exception.RevocationListFetchException;
import it.eng.dcp.model.VerifiableCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.BitSet;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for StatusList2021RevocationService covering:
 * - Cache hit reduces network calls
 * - Malformed encodedList throws exception
 * - Revoked bit index returns true
 * - Non-revoked bit index returns false
 * - Missing credentialStatus returns false
 * - Network errors trigger RevocationListFetchException
 */
@ExtendWith(MockitoExtension.class)
class StatusList2021RevocationServiceTest {

    @Mock
    private SimpleOkHttpRestClient httpClient;

    private ObjectMapper objectMapper;
    private StatusList2021RevocationService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new StatusList2021RevocationService(httpClient, objectMapper);
        service.clearCache();
    }

    @Test
    @DisplayName("isRevoked returns false when credentialStatus is missing")
    void testIsRevoked_NoCredentialStatus() {
        VerifiableCredential vc = VerifiableCredential.Builder.newInstance()
                .id("urn:uuid:test-vc-1")
                .holderDid("did:web:holder.example")
                .credentialType("VerifiableCredential")
                .issuerDid("did:web:issuer.example")
                .build();

        assertFalse(service.isRevoked(vc));
    }

    @Test
    @DisplayName("isRevoked returns false when VC is null")
    void testIsRevoked_NullVC() {
        assertFalse(service.isRevoked(null));
    }

    @Test
    @DisplayName("isRevoked returns true when bit at statusListIndex is set")
    void testIsRevoked_RevokedBitSet() throws Exception {
        // Create a BitSet with bit 5 set (revoked)
        BitSet bits = new BitSet();
        bits.set(5);
        String encodedList = createEncodedList(bits);

        // Create status list credential response
        String statusListVc = createStatusListCredential(encodedList);

        when(httpClient.executeAndDeserialize(anyString(), eq("GET"), isNull(), isNull(), eq(String.class)))
                .thenReturn(statusListVc);

        // Create VC with credentialStatus pointing to index 5
        VerifiableCredential vc = createVCWithStatus("https://example.com/status/1", 5);

        assertTrue(service.isRevoked(vc));
        verify(httpClient, times(1)).executeAndDeserialize(anyString(), eq("GET"), isNull(), isNull(), eq(String.class));
    }

    @Test
    @DisplayName("isRevoked returns false when bit at statusListIndex is not set")
    void testIsRevoked_NotRevokedBitNotSet() throws Exception {
        // Create a BitSet with bit 5 set, but we'll check bit 3
        BitSet bits = new BitSet();
        bits.set(5);
        String encodedList = createEncodedList(bits);

        String statusListVc = createStatusListCredential(encodedList);

        when(httpClient.executeAndDeserialize(anyString(), eq("GET"), isNull(), isNull(), eq(String.class)))
                .thenReturn(statusListVc);

        // Create VC with credentialStatus pointing to index 3 (not revoked)
        VerifiableCredential vc = createVCWithStatus("https://example.com/status/1", 3);

        assertFalse(service.isRevoked(vc));
    }

    @Test
    @DisplayName("Cache hit reduces network calls")
    void testIsRevoked_CacheHit() throws Exception {
        BitSet bits = new BitSet();
        bits.set(5);
        String encodedList = createEncodedList(bits);

        String statusListVc = createStatusListCredential(encodedList);

        when(httpClient.executeAndDeserialize(anyString(), eq("GET"), isNull(), isNull(), eq(String.class)))
                .thenReturn(statusListVc);

        // First call - should hit the network
        VerifiableCredential vc1 = createVCWithStatus("https://example.com/status/1", 5);
        assertTrue(service.isRevoked(vc1));

        // Second call with same status list URL - should use cache
        VerifiableCredential vc2 = createVCWithStatus("https://example.com/status/1", 3);
        assertFalse(service.isRevoked(vc2));

        // Verify only one network call was made
        verify(httpClient, times(1)).executeAndDeserialize(anyString(), eq("GET"), isNull(), isNull(), eq(String.class));
    }

    @Test
    @DisplayName("Malformed encodedList throws RevocationListFetchException")
    void testIsRevoked_MalformedEncodedList() throws Exception {
        String statusListVc = """
            {
                "credentialSubject": {
                    "encodedList": "not-valid-base64!!!"
                }
            }
            """;

        when(httpClient.executeAndDeserialize(anyString(), eq("GET"), isNull(), isNull(), eq(String.class)))
                .thenReturn(statusListVc);

        VerifiableCredential vc = createVCWithStatus("https://example.com/status/1", 5);

        assertThrows(RevocationListFetchException.class, () -> service.isRevoked(vc));
    }

    @Test
    @DisplayName("Missing credentialSubject throws RevocationListFetchException")
    void testIsRevoked_MissingCredentialSubject() throws Exception {
        String statusListVc = """
            {
                "id": "https://example.com/status/1"
            }
            """;

        when(httpClient.executeAndDeserialize(anyString(), eq("GET"), isNull(), isNull(), eq(String.class)))
                .thenReturn(statusListVc);

        VerifiableCredential vc = createVCWithStatus("https://example.com/status/1", 5);

        assertThrows(RevocationListFetchException.class, () -> service.isRevoked(vc));
    }

    @Test
    @DisplayName("Network failure throws RevocationListFetchException")
    void testIsRevoked_NetworkFailure() throws Exception {
        when(httpClient.executeAndDeserialize(anyString(), eq("GET"), isNull(), isNull(), eq(String.class)))
                .thenThrow(new IOException("Network timeout"));

        VerifiableCredential vc = createVCWithStatus("https://example.com/status/1", 5);

        assertThrows(RevocationListFetchException.class, () -> service.isRevoked(vc));
    }

    @Test
    @DisplayName("getCacheSize returns correct cache size")
    void testGetCacheSize() throws Exception {
        BitSet bits = new BitSet();
        String encodedList = createEncodedList(bits);

        String statusListVc = createStatusListCredential(encodedList);

        when(httpClient.executeAndDeserialize(anyString(), eq("GET"), isNull(), isNull(), eq(String.class)))
                .thenReturn(statusListVc);

        assertEquals(0, service.getCacheSize());

        VerifiableCredential vc1 = createVCWithStatus("https://example.com/status/1", 1);
        service.isRevoked(vc1);
        assertEquals(1, service.getCacheSize());

        VerifiableCredential vc2 = createVCWithStatus("https://example.com/status/2", 1);
        service.isRevoked(vc2);
        assertEquals(2, service.getCacheSize());
    }

    @Test
    @DisplayName("clearCache removes all cached entries")
    void testClearCache() throws Exception {
        BitSet bits = new BitSet();
        String encodedList = createEncodedList(bits);

        String statusListVc = createStatusListCredential(encodedList);

        when(httpClient.executeAndDeserialize(anyString(), eq("GET"), isNull(), isNull(), eq(String.class)))
                .thenReturn(statusListVc);

        VerifiableCredential vc = createVCWithStatus("https://example.com/status/1", 1);
        service.isRevoked(vc);

        assertEquals(1, service.getCacheSize());

        service.clearCache();
        assertEquals(0, service.getCacheSize());
    }

    // Helper methods

    private VerifiableCredential createVCWithStatus(String statusListUrl, int statusListIndex) {
        ObjectNode credentialStatus = objectMapper.createObjectNode();
        credentialStatus.put("type", "StatusList2021Entry");
        credentialStatus.put("statusListCredential", statusListUrl);
        credentialStatus.put("statusListIndex", statusListIndex);

        return VerifiableCredential.Builder.newInstance()
                .id("urn:uuid:test-vc-" + statusListIndex)
                .holderDid("did:web:holder.example")
                .credentialType("VerifiableCredential")
                .issuerDid("did:web:issuer.example")
                .credentialStatus(credentialStatus)
                .build();
    }

    private String createEncodedList(BitSet bits) throws Exception {
        byte[] bytes = bits.toByteArray();

        // Compress with GZIP
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(bytes);
        }
        byte[] compressed = baos.toByteArray();

        // Encode with Base64URL
        return Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);
    }

    private String createStatusListCredential(String encodedList) throws Exception {
        ObjectNode credential = objectMapper.createObjectNode();
        credential.put("@context", "https://www.w3.org/2018/credentials/v1");
        credential.put("id", "https://example.com/status/1");
        credential.put("type", "StatusList2021Credential");

        ObjectNode credentialSubject = objectMapper.createObjectNode();
        credentialSubject.put("type", "StatusList2021");
        credentialSubject.put("encodedList", encodedList);

        credential.set("credentialSubject", credentialSubject);

        return objectMapper.writeValueAsString(credential);
    }
}