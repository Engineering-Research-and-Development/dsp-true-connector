package it.eng.tools.client.rest;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
@Disabled
public class GenericDeserializeTest {

	String response = "{\"success\":true,\"message\":\"Fetched formats\",\"data\":[\"HTTP-pull\",\"HTTP-pull\"],\"timestamp\":\"2024-11-07T17:03:00.305644\"}";

	@Test
	public void deserializeTest() {
		
		ObjectMapper objectMapper = new ObjectMapper();
		LocalDateTimeDeserializer localDateTimeDeserializer = new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.ss"));
		JavaTimeModule module = new JavaTimeModule();
		module.addDeserializer(LocalDateTime.class, localDateTimeDeserializer);
		objectMapper.registerModule(module);
		
		TypeReference<GenericApiResponse<List<String>>> typeRef = new TypeReference<GenericApiResponse<List<String>>>() {};
	    try {
	        GenericApiResponse<List<String>> apiResp =  objectMapper.readValue(response, typeRef);
	        apiResp.getData();
	    } catch (JsonProcessingException e) {
	        // pass the original exception along as cause, preserving info.
	        throw new RuntimeException("Could not parse json", e);
	    }
	}
	
	
	private <T> GenericApiResponse<T> parseJson(Class<T> clazz) throws JsonMappingException, JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper();
		LocalDateTimeDeserializer localDateTimeDeserializer = new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
		JavaTimeModule module = new JavaTimeModule();
		module.addDeserializer(LocalDateTime.class, localDateTimeDeserializer);
		objectMapper.registerModule(module);
		
		JavaType type = objectMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, clazz);
	   return objectMapper.readValue(response, type);
	}
	
	@Test
	public void javaTYpe() {
		ObjectMapper objectMapper = new ObjectMapper();
		LocalDateTimeDeserializer localDateTimeDeserializer = new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
		JavaTimeModule module = new JavaTimeModule();
		module.addDeserializer(LocalDateTime.class, localDateTimeDeserializer);
//		objectMapper.enable(DeserializationFeature.UNWRAP_ROOT_VALUE);
		objectMapper.registerModule(module);
		
//		JavaType javaType = TypeFactory.defaultInstance()
//			        .constructParametricType(GenericApiResponse.class, List.class, String.class);

		JavaType javaType = objectMapper.getTypeFactory().constructParametricType(GenericApiResponse.class, List.class);
	
		  try {
		        GenericApiResponse<List<String>> apiResp =  objectMapper.readValue(response, javaType);
		        apiResp.getData();
		    } catch (JsonProcessingException e) {
		        // pass the original exception along as cause, preserving info.
		        throw new RuntimeException("Could not parse json", e);
		    }
	}
	
	
	@Test
	public void ig() {
		String newYorkDateTimePattern = "yyyy-MM-dd'T'HH:mm:ssXXX";
		DateTimeFormatter newYorkDateFormatter = DateTimeFormatter.ofPattern(newYorkDateTimePattern);
		System.out.println(newYorkDateFormatter.format(ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("UTC+2"))));
		OffsetDateTime  ldt =  (OffsetDateTime) newYorkDateFormatter.parse("2024-11-18T14:51:32+02:00");
		System.out.println(ldt);
	}
}
