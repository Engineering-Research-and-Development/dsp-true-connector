package it.eng.dcp.issuer.integration;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.config.CredentialMetadataConfig.CredentialConfig;
import it.eng.dcp.issuer.config.CredentialMetadataConfigLoader;
import it.eng.dcp.issuer.service.CredentialIssuanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for status list allocation, publication and revocation (DCP v1.0.1 §6.10).
 */
class StatusListControllerIT extends BaseIssuerIntegrationTest {

    private static final String BASE_URL = "http://localhost:8084";

    @Autowired
    private CredentialIssuanceService credentialIssuanceService;

    @Autowired
    private CredentialMetadataConfigLoader credentialMetadataConfigLoader;

    @Autowired
    private BaseDidDocumentConfiguration didDocumentConfiguration;

    /**
     * Gives every test a signing key with its own key ID, as production key IDs are derived from the public key.
     * Lists created by earlier tests are then re-signed with the current key when fetched.
     *
     * @throws Exception if the key cannot be generated
     */
    @BeforeEach
    void useDistinctSigningKey() throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate();
        when(keyService.getSigningJwk(any())).thenReturn(key);
    }

    @ParameterizedTest
    @EnumSource(ProfileId.class)
    @DisplayName("A new credential is valid in the published list; revoking it sets its bit in the re-signed list")
    void issueFetchRevokeFetch(ProfileId profile) throws Exception {
        Map<String, Object> credentialStatus = issueCredential(profile);
        String statusListUrl = (String) credentialStatus.get("statusListCredential");
        int index = Integer.parseInt((String) credentialStatus.get("statusListIndex"));
        assertTrue(statusListUrl.startsWith(BASE_URL + "/issuer/status/"), statusListUrl);
        String path = statusListUrl.substring(BASE_URL.length());
        String listId = path.substring("/issuer/status/".length());

        SignedJWT before = fetchStatusList(path, profile);
        assertFalse(bitAt(before, profile, index), "A newly issued credential must not be revoked");

        mockMvc.perform(post("/api/issuer/status-lists/" + listId + "/entries/" + index + "/revoke"))
                .andExpect(status().isOk());

        SignedJWT after = fetchStatusList(path, profile);
        assertTrue(bitAt(after, profile, index), "A revoked credential must have its bit set");
        assertNotEquals(before.serialize(), after.serialize(), "Revocation must re-sign the list");

        mockMvc.perform(post("/api/issuer/status-lists/" + listId + "/entries/" + index + "/revoke"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Two credentials get different indices of the same list")
    void allocationDoesNotReuseIndices() throws Exception {
        Map<String, Object> first = issueCredential(ProfileId.VC20_BSSL_JWT);
        Map<String, Object> second = issueCredential(ProfileId.VC20_BSSL_JWT);

        assertEquals(first.get("statusListCredential"), second.get("statusListCredential"));
        assertNotEquals(first.get("statusListIndex"), second.get("statusListIndex"));
    }

    @Test
    @DisplayName("An unknown status list is 404 on fetch and revoke")
    void unknownList_notFound() throws Exception {
        mockMvc.perform(get("/issuer/status/unknown-list")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/issuer/status-lists/unknown-list/entries/0/revoke"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Revoking an index that was never allocated is 400")
    void revokeUnallocatedIndex_badRequest() throws Exception {
        String url = (String) issueCredential(ProfileId.VC11_SL2021_JWT).get("statusListCredential");
        String listId = url.substring(url.lastIndexOf('/') + 1);

        mockMvc.perform(post("/api/issuer/status-lists/" + listId + "/entries/131071/revoke"))
                .andExpect(status().isBadRequest());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> issueCredential(ProfileId profile) throws Exception {
        String credentialId = credentialMetadataConfigLoader.credentialMetadataConfig().getSupported().stream()
                .filter(config -> profile.getSpecAlias().equals(config.getProfile()))
                .map(CredentialConfig::getId)
                .findFirst()
                .orElseThrow();
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .holderPid("holder-pid")
                .holderDid("did:web:holder.example.com")
                .credentialIds(List.of(credentialId))
                .build();
        CredentialMessage.CredentialContainer container = credentialIssuanceService.generateCredentials(request).get(0);
        Map<String, Object> claims = SignedJWT.parse(String.valueOf(container.getPayload())).getJWTClaimsSet().getClaims();
        Map<String, Object> vc = profile == ProfileId.VC11_SL2021_JWT ? (Map<String, Object>) claims.get("vc") : claims;
        return (Map<String, Object>) vc.get("credentialStatus");
    }

    private SignedJWT fetchStatusList(String path, ProfileId profile) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        String expectedType = profile == ProfileId.VC20_BSSL_JWT ? "application/vc+ld+jwt" : "application/jwt";
        assertEquals(expectedType, result.getResponse().getContentType());
        SignedJWT jwt = SignedJWT.parse(result.getResponse().getContentAsString());
        ECKey signingKey = keyService.getSigningJwk(didDocumentConfiguration.getDidDocumentConfig());
        assertTrue(jwt.verify(new ECDSAVerifier(signingKey.toPublicJWK())), "The list must be signed by the issuer");
        return jwt;
    }

    @SuppressWarnings("unchecked")
    private static boolean bitAt(SignedJWT jwt, ProfileId profile, int index) throws Exception {
        Map<String, Object> claims = jwt.getJWTClaimsSet().getClaims();
        Map<String, Object> vc = profile == ProfileId.VC11_SL2021_JWT ? (Map<String, Object>) claims.get("vc") : claims;
        String encodedList = (String) ((Map<String, Object>) vc.get("credentialSubject")).get("encodedList");
        if (profile == ProfileId.VC20_BSSL_JWT) {
            assertTrue(encodedList.startsWith("u"), "BitstringStatusList uses multibase base64url");
            encodedList = encodedList.substring(1);
        }
        byte[] bits = gunzip(Base64.getUrlDecoder().decode(encodedList));
        return (bits[index / 8] & (1 << (7 - index % 8))) != 0;
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        }
    }
}
