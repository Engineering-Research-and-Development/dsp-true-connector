package it.eng.connector.jsonld;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.api.CompactionApi;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.util.MockObjectUtil;
import jakarta.json.JsonStructure;

@Disabled
public class JsonLdTitaniumTest {

	@Test
	public void titaniumTest() {
		try(InputStream is = getClass().getClassLoader().getResourceAsStream("edc_cat.json")) {
			Document document = JsonDocument.of(is);
//			ExpansionApi expansionApi = JsonLd.expand(document);
			CompactionApi compacted = JsonLd.compact(document, createContextDocument());
//			CompactionApi compacted = JsonLd.compact(document, createTCContext());
//			CompactionApi compacted = JsonLd.compact(document, "https://w3id.org/dspace/2024/1/context.json");
			System.out.println(compacted.get());
			Catalog c = Serializer.deserializeProtocol(compacted.get().toString(), Catalog.class);
			System.out.println(c.getParticipantId());
			
//			System.out.println(compactedProtocol.get());
//			System.out.println(JsonLd.flatten(JsonDocument.of(compacted.get())).compactArrays(true).get());
//			System.out.println(JsonLd.compact(JsonDocument.of(expansionApi.get()), createContextDocument()).get());
		} catch (JsonLdError | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	@Test
	public void convertCompactedArraysEDC() throws IOException, URISyntaxException {
		String edc = readEDC();
		Catalog c = Serializer.deserializeProtocol(edc, Catalog.class);
		System.out.println(c.getParticipantId());
	}
	
	@Test
	public void nas() throws JsonLdError {
		CompactionApi compApi = JsonLd.compact(
				JsonDocument.of(new ByteArrayInputStream(
					Serializer.serializeProtocol(
							MockObjectUtil.CATALOG).getBytes())), 
				"https://w3id.org/dspace/2024/1/context.json");
		System.out.println(compApi
				.compactArrays(true)
				.get().asJsonArray().toString());
	}
	
	private Document createContextDocument() throws JsonLdError {
		String json = "{ \"@context\": {\r\n"
				+ "		\"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\",\r\n"
				+ "		\"edc\": \"https://w3id.org/edc/v0.0.1/ns/\",\r\n"
				+ "		\"dcat\": \"http://www.w3.org/ns/dcat#\",\r\n"
				+ "		\"dct\": \"http://purl.org/dc/terms/\",\r\n"
				+ "		\"odrl\": \"http://www.w3.org/ns/odrl/2/\",\r\n"
				+ "		\"dspace\": \"https://w3id.org/dspace/v0.8/\"\r\n"
				+ "	} "
				+ "}";
		return JsonDocument.of(new ByteArrayInputStream(json.getBytes()));
	}
	
	private Document createTCContext() throws JsonLdError {
//		String context = "{"
//				+ "\"@context\": {\r\n"
//				+ "		\"dcat\": \"http://www.w3.org/ns/dcat#\",\r\n"
//				+ "		\"dct\": \"http://purl.org/dc/terms/\",\r\n"
//				+ "		\"dspace\": \"https://w3id.org/dspace/2024/1/context.json\",\r\n"
//				+ "		\"odrl\": \"http://www.w3.org/ns/odrl/2/\"\r\n"
//				+ "	}"
//				+ "}";
		String context2 = "{"
				+ "\"@context\": {\r\n"
				+ "		\"dspace\": \"https://w3id.org/dspace/2024/1/context.json\" }"
				+ "}";
		String context3 = "{"
				+ "\"@context\":  \"https://w3id.org/dspace/2024/1/context.json\""
				+ "}";
		return JsonDocument.of(new ByteArrayInputStream(context3.getBytes()));
	}
	
	private String readEDC() throws IOException, URISyntaxException {
//		edc_cat.json
		return Files.readString(Paths.get(getClass().getClassLoader().getResource("edc_arrays.json").toURI()));
	}
}
