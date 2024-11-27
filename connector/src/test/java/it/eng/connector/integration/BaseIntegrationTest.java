package it.eng.connector.integration;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.EnableWireMock;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.negotiation.serializer.InstantDeserializer;
import it.eng.negotiation.serializer.InstantSerializer;

@SpringBootTest(
		  webEnvironment = WebEnvironment.DEFINED_PORT,
		  properties = {
		    "server.port=8090"
		  })
@AutoConfigureMockMvc
@EnableWireMock
public class BaseIntegrationTest {
	
   @Autowired
   protected MockMvc mockMvc;
   protected JsonMapper jsonMapper;
   
   protected String createNewId() {
		return "urn:uuid:" + UUID.randomUUID().toString();
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

}
