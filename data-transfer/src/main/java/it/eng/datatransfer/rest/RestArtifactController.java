package it.eng.datatransfer.rest;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE,
path = "/artifact")
@Slf4j
public class RestArtifactController {
	
	private static JsonMapper jsonMapperPlain;
	
	static {
		 SimpleModule instantConverterModule = new SimpleModule();
//        instantConverterModule.addSerializer(Instant.class, new InstantSerializer());
//        instantConverterModule.addDeserializer(Instant.class, new InstantDeserializer());
	        
		 jsonMapperPlain = JsonMapper.builder()
	                .configure(MapperFeature.USE_ANNOTATIONS, false)
	                .serializationInclusion(Include.NON_NULL)
	                .serializationInclusion(Include.NON_EMPTY)
	                .configure(SerializationFeature.INDENT_OUTPUT, true)
	                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
	                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
	                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
	                .addModule(new JavaTimeModule())
	                .addModule(instantConverterModule)
	                .build();
	}

	/**
	 * 
	 * @param authorization
	 * @param transactionId Base64.urlEncoded(consumerPid|providerPid) from TransferProcess message
	 * @param artifactId artifactId
	 * @param jsonBody
	 * @return
	 */
    @PostMapping(path = "/{transactionId}/{id}")
    protected ResponseEntity<String> getArtifact(@RequestHeader(required = false) String authorization,
										    		@PathVariable String transactionId,                                       
										    		@PathVariable String artifactId, 
                                                  @RequestBody(required = false) JsonNode jsonBody) {
    	log.info("Accessing artifact with id {}", artifactId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(getJohnDoe());

    }
    
    // TODO change to get data from repository instead hardcoded
    private String getJohnDoe() {
    	DateFormat dateFormat = new SimpleDateFormat("2023/07/13 12:34:56");
		Date date = new Date();
		String formattedDate = dateFormat.format(date);

		Map<String, String> jsonObject = new HashMap<>();
		jsonObject.put("firstName", "John");
		jsonObject.put("lastName", "Doe");
		jsonObject.put("dateOfBirth", formattedDate);
		jsonObject.put("address", "591  Franklin Street, Pennsylvania");
		jsonObject.put("checksum", "ABC123 " + formattedDate);
		
		try {
			return jsonMapperPlain.writeValueAsString(jsonObject);
		} catch (JsonProcessingException e) {
			log.error(e.getLocalizedMessage());
		}
		return null;
    }
}
