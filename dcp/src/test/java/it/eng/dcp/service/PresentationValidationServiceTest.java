package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.core.ProfileResolver;
import it.eng.dcp.model.PresentationResponseMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PresentationValidationServiceTest {

    @Mock
    private it.eng.dcp.core.ProfileResolver profileResolver;

    @Mock
    private IssuerTrustService issuerTrustService;

    @Mock
    private SchemaRegistryService schemaRegistryService;

    @InjectMocks
    private PresentationValidationServiceImpl validationService;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        // trusted issuer behaviour for TestCredentialType
//        when(issuerTrustService.isTrusted("TestCredentialType", "did:example:issuer")).thenReturn(true);
//        when(issuerTrustService.isTrusted("TestCredentialType", "did:example:untrusted")).thenReturn(false);

        // default schema existence: return false unless explicitly stubbed in a test
//        when(schemaRegistryService.exists(org.mockito.Mockito.anyString())).thenReturn(false);

        // mimic ProfileResolverStub logic via mocked resolver
        org.mockito.stubbing.Answer<it.eng.dcp.model.ProfileId> resolverAnswer = invocation -> {
            String fmt = invocation.getArgument(0, String.class);
            java.util.Map<String, Object> attrs = invocation.getArgument(1, java.util.Map.class);
            boolean hasStatusList = attrs != null && attrs.containsKey("statusList") && attrs.get("statusList") != null;
            if (fmt == null) return null;
            if ("jwt".equalsIgnoreCase(fmt) && hasStatusList) return it.eng.dcp.model.ProfileId.VC11_SL2021_JWT;
            if ("json-ld".equalsIgnoreCase(fmt) && !hasStatusList) return it.eng.dcp.model.ProfileId.VC11_SL2021_JSONLD;
            return null;
        };
        when(profileResolver.resolve(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyMap())).thenAnswer(resolverAnswer);
    }

    private JsonNode buildVpJson(JsonNode credNode, String profileId) {
        var obj = mapper.createObjectNode();
        obj.put("holderDid", "did:example:holder");
        obj.put("profileId", profileId);
        var ids = mapper.createArrayNode();
        ids.add(credNode.has("id") ? credNode.get("id").asText() : "urn:uuid:cred");
        obj.set("credentialIds", ids);
        var pres = mapper.createArrayNode();
        pres.add(credNode);
        obj.set("presentation", pres);
        return obj;
    }

    private JsonNode makeCredential(String id, String issuer, String type, Instant issuance, Instant expiry) {
        var m = mapper;
        var node = m.createObjectNode();
        node.put("id", id);
        node.put("issuer", issuer);
        var types = m.createArrayNode();
        types.add(type);
        node.set("type", types);
        if (issuance != null) node.put("issuanceDate", issuance.toString());
        if (expiry != null) node.put("expirationDate", expiry.toString());
        var schema = m.createObjectNode();
        schema.put("id", "http://example.com/schemas/" + type);
        node.set("credentialSchema", schema);
        var ctx = m.createArrayNode();
        ctx.add("https://www.w3.org/2018/credentials/v1");
        node.set("@context", ctx);
        return node;
    }

    @Test
    void happyPath_singleVc_valid() {
        Instant now = Instant.now();
        JsonNode cred = makeCredential("urn:uuid:1", "did:example:issuer", "TestCredentialType", now.minusSeconds(60), now.plusSeconds(3600));
        // mark credential as JSON-LD by including a minimal proof so ProfileResolver resolves it as VC11_SL2021_JSONLD
        ((com.fasterxml.jackson.databind.node.ObjectNode)cred).set("proof", mapper.createObjectNode().put("type","TestProof"));
        // stub schema existence on the mocked schemaRegistryService
        when(schemaRegistryService.exists("http://example.com/schemas/TestCredentialType")).thenReturn(true);
        when(issuerTrustService.isTrusted(eq("TestCredentialType"), eq("did:example:issuer"))).thenReturn(true);

        JsonNode vpJson = buildVpJson(cred, "VC11_SL2021_JSONLD");
        PresentationResponseMessage rsp = PresentationResponseMessage.Builder.newInstance().presentation(List.of(vpJson)).build();

        var report = validationService.validate(rsp, List.of("TestCredentialType"), null);
        assertTrue(report.isValid(), () -> "Expected validation to pass but got errors: " + report.getErrors());
    }

    @Test
    void edgeCase_expiredCredential_fails() {
        Instant now = Instant.now();
        JsonNode cred = makeCredential("urn:uuid:2", "did:example:issuer", "TestCredentialType", now.minusSeconds(3600), now.minusSeconds(10));
        ((com.fasterxml.jackson.databind.node.ObjectNode)cred).set("proof", mapper.createObjectNode().put("type","TestProof"));
        JsonNode vpJson = buildVpJson(cred, "VC11_SL2021_JSONLD");
        PresentationResponseMessage rsp = PresentationResponseMessage.Builder.newInstance().presentation(List.of(vpJson)).build();

        var report = validationService.validate(rsp, List.of("TestCredentialType"), null);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> "VC_EXPIRED".equals(e.getCode())));
    }

    @Test
    void edgeCase_untrustedIssuer_fails() {
        Instant now = Instant.now();
        JsonNode cred = makeCredential("urn:uuid:3", "did:example:untrusted", "TestCredentialType", now.minusSeconds(60), now.plusSeconds(3600));
        ((com.fasterxml.jackson.databind.node.ObjectNode)cred).set("proof", mapper.createObjectNode().put("type","TestProof"));
        JsonNode vpJson = buildVpJson(cred, "VC11_SL2021_JSONLD");
        PresentationResponseMessage rsp = PresentationResponseMessage.Builder.newInstance().presentation(List.of(vpJson)).build();

        var report = validationService.validate(rsp, List.of("TestCredentialType"), null);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> "ISSUER_UNTRUSTED".equals(e.getCode())));
    }

    @Test
    void edgeCase_missingRequiredType_fails() {
        Instant now = Instant.now();
        JsonNode cred = makeCredential("urn:uuid:4", "did:example:issuer", "OtherType", now.minusSeconds(60), now.plusSeconds(3600));
        ((com.fasterxml.jackson.databind.node.ObjectNode)cred).set("proof", mapper.createObjectNode().put("type","TestProof"));
        JsonNode vpJson = buildVpJson(cred, "VC11_SL2021_JSONLD");
        PresentationResponseMessage rsp = PresentationResponseMessage.Builder.newInstance().presentation(List.of(vpJson)).build();

        var report = validationService.validate(rsp, List.of("TestCredentialType"), null);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> "REQUIREMENT_UNMET".equals(e.getCode())));
    }

    @Test
    void edgeCase_mixedProfiles_fails() {
        Instant now = Instant.now();
        JsonNode cred1 = makeCredential("urn:uuid:5", "did:example:issuer", "TestCredentialType", now.minusSeconds(60), now.plusSeconds(3600));
        JsonNode cred2 = makeCredential("urn:uuid:6", "did:example:issuer", "TestCredentialType", now.minusSeconds(60), now.plusSeconds(3600));
        // make first credential look like JSON-LD (has a proof), second has credentialStatus -> leads to mixed profile
        ((com.fasterxml.jackson.databind.node.ObjectNode)cred1).set("proof", mapper.createObjectNode().put("type","TestProof"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)cred2).set("credentialStatus", mapper.createObjectNode().put("id","status"));
        var vpObj = mapper.createObjectNode();
        vpObj.put("holderDid", "did:example:holder");
        vpObj.put("profileId", "VC11_SL2021_JSONLD");
        var ids = mapper.createArrayNode(); ids.add("urn:uuid:5"); ids.add("urn:uuid:6"); vpObj.set("credentialIds", ids);
        var pres = mapper.createArrayNode(); pres.add(cred1); pres.add(cred2); vpObj.set("presentation", pres);
        PresentationResponseMessage rsp = PresentationResponseMessage.Builder.newInstance().presentation(List.of(vpObj)).build();
        var report = validationService.validate(rsp, List.of("TestCredentialType"), null);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> "PROFILE_MIXED".equals(e.getCode())));
    }
}
