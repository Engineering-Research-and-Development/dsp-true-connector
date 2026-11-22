package it.eng.dcp.issuer.service.jwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.issuer.model.StatusList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusListCredentialGeneratorTest {

    private static final String ISSUER_DID = "did:web:issuer.example.com";
    private static final String URL = "http://issuer.example.com/issuer/status/list-1";

    @Mock
    private KeyService keyService;

    @Mock
    private BaseDidDocumentConfiguration didDocumentConfig;

    private ECKey signingKey;
    private StatusListCredentialGenerator generator;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new ECKeyGenerator(Curve.P_256).keyID("issuer-key-1").generate();
        generator = new StatusListCredentialGenerator(keyService, didDocumentConfig);
    }

    @Test
    @DisplayName("vc20-bssl/jwt: a signed BitstringStatusListCredential with a multibase encoded list")
    void generate_vc20_bitstringStatusListCredential() throws Exception {
        when(keyService.getSigningJwk(any())).thenReturn(signingKey);
        StatusList list = revokedAtIndex(ProfileId.VC20_BSSL_JWT, 3);

        SignedJWT jwt = SignedJWT.parse(generator.generate(list));

        assertTrue(jwt.verify(new ECDSAVerifier(signingKey.toPublicJWK())));
        assertEquals("issuer-key-1", jwt.getHeader().getKeyID());
        assertEquals(new JOSEObjectType("vc+ld+jwt"), jwt.getHeader().getType());
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertEquals(ISSUER_DID, claims.getIssuer());
        assertEquals(URL, claims.getClaim("id"));
        assertEquals(List.of("https://www.w3.org/ns/credentials/v2"), claims.getClaim("@context"));
        assertEquals(List.of("VerifiableCredential", "BitstringStatusListCredential"), claims.getClaim("type"));
        assertNotNull(claims.getClaim("validFrom"));
        Map<String, Object> subject = claims.getJSONObjectClaim("credentialSubject");
        assertEquals(URL + "#list", subject.get("id"));
        assertEquals("BitstringStatusList", subject.get("type"));
        assertEquals("revocation", subject.get("statusPurpose"));
        String encodedList = (String) subject.get("encodedList");
        assertTrue(encodedList.startsWith("u"));
        assertArrayEquals(list.getBitstring(), decode(encodedList.substring(1)));
    }

    @Test
    @DisplayName("vc11-sl2021/jwt: a signed StatusList2021Credential in the vc claim with a base64url encoded list")
    @SuppressWarnings("unchecked")
    void generate_vc11_statusList2021Credential() throws Exception {
        when(keyService.getSigningJwk(any())).thenReturn(signingKey);
        StatusList list = revokedAtIndex(ProfileId.VC11_SL2021_JWT, 0);

        SignedJWT jwt = SignedJWT.parse(generator.generate(list));

        assertTrue(jwt.verify(new ECDSAVerifier(signingKey.toPublicJWK())));
        assertEquals(JOSEObjectType.JWT, jwt.getHeader().getType());
        Map<String, Object> vc = jwt.getJWTClaimsSet().getJSONObjectClaim("vc");
        assertEquals(URL, vc.get("id"));
        assertEquals(ISSUER_DID, vc.get("issuer"));
        assertEquals(List.of("https://www.w3.org/2018/credentials/v1", "https://w3id.org/vc/status-list/2021/v1"),
                vc.get("@context"));
        assertEquals(List.of("VerifiableCredential", "StatusList2021Credential"), vc.get("type"));
        assertNotNull(vc.get("issuanceDate"));
        Map<String, Object> subject = (Map<String, Object>) vc.get("credentialSubject");
        assertEquals("StatusList2021", subject.get("type"));
        assertEquals("revocation", subject.get("statusPurpose"));
        byte[] decoded = decode((String) subject.get("encodedList"));
        assertArrayEquals(list.getBitstring(), decoded);
        assertEquals((byte) 0x80, decoded[0]);
    }

    @Test
    @DisplayName("The status list credential carries no holder data")
    void generate_noHolderData() throws Exception {
        when(keyService.getSigningJwk(any())).thenReturn(signingKey);
        StatusList list = revokedAtIndex(ProfileId.VC20_BSSL_JWT, 1);

        JWTClaimsSet claims = SignedJWT.parse(generator.generate(list)).getJWTClaimsSet();

        assertEquals(URL + "#list", claims.getSubject());
        Map<String, Object> subject = claims.getJSONObjectClaim("credentialSubject");
        assertEquals(Set.of("id", "type", "statusPurpose", "encodedList"), subject.keySet());
    }

    @Test
    @DisplayName("isSignedWithCurrentKey is true only for a JWT signed with the current key ID")
    void isSignedWithCurrentKey() throws Exception {
        when(keyService.getSigningJwk(any())).thenReturn(signingKey);
        String jwt = generator.generate(revokedAtIndex(ProfileId.VC20_BSSL_JWT, 0));

        assertTrue(generator.isSignedWithCurrentKey(jwt));

        when(keyService.getSigningJwk(any())).thenReturn(new ECKeyGenerator(Curve.P_256).keyID("rotated").generate());
        assertFalse(generator.isSignedWithCurrentKey(jwt));
    }

    @Test
    @DisplayName("isSignedWithCurrentKey is false for a missing or unparsable credential")
    void isSignedWithCurrentKey_invalidInput() {
        assertFalse(generator.isSignedWithCurrentKey(null));
        assertFalse(generator.isSignedWithCurrentKey(" "));
        assertFalse(generator.isSignedWithCurrentKey("not-a-jwt"));
    }

    @Test
    @DisplayName("mediaTypeOf gives the media type of each profile")
    void mediaTypeOf() {
        assertEquals("application/vc+ld+jwt", StatusListCredentialGenerator.mediaTypeOf(ProfileId.VC20_BSSL_JWT));
        assertEquals("application/jwt", StatusListCredentialGenerator.mediaTypeOf(ProfileId.VC11_SL2021_JWT));
    }

    private static StatusList revokedAtIndex(ProfileId profile, int index) {
        StatusList list = StatusList.Builder.newInstance()
                .id("list-1")
                .url(URL)
                .issuerDid(ISSUER_DID)
                .statusPurpose("revocation")
                .profile(profile)
                .size(16)
                .nextIndex(index + 1)
                .build();
        list.setBit(index);
        return list;
    }

    private static byte[] decode(String base64url) throws IOException {
        byte[] compressed = Base64.getUrlDecoder().decode(base64url);
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        }
    }
}
