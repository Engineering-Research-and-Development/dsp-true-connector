package it.eng.connector.integration;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.EnableWireMock;

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
   
   protected String createNewId() {
		return "urn:uuid:" + UUID.randomUUID().toString();
	}
	   
}
