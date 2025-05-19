package it.eng.connector.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.eng.connector.util.TestUtil;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.InstantDeserializer;
import it.eng.tools.serializer.InstantSerializer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.spring.EnableWireMock;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@Slf4j
@SpringBootTest(
        webEnvironment = WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=8090"
        })
@AutoConfigureMockMvc
@EnableWireMock
@Testcontainers
public class BaseIntegrationTest {

    // starts a mongodb and localstack container; the containers are shared among all tests; docker must be running
    protected static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.12");
    protected static final LocalStackContainer localstack = new LocalStackContainer("4.3.0")
        .withServices(S3);

    @Autowired
    protected MockMvc mockMvc;
    protected JsonMapper jsonMapper;

    protected String createNewId() {
        return "urn:uuid:" + UUID.randomUUID().toString();
    }

    static {
        mongoDBContainer.start();
        localstack.start();
    }

    @DynamicPropertySource
    static void containersProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", mongoDBContainer::getFirstMappedPort);
        registry.add("s3.endpoint", () -> localstack.getEndpointOverride(S3).toString());

    }

    @BeforeEach
    public void setup() {
        SimpleModule instantConverterModule = new SimpleModule();
        instantConverterModule.addSerializer(Instant.class, new InstantSerializer());
        instantConverterModule.addDeserializer(Instant.class, new InstantDeserializer());
        jsonMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .configure(MapperFeature.USE_ANNOTATIONS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .addModule(instantConverterModule)
                .build();
    }

    protected JsonNode getContractNegotiationOverAPI()
            throws Exception, JsonProcessingException, JsonMappingException, UnsupportedEncodingException {
        final ResultActions result =
                mockMvc.perform(
                        get(ApiEndpoints.NEGOTIATION_V1)
                                .param("role", IConstants.ROLE_CONSUMER)
                                .with(user(TestUtil.CONNECTOR_USER).password("password").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        JsonNode jsonNode = jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());

//		String json = result.andReturn().getResponse().getContentAsString();
////		GenericApiResponse<List<ContractNegotiation>>
//		TypeReference<GenericApiResponse<List<ContractNegotiation>>> typeRef = new TypeReference<GenericApiResponse<List<ContractNegotiation>>>() {};
//		GenericApiResponse<List<ContractNegotiation>> apiResp =  Serializer.deserializePlain(json, typeRef);

        return jsonNode.findValues("data").get(0).get(jsonNode.findValues("data").get(0).size() - 1);
    }

    protected JsonNode getContractNegotiationOverAPI(String contractNegotiationId)
            throws Exception, JsonProcessingException, JsonMappingException, UnsupportedEncodingException {
        final ResultActions result =
                mockMvc.perform(
                        get(ApiEndpoints.NEGOTIATION_V1 + "/" + contractNegotiationId)
                                .with(user(TestUtil.CONNECTOR_USER).password("password").roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected ContractNegotiation getContractNegotiationOverAPI(String consumerPid, String providerPid)
            throws Exception, JsonProcessingException, JsonMappingException, UnsupportedEncodingException {
        final ResultActions result =
                mockMvc.perform(
                        get(ApiEndpoints.NEGOTIATION_V1)
                                .with(user(TestUtil.CONNECTOR_USER).password("password").roles("ADMIN"))
                                .param("consumerPid", consumerPid)
                                .param("providerPid", providerPid)
                                .contentType(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        String json = result.andReturn().getResponse().getContentAsString();
//		GenericApiResponse<List<ContractNegotiation>>
        TypeReference<GenericApiResponse<List<ContractNegotiation>>> typeRef = new TypeReference<GenericApiResponse<List<ContractNegotiation>>>() {
        };
        GenericApiResponse<List<ContractNegotiation>> apiResp = NegotiationSerializer.deserializePlain(json, typeRef);
        // should be exactly one in list
        return apiResp.getData().get(0);
    }

    protected void offerCheck(ContractNegotiation contractNegotiation, String offerId) {
        assertEquals(offerId, contractNegotiation.getOffer().getOriginalId());
    }

    protected void agreementCheck(ContractNegotiation contractNegotiation) {
        assertNotNull(contractNegotiation.getAgreement());
    }
}
