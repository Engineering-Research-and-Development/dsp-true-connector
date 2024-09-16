package it.eng.negotiation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serial;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.negotiation.serializer.Serializer;
import jakarta.validation.ValidationException;

public class OfferTest {

	@Test
	public void validOffer() {
		Offer offer = Offer.Builder.newInstance()
				.target(MockObjectUtil.TARGET)
				.assigner(MockObjectUtil.ASSIGNER)
				.build();
		assertNotNull(offer, "Offer should be created with mandatory fields");
		assertNotNull(offer.getId());
	}
	
	@Test
	public void invalidOffer() {
		assertThrows(ValidationException.class, 
				() -> Offer.Builder.newInstance()
					.assignee(MockObjectUtil.ASSIGNEE)
					.build());
	}	
	
	@Test
	public void equalsTrue() {
		String id = UUID.randomUUID().toString();
		Offer offer = Offer.Builder.newInstance()
				.id(id)
				.assigner(MockObjectUtil.ASSIGNER)
				.target(MockObjectUtil.TARGET)
				.build();
		Offer offerB = Offer.Builder.newInstance()
				.id(id)
				.assigner(MockObjectUtil.ASSIGNER)
				.target(MockObjectUtil.TARGET)
				.build();
		assertTrue(offer.equals(offerB));
	}

	@Test
	public void equalsFalse() {
		Offer offer = Offer.Builder.newInstance()
				.target(MockObjectUtil.TARGET)
				.assigner(MockObjectUtil.ASSIGNER)
				.build();
		Offer offerB = Offer.Builder.newInstance()
				.target("SomeDifferentTarget")
				.assigner(MockObjectUtil.ASSIGNER)
				.build();
		assertFalse(offer.equals(offerB));
	}
	
	@Test
	public void equalsTest() {
		Offer offer = MockObjectUtil.OFFER;
		String ss = Serializer.serializePlain(offer);
		Offer offer2 = Serializer.deserializePlain(ss, Offer.class);
		assertThat(offer).usingRecursiveComparison().isEqualTo(offer2);
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		Offer offer = MockObjectUtil.OFFER;
		String ss = Serializer.serializePlain(offer);
		Offer obj = Serializer.deserializePlain(ss, Offer.class);
		assertThat(offer).usingRecursiveComparison().isEqualTo(obj);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		Offer offer = MockObjectUtil.OFFER;
		String ss = Serializer.serializeProtocol(offer);
		Offer obj = Serializer.deserializeProtocol(ss, Offer.class);
		assertThat(offer).usingRecursiveComparison().isEqualTo(obj);
	}
	
