package it.eng.dcp.holder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.holder.exception.RevocationListFetchException;
import it.eng.dcp.holder.exception.RevocationListFormatException;
import it.eng.dcp.holder.model.CachedStatusList;
import it.eng.dcp.holder.model.VerifiableCredential;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Production RevocationService implementing StatusList2021 with caching.
 * This service fetches status list credentials, decodes the compressed bitstring,
 * and checks if a given credential is revoked.
 */
@Service
@Primary
@Slf4j
public class StatusList2021RevocationService implements RevocationService {

    private final Map<String, CachedStatusList> cache = new ConcurrentHashMap<>();
    private final SimpleOkHttpRestClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${dcp.revocation.cache.ttlSeconds:300}")
    private long cacheTtlSeconds;

    @Autowired
    public StatusList2021RevocationService(SimpleOkHttpRestClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isRevoked(VerifiableCredential vc) {
        if (vc == null) {
            return false;
        }

        // Extract credentialStatus from the VC
        JsonNode credentialStatus = extractCredentialStatus(vc);
        if (credentialStatus == null || credentialStatus.isNull()) {
            log.debug("No credentialStatus found for VC {}, treating as not revoked", vc.getId());
            return false;
        }

        // Extract statusListCredential URL and statusListIndex
        String statusListUrl = extractStatusListCredential(credentialStatus);
        Integer statusListIndex = extractStatusListIndex(credentialStatus);

        if (statusListUrl == null || statusListIndex == null) {
            log.warn("Invalid credentialStatus structure for VC {}, missing statusListCredential or statusListIndex", vc.getId());
            return false;
        }

        // Fetch or retrieve cached status list
        CachedStatusList cachedList = fetchOrGetCachedStatusList(statusListUrl);

        // Check the bit at the given index
        return cachedList.getDecodedBits().get(statusListIndex);
    }

    /**
     * Extract credentialStatus field from VC.
     *
     * @param vc the verifiable credential to extract status from
     * @return JsonNode containing the credentialStatus field, or null if not present
     */
    private JsonNode extractCredentialStatus(VerifiableCredential vc) {
        JsonNode vcNode = objectMapper.valueToTree(vc);
        return vcNode.path("credentialStatus");
    }

    /**
     * Extract statusListCredential URL from credentialStatus.
     *
     * @param credentialStatus the credentialStatus node to extract URL from
     * @return the status list credential URL, or null if not present or invalid
     */
    private String extractStatusListCredential(JsonNode credentialStatus) {
        JsonNode node = credentialStatus.path("statusListCredential");
        return node.isTextual() ? node.asText() : null;
    }

    /**
     * Extract statusListIndex from credentialStatus.
     *
     * @param credentialStatus the credentialStatus node to extract index from
     * @return the status list index, or null if not present or invalid
     */
    private Integer extractStatusListIndex(JsonNode credentialStatus) {
        JsonNode node = credentialStatus.path("statusListIndex");
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText());
            } catch (NumberFormatException e) {
                log.warn("Invalid statusListIndex format: {}", node.asText());
                return null;
            }
        }
        return null;
    }

    /**
     * Fetch or retrieve cached status list.
     *
     * @param statusListUrl the URL of the status list credential
     * @return the cached or freshly fetched status list
     */
    private CachedStatusList fetchOrGetCachedStatusList(String statusListUrl) {
        CachedStatusList cached = cache.get(statusListUrl);

        // Check if cache is valid
        if (cached != null && !isCacheExpired(cached)) {
            log.debug("Using cached status list for {}", statusListUrl);
            return cached;
        }

        // Fetch new status list
        log.info("Fetching status list credential from {}", statusListUrl);
        CachedStatusList fresh = fetchAndDecodeStatusList(statusListUrl);
        cache.put(statusListUrl, fresh);
        return fresh;
    }

    /**
     * Check if cached entry has expired.
     *
     * @param cached the cached status list to check
     * @return true if expired, false otherwise
     */
    private boolean isCacheExpired(CachedStatusList cached) {
        Duration age = Duration.between(cached.getFetchedAt(), Instant.now());
        return age.getSeconds() > cacheTtlSeconds;
    }

    /**
     * Fetch status list credential from URL and decode it.
     *
     * @param statusListUrl the URL to fetch the status list from
     * @return the decoded cached status list
     * @throws RevocationListFetchException if fetching fails
     */
    private CachedStatusList fetchAndDecodeStatusList(String statusListUrl) {
        try {
            // Fetch the status list credential
            String response = httpClient.executeAndDeserialize(statusListUrl, "GET", null, null, String.class   );

            if (StringUtils.isBlank(response)) {
                throw new RevocationListFetchException("Failed to fetch status list from " + statusListUrl);
            }

            // Parse the credential
            JsonNode statusListVc = objectMapper.readTree(response);

            // Extract and decode the encodedList
            BitSet decodedBits = decodeEncodedList(statusListVc);

            return CachedStatusList.Builder.newInstance()
                    .vcPayload(statusListVc)
                    .decodedBits(decodedBits)
                    .fetchedAt(Instant.now())
                    .build();

        } catch (RevocationListFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new RevocationListFetchException("Error fetching or decoding status list from " + statusListUrl, e);
        }
    }

    /**
     * Decode the encodedList from a StatusList2021Credential.
     *
     * @param statusListVc the status list credential JSON node
     * @return the decoded BitSet containing revocation statuses
     * @throws RevocationListFormatException if decoding fails
     */
    private BitSet decodeEncodedList(JsonNode statusListVc) {
        try {
            // Navigate to credentialSubject.encodedList
            JsonNode credentialSubject = statusListVc.path("credentialSubject");
            if (credentialSubject.isMissingNode()) {
                throw new RevocationListFormatException("Missing credentialSubject in status list credential");
            }

            JsonNode encodedListNode = credentialSubject.path("encodedList");
            if (!encodedListNode.isTextual()) {
                throw new RevocationListFormatException("Missing or invalid encodedList in status list credential");
            }

            String encodedList = encodedListNode.asText();

            // Decode Base64URL
            byte[] compressedBytes = Base64.getUrlDecoder().decode(encodedList);

            // Decompress GZIP
            byte[] decompressedBytes = decompressGzip(compressedBytes);

            // Convert bytes to BitSet
            BitSet bitSet = BitSet.valueOf(decompressedBytes);

            log.debug("Decoded status list with {} bytes, {} bits set", decompressedBytes.length, bitSet.cardinality());

            return bitSet;

        } catch (RevocationListFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new RevocationListFormatException("Error decoding encodedList", e);
        }
    }

    /**
     * Decompress GZIP data.
     *
     * @param compressedBytes the GZIP compressed byte array
     * @return the decompressed byte array
     * @throws Exception if decompression fails
     */
    private byte[] decompressGzip(byte[] compressedBytes) throws Exception {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedBytes))) {
            return gzipInputStream.readAllBytes();
        }
    }

    /**
     * Clear the cache (useful for testing).
     */
    public void clearCache() {
        cache.clear();
        log.debug("Revocation cache cleared");
    }

    /**
     * Get cache size (useful for monitoring).
     *
     * @return the current number of entries in the cache
     */
    public int getCacheSize() {
        return cache.size();
    }
}

