package it.eng.dcp.common.service.sts;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.common.service.did.DidResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SelfIssuedIdTokenServiceTest {
    private static final String CONNECTOR_DID = "did:web:test:verifier";
    private static final String SUBJECT_DID = "did:web:test:subject";
    private static final String KID = "test-key";
    private static final String JTI = UUID.randomUUID().toString();

    @Mock
    private DidResolverService didResolver;
    @Mock
    private JtiReplayCache jtiCache;
    @Mock
    private KeyService keyService;
    @Mock
    private DidDocumentConfig config;
    @Mock
    private DcpAuditEventPublisher auditPublisher;
    private SelfIssuedIdTokenService service;
    private ECKey ecJwk;

    @BeforeEach
    void setUp() throws Exception {
        service = new SelfIssuedIdTokenService(didResolver, jtiCache, keyService, config, auditPublisher);

        // Generate EC key for signing and verification
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        ecJwk = new ECKey.Builder(Curve.P_256, (ECPublicKey) kp.getPublic())
                .privateKey((ECPrivateKey) kp.getPrivate())
                .keyID(KID)
                .build();

        // By default the resolved DID document id equals the requested DID (sub == DID Document id)
        lenient().when(didResolver.fetchDidDocumentCached(anyString()))
                .thenAnswer(inv -> DidDocument.Builder.newInstance().id(inv.getArgument(0)).build());
    }

    private String createJwt(JWTClaimsSet claims, ECKey key) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        JWSSigner signer = new ECDSASigner(key.toECPrivateKey());
        jwt.sign(signer);
        return jwt.serialize();
    }

    private JWTClaimsSet.Builder baseClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(SUBJECT_DID)
                .subject(SUBJECT_DID)
                .audience(CONNECTOR_DID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(JTI);
    }

    @Test
    void validateToken_success() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        JWTClaimsSet claims = baseClaims().build();
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);
        doNothing().when(jtiCache).checkAndPut(anyString(), any());

        assertEquals(claims.getJWTID(), service.validateToken(jwt).getJWTID());

        ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
        verify(auditPublisher).publishEvent(
                typeCaptor.capture(),
                any(String.class),
                eq("common"),
                isNull(), isNull(), isNull(), isNull(),
                any(Map.class));
        assertEquals(DcpAuditEventType.SELF_ISSUED_TOKEN_VALIDATED, typeCaptor.getValue());
    }

    @Test
    void validateToken_issNotEqualSub() throws Exception {
        JWTClaimsSet claims = baseClaims().issuer("other:issuer").build();
        String jwt = createJwt(claims, ecJwk);
        assertThrows(SecurityException.class, () -> service.validateToken(jwt));

        ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditPublisher).publishEvent(
                typeCaptor.capture(),
                any(String.class),
                eq("common"),
                isNull(), isNull(), isNull(), isNull(),
                detailsCaptor.capture());
        assertEquals(DcpAuditEventType.TOKEN_VALIDATION_FAILED, typeCaptor.getValue());
        assertTrue(detailsCaptor.getValue().get("reason").toString().contains("iss and sub"));
    }

    @Test
    void validateToken_audNotEqualVerifier() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        JWTClaimsSet claims = baseClaims().audience("wrong:aud").build();
        String jwt = createJwt(claims, ecJwk);
        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("aud claim"));
    }

    @Test
    void validateToken_noJwkFound() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        JWTClaimsSet claims = baseClaims().build();
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(null);
        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("No JWK"));
    }

    @Test
    void validateToken_invalidSignature() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);

        // Use a different key for signing
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp2 = kpg.generateKeyPair();
        ECKey wrongKey = new ECKey.Builder(Curve.P_256, (ECPublicKey) kp2.getPublic())
                .privateKey((ECPrivateKey) kp2.getPrivate())
                .keyID(KID)
                .build();
        JWTClaimsSet claims = baseClaims().build();
        String jwt = createJwt(claims, wrongKey);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);
        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("Invalid signature"));
    }

    @Test
    void validateToken_tamperedPayload_rejected() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        String jwt = createJwt(baseClaims().build(), ecJwk);
        // Replace the payload with different claims while keeping the original signature
        String[] parts = jwt.split("\\.");
        String tamperedPayload = Base64URL.encode(baseClaims().claim("token", "injected").build().toString()).toString();
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);

        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(tampered));
        assertTrue(ex.getMessage().contains("Invalid signature"));
        verify(jtiCache, never()).checkAndPut(anyString(), any());
    }

    @Test
    void validateToken_subDiffersFromDidDocumentId_rejected() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        String jwt = createJwt(baseClaims().build(), ecJwk);
        when(didResolver.fetchDidDocumentCached(SUBJECT_DID))
                .thenReturn(DidDocument.Builder.newInstance().id("did:web:test:someone-else").build());

        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("DID Document id"));
        verify(didResolver, never()).resolvePublicKey(anyString(), any(), anyString());
    }

    @Test
    void validateToken_didDocumentNotFound_rejected() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        String jwt = createJwt(baseClaims().build(), ecJwk);
        when(didResolver.fetchDidDocumentCached(SUBJECT_DID)).thenReturn(null);

        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("DID Document id"));
    }

    @Test
    void validateToken_didDocumentFetchFails_rejected() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        String jwt = createJwt(baseClaims().build(), ecJwk);
        when(didResolver.fetchDidDocumentCached(SUBJECT_DID)).thenThrow(new IOException("unreachable"));

        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("Failed to resolve DID"));
    }

    @Test
    void validateToken_nonDidWebSub_rejectedAsSecurityException() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        String didKey = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK";
        String jwt = createJwt(baseClaims().issuer(didKey).subject(didKey).build(), ecJwk);
        when(didResolver.fetchDidDocumentCached(didKey)).thenThrow(new IllegalArgumentException("Invalid DID:web format"));

        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("Failed to resolve DID"));
        verify(auditPublisher).publishEvent(eq(DcpAuditEventType.TOKEN_VALIDATION_FAILED), anyString(), eq("common"),
                isNull(), isNull(), isNull(), isNull(), any(Map.class));
    }

    @Test
    void validateToken_unsupportedKeyType_rejected() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        String jwt = createJwt(baseClaims().build(), ecJwk);
        OctetSequenceKey symmetricKey = new OctetSequenceKeyGenerator(256).keyID(KID).generate();
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(symmetricKey);

        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("Unsupported key type"));
    }

    @Test
    void validateToken_noKidHeader_resolvesWithNullKid() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).build();
        SignedJWT signed = new SignedJWT(header, baseClaims().build());
        signed.sign(new ECDSASigner(ecJwk.toECPrivateKey()));
        when(didResolver.resolvePublicKey(SUBJECT_DID, null, "capabilityInvocation")).thenReturn(ecJwk);

        assertEquals(JTI, service.validateToken(signed.serialize()).getJWTID());
    }

    @Test
    void validateToken_nbfInFuture() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);

        Instant now = Instant.now();
        JWTClaimsSet claims = baseClaims().notBeforeTime(Date.from(now.plusSeconds(300))).build();
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);
        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("nbf"));
    }

    @Test
    void validateToken_expired() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);

        Instant now = Instant.now();
        // Set expiration to more than 120 seconds in the past to account for clock skew
        JWTClaimsSet claims = baseClaims().expirationTime(Date.from(now.minusSeconds(121))).build();
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);
        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void validateToken_iatMissing() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(SUBJECT_DID)
                .subject(SUBJECT_DID)
                .audience(CONNECTOR_DID)
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .jwtID(JTI)
                .build();
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);
        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("Missing iat"));
    }

    @Test
    void validateToken_iatTooOld() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);

        Instant now = Instant.now();
        JWTClaimsSet claims = baseClaims().issueTime(Date.from(now.minusSeconds(4000))).build();
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);
        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("issued too far"));
    }

    @Test
    void validateToken_jtiMissing() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(SUBJECT_DID)
                .subject(SUBJECT_DID)
                .audience(CONNECTOR_DID)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);
        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("Missing jti"));
    }

    @Test
    void validateToken_jtiReplay() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);

        JWTClaimsSet claims = baseClaims().build();
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);
        doThrow(new IllegalStateException("replay")).when(jtiCache).checkAndPut(anyString(), any());
        Exception ex = assertThrows(SecurityException.class, () -> service.validateToken(jwt));
        assertTrue(ex.getMessage().contains("jti has already been used"));
    }

    @Test
    @DisplayName("a negative clock skew is refused")
    void setClockSkewSeconds_negative_rejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.setClockSkewSeconds(-30));
        assertTrue(ex.getMessage().contains("dcp.clock-skew-seconds"));
    }

    @Test
    @DisplayName("jti is kept in the replay cache until exp plus the clock skew")
    void validateToken_jtiKeptUntilExpPlusClockSkew() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        JWTClaimsSet claims = baseClaims().build();
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);

        String jwt = createJwt(claims, ecJwk);
        // exp as carried by the token (whole seconds)
        Instant exp = SignedJWT.parse(jwt).getJWTClaimsSet().getExpirationTime().toInstant();

        service.validateToken(jwt);

        ArgumentCaptor<Instant> expiry = ArgumentCaptor.forClass(Instant.class);
        verify(jtiCache).checkAndPut(eq(JTI), expiry.capture());
        assertEquals(exp.plusSeconds(SelfIssuedIdTokenService.DEFAULT_CLOCK_SKEW_SECONDS), expiry.getValue());
    }

    @Test
    @DisplayName("a configured clock skew is used for the jti expiry")
    void validateToken_configuredClockSkewUsedForJtiExpiry() throws Exception {
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        service.setClockSkewSeconds(30);
        JWTClaimsSet claims = baseClaims().build();
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);

        String jwt = createJwt(claims, ecJwk);
        Instant exp = SignedJWT.parse(jwt).getJWTClaimsSet().getExpirationTime().toInstant();

        service.validateToken(jwt);

        verify(jtiCache).checkAndPut(JTI, exp.plusSeconds(30));
    }

    @Test
    @DisplayName("a replay after exp but inside the clock skew is rejected")
    void validateToken_replayAfterExpInsideClockSkew_rejected() throws Exception {
        // Real cache: the jti must outlive exp for as long as the token is still accepted
        SelfIssuedIdTokenService realCacheService = new SelfIssuedIdTokenService(
                didResolver, new InMemoryJtiReplayCache(), keyService, config, auditPublisher);
        when(config.getDid()).thenReturn(CONNECTOR_DID);
        Instant now = Instant.now();
        // Expired 60 s ago: still accepted thanks to the 120 s clock skew
        JWTClaimsSet claims = baseClaims()
                .issueTime(Date.from(now.minusSeconds(360)))
                .expirationTime(Date.from(now.minusSeconds(60)))
                .build();
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);

        assertEquals(JTI, realCacheService.validateToken(jwt).getJWTID());
        Exception ex = assertThrows(SecurityException.class, () -> realCacheService.validateToken(jwt));
        assertTrue(ex.getMessage().contains("jti has already been used"));
    }

    @Test
    void createAndSignToken_success() throws Exception {
        // Arrange
        String audienceDid = "did:web:test:audience";
        String accessToken = "access-token-value";
        DidDocumentConfig didConfig = mock(DidDocumentConfig.class);
        when(keyService.getSigningJwk(didConfig)).thenReturn(ecJwk);
        when(didConfig.getDid()).thenReturn(audienceDid);

        // Act
        String jwt = service.createAndSignToken(audienceDid, accessToken, didConfig);
        SignedJWT parsed = SignedJWT.parse(jwt);

        // Assert
        assertEquals(parsed.getJWTClaimsSet().getAudience().get(0), audienceDid);
        assertEquals(parsed.getJWTClaimsSet().getClaim("token"), accessToken);
        assertEquals(parsed.getHeader().getKeyID(), ecJwk.getKeyID());
        assertTrue(parsed.verify(new ECDSAVerifier(ecJwk.toECPublicKey())));
    }

    @Test
    void createAndSignToken_nullAudience_throws() {
        DidDocumentConfig didConfig = mock(DidDocumentConfig.class);
        assertThrows(IllegalArgumentException.class, () -> service.createAndSignToken(null, "token", didConfig));
        assertThrows(IllegalArgumentException.class, () -> service.createAndSignToken("", "token", didConfig));
    }

    @Test
    void createAndSignToken_connectorDidBlank_throws() throws Exception {
        // Arrange: create service with blank connectorDid
        SelfIssuedIdTokenService blankService = new SelfIssuedIdTokenService(didResolver, jtiCache, keyService, config, auditPublisher);
        DidDocumentConfig didConfig = mock(DidDocumentConfig.class);
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> blankService.createAndSignToken("did:web:test:audience", "token", didConfig));
    }

    @Test
    void createAndSignToken_keyServiceThrows_throws() throws Exception {
        DidDocumentConfig didConfig = mock(DidDocumentConfig.class);
        when(didConfig.getDid()).thenReturn(CONNECTOR_DID);

        when(keyService.getSigningJwk(didConfig)).thenThrow(new RuntimeException("key error"));
        assertThrows(RuntimeException.class, () -> service.createAndSignToken("did:web:test:audience", "token", didConfig));
    }

    @Test
    void validateToken_encodedIssuerDid_success() throws Exception {
        // Test with encoded DID (localhost%3A8080) - this is what we might get from a JWT
        String encodedDid = "did:web:localhost%3A8080:issuer";

        when(config.getDid()).thenReturn(CONNECTOR_DID);

        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(encodedDid)
                .subject(encodedDid)
                .audience(CONNECTOR_DID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(encodedDid, KID, "capabilityInvocation")).thenReturn(ecJwk);
        doNothing().when(jtiCache).checkAndPut(anyString(), any());
        
        JWTClaimsSet result = service.validateToken(jwt);
        assertEquals(encodedDid, result.getIssuer());
    }

    @Test
    void validateToken_encodedAndDecodedAudienceDid_success() throws Exception {
        // Connector DID is encoded, audience is decoded - should still work
        String encodedConnectorDid = "did:web:localhost%3A8080:holder";
        String decodedConnectorDid = "did:web:localhost:8080:holder";

        when(config.getDid()).thenReturn(encodedConnectorDid);

        // Create service with encoded connector DID
        SelfIssuedIdTokenService encodedService = new SelfIssuedIdTokenService(
            didResolver, jtiCache, keyService, config, auditPublisher);

        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(SUBJECT_DID)
                .subject(SUBJECT_DID)
                .audience(decodedConnectorDid)  // Token has decoded DID
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);
        doNothing().when(jtiCache).checkAndPut(anyString(), any());
        
        // Should succeed because compareDids handles encoded/decoded formats
        JWTClaimsSet result = encodedService.validateToken(jwt);
        assertEquals(decodedConnectorDid, result.getAudience().get(0));
    }

    @Test
    void validateToken_decodedAndEncodedAudienceDid_success() throws Exception {
        // Connector DID is decoded, audience is encoded - should still work
        String decodedConnectorDid = "did:web:localhost:8080:holder";
        String encodedConnectorDid = "did:web:localhost%3A8080:holder";

        when(config.getDid()).thenReturn(decodedConnectorDid);

        // Create service with decoded connector DID
        SelfIssuedIdTokenService decodedService = new SelfIssuedIdTokenService(
            didResolver, jtiCache, keyService, config, auditPublisher);

        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(SUBJECT_DID)
                .subject(SUBJECT_DID)
                .audience(encodedConnectorDid)  // Token has encoded DID
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(SUBJECT_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);
        doNothing().when(jtiCache).checkAndPut(anyString(), any());
        
        // Should succeed because compareDids handles encoded/decoded formats
        JWTClaimsSet result = decodedService.validateToken(jwt);
        assertEquals(encodedConnectorDid, result.getAudience().get(0));
    }

    @Test
    void validateToken_realWorldDebugLogScenario_success() throws Exception {
        // Issuer DID might be encoded in the JWT: "did:web:localhost%3A8080:issuer"
        // But in configuration it's decoded: "did:web:localhost:8080:issuer"
        String encodedIssuerDid = "did:web:localhost%3A8080:issuer";
        String encodedHolderDid = "did:web:localhost%3A8080:holder";

        when(config.getDid()).thenReturn(encodedHolderDid);

        // Create service with encoded holder DID (from config)
        SelfIssuedIdTokenService holderService = new SelfIssuedIdTokenService(
            didResolver, jtiCache, keyService, config, auditPublisher);

        Instant now = Instant.now();
        // JWT has encoded issuer/subject but decoded audience
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(encodedIssuerDid)
                .subject(encodedIssuerDid)
                .audience("did:web:localhost:8080:holder")  // Decoded audience
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        
        String jwt = createJwt(claims, ecJwk);
        when(didResolver.resolvePublicKey(encodedIssuerDid, KID, "capabilityInvocation")).thenReturn(ecJwk);
        doNothing().when(jtiCache).checkAndPut(anyString(), any());
        
        // Should succeed - this is the exact scenario from debug log
        JWTClaimsSet result = holderService.validateToken(jwt);
        assertEquals(encodedIssuerDid, result.getIssuer());
        assertEquals(encodedIssuerDid, result.getSubject());
    }

    // ── Access tokens for the Credential Service (ADR D-TEC-014, DCP v1.0.1 §5.3.1) ──────────────

    private static final String HOLDER_DID = "did:web:test:holder";
    private static final String VERIFIER_DID = "did:web:test:the-verifier";

    private String createAccessJwt(JWTClaimsSet claims, ECKey key, String typ) throws Exception {
        JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID());
        if (typ != null) {
            header.type(new JOSEObjectType(typ));
        }
        SignedJWT jwt = new SignedJWT(header.build(), claims);
        jwt.sign(new ECDSASigner(key.toECPrivateKey()));
        return jwt.serialize();
    }

    private JWTClaimsSet.Builder accessClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(HOLDER_DID)
                .subject(HOLDER_DID)
                .audience(VERIFIER_DID)
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID("urn:uuid:" + UUID.randomUUID())
                .claim("scope", List.of("MembershipCredential"));
    }

    @Test
    @DisplayName("createStsCompatibleToken binds the nested access token to the Verifier")
    void createStsCompatibleToken_innerAccessTokenBoundToVerifier() throws Exception {
        DidDocumentConfig didConfig = mock(DidDocumentConfig.class);
        when(didConfig.getDid()).thenReturn(HOLDER_DID);
        when(keyService.getSigningJwk(didConfig)).thenReturn(ecJwk);

        String wrapper = service.createStsCompatibleToken(VERIFIER_DID, didConfig, "MembershipCredential");

        JWTClaimsSet outer = SignedJWT.parse(wrapper).getJWTClaimsSet();
        assertEquals(List.of(VERIFIER_DID), outer.getAudience());
        assertEquals(HOLDER_DID, outer.getSubject());
        SignedJWT inner = SignedJWT.parse(outer.getStringClaim("token"));
        assertEquals(SelfIssuedIdTokenService.ACCESS_TOKEN_TYPE, inner.getHeader().getType().getType());
        assertEquals(List.of(VERIFIER_DID), inner.getJWTClaimsSet().getAudience());
        assertEquals(HOLDER_DID, inner.getJWTClaimsSet().getIssuer());
        assertEquals(HOLDER_DID, inner.getJWTClaimsSet().getSubject());
        assertEquals(List.of("MembershipCredential"), inner.getJWTClaimsSet().getStringListClaim("scope"));
        assertTrue(inner.verify(new ECDSAVerifier(ecJwk.toECPublicKey())));
    }

    @Test
    void createAccessTokenWithScopes_noScopes_omitsScopeClaim() throws Exception {
        DidDocumentConfig didConfig = mock(DidDocumentConfig.class);
        when(didConfig.getDid()).thenReturn(HOLDER_DID);
        when(keyService.getSigningJwk(didConfig)).thenReturn(ecJwk);

        String token = service.createAccessTokenWithScopes(VERIFIER_DID, didConfig);

        assertNull(SignedJWT.parse(token).getJWTClaimsSet().getClaim("scope"));
    }

    @Test
    void createAccessTokenWithScopes_blankVerifier_throws() {
        DidDocumentConfig didConfig = mock(DidDocumentConfig.class);
        assertThrows(IllegalArgumentException.class, () -> service.createAccessTokenWithScopes(" ", didConfig, "s"));
    }

    @Test
    void validateAccessToken_roundTrip_success() throws Exception {
        DidDocumentConfig didConfig = mock(DidDocumentConfig.class);
        when(didConfig.getDid()).thenReturn(HOLDER_DID);
        when(keyService.getSigningJwk(didConfig)).thenReturn(ecJwk);
        when(config.getDid()).thenReturn(HOLDER_DID);
        when(didResolver.resolvePublicKey(HOLDER_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);

        String token = service.createAccessTokenWithScopes(VERIFIER_DID, didConfig, "MembershipCredential");
        JWTClaimsSet claims = service.validateAccessToken(token, VERIFIER_DID);

        assertEquals(HOLDER_DID, claims.getSubject());
        assertEquals(List.of("MembershipCredential"), claims.getStringListClaim("scope"));
        verifyNoInteractions(jtiCache);
    }

    @Test
    void validateAccessToken_missing_rejected() {
        assertThrows(SecurityException.class, () -> service.validateAccessToken(null, VERIFIER_DID));
        assertThrows(SecurityException.class, () -> service.validateAccessToken(" ", VERIFIER_DID));
    }

    @Test
    void validateAccessToken_notAJwt_rejected() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.validateAccessToken("opaque-value", VERIFIER_DID));
        assertTrue(ex.getMessage().contains("format"));
    }

    @Test
    @DisplayName("A Self-Issued ID Token (no at+jwt typ) is not accepted as an access token")
    void validateAccessToken_wrongType_rejected() throws Exception {
        String token = createAccessJwt(accessClaims().build(), ecJwk, null);
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.validateAccessToken(token, VERIFIER_DID));
        assertTrue(ex.getMessage().contains("typ"));
    }

    @Test
    @DisplayName("An access token issued by another participant is rejected")
    void validateAccessToken_foreignIssuer_rejected() throws Exception {
        when(config.getDid()).thenReturn(HOLDER_DID);
        String token = createAccessJwt(accessClaims().issuer("did:web:test:other").subject("did:web:test:other").build(),
                ecJwk, SelfIssuedIdTokenService.ACCESS_TOKEN_TYPE);
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.validateAccessToken(token, VERIFIER_DID));
        assertTrue(ex.getMessage().contains("not issued"));
        verify(didResolver, never()).resolvePublicKey(any(), any(), any());
    }

    @Test
    void validateAccessToken_subjectNotHolder_rejected() throws Exception {
        when(config.getDid()).thenReturn(HOLDER_DID);
        String token = createAccessJwt(accessClaims().subject(VERIFIER_DID).build(),
                ecJwk, SelfIssuedIdTokenService.ACCESS_TOKEN_TYPE);
        assertThrows(SecurityException.class, () -> service.validateAccessToken(token, VERIFIER_DID));
    }

    @Test
    @DisplayName("An access token issued to one Verifier cannot be used by another")
    void validateAccessToken_otherVerifier_rejected() throws Exception {
        when(config.getDid()).thenReturn(HOLDER_DID);
        String token = createAccessJwt(accessClaims().build(), ecJwk, SelfIssuedIdTokenService.ACCESS_TOKEN_TYPE);
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.validateAccessToken(token, "did:web:test:another-verifier"));
        assertTrue(ex.getMessage().contains("aud"));
    }

    @Test
    void validateAccessToken_invalidSignature_rejected() throws Exception {
        when(config.getDid()).thenReturn(HOLDER_DID);
        ECKey otherKey = new ECKeyGenerator(Curve.P_256).keyID(KID).generate();
        String token = createAccessJwt(accessClaims().build(), otherKey, SelfIssuedIdTokenService.ACCESS_TOKEN_TYPE);
        when(didResolver.resolvePublicKey(HOLDER_DID, KID, "capabilityInvocation")).thenReturn(ecJwk.toPublicJWK());
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.validateAccessToken(token, VERIFIER_DID));
        assertTrue(ex.getMessage().contains("signature"));
    }

    @Test
    void validateAccessToken_noKey_rejected() throws Exception {
        when(config.getDid()).thenReturn(HOLDER_DID);
        String token = createAccessJwt(accessClaims().build(), ecJwk, SelfIssuedIdTokenService.ACCESS_TOKEN_TYPE);
        when(didResolver.resolvePublicKey(HOLDER_DID, KID, "capabilityInvocation")).thenReturn(null);
        assertThrows(SecurityException.class, () -> service.validateAccessToken(token, VERIFIER_DID));
    }

    @Test
    void validateAccessToken_expired_rejected() throws Exception {
        when(config.getDid()).thenReturn(HOLDER_DID);
        Instant past = Instant.now().minusSeconds(3600);
        String token = createAccessJwt(accessClaims()
                        .issueTime(Date.from(past)).notBeforeTime(Date.from(past))
                        .expirationTime(Date.from(past.plusSeconds(300))).build(),
                ecJwk, SelfIssuedIdTokenService.ACCESS_TOKEN_TYPE);
        when(didResolver.resolvePublicKey(HOLDER_DID, KID, "capabilityInvocation")).thenReturn(ecJwk);
        SecurityException ex = assertThrows(SecurityException.class,
                () -> service.validateAccessToken(token, VERIFIER_DID));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void validateAccessToken_rejection_isAudited() throws Exception {
        String token = createAccessJwt(accessClaims().build(), ecJwk, null);
        assertThrows(SecurityException.class, () -> service.validateAccessToken(token, VERIFIER_DID));
        verify(auditPublisher).publishEvent(eq(DcpAuditEventType.TOKEN_VALIDATION_FAILED), anyString(), eq("common"),
                isNull(), isNull(), isNull(), isNull(), any(Map.class));
    }

    @Test
    @DisplayName("An access token is never accepted as a Self-Issued ID Token")
    void validateToken_accessTokenType_rejected() throws Exception {
        String token = createAccessJwt(baseClaims().build(), ecJwk, SelfIssuedIdTokenService.ACCESS_TOKEN_TYPE);
        SecurityException ex = assertThrows(SecurityException.class, () -> service.validateToken(token));
        assertTrue(ex.getMessage().contains("access token"));
        verifyNoInteractions(jtiCache);
    }
}