	@Test
	public void targetAsObject() {
		String crmStr = "{\r\n"
				+ "	\"@context\": {\r\n"
				+ "		\"dspace\": \"https://w3id.org/dspace/2024/1/context.json\",\r\n"
				+ "		\"dcat\": \"http://www.w3.org/ns/dcat#\",\r\n"
				+ "		\"odrl\": \"http://www.w3.org/ns/odrl/2/\",\r\n"
				+ "		\"dct\": \"http://purl.org/dc/terms/\",\r\n"
				+ "		\"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\"\r\n"
				+ "	},\r\n"
				+ "	\"@type\": \"dspace:ContractRequestMessage\",\r\n"
				+ "	\"dspace:callbackAddress\": \"https://callback.address/callback\",\r\n"
				+ "	\"dspace:consumerPid\": \"urn:uuid:CONSUMER_PID\",\r\n"
				+ "	\"dspace:offer\": {\r\n"
				+ "		\"@type\": \"odrl:Offer\",\r\n"
				+ "		\"@id\": \"urn:uuid:bc99db42-3473-49f8-8583-11f6dca03318\",\r\n"
				+ "		\"odrl:assignee\": \"urn:uuid:ASSIGNEE_CONSUMER\",\r\n"
				+ "		\"odrl:assigner\": \"urn:uuid:ASSIGNER_PROVIDER\",\r\n"
				+ "		\"odrl:permission\": [\r\n"
				+ "			{\r\n"
				+ "				\"odrl:target\": \"urn:uuid:TARGET\",\r\n"
				+ "				\"odrl:action\": \"odrl:use\",\r\n"
				+ "				\"odrl:constraint\": [\r\n"
				+ "					{\r\n"
				+ "						\"odrl:leftOperand\": \"odrl:dateTime\",\r\n"
				+ "						\"odrl:operator\": \"odrl:GT\",\r\n"
				+ "						\"odrl:rightOperand\": \"2024-02-29T00:00:01+01:00\"\r\n"
				+ "					}\r\n"
				+ "				]\r\n"
				+ "			}\r\n"
				+ "		],\r\n"
				+ "		\"odrl:target\": {\r\n"
				+ "            \"@id\": \"urn:uuid:TARGET\"\r\n"
				+ "        }\r\n"
				+ "	},\r\n"
				+ "	\"dspace:providerPid\": \"urn:uuid:PROVIDER_PID\"\r\n"
				+ "}";
		
		String crmStrTargetStr = "{\r\n"
				+ "	\"@context\": {\r\n"
				+ "		\"dspace\": \"https://w3id.org/dspace/2024/1/context.json\",\r\n"
				+ "		\"dcat\": \"http://www.w3.org/ns/dcat#\",\r\n"
				+ "		\"odrl\": \"http://www.w3.org/ns/odrl/2/\",\r\n"
				+ "		\"dct\": \"http://purl.org/dc/terms/\",\r\n"
				+ "		\"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\"\r\n"
				+ "	},\r\n"
				+ "	\"@type\": \"dspace:ContractRequestMessage\",\r\n"
				+ "	\"dspace:callbackAddress\": \"https://callback.address/callback\",\r\n"
				+ "	\"dspace:consumerPid\": \"urn:uuid:CONSUMER_PID\",\r\n"
				+ "	\"dspace:offer\": {\r\n"
				+ "		\"@type\": \"odrl:Offer\",\r\n"
				+ "		\"@id\": \"urn:uuid:bc99db42-3473-49f8-8583-11f6dca03318\",\r\n"
				+ "		\"odrl:assignee\": \"urn:uuid:ASSIGNEE_CONSUMER\",\r\n"
				+ "		\"odrl:assigner\": \"urn:uuid:ASSIGNER_PROVIDER\",\r\n"
				+ "		\"odrl:permission\": [\r\n"
				+ "			{\r\n"
				+ "				\"odrl:target\": \"urn:uuid:TARGET\",\r\n"
				+ "				\"odrl:action\": \"odrl:use\",\r\n"
				+ "				\"odrl:constraint\": [\r\n"
				+ "					{\r\n"
				+ "						\"odrl:leftOperand\": \"odrl:dateTime\",\r\n"
				+ "						\"odrl:operator\": \"odrl:GT\",\r\n"
				+ "						\"odrl:rightOperand\": \"2024-02-29T00:00:01+01:00\"\r\n"
				+ "					}\r\n"
				+ "				]\r\n"
				+ "			}\r\n"
				+ "		],\r\n"
				+ "		\"odrl:target\": \"urn:uuid:TARGET\"\r\n"
				+ "	},\r\n"
				+ "	\"dspace:providerPid\": \"urn:uuid:PROVIDER_PID\"\r\n"
				+ "}";
		
		ContractRequestMessage crm = Serializer.deserializeProtocol(crmStr, ContractRequestMessage.class);
		System.out.println(crm.getOffer().getTarget());
		
		ContractRequestMessage crm2 = Serializer.deserializeProtocol(crmStrTargetStr, ContractRequestMessage.class);
		System.out.println(crm2.getOffer().getTarget());
	}
	
	@Test
	public void crm() {
		Offer OFFER = Offer.Builder.newInstance()
				.target(Reference.Builder.newInstance().id("urn:uuid:TARGET").build())
				.assignee(MockObjectUtil.ASSIGNEE)
				.assigner(MockObjectUtil.ASSIGNER)
				.permission(Arrays.asList(MockObjectUtil.PERMISSION))
				.build();
		
		 ContractRequestMessage CONTRACT_REQUEST_MESSAGE = ContractRequestMessage.Builder.newInstance()
					.callbackAddress(MockObjectUtil.CALLBACK_ADDRESS)
					.consumerPid(MockObjectUtil.CONSUMER_PID)
					.offer(OFFER)
					.build();
		 
		 String ss = Serializer.serializeProtocol(CONTRACT_REQUEST_MESSAGE);
		 System.out.println(ss);
		 
		 ContractRequestMessage crm = Serializer.deserializeProtocol(ss, ContractRequestMessage.class);
		 System.out.println(crm.getOffer().getTarget());
		 
	}
}
