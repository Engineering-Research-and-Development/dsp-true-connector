package it.eng.dcp.verifier.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.exception.InsecureEndpointException;
import it.eng.dcp.common.util.DcpTransportSecurity;
import it.eng.dcp.common.util.DidUrlConverter;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;

/**
 * Verifies issuer-signed revocation lists referenced by credentials.
 */
final class CredentialStatusVerifier {
    private static final int MIN_LIST_BYTES = 16 * 1024;
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final SimpleOkHttpRestClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<CacheKey, CachedList> cache = new LinkedHashMap<>(16, 0.75f, true);

    CredentialStatusVerifier(SimpleOkHttpRestClient httpClient, ObjectMapper objectMapper) {
        this(httpClient, objectMapper, Clock.systemUTC());
    }

    CredentialStatusVerifier(SimpleOkHttpRestClient httpClient, ObjectMapper objectMapper, Clock clock) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    void verify(JsonNode status, String issuerDid, boolean allowInsecureHttp,
            BiConsumer<SignedJWT, String> verifyProof) {
        if (status.isArray()) {
            if (status.isEmpty()) {
                throw new SecurityException("Empty credential status");
            }
            status.forEach(entry -> verify(entry, issuerDid, allowInsecureHttp, verifyProof));
            return;
        }
        if (!status.isObject() || !status.path("statusPurpose").asText().equals("revocation")) {
            throw new SecurityException("Invalid or unsupported credential status");
        }
        String entryType = status.path("type").asText();
        boolean vc20 = switch (entryType) {
            case "StatusList2021Entry" -> false;
            case "BitstringStatusListEntry" -> true;
            default -> throw new SecurityException("Unsupported credential status type");
        };
        if (vc20 && status.has("statusSize")
                && (!status.get("statusSize").isIntegralNumber()
                        || !status.get("statusSize").canConvertToInt() || status.get("statusSize").asInt() != 1)) {
            throw new SecurityException("Unsupported credential status size");
        }
        String url = status.path("statusListCredential").asText();
        requireSecureEndpoint(url, allowInsecureHttp);
        JsonNode indexNode = status.path("statusListIndex");
        int index;
        try {
            if (!indexNode.isTextual() && !indexNode.isIntegralNumber()) {
                throw new NumberFormatException();
            }
            index = Integer.parseInt(indexNode.asText());
            if (index < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new SecurityException("Invalid status list index", e);
        }

        CacheKey key = new CacheKey(url, issuerDid, vc20);
        CachedList list = getList(key, allowInsecureHttp, verifyProof);
        if (index / 8 >= list.bits().length) {
            throw new SecurityException("Status list index outside list");
        }
        if ((list.bits()[index / 8] & (1 << (7 - index % 8))) != 0) {
            throw new SecurityException("Credential revoked");
        }
    }

    private CachedList getList(CacheKey key, boolean allowInsecureHttp,
            BiConsumer<SignedJWT, String> verifyProof) {
        synchronized (cache) {
            CachedList cached = cache.get(key);
            if (cached != null && clock.instant().isBefore(cached.expiresAt())) {
                return cached;
            }
            cache.remove(key);
        }
        CachedList fresh = fetchList(key, allowInsecureHttp, verifyProof);
        synchronized (cache) {
            if (!cache.containsKey(key) && cache.size() >= MAX_CACHE_ENTRIES) {
                cache.remove(cache.keySet().iterator().next());
            }
            cache.put(key, fresh);
        }
        return fresh;
    }

    private CachedList fetchList(CacheKey key, boolean allowInsecureHttp,
            BiConsumer<SignedJWT, String> verifyProof) {
        try {
            Request request = new Request.Builder().url(key.url()).get().build();
            return fetchResponse(key, allowInsecureHttp, verifyProof, request);
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Invalid status list endpoint");
        }
    }

    private CachedList fetchResponse(CacheKey key, boolean allowInsecureHttp,
            BiConsumer<SignedJWT, String> verifyProof, Request request) {
        try (Response response = httpClient.executeCallWithoutRedirects(request)) {
            if (response == null || !response.isSuccessful() || response.priorResponse() != null) {
                throw new SecurityException("Status list request failed");
            }
            requireSecureEndpoint(response.request().url().toString(), allowInsecureHttp);
            ResponseBody body = response.body();
            if (body == null) {
                throw new SecurityException("Status list response has no body");
            }
            byte[] data = body.byteStream().readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) {
                throw new SecurityException("Status list response too large");
            }
            SignedJWT jwt = SignedJWT.parse(new String(data, StandardCharsets.UTF_8));
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!sameDid(claims.getIssuer(), key.issuerDid())) {
                throw new SecurityException("Status list issuer mismatch");
            }
            verifyProof.accept(jwt, key.issuerDid());
            Instant now = clock.instant();
            Instant expiresAt = now.plus(CACHE_TTL);
            if ((claims.getNotBeforeTime() != null && claims.getNotBeforeTime().toInstant().isAfter(now))
                    || (claims.getIssueTime() != null && claims.getIssueTime().toInstant().isAfter(now))
                    || (claims.getExpirationTime() != null && !claims.getExpirationTime().toInstant().isAfter(now))) {
                throw new SecurityException("Status list outside validity period");
            }
            if (claims.getExpirationTime() != null && claims.getExpirationTime().toInstant().isBefore(expiresAt)) {
                expiresAt = claims.getExpirationTime().toInstant();
            }
            JsonNode root = objectMapper.valueToTree(claims.getClaims());
            JsonNode credential = key.vc20() ? root : root.path("vc");
            if (!credential.isObject()
                    || (credential.has("id") ? !key.url().equals(credential.path("id").asText())
                            : key.vc20() || !key.url().equals(claims.getJWTID()))
                    || (claims.getJWTID() != null && !key.url().equals(claims.getJWTID()))) {
                throw new SecurityException("Status list credential ID mismatch");
            }
            String startField = key.vc20() ? "validFrom" : "issuanceDate";
            String endField = key.vc20() ? "validUntil" : "expirationDate";
            Instant validFrom = dateIfPresent(credential, startField);
            Instant validUntil = dateIfPresent(credential, endField);
            if ((validFrom != null && validFrom.isAfter(now))
                    || (validUntil != null && !validUntil.isAfter(now))) {
                throw new SecurityException("Status list outside validity period");
            }
            if (validUntil != null && validUntil.isBefore(expiresAt)) {
                expiresAt = validUntil;
            }
            JsonNode issuer = credential.path("issuer");
            String issuerId = issuer.isTextual() ? issuer.asText() : issuer.path("id").asText();
            if ((key.vc20() || credential.has("issuer")) && !sameDid(issuerId, key.issuerDid())) {
                throw new SecurityException("Status list issuer property mismatch");
            }
            String credentialType = key.vc20() ? "BitstringStatusListCredential" : "StatusList2021Credential";
            String subjectType = key.vc20() ? "BitstringStatusList" : "StatusList2021";
            JsonNode types = credential.path("type");
            boolean matchingType = types.isArray() && containsType(types, credentialType);
            JsonNode subject = credential.path("credentialSubject");
            if (!matchingType || !subject.path("type").asText().equals(subjectType)
                    || !subject.path("statusPurpose").asText().equals("revocation")
                    || !subject.path("encodedList").isTextual()) {
                throw new SecurityException("Invalid status list credential");
            }
            String encoded = subject.path("encodedList").asText();
            if (key.vc20()) {
                if (!encoded.startsWith("u")) {
                    throw new SecurityException("Invalid bitstring encoding");
                }
                encoded = encoded.substring(1);
            }
            byte[] compressed = Base64.getUrlDecoder().decode(encoded);
            try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                byte[] bits = gzip.readNBytes(MAX_BYTES + 1);
                if (bits.length < MIN_LIST_BYTES || bits.length > MAX_BYTES) {
                    throw new SecurityException("Invalid status list size");
                }
                return new CachedList(bits, expiresAt);
            }
        } catch (IOException | ParseException | IllegalArgumentException e) {
            throw new SecurityException("Unable to fetch or verify status list", e);
        }
    }

    private static boolean containsType(JsonNode types, String expected) {
        for (JsonNode type : types) {
            if (expected.equals(type.asText())) {
                return true;
            }
        }
        return false;
    }

    private static void requireSecureEndpoint(String url, boolean allowInsecureHttp) {
        try {
            DcpTransportSecurity.requireSecure(url, "status list endpoint", allowInsecureHttp);
        } catch (InsecureEndpointException e) {
            throw new SecurityException("Invalid or insecure status list endpoint");
        }
    }

    private static Instant dateIfPresent(JsonNode credential, String field) {
        if (!credential.has(field)) {
            return null;
        }
        JsonNode value = credential.get(field);
        if (!value.isTextual()) {
            throw new SecurityException("Invalid status list validity date");
        }
        try {
            return Instant.parse(value.asText());
        } catch (DateTimeParseException e) {
            throw new SecurityException("Invalid status list validity date", e);
        }
    }

    private static boolean sameDid(String candidate, String expected) {
        return candidate != null && candidate.indexOf('#') < 0 && DidUrlConverter.compareDids(candidate, expected);
    }

    private record CacheKey(String url, String issuerDid, boolean vc20) {
    }

    private record CachedList(byte[] bits, Instant expiresAt) {
    }
}
